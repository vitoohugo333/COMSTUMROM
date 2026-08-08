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
 * Converte saída técnica em uma jornada humana. O engine nunca executa mudanças:
 * ele interpreta evidência e devolve próximos passos que continuam passando pela
 * política de risco e pela confirmação da Activity.
 */
object FunctionalActionEngine {
    private val packageRegex = Regex("\\b(?:[A-Za-z][A-Za-z0-9_]*\\.){2,}[A-Za-z0-9_:-]+\\b")
    private val cpuPackageRegex = Regex("(?m)^\\s*([0-9]+(?:\\.[0-9]+)?)%\\s+\\d+/([A-Za-z0-9._:-]+)")

    fun analyze(recipeId: String, raw: String): ActionableReport = when (recipeId) {
        "diagnostico-lentidao", "processos", "memoria-zram" -> performanceReport(recipeId, raw)

        "boot-servicos" -> packageDiscoveryReport(
            "O que inicia junto com a central",
            raw,
            "Foram encontrados componentes relacionados ao boot e a serviços. Toque em qualquer package para analisar e agir sem sair deste fluxo.",
            FunctionalAction("Abrir inventário completo", "Use Apps apenas quando quiser explorar o conjunto inteiro.", ActionDestination.SCREEN, "apps")
        )

        "falhas-crashes", "logcat-curto" -> packageDiscoveryReport(
            "Falhas que merecem investigação",
            raw,
            "Packages citados em crashes, ANRs ou eventos recentes foram transformados em objetos investigáveis. Presença no log não prova culpa.",
            FunctionalAction("Ver apps rodando", "Cruza as falhas com o que está ativo agora.", ActionDestination.APPS_FILTER, "Rodando")
        )

        "wakelocks-alarmes" -> packageDiscoveryReport(
            "Quem pode estar acordando a central",
            raw,
            "Wakelocks e alarmes foram convertidos em possíveis owners. Abra o package para entender função e controle disponível.",
            FunctionalAction("Cruzar com jobs", "Complementa a análise de atividade persistente.", ActionDestination.RECIPE, "jobs-agendados")
        )

        "jobs-agendados" -> packageDiscoveryReport(
            "Quem agenda trabalho em segundo plano",
            raw,
            "Jobs foram ligados a packages quando possível. O próximo passo é inspecionar o owner e decidir se o comportamento faz sentido.",
            FunctionalAction("Cruzar com wakelocks", "Procura eventos que acordam a central.", ActionDestination.RECIPE, "wakelocks-alarmes")
        )

        "pacotes-servicos", "apps-terceiros", "apps-desativados", "pacotes-instaladores" -> packageDiscoveryReport(
            "Aplicativos encontrados",
            raw,
            "A lista técnica foi transformada em packages acionáveis com detalhe contextual, criticidade e controles reversíveis.",
            FunctionalAction("Abrir Apps", "Mostra o inventário visual completo.", ActionDestination.SCREEN, "apps")
        )

        "foreground-services" -> packageDiscoveryReport(
            "Serviços que permanecem ativos",
            raw,
            "Serviços foreground/persistentes foram associados a packages quando o Android expôs o owner. Isso ajuda a descobrir o que continua trabalhando fora da tela.",
            FunctionalAction("Cruzar com CPU", "Verifica se persistência também está consumindo processamento.", ActionDestination.RECIPE, "processos")
        )

        "appops-auditoria" -> packageDiscoveryReport(
            "Apps com operações especiais",
            raw,
            "AppOps citou packages com operações registradas. Isso não significa problema; serve para entender o papel de cada app antes de alterar algo.",
            FunctionalAction("Abrir inventário completo", "Explora todos os packages com a inteligência local.", ActionDestination.SCREEN, "apps")
        )

        "batterystats-apps" -> packageDiscoveryReport(
            "Atividade registrada pela bateria",
            raw,
            "Batterystats citou owners com atividade desde a referência disponível. Abra os candidatos e cruze com CPU, jobs e wakelocks.",
            FunctionalAction("Cruzar com wakelocks", "Investiga quem mantém ou acorda a central.", ActionDestination.RECIPE, "wakelocks-alarmes")
        )

        "uso-apps" -> packageDiscoveryReport(
            "Aplicativos usados recentemente",
            raw,
            "UsageStats ajuda a separar software realmente usado de componentes que apenas ficam residentes.",
            FunctionalAction("Ver apps rodando", "Compara uso recente com processos ativos agora.", ActionDestination.APPS_FILTER, "Rodando")
        )

        "deviceidle-whitelist" -> packageDiscoveryReport(
            "Exceções de economia de energia",
            raw,
            "Packages liberados das restrições de idle/Doze foram extraídos quando possível. Uma exceção pode ser legítima ou explicar atividade persistente.",
            FunctionalAction("Ver energia e wake", "Cruza a whitelist com o estado de energia.", ActionDestination.RECIPE, "energia-power")
        )

        "launchers-disponiveis", "launcher-defaults" -> packageDiscoveryReport(
            "Launchers e handlers HOME",
            raw,
            "Os candidatos HOME foram transformados em objetos acionáveis. Você pode analisar cada launcher e usar controle avançado sem trocar de tela.",
            FunctionalAction("Ver atividade atual", "Confirma qual app está ocupando a tela agora.", ActionDestination.RECIPE, "atividade-atual")
        )

        "processos-oom" -> packageDiscoveryReport(
            "Prioridade dos processos",
            raw,
            "O estado do ActivityManager foi associado a packages quando possível. Abra owners persistentes ou prioritários para entender dependências.",
            FunctionalAction("Ver CPU agora", "Compara importância com consumo instantâneo.", ActionDestination.RECIPE, "processos")
        )

        "notificacoes-status", "accessibility-notification" -> packageDiscoveryReport(
            "Notificações e listeners",
            raw,
            "Packages citados pelos serviços de notificação/acessibilidade foram transformados em pontos de investigação.",
            FunctionalAction("Abrir Apps", "Explora o inventário completo.", ActionDestination.SCREEN, "apps")
        )

        "animacoes-off", "animacoes-on" -> simpleReport(
            "Animações atualizadas",
            "A mudança foi executada. Confira o estado atual antes de considerar o ajuste encerrado.",
            listOf(FunctionalAction("Conferir animações", "Lê as três escalas atuais.", ActionDestination.RECIPE, "animacoes-status"))
        )

        "rotacao-auto-on", "rotacao-auto-off" -> simpleReport(
            "Rotação atualizada",
            "A configuração foi alterada de forma reversível. Confira como a TayTech está configurada agora.",
            listOf(FunctionalAction("Conferir tela e rotação", "Lê rotação, resolução, densidade e timeout.", ActionDestination.RECIPE, "tela-config"))
        )

        "stayon-on", "stayon-off" -> simpleReport(
            "Política de tela atualizada",
            "O comportamento de suspensão durante alimentação foi alterado. Valide energia e wake antes de encerrar.",
            listOf(FunctionalAction("Conferir energia e wake", "Lê PowerManager, bateria e device idle.", ActionDestination.RECIPE, "energia-power"))
        )

        "rede-adb" -> simpleReport(
            "Diagnóstico de conexão concluído",
            "A evidência de rede e ADB está pronta. Se a falha for intermitente, compare com uma nova coleta quando ela acontecer.",
            listOf(
                FunctionalAction("Repetir diagnóstico", "Executa a mesma leitura novamente.", ActionDestination.RECIPE, "rede-adb"),
                FunctionalAction("Abrir Terminal", "Permite testar um comando simples de ida e volta.", ActionDestination.SCREEN, "terminal")
            )
        )

        "fluidez-gfx" -> simpleReport(
            "Coleta de fluidez concluída",
            "SurfaceFlinger, gfxinfo e janela atual foram coletados. Cruze renderização com processos que consomem CPU.",
            listOf(
                FunctionalAction("Ver processos pesados", "Cruza engasgos com CPU/processos.", ActionDestination.RECIPE, "processos"),
                FunctionalAction("Ver app em primeiro plano", "Confirma quem estava ocupando a tela.", ActionDestination.RECIPE, "atividade-atual")
            )
        )

        "thermal" -> simpleReport(
            "Leitura térmica concluída",
            "O estado térmico foi coletado. Se houver lentidão ao mesmo tempo, cruze com CPU e memória.",
            listOf(FunctionalAction("Cruzar com lentidão", "Executa o diagnóstico composto de desempenho.", ActionDestination.RECIPE, "diagnostico-lentidao"))
        )

        "webview-provider" -> simpleReport(
            "WebView inspecionado",
            "O provider e o estado de atualização do WebView foram coletados. Isso ajuda em tela branca, crash ou renderização anormal de apps híbridos.",
            listOf(FunctionalAction("Ver crashes recentes", "Procura falhas correlacionadas.", ActionDestination.RECIPE, "falhas-crashes"))
        )

        "localizacao-gnss" -> simpleReport(
            "Localização e GNSS observados",
            "Providers e estado do serviço de localização foram coletados sem alteração.",
            listOf(
                FunctionalAction("Ver apps rodando", "Investiga quem pode estar usando localização.", ActionDestination.APPS_FILTER, "Rodando"),
                FunctionalAction("Ver sensores", "Cruza com sensores disponíveis.", ActionDestination.RECIPE, "sensores-status")
            )
        )

        "sensores-status" -> simpleReport(
            "Sensores observados",
            "SensorService foi consultado sem alteração. Use a evidência para identificar sensores e clientes ativos.",
            listOf(FunctionalAction("Ver processos rodando", "Cruza clientes com processos atuais.", ActionDestination.APPS_FILTER, "Rodando"))
        )

        "camera-status" -> simpleReport(
            "Câmeras observadas",
            "O serviço de câmera foi consultado sem alteração. Clientes ativos podem ser investigados pelo package correspondente.",
            listOf(FunctionalAction("Ver apps rodando", "Cruza clientes de câmera com processos atuais.", ActionDestination.APPS_FILTER, "Rodando"))
        )

        "rede-netstats", "ethernet-status" -> simpleReport(
            "Rede observada",
            "A pilha de rede foi consultada. Cruze interfaces/tráfego com processos e conectividade se estiver investigando atraso ou atividade em segundo plano.",
            listOf(
                FunctionalAction("Diagnosticar ADB e rede", "Confere endpoint, Wi-Fi e portas.", ActionDestination.RECIPE, "rede-adb"),
                FunctionalAction("Ver processos", "Cruza rede com atividade atual.", ActionDestination.RECIPE, "processos")
            )
        )

        "background-limits" -> simpleReport(
            "Limites de segundo plano lidos",
            "As políticas globais de processos/cache foram consultadas sem alteração.",
            listOf(
                FunctionalAction("Ver jobs", "Descobre quem agenda trabalho.", ActionDestination.RECIPE, "jobs-agendados"),
                FunctionalAction("Ver serviços persistentes", "Descobre quem permanece ativo.", ActionDestination.RECIPE, "foreground-services")
            )
        )

        "device-policy" -> simpleReport(
            "Políticas do dispositivo lidas",
            "Administradores e restrições foram coletados. Se uma mudança falhar por política, esta evidência ajuda a explicar o motivo.",
            listOf(FunctionalAction("Abrir Configurações", "Abre a superfície de configuração da TayTech.", ActionDestination.RECIPE, "abrir-configuracoes", "AMARELO"))
        )

        "tempo-sistema" -> simpleReport(
            "Horário do sistema lido",
            "Data, timezone e políticas automáticas foram coletados sem alteração.",
            listOf(FunctionalAction("Abrir Configurações", "Permite revisar data/hora manualmente na TayTech.", ActionDestination.RECIPE, "abrir-configuracoes", "AMARELO"))
        )

        else -> genericReport(raw)
    }

    private fun performanceReport(recipeId: String, raw: String): ActionableReport {
        val totalKb = metric(raw, "MemTotal")
        val availableKb = metric(raw, "MemAvailable")
        val swapTotalKb = metric(raw, "SwapTotal")
        val swapFreeKb = metric(raw, "SwapFree")
        val findings = mutableListOf<String>()

        if (totalKb != null && availableKb != null && totalKb > 0) {
            val pct = (availableKb * 100.0 / totalKb).toInt()
            findings += "Memória disponível: ${availableKb / 1024} MB de ${totalKb / 1024} MB ($pct%)."
            if (pct < 15) findings += "A memória disponível está baixa nesta fotografia; investigue consumidores antes de alterar packages."
            else if (pct < 25) findings += "A margem de memória está apertada e merece correlação com processos e swap."
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
            .take(12)
            .toList()

        cpuOwners.take(6).forEach { (cpu, owner) -> findings += "${formatCpu(cpu)}% de CPU na coleta: $owner" }

        val actions = mutableListOf<FunctionalAction>()
        cpuOwners.take(10).forEach { (cpu, owner) ->
            if (owner.contains('.')) {
                actions += FunctionalAction(
                    "Investigar ${PackageIntelligence.friendlyName(owner)}",
                    "${formatCpu(cpu)}% de CPU nesta coleta · abrir detalhe contextual sem alterar nada.",
                    ActionDestination.PACKAGE,
                    owner
                )
            }
        }
        actions += FunctionalAction("Ver todos os apps rodando", "Abre o inventário filtrado pelos processos ativos.", ActionDestination.APPS_FILTER, "Rodando")
        actions += FunctionalAction("Quem acorda a central?", "Cruza consumo com wakelocks e alarmes.", ActionDestination.RECIPE, "wakelocks-alarmes")
        actions += FunctionalAction("Comparar depois", "Repete a mesma coleta após uma alteração reversível.", ActionDestination.RECIPE, recipeId)

        val summary = when {
            cpuOwners.isNotEmpty() -> "A coleta encontrou consumidores claros de CPU. Abra os maiores owners e decida se o comportamento é esperado."
            availableKb != null -> "A fotografia de memória foi interpretada. Use os próximos passos para investigar causa antes de agir."
            else -> "A coleta terminou, mas não expôs métricas suficientes para uma conclusão estruturada. A evidência técnica continua disponível."
        }
        return ActionableReport(
            if (recipeId == "diagnostico-lentidao") "O que está pesando agora" else "Desempenho interpretado",
            summary,
            findings.ifEmpty { listOf("Nenhum indicador estruturado pôde ser extraído desta saída.") },
            actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64)
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
            listOf("Nenhum package pôde ser extraído automaticamente desta saída. A evidência técnica foi preservada.")
        } else {
            listOf("${preferred.size} packages identificados na evidência.") + preferred.take(10).map { "${PackageIntelligence.friendlyName(it)} · $it" }
        }
        val actions = preferred.take(60).map { pkg ->
            FunctionalAction(
                "Abrir ${PackageIntelligence.friendlyName(pkg)}",
                "Ver criticidade, confiança, motivos e ações no próprio contexto.",
                ActionDestination.PACKAGE,
                pkg
            )
        }.toMutableList()
        actions += fallbackAction
        return ActionableReport(
            title,
            if (preferred.isEmpty()) summaryPrefix else "$summaryPrefix ${preferred.size} packages foram transformados em objetos navegáveis.",
            findings,
            actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64)
        )
    }

    private fun simpleReport(title: String, summary: String, actions: List<FunctionalAction>): ActionableReport =
        ActionableReport(title, summary, emptyList(), actions)

    private fun genericReport(raw: String): ActionableReport {
        val packages = extractPackages(raw).take(20)
        val firstUseful = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("===") && !it.startsWith("---") }
            ?.take(220)
        val actions = packages.map { pkg ->
            FunctionalAction("Abrir ${PackageIntelligence.friendlyName(pkg)}", "Package identificado na evidência.", ActionDestination.PACKAGE, pkg)
        }.toMutableList()
        if (actions.isEmpty()) actions += FunctionalAction("Abrir Diagnóstico", "Continue a investigação com uma pergunta de alto nível.", ActionDestination.SCREEN, "diagnostics")
        return ActionableReport(
            "Resultado interpretado",
            firstUseful ?: "A operação foi concluída. A evidência técnica continua disponível se você quiser aprofundar.",
            if (packages.isEmpty()) emptyList() else listOf("${packages.size} packages identificados nesta saída."),
            actions.take(64),
            "Ver saída técnica completa"
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
        .take(100)
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

    private fun formatCpu(cpu: Double): String =
        if (cpu % 1.0 == 0.0) cpu.toInt().toString() else String.format(Locale.US, "%.1f", cpu)
}
