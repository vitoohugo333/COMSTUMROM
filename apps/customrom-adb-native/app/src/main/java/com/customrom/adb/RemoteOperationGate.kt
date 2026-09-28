package com.customrom.adb

class RemoteOperationGate {
    @Volatile
    private var remoteActive = false

    fun onRemoteState(state: RemoteJobState) {
        remoteActive = state == RemoteJobState.CLAIMED || state == RemoteJobState.RUNNING
    }

    fun canStartLocal(): Boolean = !remoteActive
}
