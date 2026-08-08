package com.customrom.adb

import java.util.Locale

enum class ActionDestination {
    PACKAGE,
    APPS_FILTER,
    RECIPE,
    SCREEN,
    TERMINAL
}

data class FunctionalAction(
    val label: String,
    val detail: String,
    val destination: ActionDestination,
    val target: String,
    val risk: String = "VERDE"
)

data class ActionableReport(
    val title: String,
    val summary: String,
    val findings: List<String>,
    val actions: List<FunctionalAction>,
    val evidenceLabel: String = "Ver evidência técnica"
)

/**
 * Converte a saída bruta das receitas em uma camada de produto.
 *
 * Regra central: o log é evidência, não o fim da jornada. O engine nunca executa
 * ações por conta própria. Ele apenas estrutura o que foi observado e devolve
 * próximos passos que a Activity ainda submete à política de risco normal.
 */
object FunctionalActionEngine {
    private val packageRegex = Regex("\\b(?:[A-Za-z][A-Za-z0-9_]*\\.){2,}[A-Za-z0-9_:-]+\\b")
    private val cpuPackageRegex = Regex("(?m)^\\s*([0-9]+(?:\\.[0-9]+)?)%\\s+\\d+/([A-Za-z0-9._:-]+)")

    fun analyze(recipeId: String, raw: String): ActionableReport = when (recipeId) {
        "diagnostico-lentidao", "processos", "memoria-zram" -> performanceReport(recipeId, raw)
        "boot-servicos" -> packageDiscoveryReport(
            title = "O que inicia junto com a central",
            raw = raw,
            summaryPrefix = "Foram encontrados componentes relacionados a boot/serviços. Abra cada aplicativo para entender função e criticidade antes de qualquer mudança.",
            fallbackAction = FunctionalAction("Abrir todos os aplicativos", "Carrega o inventário completo para investigação.", ActionDestination.SCREEN, "apps")
        )
        "falhas-crashes", "logcat-curto" -> packageDiscoveryReport(
            title = "Falhas que merecem investigação",
            raw = raw,
            summaryPrefix = "O CUSTOMROM procurou packages citados em crashes, ANRs e eventos recentes. A presença no log não prova culpa; serve como ponto de investigação.",
            fallbackAction = FunctionalAction("Ver aplicativos rodando", "Cruza as falhas com o que está ativo agora.", ActionDestination.APPS_FILTER, "Rodando")
        )
        "wakelocks-alarmes" -> packageDiscoveryReport(
            title = "Quem pode estar acordando a central",
            raw = raw,
            summaryPrefix = "Wakelocks e alarmes foram convertidos em possíveis owners. Abra o package antes de decidir se o comportamento é esperado.",
            fallbackAction = FunctionalAction("Ver jobs em segundo plano", "Complementa a análise de atividade persistente.", ActionDestination.RECIPE, "jobs-agendados")
        )
        "jobs-agendados" -> packageDiscoveryReport(
            title = "Quem agenda trabalho em segundo plano",
            raw = raw,
            summaryPrefix = "Jobs encontrados foram associados a packages quando possível. Use o detalhe do aplicativo para entender dependências e consumo.",
            fallbackAction = FunctionalAction("Ver wakelocks e alarmes", "Cruza jobs com eventos que acordam a central.", ActionDestination.RECIPE, "wakelocks-alarmes")
        )
        "pacotes-servicos", "apps-terceiros", "apps-desativados" -> packageDiscoveryReport(
            title = "Aplicativos encontrados",
            raw = raw,
            summaryPrefix = "A lista técnica foi transformada em atalhos para a área de Aplicativos, onde criticidade, confiança e ações reversíveis ficam no contexto correto.",
            fallbackAction = FunctionalAction("Abrir Aplicativos", "Mostra o inventário visual da TayTech.", ActionDestination.SCREEN, "apps")
        )
        "animacoes-off", "animacoes-on" -> simpleReport(
            title = "Animações atualizadas",
            summary = "A mudança foi executada. Confira o estado atual antes de considerar o ajuste encerrado.",
            actions = listOf(FunctionalAction("Conferir animações", "Lê as três escalas atuais.", ActionDestination.RECIPE, "animacoes-status"))
        )
        "rotacao-auto-on", "rotacao-auto-off" -> simpleReport(
            title = "Rotação atualizada",
            summary = "A configuração de rotação foi alterada de forma reversível. Confira como a TayTech está configurada agora.",
            actions = listOf(FunctionalAction("Conferir tela e rotação", "Lê rotação, resolução, densidade e timeout.", ActionDestination.RECIPE, "tela-config"))
        )
        "stayon-on", "stayon-off" -> simpleReport(
            title = "Política de tela atualizada",
            summary = "O comportamento de suspensão durante alimentação foi alterado. Valide o estado de energia antes de encerrar.",
            actions = listOf(FunctionalAction("Conferir energia e wake", "Lê PowerManager, bateria e device idle.", ActionDestination.RECIPE, "energia-power"))
        )
        "rede-adb" -> simpleReport(
            title = "Diagnóstico de conexão concluído",
            summary = "A evidência de rede e ADB está pronta. Se o problema for intermitente, compare este estado com uma nova coleta quando a falha acontecer.",
            actions = listOf(
                FunctionalAction("Repetir diagnóstico", "Executa a mesma leitura novamente.", ActionDestination.RECIPE, "rede-adb"),
                FunctionalAction("Abrir Terminal", "Permite testar um comando simples de ida e volta.", ActionDestination.SCREEN, "terminal")
            )
        )
        "fluidez-gfx" -> simpleReport(
            title = "Coleta de fluidez concluída",
            summary = "SurfaceFlinger, gfxinfo e janela atual foram coletados. Agora vale cruzar renderização com os processos que estão consumindo CPU.",
            actions = listOf(
                FunctionalAction("Ver processos pesados", "Cruza engasgos com CPU/processos.", ActionDestination.RECIPE, "processos"),
                FunctionalAction("Ver app em primeiro plano", "Confirma quem estava ocupando a tela.", ActionDestination.RECIPE, "atividade-atual")
            )
        )
        "thermal" -> simpleReport(
            title = "Leitura térmica concluída",
            summary = "O estado térmico foi coletado. Se houver lentidão ao mesmo tempo, cruze esta evidência com CPU e memória.",
            actions = listOf(FunctionalAction("Cruzar com lentidão", "Executa o diagnóstico composto de desempenho.", ActionDestination.RECIPE, "diagnostico-lentidao"))
        )
        else -> genericReport(recipeId, raw)
    }

    private fun performanceReport(recipeId: String, raw: String): ActionableReport {
        val totalKb = metric(raw, "MemTotal")
        val availableKb = metric(raw, "MemAvailable")
        val swapTotalKb = metric(raw, "SwapTotal")
        val swapFreeKb = metric(raw, "SwapFree")
        val findings = mutableListOf<String>()

        if (totalKb != null && availableKb != null && totalKb > 0) {
            val pct = (availableKb * 100.0 / totalKb).toInt()
            findings += "Memória disponível: ${availableKb / 1024} MB de ${totalKb / 1024} MB (${pct}%)."
            if (pct < 15) findings += "A memória disponível está baixa nesta fotografia; vale investigar os maiores consumidores antes de alterar packages."
            else if (pct < 25) findings += "A margem de memória está apertada nesta fotografia e merece correlação com processos e swap."
        }
        if (swapTotalKb != null && swapTotalKb > 0 && swapFreeKb != null) {
            val used = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
            findings += "Swap/ZRAM em uso: ${used / 1024} MB de ${swapTotalKb / 1024} MB."
        }

        val cpuOwners = cpuPackageRegex.findAll(raw)
            .mapNotNull { match ->
                val cpu = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val owner = match.groupValues[2].trimEnd(':')
                if (owner == "top" || owner.startsWith("android.hardware.")) null else cpu to owner
            }
            .distinctBy { it.second }
            .sortedByDescending { it.first }
            .take(8)
            .toList()

        cpuOwners.take(5).forEach { (cpu, owner) ->
            findings += "${formatCpu(cpu)}% de CPU na coleta: $owner"
        }

        val actions = mutableListOf<FunctionalAction>()
        cpuOwners.take(6).forEach { (cpu, owner) ->
            if (owner.contains('.')) {
                actions += FunctionalAction(
                    label = "Investigar ${PackageIntelligence.friendlyName(owner)}",
                    detail = "${formatCpu(cpu)}% de CPU nesta coleta · abrir no Apps sem alterar nada.",
                    destination = ActionDestination.PACKAGE,
                    target = owner
                )
            }
        }
        actions += FunctionalAction("Ver todos os apps rodando", "Abre o inventário filtrado pelos processos ativos.", ActionDestination.APPS_FILTER, "Rodando")
        if (recipeId != "wakelocks-alarmes") {
            actions += FunctionalAction("Quem acorda a central?", "Cruza consumo com wakelocks e alarmes.", ActionDestination.RECIPE, "wakelocks-alarmes")
        }
        actions += FunctionalAction("Comparar depois", "Repita a mesma coleta após uma alteração reversível para medir efeito.", ActionDestination.RECIPE, recipeId)

        val summary = when {
            cpuOwners.isNotEmpty() -> "A coleta encontrou consumidores claros de CPU. O próximo passo é abrir os maiores owners e descobrir se são aplicativos comuns, serviços essenciais ou componentes automotivos."
            availableKb != null -> "A fotografia de memória foi interpretada. Nenhuma mudança é sugerida automaticamente; use os próximos passos para investigar causa antes de agir."
            else -> "A coleta terminou, mas esta build não expôs métricas suficientes para uma conclusão estruturada. A evidência técnica continua disponível."
        }
        return ActionableReport(
            title = if (recipeId == "diagnostico-lentidao") "O que está pesando agora" else "Desempenho interpretado",
            summary = summary,
            findings = findings.ifEmpty { listOf("Nenhum indicador estruturado pôde ser extraído desta saída.") },
            actions = actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(12)
        )
    }

    private fun packageDiscoveryReport(
        title: String,
        raw: String,
        summaryPrefix: String,
        fallbackAction: FunctionalAction
    ): ActionableReport {
        val packages = extractPackages(raw)
        val preferred = packages.sortedWith(compareBy<String> { packagePriority(it) }.thenBy { it })
        val findings = if (preferred.isEmpty()) {
            listOf("Nenhum package pôde ser extraído automaticamente desta saída. A evidência técnica foi preservada para inspeção.")
        } else {
            listOf("${preferred.size} packages identificados na evidência.") + preferred.take(8).map { PackageIntelligence.friendlyName(it) + " · " + it }
        }
        val actions = preferred.take(10).map { pkg ->
            FunctionalAction(
                label = "Abrir ${PackageIntelligence.friendlyName(pkg)}",
                detail = "Ver criticidade, confiança, motivos e ações permitidas.",
                destination = ActionDestination.PACKAGE,
                target = pkg
            )
        }.toMutableList()
        actions += fallbackAction
        return ActionableReport(
            title = title,
            summary = if (preferred.isEmpty()) summaryPrefix else "$summaryPrefix ${preferred.size} packages foram transformados em objetos navegáveis.",
            findings = findings,
            actions = actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(12)
        )
    }

    private fun simpleReport(title: String, summary: String, actions: List<FunctionalAction>): ActionableReport =
        ActionableReport(title, summary, emptyList(), actions)

    private fun genericReport(recipeId: String, raw: String): ActionableReport {
        val packages = extractPackages(raw).take(6)
        val firstUseful = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("===") && !it.startsWith("---") }
            ?.take(220)
        val actions = packages.map { pkg ->
            FunctionalAction("Abrir ${PackageIntelligence.friendlyName(pkg)}", "Package identificado na saída.", ActionDestination.PACKAGE, pkg)
        }.toMutableList()
        if (actions.isEmpty()) actions += FunctionalAction("Abrir Diagnóstico", "Continue a investigação com uma pergunta de alto nível.", ActionDestination.SCREEN, "diagnostics")
        return ActionableReport(
            title = "Resultado interpretado",
            summary = firstUseful ?: "A operação foi concluída. A evidência técnica continua disponível se você quiser aprofundar.",
            findings = if (packages.isEmpty()) emptyList() else listOf("Packages identificados: ${packages.joinToString()}."),
            actions = actions,
            evidenceLabel = "Ver saída técnica completa"
        )
    }

    private fun extractPackages(raw: String): List<String> = packageRegex.findAll(raw)
        .map { it.value.trimEnd(':', ',', ')', ']', '}') }
        .filter { candidate ->
            candidate.count { it == '.' } >= 2 &&
                !candidate.contains("intent.action", true) &&
                !candidate.startsWith("java.") &&
                !candidate.startsWith("kotlin.")
        }
        .distinct()
        .take(80)
        .toList()

    private fun packagePriority(pkg: String): Int {
        val p = pkg.lowercase(Locale.ROOT)
        return when {
            p.startsWith("com.jancar") || p.contains("canbus") || p.contains("mcu") || p.contains("hiworld") -> 0
            p.startsWith("com.omegas") -> 1
            p.startsWith("com.google") -> 2
            p.startsWith("com.android") || p.startsWith("android") -> 4
            else -> 3
        }
    }

    private fun metric(raw: String, key: String): Long? =
        Regex("(?m)^\\s*${Regex.escape(key)}:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun formatCpu(cpu: Double): String = if (cpu % 1.0 == 0.0) cpu.toInt().toString() else String.format(Locale.US, "%.1f", cpu)
}
