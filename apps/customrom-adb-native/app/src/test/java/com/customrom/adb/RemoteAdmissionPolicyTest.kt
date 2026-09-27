package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAdmissionPolicyTest {
    private val config = GitHubControlConfig.defaults().copy(enabled = true)

    @Test
    fun acceptsExactOwnerPrefixSchemaAndTarget() {
        val result = RemoteAdmissionPolicy.evaluate(issue(), config)
        assertTrue(result is RemoteAdmissionDecision.Accepted)
        val accepted = result as RemoteAdmissionDecision.Accepted
        assertEquals("cr-20260927-0300", accepted.job.requestId)
        assertEquals("taytech-primary", accepted.job.target)
    }

    @Test
    fun rejectsWrongAuthor() {
        val result = RemoteAdmissionPolicy.evaluate(issue(author = "someone-else"), config)
        assertTrue(result is RemoteAdmissionDecision.Rejected)
        assertTrue((result as RemoteAdmissionDecision.Rejected).reason.contains("autor", ignoreCase = true))
    }

    @Test
    fun rejectsWrongTarget() {
        val result = RemoteAdmissionPolicy.evaluate(issue(target = "other-device"), config)
        assertTrue(result is RemoteAdmissionDecision.Rejected)
        assertTrue((result as RemoteAdmissionDecision.Rejected).reason.contains("alvo", ignoreCase = true))
    }

    @Test
    fun rejectsUnrelatedTitlePrefix() {
        val result = RemoteAdmissionPolicy.evaluate(issue(title = "Outra fila"), config)
        assertTrue(result is RemoteAdmissionDecision.Rejected)
    }

    @Test
    fun rejectsMalformedBodyWithoutThrowingToReceiver() {
        val result = RemoteAdmissionPolicy.evaluate(issue(body = "not-json"), config)
        assertTrue(result is RemoteAdmissionDecision.Rejected)
    }

    private fun issue(
        author: String = "viluadmcontas2-dot",
        target: String = "taytech-primary",
        title: String = "[CUSTOMROM JOB] Teste",
        body: String? = null
    ): RemoteGitHubIssue {
        val payload = body ?: """
            {
              "schema":"customrom.adb.job.v1",
              "requestId":"cr-20260927-0300",
              "target":"$target",
              "mode":"shell",
              "command":"getprop ro.product.model",
              "timeoutSeconds":30,
              "allowChanges":false
            }
        """.trimIndent()
        return RemoteGitHubIssue(
            number = 71,
            title = title,
            body = payload,
            authorLogin = author,
            htmlUrl = "https://github.com/viluadmcontas2-dot/AgentRed/issues/71"
        )
    }
}
