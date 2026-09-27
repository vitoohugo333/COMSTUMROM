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
    private val onState: (RemoteJobState, String) -> Unit = { _, _ -> }
) {
    private val activeRequestIds = mutableSetOf<String>()

    @Synchronized
    fun handle(issue: RemoteGitHubIssue): RemoteHandleResult {
        val admission = RemoteAdmissionPolicy.evaluate(issue, config)
        if (admission is RemoteAdmissionDecision.Rejected) {
            runCatching { publisher.comment(issue.number, rejectedReceipt("unknown", admission.reason)) }
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
                        if (stored.state != RemoteJobState.UNCERTAIN) publisher.close(issue.number)
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
            return failBeforeExecution(issue, job, digest, operation, "ADB indisponível")
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

    private fun executeMain(issue: RemoteGitHubIssue, job: RemoteJob, operation: ResolvedRemoteOperation, previousState: String) {
        store.markRunning(job.requestId)
        onState(RemoteJobState.RUNNING, operation.title)
        val accepted = commandPort.execute(operation.command, job.timeoutSeconds * 1000L) { outcome ->
            if (outcome.transportError != null && operation.effectful) {
                val receipt = RemoteReceiptFormatter.format(receiptData(job, operation, outcome, RemoteJobState.UNCERTAIN, previousState))
                store.markUncertain(job.requestId, receipt)
                activeRequestIds -= job.requestId
                onState(RemoteJobState.UNCERTAIN, operation.title)
                runCatching { publisher.comment(issue.number, receipt) }
            } else {
                val state = if (outcome.transportError == null && outcome.exitCode == 0) RemoteJobState.COMPLETED else RemoteJobState.FAILED
                finishTerminal(issue, job, operation, outcome, state, previousState)
            }
        }
        if (!accepted) {
            val synthetic = RemoteShellOutcome("", "", -1, 0, IllegalStateException("ADB recusou a execução antes de iniciar"))
            if (operation.effectful) {
                val receipt = RemoteReceiptFormatter.format(receiptData(job, operation, synthetic, RemoteJobState.UNCERTAIN, previousState))
                store.markUncertain(job.requestId, receipt)
                activeRequestIds -= job.requestId
                runCatching { publisher.comment(issue.number, receipt) }
            } else {
                finishTerminal(issue, job, operation, synthetic, RemoteJobState.FAILED, previousState)
            }
        }
    }

    private fun finishTerminal(
        issue: RemoteGitHubIssue,
        job: RemoteJob,
        operation: ResolvedRemoteOperation,
        outcome: RemoteShellOutcome,
        state: RemoteJobState,
        previousState: String = ""
    ) {
        val receipt = RemoteReceiptFormatter.format(receiptData(job, operation, outcome, state, previousState))
        store.markTerminal(job.requestId, state, receipt)
        activeRequestIds -= job.requestId
        onState(state, operation.title)
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
        previousState: String = ""
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
        rollbackCommand = operation.rollbackCommand
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
