package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInventoryProjectionTest {
    private val packages = listOf(
        PackageSnapshot(
            packageName = "com.spotify.music",
            apkPath = "/data/app/com.spotify.music/base.apk",
            kind = "Usuário",
            running = true
        ),
        PackageSnapshot(
            packageName = "com.google.android.apps.maps",
            apkPath = "/data/app/com.google.android.apps.maps/base.apk",
            kind = "Usuário",
            disabled = true
        ),
        PackageSnapshot(
            packageName = "com.android.systemui",
            apkPath = "/system/priv-app/SystemUI/SystemUI.apk",
            kind = "Sistema",
            running = true
        ),
        PackageSnapshot(
            packageName = "com.example.systemhelper",
            apkPath = "/system/app/Helper/Helper.apk",
            kind = "Sistema"
        )
    )

    @Test
    fun filtersAndCountsUseOneSharedProjection() {
        val changed = setOf("com.google.android.apps.maps")

        assertEquals(4, AppInventoryProjection.count(packages, "Todos", changed))
        assertEquals(2, AppInventoryProjection.count(packages, "Rodando", changed))
        assertEquals(2, AppInventoryProjection.count(packages, "Usuário", changed))
        assertEquals(2, AppInventoryProjection.count(packages, "Sistema", changed))
        assertEquals(1, AppInventoryProjection.count(packages, "Desativados", changed))
        assertEquals(1, AppInventoryProjection.count(packages, "Alterados", changed))
        assertTrue(AppInventoryProjection.count(packages, "Protegidos", changed) >= 1)
        assertTrue(AppInventoryProjection.count(packages, "Candidatos", changed) >= 1)
    }

    @Test
    fun humanSearchAndFilterCanBeCombined() {
        val result = AppInventoryProjection.filter(
            packages = packages,
            filter = "Rodando",
            query = "spotify",
            changed = emptySet()
        )

        assertEquals(listOf("com.spotify.music"), result.map { it.packageName })
    }

    @Test
    fun filterLabelsStayCanonicalForUiState() {
        assertEquals(
            listOf("Todos", "Rodando", "Usuário", "Sistema", "Desativados", "Protegidos", "Candidatos", "Alterados"),
            AppInventoryProjection.FILTERS
        )
    }
}
