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
    fun packageEnableRemainsAvailableForRecovery() {
        val resolved = registry.resolve(action("package.enable", mapOf("package" to "com.jancar.canbus"), allowChanges = true))

        assertEquals("AMARELO", resolved.risk)
        assertTrue(resolved.effectful)
        assertEquals("pm enable com.jancar.canbus", resolved.command)
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
