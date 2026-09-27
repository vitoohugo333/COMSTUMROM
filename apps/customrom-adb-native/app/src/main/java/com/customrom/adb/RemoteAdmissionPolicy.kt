package com.customrom.adb

sealed class RemoteAdmissionDecision {
    data class Accepted(val job: RemoteJob) : RemoteAdmissionDecision()
    data class Rejected(val reason: String) : RemoteAdmissionDecision()
}

object RemoteAdmissionPolicy {
    fun evaluate(issue: RemoteGitHubIssue, config: GitHubControlConfig): RemoteAdmissionDecision {
        if (!config.enabled) return RemoteAdmissionDecision.Rejected("Controle remoto desativado")
        if (!issue.title.startsWith(config.titlePrefix)) {
            return RemoteAdmissionDecision.Rejected("Issue fora da fila CUSTOMROM")
        }
        if (issue.authorLogin != config.allowedAuthor) {
            return RemoteAdmissionDecision.Rejected("Autor não autorizado: " + issue.authorLogin)
        }
        val job = try {
            CustomromJobContract.parse(issue.body)
        } catch (t: Throwable) {
            return RemoteAdmissionDecision.Rejected("Contrato inválido: " + (t.message ?: t::class.java.simpleName))
        }
        if (job.target != config.target) {
            return RemoteAdmissionDecision.Rejected("Alvo incorreto: " + job.target)
        }
        return RemoteAdmissionDecision.Accepted(job)
    }
}
