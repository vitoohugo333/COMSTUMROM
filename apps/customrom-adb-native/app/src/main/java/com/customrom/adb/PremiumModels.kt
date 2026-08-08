package com.customrom.adb

import java.io.File
import java.util.Locale

data class PremiumRecipe(
    val id: String,
    val name: String,
    val risk: String,
    val command: String,
    val output: String
)

data class PremiumExecution(
    val at: Long,
    val title: String,
    val command: String,
    val output: String,
    val error: String,
    val exitCode: Int,
    val risk: String,
    val durationMs: Long
)

data class PremiumSession(
    val id: String,
    val startedAt: Long,
    val directory: File,
    val executions: MutableList<PremiumExecution> = mutableListOf(),
    var reconnectCount: Int = 0,
    var transportErrors: Int = 0
)

object PremiumSafetyPolicy {
    private val destructiveTokens = listOf(
        "fastboot",
        " flash ",
        "erase ",
        "pm uninstall",
        "adb root",
        "remount",
        "dd if=",
        "mkfs",
        "reboot bootloader"
    )

    private val reversibleTokens = listOf(
        "pm disable",
        "pm enable",
        "am force-stop",
        "settings put",
        "pm clear",
        "svc ",
        "setprop ",
        "reboot"
    )

    private val protectedPackageTokens = listOf(
        "canbus",
        "jancar",
        "mcu",
        "vehicle",
        "carservice",
        "car.service",
        "hvac",
        "climate",
        "reverse",
        "camera",
        "parking",
        "sensor",
        "radio",
        "dsp",
        "amplifier",
        "audio",
        "bluetooth",
        "btservice",
        "sleep",
        "wake",
        "accservice"
    )

    fun classify(command: String): String {
        val normalized = " ${command.lowercase(Locale.ROOT)} "
        if (destructiveTokens.any { normalized.contains(it) }) return "VERMELHO"
        if (reversibleTokens.any { normalized.contains(it) }) return "AMARELO"
        return "VERDE"
    }

    fun isProtectedPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.ROOT)
        return protectedPackageTokens.any { normalized.contains(it) }
    }

    fun explanation(risk: String): String = when (risk) {
        "VERMELHO" -> "Ação estrutural ou destrutiva. Não faz parte do fluxo comum do CUSTOMROM e exige autorização específica e plano de recuperação."
        "AMARELO" -> "Ação que altera estado de forma reversível. Confirme o alvo e mantenha o caminho de restauração visível."
        else -> "Somente leitura ou ação observacional."
    }
}
