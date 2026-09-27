package com.customrom.adb

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

object CustomromJobContract {
    const val SCHEMA = "customrom.adb.job.v1"

    private val allowedFields = setOf(
        "schema",
        "requestId",
        "target",
        "mode",
        "action",
        "args",
        "command",
        "timeoutSeconds",
        "allowChanges"
    )

    private val requestIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{2,99}$")
    private val targetPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")

    fun parse(body: String): RemoteJob {
        val root = runCatching { JSONObject(body) }
            .getOrElse { throw IllegalArgumentException("Job body must be one JSON object", it) }

        val unknown = root.keys().asSequence().filterNot(allowedFields::contains).toList()
        require(unknown.isEmpty()) { "Unknown job field(s): ${unknown.sorted().joinToString(",")}" }

        val schema = root.requireString("schema")
        require(schema == SCHEMA) { "Unsupported schema: $schema" }

        val requestId = root.requireString("requestId")
        require(requestIdPattern.matches(requestId)) { "Invalid requestId" }

        val target = root.requireString("target")
        require(targetPattern.matches(target)) { "Invalid target" }

        val mode = when (root.requireString("mode").lowercase()) {
            "action" -> RemoteJobMode.ACTION
            "shell" -> RemoteJobMode.SHELL
            else -> throw IllegalArgumentException("Unsupported mode")
        }

        val timeoutSeconds = if (root.has("timeoutSeconds")) root.getInt("timeoutSeconds") else 60
        require(timeoutSeconds in 1..3600) { "timeoutSeconds must be 1..3600" }

        val allowChanges = if (root.has("allowChanges")) root.getBoolean("allowChanges") else false
        val args = parseArgs(root.opt("args"))

        return when (mode) {
            RemoteJobMode.ACTION -> {
                val action = root.requireString("action")
                require(!root.has("command")) { "Action jobs cannot include command" }
                RemoteJob(
                    schema = schema,
                    requestId = requestId,
                    target = target,
                    mode = mode,
                    action = action,
                    args = args,
                    timeoutSeconds = timeoutSeconds,
                    allowChanges = allowChanges
                )
            }
            RemoteJobMode.SHELL -> {
                val command = root.requireString("command")
                require(!root.has("action")) { "Shell jobs cannot include action" }
                require(args.isEmpty()) { "Shell jobs cannot include args" }
                RemoteJob(
                    schema = schema,
                    requestId = requestId,
                    target = target,
                    mode = mode,
                    command = command,
                    timeoutSeconds = timeoutSeconds,
                    allowChanges = allowChanges
                )
            }
        }
    }

    fun canonicalDigest(job: RemoteJob): String {
        val canonical = buildString {
            field("schema", job.schema)
            field("requestId", job.requestId)
            field("target", job.target)
            field("mode", job.mode.name)
            field("action", job.action)
            job.args.toSortedMap().forEach { (key, value) -> field("arg:$key", value) }
            field("command", job.command)
            field("timeoutSeconds", job.timeoutSeconds.toString())
            field("allowChanges", job.allowChanges.toString())
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun parseArgs(value: Any?): Map<String, String> {
        if (value == null || value == JSONObject.NULL) return emptyMap()
        require(value is JSONObject) { "args must be an object" }
        return value.keys().asSequence().sorted().associateWith { key ->
            when (val item = value.get(key)) {
                is String, is Number, is Boolean -> item.toString()
                JSONObject.NULL -> ""
                is JSONObject, is JSONArray -> throw IllegalArgumentException("args values must be scalar")
                else -> throw IllegalArgumentException("Unsupported args value for $key")
            }
        }
    }

    private fun JSONObject.requireString(name: String): String {
        require(has(name)) { "Missing $name" }
        val value = get(name)
        require(value is String && value.isNotBlank()) { "$name must be a non-empty string" }
        return value
    }

    private fun StringBuilder.field(name: String, value: String) {
        append(name.length).append(':').append(name)
        append('=').append(value.length).append(':').append(value).append('\n')
    }
}
