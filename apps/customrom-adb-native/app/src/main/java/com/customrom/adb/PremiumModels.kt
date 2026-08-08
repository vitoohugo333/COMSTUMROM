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
        "wipe ",
        "pm uninstall",
        "adb root",
        " remount",
        "mount -o rw",
        "dd if=",
        "dd of=",
        "mkfs",
        "parted ",
        "fdisk ",
        "sgdisk ",
        "avbctl ",
        "magisk",
        "setenforce 0",
        "reboot bootloader",
        "reboot recovery",
        "rm -rf",
        "rm -r "
    )

    private val reversibleTokens = listOf(
        "pm disable",
        "pm enable",
        "am force-stop",
        "am start",
        "am broadcast",
        "settings put",
        "pm clear",
        "svc ",
        "setprop ",
        "reboot",
        " kill ",
        "pkill ",
        "killall ",
        "chmod ",
        "chown ",
        "cmd package install",
        "cmd package set-",
        "cmd overlay enable",
        "cmd overlay disable",
        "input tap",
        "input swipe",
        "input keyevent",
        "uiautomator dump",
        "rm -f "
    )

    private val protectedPackageTokens = listOf(
        "canbus",
        "canbox",
        "jancar",
        "hiworld",
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
        "steering",
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
        val normalized = " ${command.lowercase(Locale.ROOT).replace('\n', ' ')} "
        if (destructiveTokens.any { normalized.contains(it) }) return "VERMELHO"
        if (reversibleTokens.any { normalized.contains(it) }) return "AMARELO"
        return "VERDE"
    }

    fun isProtectedPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase(Locale.ROOT)
        return protectedPackageTokens.any { normalized.contains(it) }
    }

    fun explanation(risk: String): String = when (risk) {
        "VERMELHO" -> "Ação estrutural, destrutiva ou com potencial de exigir recuperação. O fluxo comum do CUSTOMROM bloqueia esta classe e exige autorização específica fora desta tela."
        "AMARELO" -> "Ação que altera estado, abre uma superfície remota ou interage ativamente com o alvo. Confirme o efeito esperado e mantenha o caminho de restauração visível quando houver rollback."
        else -> "Somente leitura ou ação observacional."
    }
}
