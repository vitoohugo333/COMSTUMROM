package com.customrom.adb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class OperationPhase {
    IDLE,
    QUEUED,
    RUNNING,
    SUCCESS_WITH_OUTPUT,
    SUCCESS_EMPTY,
    COMMAND_ERROR,
    TRANSPORT_ERROR,
    CANCELLED
}

data class HumanOperationResult(
    val phase: OperationPhase,
    val title: String,
    val detail: String,
    val technical: String,
    val success: Boolean
)

object OperationPresenter {
    fun running(title: String): HumanOperationResult = HumanOperationResult(
        phase = OperationPhase.RUNNING,
        title = "Executando…",
        detail = title,
        technical = "",
        success = false
    )

    fun cancelled(title: String): HumanOperationResult = HumanOperationResult(
        phase = OperationPhase.CANCELLED,
        title = "Operação cancelada",
        detail = title,
        technical = "cancelled=true",
        success = false
    )

    fun transportError(title: String, message: String, durationMs: Long): HumanOperationResult = HumanOperationResult(
        phase = OperationPhase.TRANSPORT_ERROR,
        title = "Falha de conexão",
        detail = "A comunicação ADB com a TayTech foi interrompida. O CUSTOMROM tentará recuperar a conexão.",
        technical = "$title · ${durationMs} ms\n$message",
        success = false
    )

    fun fromShell(
        title: String,
        stdout: String,
        stderr: String,
        exitCode: Int,
        durationMs: Long
    ): HumanOperationResult {
        val cleanOut = stdout.trim()
        val cleanErr = stderr.trim()
        val technical = buildString {
            append("exit code: $exitCode · ${durationMs} ms")
            if (cleanErr.isNotEmpty()) append("\n\nstderr:\n$cleanErr")
        }
        if (exitCode != 0) {
            return HumanOperationResult(
                phase = OperationPhase.COMMAND_ERROR,
                title = "O comando não foi concluído",
                detail = cleanErr.ifBlank { cleanOut.ifBlank { "A TayTech respondeu com código $exitCode." } },
                technical = technical,
                success = false
            )
        }
        if (cleanOut.isEmpty() && cleanErr.isEmpty()) {
            return HumanOperationResult(
                phase = OperationPhase.SUCCESS_EMPTY,
                title = "Concluído",
                detail = "A TayTech aceitou a operação. Nenhum texto foi retornado pelo comando.",
                technical = technical,
                success = true
            )
        }
        return HumanOperationResult(
            phase = OperationPhase.SUCCESS_WITH_OUTPUT,
            title = "Concluído",
            detail = cleanOut.ifBlank { cleanErr },
            technical = technical,
            success = true
        )
    }
}

enum class PackageCriticality(val label: String) {
    PROTECTED("PROTEGIDO"),
    HIGH("ALTA"),
    MEDIUM("MÉDIA"),
    LOW("BAIXA"),
    UNKNOWN("DESCONHECIDA")
}

enum class AssessmentConfidence(val label: String) {
    HIGH("alta"),
    MEDIUM("média"),
    LOW("baixa")
}

data class PackageSnapshot(
    val packageName: String,
    val apkPath: String = "",
    val kind: String = "Desconhecido",
    val disabled: Boolean = false,
    val running: Boolean = false,
    val uid: String = "",
    val metadata: String = ""
)

data class PackageAssessment(
    val criticality: PackageCriticality,
    val confidence: AssessmentConfidence,
    val reasons: List<String>,
    val candidateForReversibleTest: Boolean
)

object PackageIntelligence {
    private val automotiveTokens = listOf(
        "canbus", "canbox", "jancar", "hiworld", "mcu", "vehicle", "carservice", "car.service",
        "hvac", "climate", "reverse", "rearview", "camera", "parking", "sensor", "steering",
        "radio", "dsp", "amplifier", "audio", "bluetooth", "btservice", "accservice", "sleep", "wake"
    )

    private val coreTokens = listOf(
        "systemui", "permissioncontroller", "packageinstaller", "networkstack", "settings",
        "telephony", "phone", "launcher", "inputmethod", "providers.settings", "externalstorage",
        "downloads.provider", "documentsui", "wifi", "ethernet", "location", "framework"
    )

    private val knownProtectedPrefixes = listOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.google.android.permissioncontroller", "com.google.android.networkstack"
    )

    fun assess(snapshot: PackageSnapshot): PackageAssessment {
        val packageLower = snapshot.packageName.lowercase(Locale.ROOT)
        val pathLower = snapshot.apkPath.lowercase(Locale.ROOT)
        val metaLower = snapshot.metadata.lowercase(Locale.ROOT)
        val reasons = mutableListOf<String>()

        val automotiveHits = automotiveTokens.filter { token ->
            packageLower.contains(token) || pathLower.contains(token) || metaLower.contains(token)
        }
        if (automotiveHits.isNotEmpty()) {
            reasons += "sinais automotivos detectados: ${automotiveHits.distinct().take(4).joinToString()}"
            if (pathLower.contains("/vendor/") || pathLower.contains("/system/priv-app/")) {
                reasons += "componente instalado em área privilegiada/vendor"
            }
            if (metaLower.contains("persistent") || metaLower.contains("boot_completed")) {
                reasons += "há indício de serviço persistente ou inicialização no boot"
            }
            return PackageAssessment(PackageCriticality.PROTECTED, AssessmentConfidence.HIGH, reasons, false)
        }

        if (knownProtectedPrefixes.any { packageLower == it || packageLower.startsWith("$it.") }) {
            reasons += "pertence ao núcleo Android/serviço essencial conhecido"
            return PackageAssessment(PackageCriticality.PROTECTED, AssessmentConfidence.HIGH, reasons, false)
        }

        val coreHits = coreTokens.filter { packageLower.contains(it) || metaLower.contains(it) }
        val privileged = pathLower.contains("/system/priv-app/") || pathLower.contains("/apex/")
        val vendor = pathLower.contains("/vendor/") || pathLower.contains("/odm/")
        val system = snapshot.kind.equals("Sistema", true) || pathLower.contains("/system/") || pathLower.contains("/product/")
        val userApp = snapshot.kind.equals("Usuário", true) || pathLower.contains("/data/app/")
        val persistent = metaLower.contains("persistent=true") || metaLower.contains("flags=[ persistent") || metaLower.contains("boot_completed")
        val sharedSystemUid = metaLower.contains("shareduserid=android.uid") || metaLower.contains("shared user") && metaLower.contains("android.uid")

        if (coreHits.isNotEmpty() || privileged || vendor || persistent || sharedSystemUid) {
            if (coreHits.isNotEmpty()) reasons += "função central detectada: ${coreHits.distinct().take(4).joinToString()}"
            if (privileged) reasons += "APK em /system/priv-app ou APEX"
            if (vendor) reasons += "APK em partição vendor/odm"
            if (persistent) reasons += "indício de processo persistente/boot receiver"
            if (sharedSystemUid) reasons += "indício de UID compartilhado do sistema"
            return PackageAssessment(PackageCriticality.HIGH, AssessmentConfidence.HIGH, reasons, false)
        }

        if (userApp) {
            reasons += "aplicativo instalado em área de usuário (/data/app)"
            reasons += "nenhum sinal automotivo ou de núcleo foi detectado"
            if (snapshot.disabled) reasons += "já está desativado no usuário atual"
            return PackageAssessment(PackageCriticality.LOW, AssessmentConfidence.HIGH, reasons, true)
        }

        if (system) {
            reasons += "aplicativo faz parte da imagem de sistema/produto"
            reasons += "nenhum sinal automotivo forte foi encontrado, mas dependências ainda são incertas"
            return PackageAssessment(PackageCriticality.MEDIUM, AssessmentConfidence.MEDIUM, reasons, false)
        }

        reasons += "evidência insuficiente para classificar com segurança"
        return PackageAssessment(PackageCriticality.UNKNOWN, AssessmentConfidence.LOW, reasons, false)
    }

    fun friendlyName(packageName: String): String {
        val known = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.android.chrome" to "Chrome",
            "com.google.android.apps.maps" to "Google Maps",
            "com.spotify.music" to "Spotify",
            "com.android.settings" to "Configurações",
            "com.android.systemui" to "Sistema Android"
        )
        known[packageName]?.let { return it }
        val tail = packageName.substringAfterLast('.').replace('_', ' ').replace('-', ' ').trim()
        return tail.split(' ').filter { it.isNotBlank() }.joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }.ifBlank { packageName }
    }
}

data class ChangeRecord(
    val packageName: String,
    val action: String,
    val previousState: String,
    val newState: String,
    val at: Long,
    val sessionId: String,
    val exitCode: Int,
    val rollbackCommand: String
)

class ChangeLedger(context: Context) {
    private val file = File(context.filesDir, "customrom_change_ledger.json")

    @Synchronized
    fun list(): List<ChangeRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        ChangeRecord(
                            packageName = o.getString("packageName"),
                            action = o.getString("action"),
                            previousState = o.optString("previousState"),
                            newState = o.optString("newState"),
                            at = o.getLong("at"),
                            sessionId = o.optString("sessionId"),
                            exitCode = o.optInt("exitCode", -1),
                            rollbackCommand = o.optString("rollbackCommand")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun append(record: ChangeRecord) {
        val records = list().toMutableList().apply { add(record) }
        val array = JSONArray()
        records.takeLast(500).forEach { item ->
            array.put(JSONObject().apply {
                put("packageName", item.packageName)
                put("action", item.action)
                put("previousState", item.previousState)
                put("newState", item.newState)
                put("at", item.at)
                put("sessionId", item.sessionId)
                put("exitCode", item.exitCode)
                put("rollbackCommand", item.rollbackCommand)
            })
        }
        file.writeText(array.toString(2), Charsets.UTF_8)
    }

    fun wasDisabledByCustomrom(packageName: String): Boolean {
        val last = list().lastOrNull { it.packageName == packageName } ?: return false
        return last.action == "disable" && last.exitCode == 0 && last.newState == "disabled"
    }
}
