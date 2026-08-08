# Estado oficial — CUSTOMROM ADB S23 Practical Operations V6.1

**Atualizado em:** 2026-08-08 BRT  
**Linha de trabalho:** `refactor/customrom-adb-s23-premium`  
**Estado:** implementação, validador, build e artifacts PASS; validação física S23 → TayTech pendente.  
**Fonte validada:** `d0fa9f0caafd089774b45f524e9a32b51c72e6d7`  
**CI final:** run `31281894532`

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

Artifact: `CUSTOMROM-ADB-S23-PRACTICAL-V6.1`

### APK principal S23

`CUSTOMROM-ADB-S23-PRACTICAL-V6.1-debug.apk`

SHA-256:
`018bb78c27697f045e1a5706badebe5b8d83e1c2603d6e3f322185d55a5ea338`

### APK Agent TayTech

`CUSTOMROM-Agent-TayTech-debug.apk`

SHA-256:
`a24a5084555351c0d7aeca4b35f6a2620d60b4dad36c361ee06a24c9add05734`

### Provas independentes

- validator: PASS;
- Android build: PASS;
- source promotion: PASS;
- artifact upload: PASS;
- ambos os APKs passaram no teste de integridade ZIP;
- o Agent embutido no APK S23 tem exatamente o mesmo SHA-256 do Agent standalone;
- `agent/build/` foi removido da fonte versionada e `agent/.gitignore` contém `/build/`.

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
