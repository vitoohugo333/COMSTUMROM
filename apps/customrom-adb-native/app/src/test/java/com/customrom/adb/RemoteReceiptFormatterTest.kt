package com.customrom.adb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteReceiptFormatterTest {
    @Test
    fun formatsHumanSummaryBeforeTechnicalDetails() {
        val receipt = RemoteReceiptFormatter.format(
            RemoteReceiptData(
                requestId = "cr-20260927-0210",
                title = "Diagnóstico de memória",
                state = RemoteJobState.COMPLETED,
                risk = "VERDE",
                durationMs = 3200,
                stdout = "MemTotal: 4096000 kB",
                stderr = "",
                exitCode = 0
            )
        )

        assertTrue(receipt.indexOf("Diagnóstico de memória") < receipt.indexOf("Detalhes técnicos"))
        assertTrue(receipt.contains("CONCLUÍDO"))
        assertTrue(receipt.contains("MemTotal"))
    }

    @Test
    fun distinguishesTransportFailureFromCommandFailure() {
        val receipt = RemoteReceiptFormatter.format(
            RemoteReceiptData(
                requestId = "cr-20260927-0211",
                title = "Ler serviços",
                state = RemoteJobState.FAILED,
                risk = "VERDE",
                durationMs = 900,
                stdout = "",
                stderr = "",
                exitCode = -1,
                transportError = "TayTech não conectada"
            )
        )

        assertTrue(receipt.contains("Falha de transporte"))
        assertTrue(receipt.contains("TayTech não conectada"))
    }

    @Test
    fun redactsGithubCredentialsFromAllTechnicalOutput() {
        val receipt = RemoteReceiptFormatter.format(
            RemoteReceiptData(
                requestId = "cr-20260927-0212",
                title = "Teste",
                state = RemoteJobState.FAILED,
                risk = "VERDE",
                durationMs = 10,
                stdout = "Authorization: Bearer github_pat_SUPERSECRET123456",
                stderr = "ghp_ABCDEF1234567890",
                exitCode = 1
            )
        )

        assertFalse(receipt.contains("SUPERSECRET"))
        assertFalse(receipt.contains("ghp_ABCDEF"))
        assertTrue(receipt.contains("[REDACTED]"))
    }

    @Test
    fun boundsLargeOutputAndMarksTruncation() {
        val receipt = RemoteReceiptFormatter.format(
            RemoteReceiptData(
                requestId = "cr-20260927-0213",
                title = "Log grande",
                state = RemoteJobState.COMPLETED,
                risk = "VERDE",
                durationMs = 20,
                stdout = "x".repeat(20000),
                stderr = "",
                exitCode = 0
            ),
            maxTechnicalChars = 1000
        )

        assertTrue(receipt.contains("TRUNCADO"))
        assertTrue(receipt.length < 3000)
    }

    @Test
    fun rendersRollbackWhenAvailable() {
        val receipt = RemoteReceiptFormatter.format(
            RemoteReceiptData(
                requestId = "cr-20260927-0214",
                title = "Aplicativo desativado",
                state = RemoteJobState.COMPLETED,
                risk = "AMARELO",
                durationMs = 250,
                stdout = "Package com.spotify.music new state: disabled-user",
                stderr = "",
                exitCode = 0,
                previousState = "enabled",
                currentState = "disabled",
                rollbackCommand = "pm enable com.spotify.music"
            )
        )

        assertTrue(receipt.contains("Estado anterior: enabled"))
        assertTrue(receipt.contains("Estado atual: disabled"))
        assertTrue(receipt.contains("Rollback: disponível"))
        assertTrue(receipt.contains("pm enable com.spotify.music"))
    }
}
