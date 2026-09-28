// Safety-first typed remote operation tests.
package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOperationRegistryTest {
    private val registry = RemoteOperationRegistry()

    @Test
    fun resolvesReadOnlyMemoryDiagnostic() {
        val job = action("diagnostic.memory")
        val resolved = registry.resolve(job)

        assertEquals("VERDE", resolved.risk)
        assertFalse(resolved.effectful)
        assertTrue(resolved.command.contains("/proc/meminfo"))
    }

    @Test
    fun packageInspectUsesLiteralValidatedPackage() {
        val resolved = registry.resolve(action("package.inspect", mapOf("package" to "com.spotify.music")))

        assertEquals("VERDE", resolved.risk)
        assertTrue(resolved.command.contains("dumpsys package com.spotify.music"))
        assertFalse(resolved.effectful)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPackageArgumentWithShellMetacharacters() {
        registry.resolve(action("package.inspect", mapOf("package" to "com.spotify.music; reboot")))
    }

    @Test
    fun disableIsYellowEffectfulAndCarriesRollback() {
        val resolved = registry.resolve(action("package.disable", mapOf("package" to "com.spotify.music"), allowChanges = true))

        assertEquals("AMARELO", resolved.risk)
        assertTrue(resolved.effectful)
        assertTrue(resolved.requiresAllowChanges)
        assertEquals("pm enable com.spotify.music", resolved.rollbackCommand)
        assertTrue(resolved.preflightCommand.isNotBlank())
        assertTrue(resolved.verificationCommand.isNotBlank())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesProtectedAutomotivePackageDisable() {
        registry.resolve(action("package.disable", mapOf("package" to "com.jancar.canbus"), allowChanges = true))
    }

    @Test
    fun remoteDisableRejectsOwnerCriticalAndCorePackages() {
        val protectedPackages = listOf(
            "com.omegas.v7.test",
            "ginlemon.flowerfree",
            "com.android.systemui",
            "com.android.settings",
            "com.jancar.canbus",
            "com.jancar.launcher"
        )

        protectedPackages.forEach { pkg ->
            val attempt = runCatching {
                registry.resolve(
                    action(
                        "package.disable",
                        mapOf("package" to pkg),
                        allowChanges = true
                    )
                )
            }
            assertTrue("Expected remote disable rejection for $pkg", attempt.isFailure)
        }
    }

    @Test
    fun packageEnableRemainsAvailableForRecovery() {
        val resolved = registry.resolve(action("package.enable", mapOf("package" to "com.jancar.canbus"), allowChanges = true))

        assertEquals("AMARELO", resolved.risk)
        assertTrue(resolved.effectful)
        assertEquals("pm enable com.jancar.canbus", resolved.command)
    }

    @Test
    fun packageDisableVerificationRequiresDisabledState() {
        val resolved = registry.resolve(action("package.disable", mapOf("package" to "com.spotify.music"), allowChanges = true))

        assertTrue(resolved.verificationSatisfied("state=disabled"))
        assertFalse(resolved.verificationSatisfied("state=enabled"))
    }

    @Test
    fun packageEnableVerificationRequiresEnabledState() {
        val resolved = registry.resolve(action("package.enable", mapOf("package" to "com.spotify.music"), allowChanges = true))

        assertTrue(resolved.verificationSatisfied("state=enabled"))
        assertFalse(resolved.verificationSatisfied("state=disabled"))
    }

    @Test
    fun forceStopVerificationRequiresProcessToDisappear() {
        val resolved = registry.resolve(action("package.forceStop", mapOf("package" to "com.spotify.music"), allowChanges = true))

        assertTrue(resolved.verificationSatisfied(""))
        assertFalse(resolved.verificationSatisfied("1234"))
    }

    @Test
    fun animationVerificationRequiresAllThreeObservedValues() {
        val resolved = registry.resolve(action("settings.animations", mapOf("enabled" to "false"), allowChanges = true))

        assertTrue(resolved.verificationSatisfied("window=0.0\ntransition=0.0\nanimator=0.0"))
        assertFalse(resolved.verificationSatisfied("window=0.0\ntransition=1.0\nanimator=0.0"))
    }

    @Test
    fun shellRiskIsAlwaysClassifiedLocally() {
        val job = RemoteJob(
            schema = CustomromJobContract.SCHEMA,
            requestId = "cr-20260927-0200",
            target = "taytech-primary",
            mode = RemoteJobMode.SHELL,
            command = "pm disable-user --user 0 com.spotify.music",
            timeoutSeconds = 60,
            allowChanges = true
        )

        val resolved = registry.resolve(job)

        assertEquals("AMARELO", resolved.risk)
        assertTrue(resolved.requiresAllowChanges)
    }

    @Test
    fun destructiveShellRiskCannotBeBypassedWithRepeatedWhitespace() {
        val resolved = registry.resolve(
            RemoteJob(
                schema = CustomromJobContract.SCHEMA,
                requestId = "cr-20260927-0298",
                target = "taytech-primary",
                mode = RemoteJobMode.SHELL,
                command = "pm   uninstall com.spotify.music",
                timeoutSeconds = 60,
                allowChanges = true
            )
        )

        assertEquals("VERMELHO", resolved.risk)
    }

    @Test
    fun clearingApplicationDataIsRedNotReversibleYellow() {
        val resolved = registry.resolve(
            RemoteJob(
                schema = CustomromJobContract.SCHEMA,
                requestId = "cr-20260927-0299",
                target = "taytech-primary",
                mode = RemoteJobMode.SHELL,
                command = "pm clear com.spotify.music",
                timeoutSeconds = 60,
                allowChanges = true
            )
        )

        assertEquals("VERMELHO", resolved.risk)
    }

    @Test
    fun genericFileWriteIsYellowAndRequiresExplicitChanges() {
        val resolved = registry.resolve(
            shell("echo probe > /data/local/tmp/customrom-probe", allowChanges = true)
        )

        assertEquals("AMARELO", resolved.risk)
        assertTrue(resolved.requiresAllowChanges)
    }

    @Test
    fun directDeviceNodeWriteIsRed() {
        val resolved = registry.resolve(
            shell("echo 01 > /dev/can0", allowChanges = true)
        )

        assertEquals("VERMELHO", resolved.risk)
    }

    @Test
    fun genericBinderServiceCallIsRed() {
        val resolved = registry.resolve(
            shell("service call vehicle 3 i32 1", allowChanges = true)
        )

        assertEquals("VERMELHO", resolved.risk)
    }

    @Test
    fun automotiveBroadcastIsRedButReadOnlyInspectionStaysGreen() {
        val active = registry.resolve(
            shell("am broadcast -a com.vendor.mcu.SET_MODE --ei mode 1", allowChanges = true)
        )
        val inspect = registry.resolve(
            shell("dumpsys package com.jancar.canbus")
        )

        assertEquals("VERMELHO", active.risk)
        assertEquals("VERDE", inspect.risk)
    }

    @Test
    fun recipeRunUsesExistingRecipeAndLocalRisk() {
        val recipes = listOf(
            PremiumRecipe("cpu-known", "CPU conhecida", "VERDE", "top -n 1", "cpu.txt")
        )
        val resolved = RemoteOperationRegistry(recipes).resolve(
            action("recipe.run", mapOf("id" to "cpu-known"))
        )

        assertEquals("CPU conhecida", resolved.title)
        assertEquals("top -n 1", resolved.command)
        assertEquals("VERDE", resolved.risk)
    }

    private fun shell(command: String, allowChanges: Boolean = false): RemoteJob = RemoteJob(
        schema = CustomromJobContract.SCHEMA,
        requestId = "cr-20260927-shell",
        target = "taytech-primary",
        mode = RemoteJobMode.SHELL,
        command = command,
        timeoutSeconds = 60,
        allowChanges = allowChanges
    )

    private fun action(
        name: String,
        args: Map<String, String> = emptyMap(),
        allowChanges: Boolean = false
    ): RemoteJob = RemoteJob(
        schema = CustomromJobContract.SCHEMA,
        requestId = "cr-20260927-0201",
        target = "taytech-primary",
        mode = RemoteJobMode.ACTION,
        action = name,
        args = args,
        timeoutSeconds = 60,
        allowChanges = allowChanges
    )
}
