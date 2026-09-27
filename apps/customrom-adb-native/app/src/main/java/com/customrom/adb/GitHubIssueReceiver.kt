package com.customrom.adb

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface RemoteIssueSource {
    fun listOpenJobs(): List<RemoteGitHubIssue>
}

interface RemoteIssueHandler {
    fun handle(issue: RemoteGitHubIssue): RemoteHandleResult
}

class GitHubIssueReceiver(
    private val source: RemoteIssueSource,
    private val handler: RemoteIssueHandler,
    private val pollSeconds: Int,
    private val onState: (String) -> Unit = {}
) : AutoCloseable {
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "customrom-github-issues").apply { isDaemon = true }
        }
    private val polling = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    init {
        require(pollSeconds in 5..300) { "pollSeconds must be 5..300" }
    }

    fun start() {
        if (closed.get()) return
        scheduler.scheduleWithFixedDelay(
            { runCatching { pollOnce() }.onFailure { onState("Remoto · falha: " + shortMessage(it)) } },
            0L,
            pollSeconds.toLong(),
            TimeUnit.SECONDS
        )
    }

    fun pollOnce(): Int {
        if (closed.get() || !polling.compareAndSet(false, true)) return 0
        return try {
            onState("Remoto · verificando")
            val issues = source.listOpenJobs()
            issues.forEach { issue ->
                runCatching { handler.handle(issue) }
                    .onFailure { onState("Remoto · Issue #${issue.number} falhou: " + shortMessage(it)) }
            }
            onState("Remoto · ${issues.size} pedido(s)")
            issues.size
        } finally {
            polling.set(false)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scheduler.shutdownNow()
        onState("Remoto · parado")
    }

    private fun shortMessage(error: Throwable): String =
        error.message?.take(160)?.ifBlank { null } ?: error::class.java.simpleName
}
