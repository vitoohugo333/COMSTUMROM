package com.customrom.adb

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

enum class ConsoleSequenceDecision {
    ACCEPT,
    WAIT,
    REJECT_STALE
}

data class RemoteConsoleSessionState(
    val sessionId: String,
    val lastDeliveredSequence: Long,
    val lastRequestId: String,
    val lastState: RemoteJobState,
    val updatedAt: Long = System.currentTimeMillis()
)

class RemoteConsoleSessionStore(private val file: File) {
    @Volatile private var unreadable = false

    @Synchronized
    fun evaluate(job: RemoteJob): ConsoleSequenceDecision {
        if (job.sessionId.isBlank()) return ConsoleSequenceDecision.ACCEPT
        if (unreadable) return ConsoleSequenceDecision.WAIT
        val current = get(job.sessionId)
        if (unreadable) return ConsoleSequenceDecision.WAIT
        val last = current?.lastDeliveredSequence ?: 0L
        val expected = last + 1L
        return when {
            job.sequence == expected -> ConsoleSequenceDecision.ACCEPT
            job.sequence > expected -> ConsoleSequenceDecision.WAIT
            else -> ConsoleSequenceDecision.REJECT_STALE
        }
    }

    @Synchronized
    fun get(sessionId: String): RemoteConsoleSessionState? =
        readAll().lastOrNull { it.sessionId == sessionId }

    @Synchronized
    fun markDelivered(sessionId: String, sequence: Long, requestId: String, state: RemoteJobState) {
        if (sessionId.isBlank() || sequence <= 0L) return
        val records = readAll()
        if (unreadable) throw IllegalStateException("Remote console session store is unreadable")
        val current = records.lastOrNull { it.sessionId == sessionId }
        val last = current?.lastDeliveredSequence ?: 0L
        if (sequence <= last) return
        require(sequence == last + 1L) {
            "Cannot deliver session sequence out of order: expected " + (last + 1L) + ", got " + sequence
        }
        val next = RemoteConsoleSessionState(
            sessionId = sessionId,
            lastDeliveredSequence = sequence,
            lastRequestId = requestId,
            lastState = state
        )
        val updated = records.filterNot { it.sessionId == sessionId }.toMutableList()
        updated += next
        writeAll(updated)
    }

    private fun readAll(): List<RemoteConsoleSessionState> {
        if (!file.isFile) {
            unreadable = false
            return emptyList()
        }
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        RemoteConsoleSessionState(
                            sessionId = item.getString("sessionId"),
                            lastDeliveredSequence = item.getLong("lastDeliveredSequence"),
                            lastRequestId = item.getString("lastRequestId"),
                            lastState = RemoteJobState.valueOf(item.getString("lastState")),
                            updatedAt = item.optLong("updatedAt", 0L)
                        )
                    )
                }
            }.also { unreadable = false }
        } catch (_: Throwable) {
            unreadable = true
            emptyList()
        }
    }

    private fun writeAll(records: List<RemoteConsoleSessionState>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("sessionId", record.sessionId)
                    put("lastDeliveredSequence", record.lastDeliveredSequence)
                    put("lastRequestId", record.lastRequestId)
                    put("lastState", record.lastState.name)
                    put("updatedAt", record.updatedAt)
                }
            )
        }
        val parent = file.parentFile ?: file.absoluteFile.parentFile
        val temp = File(parent, file.name + ".tmp")
        temp.writeText(array.toString(), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
        unreadable = false
    }
}
