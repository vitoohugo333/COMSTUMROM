package com.customrom.adb

data class RemoteReceiptData(
    val requestId: String,
    val title: String,
    val state: RemoteJobState,
    val risk: String,
    val durationMs: Long,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val transportError: String = "",
    val previousState: String = "",
    val currentState: String = "",
    val rollbackCommand: String = ""
)

object RemoteReceiptFormatter {
    fun format(data: RemoteReceiptData, maxTechnicalChars: Int = 6000): String {
        require(maxTechnicalChars >= 128) { "maxTechnicalChars too small" }

        val humanState = when (data.state) {
            RemoteJobState.COMPLETED -> "CONCLUÍDO"
            RemoteJobState.FAILED -> "FALHOU"
            RemoteJobState.REJECTED -> "REJEITADO"
            RemoteJobState.UNCERTAIN -> "INCERTO"
            RemoteJobState.RUNNING -> "EXECUTANDO"
            RemoteJobState.CLAIMED -> "PREPARANDO"
            RemoteJobState.RECEIVED -> "RECEBIDO"
        }
        val icon = when (data.state) {
            RemoteJobState.COMPLETED -> "✅"
            RemoteJobState.REJECTED -> "⛔"
            RemoteJobState.UNCERTAIN -> "⚠️"
            RemoteJobState.FAILED -> "❌"
            else -> "●"
        }

        val transport = data.transportError.trim()
        val summary = when {
            data.state == RemoteJobState.REJECTED ->
                "A política local do CUSTOMROM bloqueou a operação."
            data.state == RemoteJobState.UNCERTAIN ->
                "A execução foi interrompida sem prova suficiente do estado final. O efeito não será repetido automaticamente."
            transport.isNotEmpty() -> "Falha de transporte: ${redact(transport)}"
            data.state == RemoteJobState.COMPLETED && data.exitCode == 0 ->
                "A TayTech respondeu e a operação terminou."
            data.exitCode != 0 ->
                "A TayTech respondeu, mas o comando não foi concluído."
            else -> "Estado remoto atualizado."
        }

        val technical = buildString {
            append("requestId: ").append(data.requestId).append('\n')
            append("exitCode: ").append(data.exitCode).append('\n')
            append("transportError: ").append(if (transport.isEmpty()) "none" else transport).append('\n')
            if (data.stdout.isNotBlank()) {
                append("\nstdout:\n").append(data.stdout.trim())
            }
            if (data.stderr.isNotBlank()) {
                append("\n\nstderr:\n").append(data.stderr.trim())
            }
        }.let(::redact).let { bound(it, maxTechnicalChars) }

        return buildString {
            append(icon).append(' ').append(data.title).append('\n').append('\n')
            append(summary).append('\n').append('\n')
            append("Estado: ").append(humanState).append('\n')
            append("Risco: ").append(data.risk).append('\n')
            append("Duração: ").append(formatDuration(data.durationMs)).append('\n')
            if (data.previousState.isNotBlank()) {
                append("Estado anterior: ").append(redact(data.previousState)).append('\n')
            }
            if (data.currentState.isNotBlank()) {
                append("Estado atual: ").append(redact(data.currentState)).append('\n')
            }
            if (data.rollbackCommand.isNotBlank()) {
                append("Rollback: disponível").append('\n')
                append("Comando de rollback: ").append(redact(data.rollbackCommand)).append('\n')
            }
            append('\n').append("Detalhes técnicos").append('\n')
            append(technical)
        }
    }

    private fun formatDuration(durationMs: Long): String =
        if (durationMs >= 1000L) {
            String.format(java.util.Locale.US, "%.1f s", durationMs / 1000.0)
        } else {
            "${durationMs.coerceAtLeast(0L)} ms"
        }

    private fun bound(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        val marker = "\n… [TRUNCADO]"
        val keep = (maxChars - marker.length).coerceAtLeast(0)
        return value.take(keep) + marker
    }

    private fun redact(value: String): String {
        var safe = value
        safe = AUTHORIZATION.replace(safe) { match ->
            match.groupValues[1] + "[REDACTED]"
        }
        safe = GITHUB_TOKEN.replace(safe, "[REDACTED]")
        safe = QUERY_TOKEN.replace(safe) { match ->
            match.groupValues[1] + "[REDACTED]"
        }
        return safe
    }

    private val AUTHORIZATION = Regex(
        "(?i)(authorization\\s*:\\s*(?:bearer|token)\\s+)[^\\s]+"
    )
    private val GITHUB_TOKEN = Regex(
        "(?i)\\b(?:github_pat|ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{8,}\\b"
    )
    private val QUERY_TOKEN = Regex(
        "(?i)((?:access[_-]?token|token)\\s*[=:]\\s*)[^\\s&]+"
    )
}
