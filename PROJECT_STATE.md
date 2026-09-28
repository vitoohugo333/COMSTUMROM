# Estado oficial — CUSTOMROM GPT Remote Console

**Atualizado em:** 2026-09-27/28 BRT  
**Linha de trabalho:** `main`  
**Estado:** GPT Remote Console + Premium Operational Truth Pass concluídos e verificados software-side em `main`; validação física S23 → TayTech pendente.  
**Fonte validada:** `b5e5f6b1e6cc85e593936a747c13f3e3b8cb3adc`  
**CI final do código:** run `36432572784` — validator PASS, suíte JVM PASS, Android build PASS, artifact upload PASS, build-proof PASS.  
**CI autônoma da mesma fonte:** run `36432572961` — PASS.  
**Notion sync:** PASS — checkpoint `3e98ee52-ac54-8136-a3b5-dc4e350a0ce5` em “CUSTOMROM TAYTECH — Central Oficial do Projeto”.  
**Próximo passo:** instalar o APK premium final no S23, validar visualmente telemetria/Apps/feedback na TayTech real e retomar o smoke test #1318; depois validar `sequence=2` e uma única ação YELLOW reversível + rollback.

## Integração em main

- `main` foi avançada por fast-forward para a linha validada, sem divergência e sem merge commit artificial;
- `refactor/customrom-adb-s23-premium` foi provada como totalmente contida em `main` e removida;
- `refactor/customrom-adb-s23-premium-v6-work` era ancestral da linha premium, foi provada como totalmente contida em `main` e removida;
- o workflow temporário usado para verificar `main` e limpar branches foi removido após concluir com sucesso;
- autoridade remota atual: somente `main`.

## Premium Operational Truth Pass — 28/09/2026

A crítica física do proprietário mostrou que o app tinha motor funcional, mas ainda não comunicava estado, consequência e contexto com a qualidade exigida pelos blueprints CUSTOMROM + Omega Dev. O passe foi tratado como correção sistêmica, não como remendo de três telas.

Entregue e verificado:

- **Saúde viva da TayTech** em Comandos, sem exigir executar receita: RAM usada/total/disponível, CPU amostrada, load 1m, horário da amostra, barras e ranking dos maiores consumidores de RAM/CPU;
- detalhe didático explica PSS, por que a soma dos apps não precisa fechar o total de RAM e mantém evidência técnica sob demanda;
- coleta live passa pelo `AdbRemoteController`, é somente leitura e não compete com operação local/remota já ativa;
- **Apps** agora têm filtros com seleção visual persistente + contagem real por estado;
- lista de Apps mostra `RODANDO`, `DESATIVADO`, `ATIVO`, `ALTERADO`, criticidade e RAM/CPU atribuídas quando observadas;
- detalhe de app mostra estado e consumo da última amostra antes das ações;
- inventário é carregado automaticamente quando Apps abre conectado, com feedback local de carregando/sucesso/falha;
- analisar, parar, desativar, ativar, restaurar, coletar logs e abrir app comunicam preparação, sucesso ou falha no próprio contexto;
- Terminal mostra `Recebido`, muda botão para `Executando…`, bloqueia entrada durante execução e restaura o estado ao terminar;
- banner operacional usa fases explícitas `AGUARDANDO / EXECUTANDO / CONCLUÍDO / FALHA / INTERROMPIDO`;
- toque recebeu feedback visual/háptico curto sem animação pesada;
- atualização live de Apps é **in-place**: não reconstrói a lista a cada tick e não relê o ChangeLedger para cada linha;
- detecção `running` usa nome de processo exato / processo-filho, evitando falso positivo por substring;
- atalhos de Apps deixaram de disparar carga duplicada;
- top status do Console expõe estado legível além do ponto verde;
- validator fixa LiveTelemetry, AppInventoryProjection, seleção de filtro, uso por app, feedback explícito e atualização in-place como contratos obrigatórios.

### Evidência desta fotografia

- source testado: `b5e5f6b1e6cc85e593936a747c13f3e3b8cb3adc`;
- workflow nativo: `36432572784` — **SUCCESS**;
- CI autônoma: `36432572961` — **SUCCESS**;
- validator: **PASS**;
- JVM unit suite: **PASS**;
- Android debug APK: **PASS**;
- artifact upload: **PASS**;
- build-proof: **PASS**;
- artifact: `CUSTOMROM-ADB-native`;
- artifact id: `10973659215`;
- APK: `CUSTOMROM-ADB-native-debug.apk`;
- APK SHA-256: `91bc9bb554d278e311c4353287c58254a0f6846af4c70f67b06ee7761840a3f9`;
- artifact ZIP digest: `sha256:7273d8934b9a150610361b9aa3494ecff1837398512e90da15e44a60ddb4018f`;
- revisão independente encontrou e corrigiu carga duplicada de inventário, detecção de processo por substring, reconstrução completa da lista a cada tick e corrida recorrente do commit de build-proof.

## GPT Remote Console

O CUSTOMROM agora suporta um console operacional persistente para o GPT sem backend novo e sem LLM embarcado:

`GPT → GitHub Issues → S23/CUSTOMROM → AdbRemoteController/Kadb → TayTech → receipt → GPT`

Contratos verificados:

- cada comando continua sendo uma Issue `[CUSTOMROM JOB]`, preservando compatibilidade com jobs antigos;
- `requestId` continua sendo a identidade de idempotência e anti-replay;
- jobs de console podem adicionar `sessionId` + `sequence` para manter ordem causal entre comandos;
- uma sequência futura aguarda até o receipt da sequência anterior estar realmente publicado no GitHub;
- gaps de sequência não executam nem consomem o job;
- sequência stale com outro `requestId` é rejeitada, recebe receipt e fecha sem avançar a sessão;
- o session store é persistente e não descarta silenciosamente sessões antigas;
- polling padrão do console é `5 s`, com `ETag` / `If-None-Match` e body cacheado em `304 Not Modified`;
- receipts de console mostram sessão e sequência;
- jobs legados sem `sessionId` continuam com a semântica anterior;
- `GREEN / AMARELO / VERMELHO`, Keystore, rollback, proteção automotiva e `UNCERTAIN` continuam sendo autoridades locais;
- a UI trata a capacidade como **CONSOLE**, sem criar um destino GitHub na navegação.

## Ponte remota GitHub Issues

A topologia de controle remoto software-side está fechada:

`GitHub Issue privada → CUSTOMROM no S23 → AdbRemoteController/Kadb existente → TayTech → receipt na mesma Issue`

Contratos verificados:

- schema estrito `customrom.adb.job.v1`;
- autor, prefixo e alvo validados antes de claim;
- token GitHub protegido por Android Keystore e nunca versionado/logado;
- GREEN automático após admission; YELLOW exige `allowChanges=true`; VERMELHO remoto bloqueado;
- `requestId` persistente impede replay de efeito e conflito de payload;
- resultado terminal é persistido antes da publicação do receipt;
- receipt publicado não é duplicado em polling posterior;
- `UNCERTAIN` permanece aberto para reconciliação e não é repetido automaticamente;
- shell remoto continua disponível, mas comandos não reconhecidos como leitura explícita sobem conservadoramente para AMARELO;
- shell composto (`;`, pipe, `&&`, newline, backtick ou `$()`) não pode herdar GREEN apenas por começar com um comando de leitura;
- mutações sobre superfícies automotivas protegidas, inclusive verbos OEM/vendor como set/write/send/inject/transmit/control, sobem para VERMELHO;
- packages protegidos e owner-critical não passam pelo disable/force-stop remoto genérico;
- rollback de package e animações é calculado a partir do estado realmente observado;
- ChangeLedger recebe alterações remotas verificadas;
- job remoto em CLAIMED/RUNNING bloqueia início de nova operação local.

Artifact nativo verificado: `CUSTOMROM-ADB-native`  
APK: `CUSTOMROM-ADB-native-debug.apk`  
SHA-256: `91bc9bb554d278e311c4353287c58254a0f6846af4c70f67b06ee7761840a3f9`  
Workflow: `36432572784`

## Topologia oficial

O **Galaxy S23 é o controlador**. A **TayTech é o alvo remoto** por ADB/Wi‑Fi. A UI premium aprovada no S23 continua sendo a superfície principal.

A regra operacional é:

`INTENÇÃO HUMANA/GPT → ISSUE ORDENADA → ADB → EVIDÊNCIA → RECEIPT → PRÓXIMA DECISÃO → HISTÓRICO/ROLLBACK`

Terminal serve como bancada de descoberta; rotina útil deve virar fluxo do CUSTOMROM.

## Contratos fixos desta fotografia

### ÔMEGAS

`com.omegas.v7.test` é carga obrigatória do proprietário.

- pode aparecer em evidência bruta;
- fica **PROTECTED**;
- é excluído automaticamente dos rankings e sugestões de otimização/debloat;
- nunca recebe sugestão de force-stop ou disable.

### Launchers

- `ginlemon.flowerfree` = **Smart Launcher escolhido pelo proprietário** → PROTECTED;
- `com.jancar.launcher` = **launcher OEM TayTech** → HIGH, analisável e controlável de forma reversível após confirmar HOME atual.

`PROTECTED` continua reservado a itens que podem eliminar o próprio caminho de recuperação/controle. Criticidade alta não é prisão: ação avançada AMARELA continua possível quando reversível e explicitamente confirmada.

## Operação pragmática V6.1

A área de comandos expõe fluxos humanos de alto valor:

### Analisar e enxugar a central

Uma única execução cruza:

- CPU e processos;
- RAM e ZRAM;
- HOME atual;
- WebView e contexto;
- Google App e Google Drive;
- packages relevantes;
- tempestade de logs WOBLE.

O resultado não termina em dump: abre packages, filtros e diagnósticos correlatos como próximas ações.

Nenhuma desativação em lote é automática. Mudanças são feitas uma por vez para preservar causalidade.

### Tempestade WOBLE

Existe diagnóstico dedicado para medir volume/contexto de `WOBLE` sem desligar automaticamente Bluetooth, wireless ou drivers automotivos.

### ADB após reinício

O fluxo próprio lê em uma execução:

- `adb_enabled`;
- `adb_wifi_enabled`;
- `service.adb.tcp.port`;
- `persist.adb.tcp.port`;
- estado do `adbd`;
- presença/permissão do CUSTOMROM Agent;
- disponibilidade de rollback.

A tentativa AMARELA salva rollback antes de tentar manter ADB/Wireless ativos e persistir `5555`. Ela não reinicia deliberadamente o daemon durante o próprio caminho de controle.

## CUSTOMROM Agent

Companion Android: `com.customrom.agent`.

Objetivo: fallback local na TayTech quando a ROM desligar Wireless Debugging após boot.

Capacidades:

- receiver de `BOOT_COMPLETED` e `LOCKED_BOOT_COMPLETED`;
- tentativa de manter `ADB_ENABLED=1` e `adb_wifi_enabled=1`;
- usa `WRITE_SECURE_SETTINGS` quando a ROM concede essa permissão;
- tela local mínima com ação **Ativar ADB agora**;
- não toca em MCU, CAN, firmware, root, remount ou AVB.

### Instalação absorvida pelo app S23

O APK do Agent é gerado primeiro e embutido em `assets/CUSTOMROM-Agent-TayTech-debug.apk` dentro do APK principal.

Quando o companion não existe na TayTech, o próprio CUSTOMROM oferece:

**Instalar CUSTOMROM Agent**

O fluxo:

1. extrai o APK embutido para cache no S23;
2. instala remotamente usando Kadb pela conexão ADB atual;
3. tenta conceder `WRITE_SECURE_SETTINGS`;
4. envia `com.customrom.agent.APPLY`;
5. relê o estado e apresenta o resultado humano.

Não é necessário transferir manualmente o APK do Agent para a TayTech.

## Segurança e rollback

- nenhuma ação automotiva destrutiva automática;
- `main` é a autoridade remota única e toda alteração desta fotografia foi submetida a TDD + CI antes do fechamento;
- nenhum release/flash foi executado nesta fotografia;
- force-stop/disable/enable continuam explícitos;
- ações AMARELAS exigem confirmação;
- `pm disable-user --user 0` mantém rollback por `pm enable --user 0`;
- tentativa de persistência ADB salva script de restauração em `/storage/emulated/0/CUSTOMROM/experimentos/adb_persistencia_rollback.sh`;
- ChangeLedger e sessões preservam evidência operacional;
- revisão final de segurança fechou dois bypasses de classificação remota por TDD: shell desconhecido não é mais GREEN por padrão e prefixo read-only não mascara composição shell mutante.

## Evidência de build final

### GPT Remote Console — prova atual

- source: `9e62e1e3b4d07a7589c8ca47c1b0113fd31f55c0`;
- native validator: PASS;
- JVM unit tests: PASS (`91` testes na suíte final);
- Android debug build: PASS;
- artifact upload: PASS;
- CI autônoma da mesma fonte: PASS;
- workflow nativo: `36373950231`;
- workflow autônomo: `36373950324`;
- artifact: `CUSTOMROM-ADB-native`;
- APK: `CUSTOMROM-ADB-native-debug.apk`;
- APK SHA-256: `139f5a314a995183f4b51130b10113ef325a0a025e8c9167fea0d5de626c0c78`;
- artifact id: `10950012641`;
- artifact ZIP digest: `sha256:7f15948de9a9780d251352dea7902b6193b72c471ba6c2453b95977df9412b53`;
- TDD inicial: RED por capacidades ausentes de sessão/ETag → GREEN;
- revisão independente: RED final `91 tests completed, 2 failed` para retenção >200 sessões e stale sequence → correção → mesma suíte GREEN + APK GREEN.


### Base prática / companion

O workflow prático `36364171238` também fechou com unit tests, Android build e upload PASS para `CUSTOMROM-ADB-S23-PRACTICAL-V6.1`. A prova canônica desta fotografia para o bridge remoto é o artifact nativo acima.

## Gate físico ainda aberto

CI não prova comportamento do aparelho real.

Validação física necessária:

1. instalar o APK `CUSTOMROM-ADB-native-debug.apk` no S23;
2. abrir o CUSTOMROM e habilitar **CONSOLE** com a credencial fine-grained do repositório privado;
3. confirmar a conexão ADB real S23 → TayTech;
4. emitir um job GREEN com `sessionId=<sessão>` e `sequence=1`;
5. comprovar execução na TayTech e receipt na mesma Issue;
6. somente após o receipt, emitir `sequence=2` da mesma sessão e comprovar a ordem causal;
7. fechar com uma única operação YELLOW reversível, baseline anterior, verificação posterior e rollback;
8. validar funções automotivas aplicáveis sem regressão.

Até esse teste, o status correto é **Software-side PASS · validação física PENDENTE**.

## Histórico

Detalhes de V5, Spotify Diagnostic e incidentes anteriores permanecem documentados no Notion oficial e em `docs/incidents/`; não devem substituir esta fotografia atual.
