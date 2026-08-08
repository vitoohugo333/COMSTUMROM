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
    private val ignoredOptimizationOwners = setOf("com.omegas.v7.test")

    fun analyze(recipeId: String, raw: String): ActionableReport = when (recipeId) {
        "diagnostico-lentidao", "processos", "memoria-zram" -> performanceReport(recipeId, raw)
        "otimizacao-pragmatica" -> pragmaticOptimizationReport(raw)
        "adb-persistencia-diagnostico", "adb-persistencia-aplicar", "adb-persistencia-restaurar" -> adbPersistenceReport(recipeId, raw)
        "log-storm" -> logStormReport(raw)
        "customrom-agent-status", "customrom-agent-preparar" -> agentStatusReport(raw)

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

        "spotify-diagnostico" -> spotifyReport(raw)

        "tempo-sistema" -> simpleReport(
            "Horário do sistema lido",
            "Data, timezone e políticas automáticas foram coletados sem alteração.",
            listOf(FunctionalAction("Abrir Configurações", "Permite revisar data/hora manualmente na TayTech.", ActionDestination.RECIPE, "abrir-configuracoes", "AMARELO"))
        )

        else -> genericReport(raw)
    }


    private fun pragmaticOptimizationReport(raw: String): ActionableReport {
        val findings = mutableListOf<String>()
        val actions = mutableListOf<FunctionalAction>()
        val home = lineValue(raw, "HOME_CURRENT")
        val homePkg = home?.substringBefore('/')?.trim()
        if (!homePkg.isNullOrBlank()) {
            findings += when (homePkg) {
                "ginlemon.flowerfree" -> "HOME atual confirmado: Smart Launcher. Ele é preferência do proprietário e fica protegido."
                "com.jancar.launcher" -> "HOME atual ainda é o launcher OEM Jancar. Não o desative antes de definir o Smart Launcher como HOME."
                else -> "HOME atual: $homePkg."
            }
        }

        val totalKb = metric(raw, "MemTotal")
        val availableKb = metric(raw, "MemAvailable")
        val swapTotalKb = metric(raw, "SwapTotal")
        val swapFreeKb = metric(raw, "SwapFree")
        if (totalKb != null && availableKb != null && totalKb > 0) {
            val pct = (availableKb * 100.0 / totalKb).toInt()
            findings += "Memória disponível: ${availableKb / 1024} MB de ${totalKb / 1024} MB ($pct%)."
        }
        if (swapTotalKb != null && swapFreeKb != null && swapTotalKb > 0) {
            findings += "Swap/ZRAM em uso: ${(swapTotalKb - swapFreeKb).coerceAtLeast(0) / 1024} MB de ${swapTotalKb / 1024} MB."
        }

        val cpuOwners = cpuPackageRegex.findAll(raw)
            .mapNotNull { match ->
                val cpu = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val owner = match.groupValues[2].trimEnd(':')
                if (owner == "top" || owner.startsWith("android.hardware.") || ignoredOptimizationOwners.contains(owner.substringBefore(':'))) null else cpu to owner
            }
            .distinctBy { it.second }
            .sortedByDescending { it.first }
            .take(12)
            .toList()
        cpuOwners.take(6).forEach { (cpu, owner) -> findings += "${formatCpu(cpu)}% de CPU: $owner" }

        val webviewBusy = cpuOwners.any { (_, owner) -> owner.startsWith("com.google.android.webview") } || raw.contains("com.google.android.webview:sandboxed_process0")
        val googleAppSeen = raw.contains("com.google.android.googlequicksearchbox")
        if (webviewBusy && googleAppSeen) {
            findings += "WebView pesado correlacionado ao Google App nesta fotografia. O próximo teste pode ser feito no próprio detalhe do Google App."
            actions += FunctionalAction("Abrir Google App", "Analisar, parar temporariamente ou desativar reversivelmente com confirmação.", ActionDestination.PACKAGE, "com.google.android.googlequicksearchbox")
        }
        cpuOwners.firstOrNull { it.second.substringBefore(':') == "com.google.android.apps.docs" }?.let { (cpu, _) ->
            findings += "Google Drive aparece consumindo ${formatCpu(cpu)}% de CPU nesta fotografia."
            actions += FunctionalAction("Abrir Google Drive", "Teste temporário e controle reversível ficam no detalhe do package.", ActionDestination.PACKAGE, "com.google.android.apps.docs")
        }

        if (raw.contains("com.jancar.launcher")) {
            actions += FunctionalAction("Abrir launcher nativo TayTech", "Launcher OEM: pode ser testado/desativado no usuário 0 se o Smart Launcher estiver como HOME.", ActionDestination.PACKAGE, "com.jancar.launcher", "AMARELO")
        }

        val wobble = lineValue(raw, "LOGTAG_WOBLE")?.toIntOrNull() ?: 0
        if (wobble >= 100) {
            findings += "Tempestade de log detectada: $wobble mensagens WOBLE na janela coletada. Isso merece investigação própria antes de mexer em Bluetooth/driver."
            actions += FunctionalAction("Investigar tempestade WOBLE", "Descobre volume, PIDs e contexto sem alterar serviços automotivos.", ActionDestination.RECIPE, "log-storm")
        }

        actions += FunctionalAction("Ver apps rodando", "Explora somente os processos ativos agora.", ActionDestination.APPS_FILTER, "Rodando")
        actions += FunctionalAction("Comparar depois", "Repete a fotografia depois de uma única mudança reversível.", ActionDestination.RECIPE, "otimizacao-pragmatica")

        val summary = when {
            cpuOwners.isNotEmpty() -> "A central foi analisada em um único fluxo. O ÔMEGAS foi excluído automaticamente e os próximos alvos são apresentados como ações, não como comandos para copiar."
            else -> "A coleta pragmática terminou. O ÔMEGAS foi excluído da otimização e a evidência técnica ficou preservada."
        }
        return ActionableReport("Analisar e enxugar a central", summary, findings, actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64))
    }

    private fun adbPersistenceReport(recipeId: String, raw: String): ActionableReport {
        val findings = mutableListOf<String>()
        val actions = mutableListOf<FunctionalAction>()
        val enabled = lineValue(raw, "adb_enabled")
        val wifi = lineValue(raw, "adb_wifi_enabled")
        val persistPort = lineValue(raw, "persist_port")
        val servicePort = lineValue(raw, "service_port")
        val agentInstalled = lineValue(raw, "agent_installed") == "1"
        val rollback = lineValue(raw, "rollback_available") == "1"

        findings += "ADB do Android: ${enabled ?: "?"} · Wireless debugging: ${wifi ?: "?"}."
        findings += "Porta temporária: ${servicePort?.ifBlank { "não definida" } ?: "?"} · porta persistente: ${persistPort?.ifBlank { "não definida" } ?: "?"}."
        findings += if (agentInstalled) "CUSTOMROM Agent detectado na TayTech." else "CUSTOMROM Agent ainda não foi detectado na TayTech."

        val ideal = enabled == "1" && wifi == "1" && persistPort == "5555"
        if (ideal) {
            findings += "A ROM aceitou o estado ideal desta estratégia: ADB ligado, Wireless debugging ligado e persist.adb.tcp.port=5555. A prova definitiva ainda é reiniciar a TayTech."
        } else if (recipeId == "adb-persistencia-aplicar") {
            findings += "A tentativa foi aplicada e verificada com os valores acima. O que a ROM recusou permanece explícito; nenhuma falha é mascarada."
        }

        if (!ideal) actions += FunctionalAction("Tentar ADB persistente", "Salva rollback, mantém ADB/Wireless ligados e tenta persistir 5555 sem reiniciar o daemon.", ActionDestination.RECIPE, "adb-persistencia-aplicar", "AMARELO")
        actions += if (agentInstalled) {
            FunctionalAction("Preparar CUSTOMROM Agent", "Concede WRITE_SECURE_SETTINGS e manda o companion aplicar a recuperação de Wireless ADB.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        } else {
            FunctionalAction("Instalar CUSTOMROM Agent", "O APK companion já está dentro do CUSTOMROM no S23; instalar e preparar é um fluxo de um toque.", ActionDestination.RECIPE, "customrom-agent-instalar", "AMARELO")
        }
        actions += FunctionalAction("Abrir opções de desenvolvedor", "Fallback visual direto na TayTech.", ActionDestination.RECIPE, "abrir-depuracao-sem-fio", "AMARELO")
        if (rollback) actions += FunctionalAction("Restaurar configuração anterior", "Executa o rollback salvo antes da tentativa de persistência.", ActionDestination.RECIPE, "adb-persistencia-restaurar", "AMARELO")
        actions += FunctionalAction("Conferir novamente", "Lê tudo em uma única coleta.", ActionDestination.RECIPE, "adb-persistencia-diagnostico")

        return ActionableReport(
            "ADB após reinício",
            if (ideal) "Configuração persistente aceita nesta sessão; falta apenas o teste físico de reboot." else "O CUSTOMROM identificou o melhor caminho disponível sem exigir que você monte comandos manualmente.",
            findings,
            actions.distinctBy { "${it.destination}:${it.target}:${it.label}" }.take(64)
        )
    }

    private fun logStormReport(raw: String): ActionableReport {
        val count = lineValue(raw, "LOGTAG_WOBLE")?.toIntOrNull() ?: 0
        val findings = mutableListOf<String>()
        findings += if (count > 0) "$count mensagens WOBLE foram contadas na janela recente." else "Nenhuma mensagem WOBLE foi contada na janela recente."
        if (count >= 100) findings += "Volume alto confirmado. Como WOBLE pode vir de camada wireless/driver, o CUSTOMROM não desativa Bluetooth ou serviço automotivo automaticamente."
        val actions = listOf(
            FunctionalAction("Cruzar com processos", "Vê CPU/processos no mesmo contexto.", ActionDestination.RECIPE, "otimizacao-pragmatica"),
            FunctionalAction("Ver Bluetooth", "Lê o estado Bluetooth sem alterar nada.", ActionDestination.RECIPE, "bluetooth-status"),
            FunctionalAction("Repetir tempestade de logs", "Mede novamente para confirmar recorrência.", ActionDestination.RECIPE, "log-storm")
        )
        return ActionableReport("Tempestade de logs", "O volume foi medido e separado da decisão de desativar qualquer componente.", findings, actions)
    }

    private fun agentStatusReport(raw: String): ActionableReport {
        val installed = lineValue(raw, "agent_installed") == "1"
        val granted = lineValue(raw, "write_secure_settings") == "granted"
        val findings = mutableListOf<String>()
        findings += if (installed) "CUSTOMROM Agent instalado." else "CUSTOMROM Agent não instalado. O artifact V6 inclui o APK companion para a TayTech."
        if (installed) findings += if (granted) "Permissão WRITE_SECURE_SETTINGS concedida." else "Agent instalado, mas ainda sem WRITE_SECURE_SETTINGS."
        val actions = mutableListOf<FunctionalAction>()
        when {
            !installed -> actions += FunctionalAction("Instalar Agent na TayTech", "Transfere e instala o companion embutido usando a conexão ADB atual.", ActionDestination.RECIPE, "customrom-agent-instalar", "AMARELO")
            !granted -> actions += FunctionalAction("Preparar Agent agora", "Concede a permissão de desenvolvimento e executa o recovery uma vez.", ActionDestination.RECIPE, "customrom-agent-preparar", "AMARELO")
        }
        actions += FunctionalAction("Conferir ADB após reinício", "Volta ao diagnóstico completo de persistência.", ActionDestination.RECIPE, "adb-persistencia-diagnostico")
        return ActionableReport("CUSTOMROM Agent", if (installed && granted) "Companion pronto para tentar reativar Wireless ADB a cada boot." else "O app mostrou exatamente o que falta para automatizar o boot.", findings, actions)
    }

    private fun spotifyReport(raw: String): ActionableReport {
        val findings = mutableListOf<String>()
        val actions = mutableListOf<FunctionalAction>()

        val version = inlineValue(raw, "versionName")
        val minSdk = inlineValue(raw, "minSdk")
        val targetSdk = inlineValue(raw, "targetSdk")
        val spotifyAbi = inlineValue(raw, "primaryCpuAbi")
        val release = lineValue(raw, "system_release")
        val sdk = lineValue(raw, "system_sdk")
        val patch = lineValue(raw, "system_security_patch")
        val systemAbi = lineValue(raw, "system_abi")
        val systemAbis = lineValue(raw, "system_abilist")
        val systemAbis64 = lineValue(raw, "system_abilist64")
        val spotifyPid = lineValue(raw, "spotify_pid")

        if (version != null) {
            findings += buildString {
                append("Spotify $version")
                if (minSdk != null || targetSdk != null) append(" · minSdk ${minSdk ?: "?"} · targetSdk ${targetSdk ?: "?"}")
                append('.')
            }
        }

        if (spotifyAbi != null && systemAbis != null) {
            val compatible = systemAbis.split(',').map { it.trim() }.contains(spotifyAbi)
            if (compatible) {
                findings += "Arquitetura compatível: o Spotify está em $spotifyAbi e a central oferece $systemAbis. ABI não explica a lentidão desta instalação."
            } else {
                findings += "Possível incompatibilidade de ABI: Spotify=$spotifyAbi, central=$systemAbis. Esta divergência precisa ser tratada antes de otimização."
            }
        } else if (systemAbi != null) {
            findings += "ABI principal da central: $systemAbi${if (systemAbis64.isNullOrBlank()) " · sem ABI 64-bit anunciada" else " · ABI64 $systemAbis64"}."
        }

        if (release != null || sdk != null) {
            findings += "Framework reportado: Android ${release ?: "?"} · SDK ${sdk ?: "?"}${patch?.let { " · patch $it" } ?: ""}."
            if (release == "13" && sdk == "30") {
                findings += "Combinação não padrão confirmada: a central anuncia release 13, mas expõe SDK 30. Isso aumenta o risco de comportamento irregular em aplicativos modernos mesmo quando a instalação é formalmente compatível."
            }
        }

        if (spotifyPid.isNullOrBlank()) {
            findings += "O Spotify não estava em execução nesta coleta; CPU, renderização e áudio do app ficam inconclusivos até repetir com ele aberto."
        } else {
            findings += "Spotify ativo nesta coleta · PID $spotifyPid."
        }

        val totalKb = metric(raw, "MemTotal")
        val availableKb = metric(raw, "MemAvailable")
        val swapTotalKb = metric(raw, "SwapTotal")
        val swapFreeKb = metric(raw, "SwapFree")
        if (totalKb != null && availableKb != null && totalKb > 0) {
            val pct = (availableKb * 100.0 / totalKb).toInt()
            findings += "Memória disponível do sistema: ${availableKb / 1024} MB de ${totalKb / 1024} MB ($pct%)."
            if (pct < 25) findings += "A margem de RAM está apertada; isso pode aumentar compactação, reclaim e disputa com processos de segundo plano."
        }
        if (swapTotalKb != null && swapTotalKb > 0 && swapFreeKb != null) {
            val usedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
            findings += "Swap/ZRAM em uso: ${usedKb / 1024} MB de ${swapTotalKb / 1024} MB."
        }

        spotifyPssKb(raw)?.let { findings += "Spotify consumia aproximadamente ${it / 1024} MB de PSS no momento da coleta." }

        val cpuOwners = cpuPackageRegex.findAll(raw)
            .mapNotNull { match ->
                val cpu = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                cpu to match.groupValues[2].trimEnd(':')
            }
            .filter { (_, owner) ->
                owner == "com.spotify.music" || owner.startsWith("com.google.android.gms") || owner == "system_server" || owner == "surfaceflinger" || owner == "audioserver"
            }
            .distinctBy { it.second }
            .sortedByDescending { it.first }
            .toList()
        cpuOwners.take(8).forEach { (cpu, owner) -> findings += "CPU na fotografia: ${formatCpu(cpu)}% · $owner" }

        val spotifyCpu = cpuOwners.firstOrNull { it.second == "com.spotify.music" }?.first
        val gmsCpu = cpuOwners.firstOrNull { it.second.startsWith("com.google.android.gms") }?.first
        if (gmsCpu != null && gmsCpu >= 25.0) {
            findings += "Google Play Services está concorrendo fortemente por CPU nesta fotografia (${formatCpu(gmsCpu)}%). Isso pode degradar um app pesado mesmo sem defeito no Spotify."
        }
        if (spotifyCpu != null && spotifyCpu >= 75.0) {
            findings += "O próprio Spotify está usando perto de um núcleo inteiro ou mais nesta fotografia (${formatCpu(spotifyCpu)}%)."
        }

        val jank = Regex("Janky frames:\\s*(\\d+)\\s*\\(([0-9.]+)%\\)", RegexOption.IGNORE_CASE).find(raw)
        if (jank != null) {
            findings += "Renderização do Spotify: ${jank.groupValues[1]} frames janky (${jank.groupValues[2]}%). Isso é evidência direta de perda de fluidez na janela medida."
        }

        val spotifyGcMs = Regex("(?mi)^.*(?:m\\.spotify\\.musi|com\\.spotify\\.music).*GC.*total\\s+([0-9.]+)ms")
            .findAll(raw)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .maxOrNull()
        if (spotifyGcMs != null) {
            findings += "Maior ciclo de GC do Spotify visto nos logs recentes: ${formatMs(spotifyGcMs)} ms totais. Tempo total de GC não equivale automaticamente a uma pausa de interface."
        }

        val explicitAnr = Regex("(?mi)(ANR in com\\.spotify\\.music|am_anr.*com\\.spotify\\.music)").containsMatchIn(raw)
        val fatal = Regex("(?mi)(FATAL EXCEPTION.*(?:com\\.spotify\\.music|m\\.spotify\\.musi)|Process: com\\.spotify\\.music.*FATAL)").containsMatchIn(raw)
        when {
            explicitAnr -> findings += "Há evidência explícita de ANR do Spotify nesta coleta."
            fatal -> findings += "Há evidência explícita de crash fatal do Spotify nesta coleta."
            else -> findings += "Nenhum ANR/crash fatal do Spotify foi identificado pelos padrões estritos desta coleta. SIGQUIT/dump de stack isolado não é classificado como ANR."
        }

        if (Regex("(?mi)(OTHER KILLS BY SYSTEM.*empty|reason=13.*empty|empty for \\d+s)").containsMatchIn(raw)) {
            findings += "O histórico mostra descarte de processo vazio pelo sistema; isso é reclaim de background, não prova crash do Spotify."
        }
        if (Regex("(?mi)(A2DP.*disconnect|BluetoothA2dp.*disconnect|STATE_CONNECTED.*STATE_DISCONNECTED)").containsMatchIn(raw)) {
            findings += "Há menção recente a desconexão A2DP. Se o sintoma for corte/troca de áudio, a rota Bluetooth deve ser investigada separadamente do desempenho da UI."
        }

        actions += FunctionalAction("Abrir detalhe do Spotify", "Confere estado, logs e controles contextuais sem sair desta jornada.", ActionDestination.PACKAGE, "com.spotify.music")
        actions += FunctionalAction("Investigar Google Play Services", "Cruza a concorrência de CPU/memória com o principal serviço Google.", ActionDestination.PACKAGE, "com.google.android.gms")
        actions += FunctionalAction("Cruzar com CPU do sistema", "Compara Spotify com todos os consumidores pesados.", ActionDestination.RECIPE, "processos")
        actions += FunctionalAction("Cruzar com áudio", "Verifica foco, rota ativa e sessão de mídia.", ActionDestination.RECIPE, "audio-radio")
        actions += FunctionalAction("Cruzar com renderização", "Amplia a leitura de SurfaceFlinger e gfxinfo.", ActionDestination.RECIPE, "fluidez-gfx")
        actions += FunctionalAction("Ver crashes e ANRs", "Confere evidência histórica sem tratar dump de stack como ANR.", ActionDestination.RECIPE, "falhas-crashes")
        actions += FunctionalAction("Repetir com Spotify aberto", "Execute novamente durante a lentidão para obter uma fotografia causal melhor.", ActionDestination.RECIPE, "spotify-diagnostico")

        val abiCompatible = spotifyAbi != null && systemAbis?.split(',')?.map { it.trim() }?.contains(spotifyAbi) == true
        val summary = when {
            explicitAnr || fatal -> "A coleta encontrou instabilidade explícita do Spotify. A prioridade é correlacionar o evento com CPU, memória e áudio antes de alterar o sistema."
            spotifyPid.isNullOrBlank() -> "A compatibilidade estática foi analisada, mas falta a parte decisiva da prova: repetir enquanto o Spotify estiver aberto e lento."
            abiCompatible && (gmsCpu ?: 0.0) >= 25.0 -> "A ABI está correta. Nesta fotografia, a principal pista é concorrência de recursos do ambiente — especialmente Google Play Services — e não arquitetura incompatível."
            abiCompatible -> "A ABI está correta. O diagnóstico agora separa pressão de memória, CPU, renderização, áudio e peculiaridades do framework para localizar o gargalo real."
            else -> "A coleta foi estruturada para distinguir incompatibilidade de arquitetura de gargalos do sistema. Revise os achados antes de qualquer mudança."
        }

        return ActionableReport(
            "Por que o Spotify está lento?",
            summary,
            findings.ifEmpty { listOf("A build não expôs métricas suficientes para interpretar o Spotify nesta fotografia.") },
            actions.distinctBy { "${it.destination}:${it.target}:${it.label}" },
            "Ver coleta técnica do Spotify"
        )
    }

    private fun lineValue(raw: String, key: String): String? =
        Regex("(?m)^${Regex.escape(key)}=(.*)$").find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun inlineValue(raw: String, key: String): String? =
        Regex("\\b${Regex.escape(key)}=([^\\s]+)").find(raw)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    private fun spotifyPssKb(raw: String): Long? =
        Regex("(?m)^\\s*TOTAL PSS:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: Regex("(?m)^\\s*TOTAL\\s+(\\d+)\\s+").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun formatMs(ms: Double): String =
        if (ms % 1.0 == 0.0) ms.toInt().toString() else String.format(Locale.US, "%.1f", ms)

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
                if (owner == "top" || owner.startsWith("android.hardware.") || ignoredOptimizationOwners.contains(owner.substringBefore(':'))) null else cpu to owner
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
                !ignoredOptimizationOwners.contains(candidate.substringBefore(':')) &&
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
            p.startsWith("com.google") -> 1
            p.startsWith("com.android") || p.startsWith("android") -> 4
            else -> 3
        }
    }

    private fun metric(raw: String, key: String): Long? =
        Regex("(?m)^\\s*${Regex.escape(key)}:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun formatCpu(cpu: Double): String =
        if (cpu % 1.0 == 0.0) cpu.toInt().toString() else String.format(Locale.US, "%.1f", cpu)
}
