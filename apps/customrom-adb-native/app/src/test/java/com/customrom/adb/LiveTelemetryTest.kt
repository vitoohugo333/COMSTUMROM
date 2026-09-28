package com.customrom.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTelemetryTest {
    private val sample = """
        __MEMINFO__
        MemTotal:        4096000 kB
        MemAvailable:     819200 kB
        MemFree:          180000 kB
        Cached:           600000 kB
        __PSS__
        Total PSS by process:
          620,000K: com.spotify.music (pid 2100 / activities)
          410,000K: system (pid 500)
          120,000K: com.spotify.music:service (pid 2200)
           80,000K: com.google.android.apps.maps (pid 3300)
        Total PSS by OOM adjustment:
        __CPUINFO__
          18% 2100/com.spotify.music: 12% user + 6% kernel
          5.5% 500/system_server: 3% user + 2.5% kernel
          2% 2200/com.spotify.music:service: 1% user + 1% kernel
        TOTAL: 31% user + 7% kernel + 0% iowait
        __LOAD__
        2.10 1.45 0.90 2/753 18234
    """.trimIndent()

    @Test
    fun parsesLiveRamCpuAndTopConsumers() {
        val snapshot = LiveTelemetryParser.parse(sample, capturedAtMs = 1234L)

        assertEquals(4096000L, snapshot.totalRamKb)
        assertEquals(819200L, snapshot.availableRamKb)
        assertEquals(3276800L, snapshot.usedRamKb)
        assertEquals(80, snapshot.usedRamPercent)
        assertEquals(38.0, snapshot.cpuTotalPercent ?: -1.0, 0.01)
        assertEquals(2.10, snapshot.load1 ?: -1.0, 0.01)
        assertEquals("com.spotify.music", snapshot.topMemory(1).single().processName)
        assertEquals(620000L, snapshot.topMemory(1).single().pssKb)
        assertEquals("com.spotify.music", snapshot.topCpu(1).single().processName)
        assertEquals(18.0, snapshot.topCpu(1).single().cpuPercent, 0.01)
        assertEquals(1234L, snapshot.capturedAtMs)
    }

    @Test
    fun aggregatesMainAndChildProcessesForAppRows() {
        val snapshot = LiveTelemetryParser.parse(sample)

        val spotify = snapshot.usageForPackage("com.spotify.music")

        assertEquals(740000L, spotify.pssKb)
        assertEquals(20.0, spotify.cpuPercent, 0.01)
    }

    @Test
    fun missingSectionsDegradeWithoutInventingNumbers() {
        val snapshot = LiveTelemetryParser.parse("__MEMINFO__\nMemTotal: 2048000 kB\n")

        assertEquals(2048000L, snapshot.totalRamKb)
        assertEquals(0L, snapshot.availableRamKb)
        assertEquals(0, snapshot.usedRamPercent)
        assertTrue(snapshot.processes.isEmpty())
        assertEquals(null, snapshot.cpuTotalPercent)
        assertEquals(null, snapshot.load1)
    }

    @Test
    fun commandCollectsHumanUsefulSectionsWithoutChangingState() {
        val command = LiveTelemetryCollector.COMMAND

        assertTrue(command.contains("__MEMINFO__"))
        assertTrue(command.contains("/proc/meminfo"))
        assertTrue(command.contains("__PSS__"))
        assertTrue(command.contains("dumpsys meminfo"))
        assertTrue(command.contains("__CPUINFO__"))
        assertTrue(command.contains("dumpsys cpuinfo"))
        assertTrue(command.contains("__LOAD__"))
        assertEquals("VERDE", PremiumSafetyPolicy.classify(command))
    }
}
