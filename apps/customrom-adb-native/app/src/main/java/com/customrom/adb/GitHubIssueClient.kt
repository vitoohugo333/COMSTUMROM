package com.customrom.adb

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class GitHubControlConfig(
    val owner: String,
    val repo: String,
    val allowedAuthor: String,
    val target: String,
    val titlePrefix: String,
    val pollSeconds: Int = 10,
    val enabled: Boolean = false
) {
    init {
        require(SEGMENT.matches(owner)) { "Invalid GitHub owner" }
        require(SEGMENT.matches(repo)) { "Invalid GitHub repository" }
        require(SEGMENT.matches(allowedAuthor)) { "Invalid allowed author" }
        require(target.isNotBlank()) { "Target is required" }
        require(titlePrefix.isNotBlank()) { "Title prefix is required" }
        require(pollSeconds in 5..300) { "pollSeconds must be 5..300" }
    }

    companion object {
        private val SEGMENT = Regex("^[A-Za-z0-9_.-]+$")

        fun defaults(): GitHubControlConfig = GitHubControlConfig(
            owner = "viluadmcontas2-dot",
            repo = "AgentRed",
            allowedAuthor = "viluadmcontas2-dot",
            target = "taytech-primary",
            titlePrefix = "[CUSTOMROM JOB]",
            pollSeconds = 10,
            enabled = false
        )
    }
}

data class RemoteGitHubIssue(
    val number: Long,
    val title: String,
    val body: String,
    val authorLogin: String,
    val htmlUrl: String
)

class GitHubApiException(val statusCode: Int, message: String) : RuntimeException(message)

class GitHubIssueClient(
    private val config: GitHubControlConfig,
    private val tokenProvider: () -> String?,
    private val apiBase: String = "https://api.github.com"
) {
    fun listOpenJobs(): List<RemoteGitHubIssue> {
        val response = request(
            method = "GET",
            path = "/repos/${config.owner}/${config.repo}/issues?state=open&per_page=50"
        )
        return parseIssues(response, config.titlePrefix)
    }

    fun comment(issueNumber: Long, body: String) {
        require(issueNumber > 0) { "Invalid issue number" }
        request(
            method = "POST",
            path = "/repos/${config.owner}/${config.repo}/issues/$issueNumber/comments",
            body = JSONObject().put("body", body).toString()
        )
    }

    fun close(issueNumber: Long) {
        require(issueNumber > 0) { "Invalid issue number" }
        request(
            method = "PATCH",
            path = "/repos/${config.owner}/${config.repo}/issues/$issueNumber",
            body = JSONObject().put("state", "closed").toString()
        )
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val token = tokenProvider()?.trim().orEmpty()
        require(token.isNotEmpty()) { "GitHub control is not authenticated" }
        require(path.startsWith("/repos/")) { "Unexpected GitHub API path" }

        val connection = URL(apiBase.trimEnd('/') + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "CUSTOMROM-ADB-S23")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.useCaches = false

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_REQUEST_BYTES) { "GitHub request body too large" }
                connection.outputStream.use { it.write(bytes) }
            }

            val status = connection.responseCode
            val response = readBounded(
                if (status in 200..299) connection.inputStream else connection.errorStream,
                MAX_RESPONSE_BYTES
            )
            if (status !in 200..299) {
                throw GitHubApiException(status, "GitHub API returned HTTP $status: ${response.take(512)}")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: InputStream?, maxBytes: Int): String {
        if (stream == null) return ""
        return stream.use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "GitHub response exceeded limit" }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REQUEST_BYTES = 64 * 1024
        private const val MAX_RESPONSE_BYTES = 1024 * 1024

        fun parseIssues(json: String, titlePrefix: String): List<RemoteGitHubIssue> {
            require(titlePrefix.isNotBlank()) { "Title prefix is required" }
            val array = JSONArray(json)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    if (item.has("pull_request")) continue
                    val title = item.optString("title", "")
                    if (!title.startsWith(titlePrefix)) continue
                    val bodyValue = item.opt("body")
                    if (bodyValue == null || bodyValue == JSONObject.NULL) continue
                    val body = bodyValue.toString().trim()
                    if (body.isEmpty()) continue
                    val number = item.optLong("number", 0L)
                    if (number <= 0L) continue
                    val author = item.optJSONObject("user")?.optString("login", "").orEmpty()
                    if (author.isBlank()) continue
                    add(
                        RemoteGitHubIssue(
                            number = number,
                            title = title,
                            body = body,
                            authorLogin = author,
                            htmlUrl = item.optString("html_url", "")
                        )
                    )
                }
            }
        }
    }
}
