package com.customrom.adb

enum class RemoteJobMode {
    ACTION,
    SHELL
}

data class RemoteJob(
    val schema: String,
    val requestId: String,
    val target: String,
    val mode: RemoteJobMode,
    val action: String = "",
    val args: Map<String, String> = emptyMap(),
    val command: String = "",
    val timeoutSeconds: Int = 60,
    val allowChanges: Boolean = false
)

enum class RemoteJobState {
    RECEIVED,
    CLAIMED,
    RUNNING,
    COMPLETED,
    FAILED,
    REJECTED,
    UNCERTAIN
}

enum class ReplayDecision {
    NEW,
    IN_PROGRESS,
    REUSE_TERMINAL,
    CONFLICT,
    UNCERTAIN
}

data class StoredRemoteJob(
    val requestId: String,
    val digest: String,
    val state: RemoteJobState,
    val effectful: Boolean,
    val receipt: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)


data class VerifiedRemoteChange(
    val job: RemoteJob,
    val operation: ResolvedRemoteOperation,
    val previousState: String,
    val currentState: String,
    val outcome: RemoteShellOutcome
)
