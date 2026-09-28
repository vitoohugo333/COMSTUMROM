package com.customrom.adb

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class IssueJobStore(private val file: File) {
    @Volatile private var unreadable = false

    init {
        recoverInterrupted()
    }

    @Synchronized
    fun check(requestId: String, digest: String): ReplayDecision {
        if (unreadable) return ReplayDecision.UNCERTAIN
        val current = get(requestId)
        if (unreadable) return ReplayDecision.UNCERTAIN
        if (current == null) return ReplayDecision.NEW
        if (current.digest != digest) return ReplayDecision.CONFLICT
        return when (current.state) {
            RemoteJobState.COMPLETED,
            RemoteJobState.FAILED,
            RemoteJobState.REJECTED -> ReplayDecision.REUSE_TERMINAL
            RemoteJobState.UNCERTAIN -> ReplayDecision.UNCERTAIN
            RemoteJobState.CLAIMED,
            RemoteJobState.RECEIVED -> ReplayDecision.NEW
            else -> ReplayDecision.IN_PROGRESS
        }
    }

    @Synchronized
    fun get(requestId: String): StoredRemoteJob? =
        readAll().lastOrNull { it.requestId == requestId }

    @Synchronized
    fun markClaimed(requestId: String, digest: String, effectful: Boolean) {
        upsert(
            StoredRemoteJob(
                requestId = requestId,
                digest = digest,
                state = RemoteJobState.CLAIMED,
                effectful = effectful
            )
        )
    }

    @Synchronized
    fun markRunning(requestId: String) {
        transition(requestId, RemoteJobState.RUNNING)
    }

    @Synchronized
    fun markTerminal(requestId: String, state: RemoteJobState, receipt: String) {
        require(state in setOf(RemoteJobState.COMPLETED, RemoteJobState.FAILED, RemoteJobState.REJECTED)) {
            "Terminal state required"
        }
        transition(requestId, state, receipt)
    }

    @Synchronized
    fun markUncertain(requestId: String, receipt: String = "") {
        transition(requestId, RemoteJobState.UNCERTAIN, receipt)
    }

    @Synchronized
    fun markReceiptPublished(requestId: String) {
        val current = readAll().lastOrNull { it.requestId == requestId }
            ?: throw IllegalStateException("Unknown requestId: $requestId")
        if (!current.receiptPublished) {
            upsert(current.copy(receiptPublished = true, updatedAt = System.currentTimeMillis()))
        }
    }

    @Synchronized
    fun list(): List<StoredRemoteJob> = readAll()

    private fun transition(requestId: String, state: RemoteJobState, receipt: String? = null) {
        val current = readAll().lastOrNull { it.requestId == requestId }
            ?: throw IllegalStateException("Unknown requestId: $requestId")
        upsert(
            current.copy(
                state = state,
                receipt = receipt ?: current.receipt,
                receiptPublished = if (receipt != null && receipt != current.receipt) false else current.receiptPublished,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun recoverInterrupted() {
        val records = readAll()
        if (records.none { it.state == RemoteJobState.RUNNING }) return
        writeAll(
            records.map {
                if (it.state == RemoteJobState.RUNNING) {
                    it.copy(state = RemoteJobState.UNCERTAIN, updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        )
    }

    private fun upsert(record: StoredRemoteJob) {
        check(!unreadable) { "Remote job store is unreadable; refusing to overwrite replay evidence" }
        val records = readAll().filterNot { it.requestId == record.requestId }.toMutableList()
        check(!unreadable) { "Remote job store is unreadable; refusing to overwrite replay evidence" }
        records += record
        writeAll(records)
    }

    private fun readAll(): List<StoredRemoteJob> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredRemoteJob(
                            requestId = item.getString("requestId"),
                            digest = item.getString("digest"),
                            state = RemoteJobState.valueOf(item.getString("state")),
                            effectful = item.optBoolean("effectful", false),
                            receipt = item.optString("receipt", ""),
                            receiptPublished = item.optBoolean("receiptPublished", false),
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            unreadable = true
            emptyList()
        }
    }

    private fun writeAll(records: List<StoredRemoteJob>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("requestId", record.requestId)
                    put("digest", record.digest)
                    put("state", record.state.name)
                    put("effectful", record.effectful)
                    put("receipt", record.receipt)
                    put("receiptPublished", record.receiptPublished)
                    put("updatedAt", record.updatedAt)
                }
            )
        }
        val parent = file.parentFile ?: file.absoluteFile.parentFile
        val temp = File(parent, "${file.name}.tmp")
        temp.writeText(array.toString(), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }
}
