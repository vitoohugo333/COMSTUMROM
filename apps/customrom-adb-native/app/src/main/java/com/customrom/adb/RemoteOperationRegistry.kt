package com.customrom.adb

data class ResolvedRemoteOperation(
    val title: String,
    val command: String,
    val risk: String,
    val effectful: Boolean,
    val requiresAllowChanges: Boolean,
    val preflightCommand: String = "",
    val verificationCommand: String = "",
    val rollbackCommand: String = "",
    val verificationMustContain: List<String> = emptyList(),
    val verificationMustBeBlank: Boolean = false
) {
    fun verificationSatisfied(stdout: String): Boolean {
        val observed = stdout.trim()
        if (verificationMustBeBlank && observed.isNotEmpty()) return false
        return verificationMustContain.all { required -> observed.contains(required) }
    }
}

class RemoteOperationRegistry(
    private val recipes: List<PremiumRecipe> = emptyList()
) {
    fun resolve(job: RemoteJob): ResolvedRemoteOperation =
        when (job.mode) {
            RemoteJobMode.SHELL -> resolveShell(job)
            RemoteJobMode.ACTION -> resolveAction(job)
        }

    private fun resolveShell(job: RemoteJob): ResolvedRemoteOperation {
        val command = job.command.trim()
        require(command.isNotEmpty()) { "Shell command is empty" }
        val risk = PremiumSafetyPolicy.classify(command)
        return ResolvedRemoteOperation(
            title = "Terminal remoto",
            command = command,
            risk = risk,
            effectful = risk != "VERDE",
            requiresAllowChanges = risk == "AMARELO"
        )
    }

    private fun resolveAction(job: RemoteJob): ResolvedRemoteOperation = when (job.action) {
        "diagnostic.memory" -> readOnly(
            "Diagnóstico de memória",
            "cat /proc/meminfo; echo __SWAPS__; cat /proc/swaps; echo __DUMPSYS_MEMINFO__; dumpsys meminfo"
        )
        "diagnostic.cpu" -> readOnly(
            "Diagnóstico de CPU",
            "top -n 1; echo __PS__; ps -A"
        )
        "diagnostic.system" -> readOnly(
            "Diagnóstico do sistema",
            "getprop; echo __MEMINFO__; cat /proc/meminfo; echo __SWAPS__; cat /proc/swaps; echo __DUMPSYS_MEMINFO__; dumpsys meminfo; echo __PS__; ps -A; echo __TOP__; top -n 1; echo __DF__; df -h"
        )
        "adb.persistence.inspect" -> readOnly(
            "ADB após reinício",
            "echo adb_enabled=$(settings get global adb_enabled); echo adb_wifi_enabled=$(settings get global adb_wifi_enabled); echo service_adb_tcp_port=$(getprop service.adb.tcp.port); echo persist_adb_tcp_port=$(getprop persist.adb.tcp.port); ps -A | grep '[a]dbd' || true"
        )
        "package.inspect" -> {
            val pkg = requirePackage(job)
            readOnly(
                "Analisar aplicativo",
                "dumpsys package $pkg; echo __MEMINFO__; dumpsys meminfo $pkg; echo __PROCESS__; ps -A | grep '$pkg' || true"
            )
        }
        "package.forceStop" -> {
            val pkg = requirePackage(job)
            requireNotProtectedForDisruption(pkg)
            yellow(
                title = "Parar aplicativo",
                command = "am force-stop $pkg",
                preflight = "pidof $pkg || true",
                verification = "pidof $pkg || true",
                verificationMustBeBlank = true
            )
        }
        "package.disable" -> {
            val pkg = requirePackage(job)
            requireNotProtectedForDisruption(pkg)
            yellow(
                title = "Desativar aplicativo",
                command = "pm disable-user --user 0 $pkg",
                preflight = packageStateCommand(pkg),
                verification = packageStateCommand(pkg),
                rollback = "pm enable $pkg",
                verificationMustContain = listOf("state=disabled")
            )
        }
        "package.enable" -> {
            val pkg = requirePackage(job)
            yellow(
                title = "Restaurar aplicativo",
                command = "pm enable $pkg",
                preflight = packageStateCommand(pkg),
                verification = packageStateCommand(pkg),
                rollback = "pm disable-user --user 0 $pkg",
                verificationMustContain = listOf("state=enabled")
            )
        }
        "settings.animations" -> {
            val enabled = job.args["enabled"]?.lowercase()?.let {
                when (it) {
                    "true", "1", "on" -> true
                    "false", "0", "off" -> false
                    else -> throw IllegalArgumentException("enabled must be true/false")
                }
            } ?: throw IllegalArgumentException("Missing enabled")
            val value = if (enabled) "1" else "0"
            yellow(
                title = if (enabled) "Ativar animações" else "Desativar animações",
                command = "settings put global window_animation_scale $value; settings put global transition_animation_scale $value; settings put global animator_duration_scale $value",
                preflight = animationStateCommand(),
                verification = animationStateCommand(),
                verificationMustContain = listOf(
                    "window=$value",
                    "transition=$value",
                    "animator=$value"
                )
            )
        }
        "recipe.run" -> {
            val id = job.args["id"]?.trim().orEmpty()
            require(id.isNotEmpty()) { "Missing recipe id" }
            val recipe = recipes.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown recipe: $id")
            val risk = maxRisk(recipe.risk, PremiumSafetyPolicy.classify(recipe.command))
            ResolvedRemoteOperation(
                title = recipe.name,
                command = recipe.command,
                risk = risk,
                effectful = risk != "VERDE",
                requiresAllowChanges = risk == "AMARELO"
            )
        }
        else -> throw IllegalArgumentException("Unknown action: ${job.action}")
    }

    private fun readOnly(title: String, command: String): ResolvedRemoteOperation =
        ResolvedRemoteOperation(
            title = title,
            command = command,
            risk = "VERDE",
            effectful = false,
            requiresAllowChanges = false
        )

    private fun yellow(
        title: String,
        command: String,
        preflight: String = "",
        verification: String = "",
        rollback: String = "",
        verificationMustContain: List<String> = emptyList(),
        verificationMustBeBlank: Boolean = false
    ): ResolvedRemoteOperation {
        val localRisk = PremiumSafetyPolicy.classify(command)
        require(localRisk != "VERMELHO") { "Operation classified RED locally" }
        return ResolvedRemoteOperation(
            title = title,
            command = command,
            risk = maxRisk("AMARELO", localRisk),
            effectful = true,
            requiresAllowChanges = true,
            preflightCommand = preflight,
            verificationCommand = verification,
            rollbackCommand = rollback,
            verificationMustContain = verificationMustContain,
            verificationMustBeBlank = verificationMustBeBlank
        )
    }

    private fun requirePackage(job: RemoteJob): String {
        val pkg = job.args["package"]?.trim().orEmpty()
        require(PACKAGE_PATTERN.matches(pkg)) { "Invalid Android package name" }
        return pkg
    }

    private fun requireNotProtectedForDisruption(pkg: String) {
        require(!PremiumSafetyPolicy.isProtectedPackage(pkg)) {
            "Protected automotive package cannot be stopped or disabled remotely"
        }
    }

    private fun packageStateCommand(pkg: String): String =
        "if pm list packages -d | grep -Fx 'package:$pkg' >/dev/null; then echo state=disabled; else echo state=enabled; fi"

    private fun animationStateCommand(): String =
        "echo window=$(settings get global window_animation_scale); echo transition=$(settings get global transition_animation_scale); echo animator=$(settings get global animator_duration_scale)"

    private fun maxRisk(first: String, second: String): String {
        fun rank(value: String): Int = when (value) {
            "VERMELHO" -> 2
            "AMARELO" -> 1
            else -> 0
        }
        return if (rank(first) >= rank(second)) first else second
    }

    companion object {
        private val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
    }
}
