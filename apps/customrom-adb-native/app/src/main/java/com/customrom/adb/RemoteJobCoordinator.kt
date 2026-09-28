package com.customrom.adb

interface RemoteCommandPort {
    fun isAvailable(): Boolean
    fun execute(command: String, timeoutMs: Long, callback: (RemoteShellOutcome) -> Unit): Boolean
}

interface RemoteReceiptPublisher {
    fun comment(issueNumber: Long, body: String)
    fun close(issueNumber: Long)
}

enum class RemoteHandleResult { STARTED, REJECTED, REPLAYED, BUSY }

class RemoteJobCoordinator(
    private val config: GitHubControlConfig,
    private val registry: RemoteOperationRegistry,
    private val store: IssueJobStore,
    private val commandPort: RemoteCommandPort,
    private val publisher: RemoteReceiptPublisher,
    private val onState: (RemoteJobState, String) -> Unit = { _, _ -> },
    private val onVerifiedChange: (VerifiedRemoteChange) -> Unit = {}
) : RemoteIssueHandler {
    private val activeRequestIds = mutableSetOf<String>()

    @Synchronized
    override fun handle(issue: RemoteGitHubIssue): RemoteHandleResult {
        val admission = RemoteAdmissionPolicy.evaluate(issue, config)
        if (admission is RemoteAdmissionDecision.Rejected) {
            val receipt = rejectedReceipt("unknown", admission.reason)
            runCatching {
            publisher.comment(issue.number, receipt)
            publisher.close(issue.number)
        }
            runCatching { publisher.close(issue.number) }
            return RemoteHandleResult.REJECTED
        }

        val job = (admission as RemoteAdmissionDecision.Accepted).job
        val digest = CustomromJobContract.canonicalDigest(job)
        if (activeRequestIds.contains(job.requestId)) return RemoteHandleResult.BUSY

        when (store.check(job.requestId, digest)) {
            ReplayDecision.CONFLICT -> return reject(issue, job, digest, "requestId reutilizado com contrato diferente")
            ReplayDecision.REUSE_TERMINAL, ReplayDecision.UNCERTAIN -> {
                val stored = store.get(job.requestId)
                if (stored != null && stored.receipt.isNotBlank()) {
                    runCatching {
                        publisher.comment(issue.number, stored.receipt)
                        publisher.close(issue.number)
                    }
                }
                return RemoteHandleResult.REPLAYED
            }
            ReplayDecision.IN_PROGRESS -> return RemoteHandleResult.BUSY
            ReplayDecision.NEW -> Unit
        }

        val operation = try {
            registry.resolve(job)
        } catch (t: Throwable) {
            return reject(issue, job, digest, t.message ?: "Operação inválida")
        }

        if (operation.risk == "VERMELHO") {
            return reject(issue, job, digest, "Operação VERMELHA bloqueada pela política local")
        }
        if (operation.requiresAllowChanges && !job.allowChanges) {
            return reject(issue, job, digest, "A operação altera estado e exige allowChanges=true")
        }
        if (!commandPort.isAvailable()) {
            return RemoteHandleResult.BUSY
        }

        store.markClaimed(job.requestId, digest, operation.effectful)
        activeRequestIds += job.requestId
        onState(RemoteJobState.CLAIMED, operation.title)

        if (operation.preflightCommand.isNotBlank()) {
            val accepted = commandPort.execute(operation.preflightCommand, job.timeoutSeconds * 1000L) { preflight ->
                if (preflight.transportError != null || preflight.exitCode != 0) {
                    finishTerminal(issue, job, operation, preflight, RemoteJobState.FAILED)
                } else {
                    executeMain(issue, job, operation, preflight.stdout.trim())
                }
            }
            if (!accepted) {
                activeRequestIds -= job.requestId
                return failBeforeExecution(issue, job, digest, operation, "ADB ocupado ou indisponível")
            }
            return RemoteHandleResult.STARTED
        }

        executeMain(issue, job, operation, "")
        return RemoteHandleResult.STARTED
    }

    private fun executeMain(
        issue: RemoteGitHubIssue,
        job: RemoteJob,
        operation: ResolvedRemoteOperation,
        previousState: String
    ) {
        store.markRunning(job.requestId)
        onState(RemoteJobState.RUNNING, operation.title)
        val accepted = commandPort.execute(operation.command, job.timeoutSeconds * 1000L) { outcome ->
            when {
                outcome.transportError != null && operation.effectful -> {
                    finishUncertain(issue, job, operation, outcome, previousState)
                }
                outcome.exitCode != 0 && operation.effectful -> {
                    finishUncertain(issue, job, operation, outcome, previousState)
                }
                outcome.transportError != null || outcome.exitCode != 0 -> {
                    finishTerminal(issue, job, operation, outcome, RemoteJobState.FAILED, previousState)
                }
                operation.verificationCommand.isNotBlank() -> {
                    executeVerification(issue, job, operation, outcome, previousState)
                }
                else -> {
                    finishTerminal(issue, job, operation, outcome, RemoteJobState.COMPLETED, previousState)
                }
            }
        }
        if (!accepted) {
            val synthetic = RemoteShellOutcome("", "", -1, 0, IllegalStateException("ADB recusou a execução antes de iniciar"))
            if (operation.effectful) {
                finishUncertain(issue, job, operation, synthetic, previousState)
            } else {
                finishTerminal(issue, job, operation, synthetic, RemoteJobState.FAILED, previousState)
            }
        }
    }

    private fun executeVerification(
        issue: RemoteGitHubIssue,
        job: RemoteJob,
        operation: ResolvedRemoteOperation,
        mainOutcome: RemoteShellOutcome,
        previousState: String
    ) {
        val accepted = commandPort.execute(operation.verificationCommand, job.timeoutSeconds * 1000L) { verification ->
            val semanticVerified = operation.verificationSatisfied(verification.stdout)
            if (verification.transportError != null || verification.exitCode != 0 || !semanticVerified) {
                val semanticMessage =
                    if (!semanticVerified && verification.transportError == null && verification.exitCode == 0) {
                        "Verificação pós-alteração não confirmou o estado esperado"
                    } else {
                        ""
                    }
                val failure = RemoteShellOutcome(
                    stdout = mainOutcome.stdout,
                    stderr = listOf(mainOutcome.stderr, verification.stderr, semanticMessage).filter { it.isNotBlank() }.joinToString("\n"),
                    exitCode = if (!semanticVerified && verification.exitCode == 0) -2 else verification.exitCode,
                    durationMs = mainOutcome.durationMs + verification.durationMs,
                    transportError = verification.transportError
                )
                if (operation.effectful) {
                    finishUncertain(
                        issue = issue,
                        job = job,
                        operation = operation,
                        outcome = failure,
                        previousState = previousState,
                        currentState = verification.stdout.trim()
                    )
                } else {
                    finishTerminal(
                        issue = issue,
                        job = job,
                        operation = operation,
                        outcome = failure,
                        state = RemoteJobState.FAILED,
                        previousState = previousState,
                        currentState = verification.stdout.trim()
                    )
                }
            } else {
                val combined = mainOutcome.copy(durationMs = mainOutcome.durationMs + verification.durationMs)
                finishTerminal(
                    issue = issue,
                    job = job,
                    operation = operation,
                    outcome = combined,
                    state = RemoteJobState.COMPLETED,
                    previousState = previousState,
                    currentState = verification.stdout.trim()
                )
            }
        }
        if (!accepted) {
            val synthetic = RemoteShellOutcome(
                stdout = mainOutcome.stdout,
                stderr = mainOutcome.stderr,
                exitCode = -1,
                durationMs = mainOutcome.durationMs,
                transportError = IllegalStateException("ADB indisponível durante a verificação pós-alteração")
            )
            if (operation.effectful) {
                finishUncertain(issue, job, operation, synthetic, previousState)
            } else {
                finishTerminal(issue, job, operation, synthetic, RemoteJobState.FAILED, previousState)
            }
        }
    }

    private fun finishUncertain(
        issue: RemoteGitHubIssue,
        job: RemoteJob,
        operation: ResolvedRemoteOperation,
        outcome: RemoteShellOutcome,
        previousState: String,
        currentState: String = ""
    ) {
        val receipt = RemoteReceiptFormatter.format(
            receiptData(job, operation, outcome, RemoteJobState.UNCERTAIN, previousState, currentState)
        )
        store.markUncertain(job.requestId, receipt)
        activeRequestIds -= job.requestId
        onState(RemoteJobState.UNCERTAIN, operation.title)
        runCatching { publisher.comment(issue.number, receipt) }
    }

    private fun finishTerminal(
        issue: RemoteGitHubIssue,
        job: RemoteJob,
        operation: ResolvedRemoteOperation,
        outcome: RemoteShellOutcome,
        state: RemoteJobState,
        previousState: String = "",
        currentState: String = ""
    ) {
        val receipt = RemoteReceiptFormatter.format(
            receiptData(job, operation, outcome, state, previousState, currentState)
        )
        store.markTerminal(job.requestId, state, receipt)
        activeRequestIds -= job.requestId
        onState(state, operation.title)
        if (
            state == RemoteJobState.COMPLETED &&
            operation.effectful &&
            operation.verificationCommand.isNotBlank()
        ) {
            runCatching {
                onVerifiedChange(
                    VerifiedRemoteChange(
                        job = job,
                        operation = operation,
                        previousState = previousState,
                        currentState = currentState,
                        outcome = outcome
                    )
                )
            }
        }
        runCatching {
            publisher.comment(issue.number, receipt)
            publisher.close(issue.number)
        }
    }

    private fun reject(issue: RemoteGitHubIssue, job: RemoteJob, digest: String, reason: String): RemoteHandleResult {
        store.markClaimed(job.requestId, digest, effectful = false)
        val receipt = rejectedReceipt(job.requestId, reason)
        store.markTerminal(job.requestId, RemoteJobState.REJECTED, receipt)
        onState(RemoteJobState.REJECTED, reason)
        runCatching {
            publisher.comment(issue.number, receipt)
            publisher.close(issue.number)
        }
        return RemoteHandleResult.REJECTED
    }

    private fun failBeforeExecution(
        issue: RemoteGitHubIssue,
        job: RemoteJob,
        digest: String,
        operation: ResolvedRemoteOperation,
        reason: String
    ): RemoteHandleResult {
        store.markClaimed(job.requestId, digest, operation.effectful)
        val outcome = RemoteShellOutcome("", "", -1, 0, IllegalStateException(reason))
        finishTerminal(issue, job, operation, outcome, RemoteJobState.FAILED)
        return RemoteHandleResult.REJECTED
    }

    private fun receiptData(
        job: RemoteJob,
        operation: ResolvedRemoteOperation,
        outcome: RemoteShellOutcome,
        state: RemoteJobState,
        previousState: String = "",
        currentState: String = ""
    ): RemoteReceiptData = RemoteReceiptData(
        requestId = job.requestId,
        title = operation.title,
        state = state,
        risk = operation.risk,
        durationMs = outcome.durationMs,
        stdout = outcome.stdout,
        stderr = outcome.stderr,
        exitCode = outcome.exitCode,
        transportError = outcome.transportError?.message.orEmpty(),
        previousState = previousState,
        currentState = currentState,
        rollbackCommand = operation.rollbackCommandFor(previousState)
    )

    private fun rejectedReceipt(requestId: String, reason: String): String = RemoteReceiptFormatter.format(
        RemoteReceiptData(
            requestId = requestId,
            title = "Operação remota rejeitada",
            state = RemoteJobState.REJECTED,
            risk = "VERMELHO",
            durationMs = 0,
            stdout = "",
            stderr = reason,
            exitCode = -1
        )
    )
}
