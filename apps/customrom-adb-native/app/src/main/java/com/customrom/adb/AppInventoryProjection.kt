package com.customrom.adb

import java.util.Locale

object AppInventoryProjection {
    val FILTERS = listOf(
        "Todos",
        "Rodando",
        "Usuário",
        "Sistema",
        "Desativados",
        "Protegidos",
        "Candidatos",
        "Alterados"
    )

    fun filter(
        packages: List<PackageSnapshot>,
        filter: String,
        query: String,
        changed: Set<String>
    ): List<PackageSnapshot> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return packages.filter { snapshot ->
            val assessment = PackageIntelligence.assess(snapshot)
            val matchesQuery = normalizedQuery.isBlank() ||
                snapshot.packageName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                PackageIntelligence.friendlyName(snapshot.packageName)
                    .lowercase(Locale.ROOT)
                    .contains(normalizedQuery)
            matchesQuery && matchesFilter(snapshot, assessment, filter, changed)
        }.sortedWith(
            compareBy<PackageSnapshot>(
                { PackageIntelligence.assess(it).criticality.ordinal },
                { PackageIntelligence.friendlyName(it.packageName) }
            )
        )
    }

    fun count(
        packages: List<PackageSnapshot>,
        filter: String,
        changed: Set<String>
    ): Int = packages.count { snapshot ->
        matchesFilter(snapshot, PackageIntelligence.assess(snapshot), filter, changed)
    }

    private fun matchesFilter(
        snapshot: PackageSnapshot,
        assessment: PackageAssessment,
        filter: String,
        changed: Set<String>
    ): Boolean = when (filter) {
        "Rodando" -> snapshot.running
        "Usuário" -> snapshot.kind == "Usuário"
        "Sistema" -> snapshot.kind == "Sistema"
        "Desativados" -> snapshot.disabled
        "Protegidos" ->
            assessment.criticality == PackageCriticality.PROTECTED ||
                assessment.criticality == PackageCriticality.HIGH
        "Candidatos" -> assessment.candidateForReversibleTest
        "Alterados" -> changed.contains(snapshot.packageName)
        else -> true
    }
}
