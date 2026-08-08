# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** governança pronta; porta ADB `5555` confirmada na sessão atual; **Bloco 00 — Evoluir o cliente ADB** em execução com escopo ampliado de cockpit de engenharia.  
**Papel da `main`:** linha principal de governança, documentação, evidência e ferramentas seguras.

## Onde estamos

O gargalo operacional deixou de ser tratado apenas como “melhorar alguns toques”. O objetivo agora é construir uma camada operacional potente em cima das capacidades ADB já comprovadas do APK de referência, preservando subsistemas úteis e evitando reescrita gratuita.

A porta `5555` foi configurada e o proprietário confirmou conexão. **Persistência após reboot ainda não foi provada.**

## Fotografia humana atual

**APK de referência mapeado + reconexão existente confirmada por evidência estática + catálogo de receitas criado + formato de Evidence Pack definido + pipeline reproduzível de decode/patch/rebuild criado + visão ampliada de cockpit registrada. A primeira APK modificada ainda não foi compilada/testada em runtime.**

## Evidência estática confirmada no APK fornecido

Foram observados diretamente no artefato:

- `AdbClient` / `AdbClient2`;
- `AdbDeviceHolder`;
- `AdbShellRepository`;
- `AdbCommandProcessor`;
- `TargetConnectionsManager`;
- `MdnsSdResolver`;
- `_adb-tls-pairing` e `_adb-tls-connect`;
- `saveConnectDataForReconnect`;
- `reconnectLastWifiConnections`;
- `handleReconnectLastWifiConnections`;
- preferências `key.reconnect.last.wifi.targets`, `key.autofetch.connection.params`, `key.autofetch.pairing.info` e `key.start.adb.server.foreground`;
- layouts separados para main, shell, conexão, pareamento, comandos, arquivos e logcat;
- aviso interno de que o shell já equivale a `adb shell`;
- gate comercial da versão gratuita confirmado por string interna — não será neutralizado.

## Direção de produto

CUSTOMROM ADB passa a ser pensado como **cockpit de engenharia Android remota**, com:

1. Device Workspace persistente;
2. Connection Orchestrator;
3. Terminal Workspace;
4. Command Recipes;
5. Session Timeline;
6. Evidence Pack para ChatGPT/GitHub/Notion;
7. Compare Mode antes/depois;
8. Live Capture;
9. File Workbench;
10. APK Workbench enxuto;
11. Logcat Workbench;
12. Safety Layer VERDE/AMARELO/VERMELHO;
13. Profiles/Contextos;
14. Command Palette;
15. Home adaptativa.

A meta não é reduzir toques de forma literal. A meta é aumentar poder operacional, manter contexto, tornar reconexão robusta e reduzir trabalho manual inútil.

## Implementação já versionada

### Patch cirúrgico

`tools/bugjaeger_mod/patch_defaults.py`

- ativa por padrão recursos já existentes de reconexão/autofetch/background quando encontrados na árvore Apktool;
- altera somente o nome `app_name` quando localizável com segurança;
- não toca em anúncios, premium gates ou monetização;
- não altera o protocolo ADB.

### Pipeline de rebuild

`tools/bugjaeger_mod/build_mod.sh`

- localiza APK fornecido diretamente ou dentro de ZIP;
- preserva SHA-256 do original;
- decodifica via Apktool;
- aplica somente patches CUSTOMROM próprios;
- recompila;
- gera metadados e relatório de patch.

### Workflow manual

`.github/workflows/build-customrom-adb-mod.yml`

- execução manual;
- instala Apktool fixado em v3.0.2;
- executa o pipeline;
- alinha e assina a build com chave temporária de desenvolvimento;
- verifica assinatura e SHA-256;
- publica artefato de build por 7 dias.

### Receitas

`apps/customrom-adb/recipes/recipes.json`

Inclui inicialmente:

- Estado geral da central;
- Memória, swap e ZRAM;
- Processos mais pesados;
- Inventário de aplicativos e pacotes;
- Serviços Android ativos;
- Logcat de 30 segundos;
- Estado da rede e ADB;
- Snapshot completo para análise.

### Evidence Pack

`apps/customrom-adb/schemas/evidence-pack.schema.json`

Define sessão, alvo, estratégia de conexão, execuções, risco, status, arquivos e checksums em formato estruturado.

### Visão ampliada

`docs/VISAO_PRODUTO_CUSTOMROM_ADB.md`

Registra o escopo amplo sem obrigar implementação indiscriminada.

## O que ainda precisa de prova em runtime

- se a preferência padrão de reconexão é suficiente para o comportamento desejado;
- persistência real da conexão em background;
- comportamento após retorno de Wi-Fi;
- recuperação do shell;
- compatibilidade da rebuild assinada com o S23;
- pareamento após troca de assinatura/app data;
- recursos originais preservados;
- persistência da porta 5555 após reboot.

## Limite comercial

O código proprietário do APK de referência não passa a ser nosso apenas porque o modificamos. O projeto **não neutraliza gates pagos**. Se o gate impedir nosso fluxo, substituímos especificamente a camada limitada por implementação própria/open source, sem reescrever o restante por esporte.

## Notion sync

**EM ATUALIZAÇÃO neste checkpoint:** Bloco 00 deve refletir o escopo ampliado, os artefatos já criados e o fato de que a build real ainda não foi executada.

## Codex Engineering Guardrails

`code-work` está ativo. A operação segue mudança incremental, preservação do original, rastreabilidade e verificação antes de declarar funcionalidade pronta.

## Testes/CI

- verificações estáticas locais do APK foram executadas por inspeção de ZIP/DEX/resources;
- SHA-256 do APK de referência permanece `65d2e6f73a62bc5ae4cdcf9a8c9271ff0bab499eca9d9464ca37425931ba015b`;
- pipeline de build foi criado, mas **a workflow de rebuild ainda não foi executada**;
- não declarar APK modificada como funcional até instalação e teste real no S23/TayTech.

## Próximo passo

**Executar o workflow manual de rebuild assim que o APK/ZIP de referência for localizado pelo runner do repositório; em seguida instalar a APK assinada no S23 e validar reconexão, pareamento e regressões.**
