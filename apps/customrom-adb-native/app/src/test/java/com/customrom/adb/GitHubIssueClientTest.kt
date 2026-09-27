package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubIssueClientTest {
    @Test
    fun parsesOnlyCustomromIssuesAndExtractsAuthor() {
        val json = """
            [
              {
                "number": 41,
                "title": "[CUSTOMROM JOB] Diagnosticar memória",
                "body": "{\"schema\":\"customrom.adb.job.v1\"}",
                "html_url": "https://github.com/viluadmcontas2-dot/AgentRed/issues/41",
                "user": {"login": "viluadmcontas2-dot"}
              },
              {
                "number": 42,
                "title": "Assunto comum",
                "body": "ignore",
                "html_url": "https://github.com/viluadmcontas2-dot/AgentRed/issues/42",
                "user": {"login": "viluadmcontas2-dot"}
              }
            ]
        """.trimIndent()

        val issues = GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]")

        assertEquals(1, issues.size)
        assertEquals(41L, issues.single().number)
        assertEquals("viluadmcontas2-dot", issues.single().authorLogin)
        assertTrue(issues.single().body.contains("customrom.adb.job.v1"))
    }

    @Test
    fun ignoresPullRequestsReturnedByIssuesApi() {
        val json = """
            [
              {
                "number": 50,
                "title": "[CUSTOMROM JOB] Isto é PR",
                "body": "payload",
                "html_url": "https://github.com/x/y/pull/50",
                "user": {"login": "viluadmcontas2-dot"},
                "pull_request": {"url": "https://api.github.com/repos/x/y/pulls/50"}
              }
            ]
        """.trimIndent()

        assertTrue(GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]").isEmpty())
    }

    @Test
    fun ignoresJobWithoutBody() {
        val json = """
            [
              {
                "number": 51,
                "title": "[CUSTOMROM JOB] Sem payload",
                "body": null,
                "html_url": "https://github.com/x/y/issues/51",
                "user": {"login": "viluadmcontas2-dot"}
              }
            ]
        """.trimIndent()

        assertTrue(GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]").isEmpty())
    }

    @Test
    fun titlePrefixMustBeExactAtStart() {
        val json = """
            [
              {
                "number": 52,
                "title": "prefixo [CUSTOMROM JOB] no meio",
                "body": "{}",
                "html_url": "https://github.com/x/y/issues/52",
                "user": {"login": "viluadmcontas2-dot"}
              }
            ]
        """.trimIndent()

        assertTrue(GitHubIssueClient.parseIssues(json, "[CUSTOMROM JOB]").isEmpty())
    }

    @Test
    fun defaultControlConfigIsPrivateAgentRedNamespace() {
        val config = GitHubControlConfig.defaults()

        assertEquals("viluadmcontas2-dot", config.owner)
        assertEquals("AgentRed", config.repo)
        assertEquals("[CUSTOMROM JOB]", config.titlePrefix)
        assertEquals("taytech-primary", config.target)
        assertEquals("viluadmcontas2-dot", config.allowedAuthor)
    }
}
