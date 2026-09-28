package com.customrom.adb

import kotlin.math.roundToInt

data class ProcessResourceUsage(
    val processName: String,
    val pid: Int? = null,
    val pssKb: Long = 0L,
    val cpuPercent: Double = 0.0
)

data class LiveTelemetrySnapshot(
    val totalRamKb: Long = 0L,
    val availableRamKb: Long = 0L,
    val cpuTotalPercent: Double? = null,
    val load1: Double? = null,
    val processes: List<ProcessResourceUsage> = emptyList(),
    val capturedAtMs: Long = System.currentTimeMillis()
) {
    val usedRamKb: Long
        get() = if (totalRamKb > 0L && availableRamKb in 1..totalRamKb) totalRamKb - availableRamKb else 0L

    val usedRamPercent: Int
        get() = if (usedRamKb > 0L && totalRamKb > 0L) {
            ((usedRamKb.toDouble() / totalRamKb.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

    fun topMemory(limit: Int = 5): List<ProcessResourceUsage> =
        processes.filter { it.pssKb > 0L }.sortedByDescending { it.pssKb }.take(limit.coerceAtLeast(0))

    fun topCpu(limit: Int = 5): List<ProcessResourceUsage> =
        processes.filter { it.cpuPercent > 0.0 }.sortedByDescending { it.cpuPercent }.take(limit.coerceAtLeast(0))

    fun usageForPackage(packageName: String): ProcessResourceUsage {
        val matching = processes.filter {
            it.processName == packageName || it.processName.startsWith(packageName + ":")
        }
        return ProcessResourceUsage(
            processName = packageName,
            pid = matching.mapNotNull { it.pid }.firstOrNull(),
            pssKb = matching.sumOf { it.pssKb },
            cpuPercent = matching.sumOf { it.cpuPercent }
        )
    }
}

object LiveTelemetryCollector {
    const val COMMAND =
        "echo __MEMINFO__; " +
        "cat /proc/meminfo 2>/dev/null | grep -E '^(MemTotal|MemAvailable|MemFree|Buffers|Cached|SReclaimable|SwapTotal|SwapFree):'; " +
        "echo __PSS__; " +
        "dumpsys meminfo 2>/dev/null | sed -n '/Total PSS by process:/,/Total PSS by OOM adjustment:/p' | head -n 80; " +
        "echo __CPUINFO__; " +
        "dumpsys cpuinfo 2>/dev/null | head -n 80; " +
        "echo __LOAD__; " +
        "cat /proc/loadavg 2>/dev/null"
}

object LiveTelemetryParser {
    private val memoryLine = Regex("^([A-Za-z]+):\\s+([0-9]+)\\s+kB$")
    private val pssLine = Regex("^\\s*([0-9][0-9,]*)K:\\s+(.+?)\\s+\\(pid\\s+(\\d+)(?:\\s*/.*)?\\)\\s*$")
    private val cpuProcessLine = Regex("^\\s*([0-9]+(?:\\.[0-9]+)?)%\\s+(\\d+)/([^:]+(?::[^:]+)*):\\s+.*$")
    private val cpuTotalLine = Regex("^TOTAL:\\s*(.*)$")
    private val percentValue = Regex("([0-9]+(?:\\.[0-9]+)?)%")

    fun parse(raw: String, capturedAtMs: Long = System.currentTimeMillis()): LiveTelemetrySnapshot {
        var section = ""
        var totalRamKb = 0L
        var availableRamKb = 0L
        var cpuTotalPercent: Double? = null
        var load1: Double? = null
        val pssByPid = linkedMapOf<Int, ProcessResourceUsage>()
        val cpuByPid = linkedMapOf<Int, ProcessResourceUsage>()

        raw.lineSequence().forEach { original ->
            val line = original.trimEnd()
            when (line.trim()) {
                "__MEMINFO__", "__PSS__", "__CPUINFO__", "__LOAD__" -> {
                    section = line.trim()
                    return@forEach
                }
            }

            when (section) {
                "__MEMINFO__" -> {
                    val match = memoryLine.matchEntire(line.trim()) ?: return@forEach
                    val value = match.groupValues[2].toLongOrNull() ?: 0L
                    when (match.groupValues[1]) {
                        "MemTotal" -> totalRamKb = value
                        "MemAvailable" -> availableRamKb = value
                    }
                }
                "__PSS__" -> {
                    val match = pssLine.matchEntire(line) ?: return@forEach
                    val pss = match.groupValues[1].replace(",", "").toLongOrNull() ?: 0L
                    val name = match.groupValues[2].trim()
                    val pid = match.groupValues[3].toIntOrNull() ?: return@forEach
                    pssByPid[pid] = ProcessResourceUsage(name, pid, pssKb = pss)
                }
                "__CPUINFO__" -> {
                    val processMatch = cpuProcessLine.matchEntire(line)
                    if (processMatch != null) {
                        val cpu = processMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        val pid = processMatch.groupValues[2].toIntOrNull() ?: return@forEach
                        val name = processMatch.groupValues[3].trim()
                        cpuByPid[pid] = ProcessResourceUsage(name, pid, cpuPercent = cpu)
                    } else {
                        val total = cpuTotalLine.matchEntire(line.trim())
                        if (total != null) {
                            cpuTotalPercent = percentValue.findAll(total.groupValues[1])
                                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                                .sum()
                                .coerceAtMost(100.0)
                        }
                    }
                }
                "__LOAD__" -> {
                    if (load1 == null) {
                        load1 = line.trim().substringBefore(' ').toDoubleOrNull()
                    }
                }
            }
        }

        val pids = linkedSetOf<Int>().apply {
            addAll(pssByPid.keys)
            addAll(cpuByPid.keys)
        }
        val processes = pids.map { pid ->
            val memory = pssByPid[pid]
            val cpu = cpuByPid[pid]
            ProcessResourceUsage(
                processName = memory?.processName ?: cpu?.processName.orEmpty(),
                pid = pid,
                pssKb = memory?.pssKb ?: 0L,
                cpuPercent = cpu?.cpuPercent ?: 0.0
            )
        }.filter { it.processName.isNotBlank() }

        return LiveTelemetrySnapshot(
            totalRamKb = totalRamKb,
            availableRamKb = availableRamKb,
            cpuTotalPercent = cpuTotalPercent,
            load1 = load1,
            processes = processes,
            capturedAtMs = capturedAtMs
        )
    }
}
