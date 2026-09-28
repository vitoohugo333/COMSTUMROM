package com.customrom.adb

object RemoteChangeLedgerMapper {
    fun toRecord(
        change: VerifiedRemoteChange,
        sessionId: String,
        at: Long
    ): ChangeRecord? {
        val action = change.job.action
        return when (action) {
            "package.disable", "package.enable", "package.forceStop" -> packageRecord(change, sessionId, at)
            "settings.animations" -> ChangeRecord(
                packageName = "android.settings.animations",
                action = "settings-animations",
                previousState = change.previousState,
                newState = change.currentState,
                at = at,
                sessionId = sessionId,
                exitCode = change.outcome.exitCode,
                rollbackCommand = change.rollbackCommand
            )
            else -> null
        }
    }

    private fun packageRecord(
        change: VerifiedRemoteChange,
        sessionId: String,
        at: Long
    ): ChangeRecord? {
        val packageName = change.job.args["package"]?.trim().orEmpty()
        if (packageName.isEmpty()) return null

        val action = when (change.job.action) {
            "package.disable" -> "disable"
            "package.enable" -> "enable"
            "package.forceStop" -> "force-stop"
            else -> return null
        }

        fun packageState(raw: String, fallback: String): String =
            Regex("state=(enabled|disabled)").find(raw)?.groupValues?.getOrNull(1) ?: fallback

        val previousState = when (action) {
            "disable", "enable" -> packageState(change.previousState, "unknown")
            "force-stop" -> if (change.previousState.isBlank()) "not-running" else "running"
            else -> "unknown"
        }
        val currentState = when (action) {
            "disable", "enable" -> packageState(
                change.currentState,
                if (action == "disable") "disabled" else "enabled"
            )
            "force-stop" -> if (change.currentState.isBlank()) "stopped" else "running"
            else -> "unknown"
        }

        return ChangeRecord(
            packageName = packageName,
            action = action,
            previousState = previousState,
            newState = currentState,
            at = at,
            sessionId = sessionId,
            exitCode = change.outcome.exitCode,
            rollbackCommand = change.rollbackCommand
        )
    }
}
