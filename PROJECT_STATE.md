# Estado oficial — CUSTOMROM ADB Remote Bridge

**Atualizado em:** 2026-09-27 BRT  
**Linha de trabalho:** `refactor/customrom-adb-s23-premium`  
**Estado:** ponte GitHub Issues → S23 → ADB → TayTech concluída e verificada software-side; validação física S23 → TayTech pendente.  
**Fonte validada:** `a8a6435d7b03acf4622ed02ad4d3bce1f802e5a7`  
**CI final:** run `36364171239` — validator PASS, JVM unit tests PASS, Android build PASS, artifact upload PASS.  
**Notion sync:** PASS — checkpoint `3e98ee52-ac54-8136-a3b5-dc4e350a0ce5` em “CUSTOMROM TAYTECH — Central Oficial do Projeto”.  
**Próximo passo:** instalar o APK verificado no S23 e executar validação física GREEN Issue → S23 → ADB → TayTech → receipt; depois uma única ação YELLOW reversível + rollback.

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
- shell remoto continua disponível, mas escrita genérica sobe para AMARELO e superfícies estruturais/automotivas ativas sobem para VERMELHO;
- packages protegidos e owner-critical não passam pelo disable/force-stop remoto genérico;
- rollback de package e animações é calculado a partir do estado realmente observado;
- ChangeLedger recebe alterações remotas verificadas;
- job remoto em CLAIMED/RUNNING bloqueia início de nova operação local.

Artifact nativo verificado: `CUSTOMROM-ADB-S23-PREMIUM`  
APK: `CUSTOMROM-ADB-S23-PREMIUM-debug.apk`  
SHA-256: `c5a43317e2b341f1f9a9fcb44cf95c957ee4229875b7367d429942a5352f25d1`  
Workflow: `36364171239`

## Topologia oficial

O **Galaxy S23 é o controlador**. A **TayTech é o alvo remoto** por ADB/Wi‑Fi. A UI premium aprovada no S23 continua sendo a superfície principal.

A regra operacional é:

`INTENÇÃO HUMANA → COLETA ÚNICA → INTERPRETAÇÃO → AÇÃO NO PRÓPRIO APP → VERIFICAÇÃO → HISTÓRICO/ROLLBACK`

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
- nenhuma alteração de `main`;
- nenhum merge/release nesta fotografia;
- force-stop/disable/enable continuam explícitos;
- ações AMARELAS exigem confirmação;
- `pm disable-user --user 0` mantém rollback por `pm enable --user 0`;
- tentativa de persistência ADB salva script de restauração em `/storage/emulated/0/CUSTOMROM/experimentos/adb_persistencia_rollback.sh`;
- ChangeLedger e sessões preservam evidência operacional.

## Evidência de build final

### Bridge remoto — prova atual

- source: `a8a6435d7b03acf4622ed02ad4d3bce1f802e5a7`;
- native validator: PASS;
- JVM unit tests: PASS;
- Android debug build: PASS;
- artifact upload: PASS;
- artifact: `CUSTOMROM-ADB-S23-PREMIUM`;
- APK: `CUSTOMROM-ADB-S23-PREMIUM-debug.apk`;
- APK SHA-256: `c5a43317e2b341f1f9a9fcb44cf95c957ee4229875b7367d429942a5352f25d1`;
- workflow: `36364171239`;
- artifact id: `10947105718`;
- artifact ZIP digest: `sha256:4b1c112293e9f9c9b20aecddd43176f91240f73a8592ecac6228e7115fc3f557`.

### Base prática / companion

O workflow prático `36364171238` também fechou com unit tests, Android build e upload PASS para `CUSTOMROM-ADB-S23-PRACTICAL-V6.1`. A prova canônica desta fotografia para o bridge remoto é o artifact nativo acima.

## Gate físico ainda aberto

CI não prova comportamento da ROM real.

Próxima validação no aparelho:

1. instalar o APK V6.1 no S23;
2. conectar à TayTech normalmente;
3. executar **ADB após reinício**;
4. se o app indicar Agent ausente, usar **Instalar CUSTOMROM Agent** no próprio fluxo;
5. confirmar o estado apresentado;
6. reiniciar a TayTech;
7. observar se Wireless ADB volta e se o S23 reconecta por `:5555` ou mDNS;
8. validar Smart Launcher, câmera, CAN, áudio e Bluetooth sem regressões.

Até esse teste, o status correto é **Aguardando validação física**.

## Histórico

Detalhes de V5, Spotify Diagnostic e incidentes anteriores permanecem documentados no Notion oficial e em `docs/incidents/`; não devem substituir esta fotografia atual.
