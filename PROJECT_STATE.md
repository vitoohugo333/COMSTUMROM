# Estado oficial — CUSTOMROM ADB S23 Premium / Contextual Control V5

**Atualizado em:** 2026-08-08 BRT  
**Linha de trabalho:** `refactor/customrom-adb-s23-premium`  
**Estado:** UI premium preservada; jornadas acionáveis refinadas; controle contextual de packages compilado; catálogo com 62 receitas; validação física desta nova fotografia pendente.

## Norte atual

O Galaxy S23 é o controlador e a TayTech é o alvo remoto por Wireless ADB.

A UI visual aprovada não será redesenhada nesta fase. O foco é **premium operacional**:

`INTENÇÃO → COLETA → INTERPRETAÇÃO → OBJETOS ACIONÁVEIS → AÇÃO HUMANA → VERIFICAÇÃO → HISTÓRICO/ROLLBACK`

Log bruto continua preservado, mas é evidência secundária quando existe uma próxima ação natural.

## Feedback físico que originou o V5

O proprietário validou a build anterior no S23 e encontrou:

1. `O que inicia junto com a central` encontrou 45 packages, porém o modal não permitia alcançar todo o conteúdo por scroll vertical;
2. tocar em um package fechava o contexto e navegava automaticamente para Apps;
3. o classificador tratava função automotiva relevante como veto de ação — o proprietário quer decidir conscientemente quando usar `pm disable-user --user 0`;
4. o catálogo ainda podia ser ampliado com diagnósticos/observabilidade úteis.

## Interação contextual nova

### Resultados longos

- `premiumDialog` usa `ScrollView` vertical com viewport controlada;
- resultado acionável pode exibir até 64 próximas ações;
- filtros da área Apps usam `HorizontalScrollView` real.

### Package sem vai-e-vem

`ActionDestination.PACKAGE` agora abre `openPackageContext()`.

Se o package já está no inventário, abre detalhe imediatamente. Caso contrário, o CUSTOMROM coleta somente o necessário daquele alvo:

- `pm path`;
- estado disabled;
- `pidof`;
- `dumpsys package` limitado.

O detalhe abre por cima da jornada. Fechar retorna ao relatório anterior, preservando contexto e posição.

O detalhe oferece, conforme estado:

- criticidade, confiança e razões;
- enabled/disabled/running;
- **Analisar com mais evidência**;
- **Parar temporariamente**;
- **Desativar para usuário 0**;
- **Ativar para usuário 0 / Restaurar**;
- logs recentes do package;
- abrir app na TayTech.

`Analisar com mais evidência` volta ao detalhe humano atualizado; não termina em dump técnico.

## Autonomia e segurança — regra corrigida

**Criticidade não é veto.**

Packages com sinais automotivos como rádio, CAN, MCU, HVAC, câmera, DSP, reverse etc. recebem criticidade **ALTA** e aviso de consequência, mas continuam podendo receber controle manual reversível no usuário 0.

O mesmo vale para packages em `/vendor` ou `/odm`: ALTA, não proibição automática.

`PROTEGIDO` fica reservado ao núcleo Android/ADB/recovery conhecido em que desativar pode eliminar o próprio caminho de recuperação, incluindo exemplos como shell, SystemUI, Settings, PackageInstaller, PermissionController, network stack e famílias de hardware explicitamente protegidas.

Não existe desativação automática.

### Escrita reversível

- disable: `pm disable-user --user 0 <pkg>`;
- enable: `pm enable --user 0 <pkg>`;
- ambas continuam AMARELAS e exigem confirmação explícita;
- após disable, o CUSTOMROM verifica `pm list packages -d`;
- após enable, confirma que o package não permanece na lista disabled;
- ChangeLedger registra a alteração e rollback conhecido.

## Catálogo de receitas

Catálogo ampliado de **44 para 62** rotinas, preservando as anteriores.

Novas capacidades incluem:

- foreground/persistent services;
- AppOps;
- batterystats por apps;
- UsageStats;
- device-idle whitelist;
- launchers HOME;
- WebView provider;
- localização/GNSS;
- sensores;
- câmera;
- processos/OOM;
- netstats;
- device policy;
- notificações/listeners;
- origem/installer dos packages;
- limites de background;
- Ethernet;
- data/timezone.

`FunctionalActionEngine.kt` foi ampliado para ligar as novas coletas a packages contextuais, filtros, diagnósticos correlatos e próximas ações. A expansão não é apenas um catálogo maior de dumps.

## Skills destiladas

Referências externas usadas somente como matéria-prima:

- `haowu77/android-adb-skill`: observar → agir → verificar e preservar contexto;
- `wesleydonk/ai-skill-android-logcat`: log filtrado por package/PID, saída delimitada e warnings;
- `songhuiming2007-coder/android-audit`: inventário → classificação → escolha humana → ação user 0 → verificação → restauração.

Listas/regras genéricas dessas skills não substituem AGENTS, Notion, evidência TayTech nem instrução do proprietário.

## Evidência automatizada final

Fotografia final compilada:

- source: `724312d8cc0e4eed810890df930b6a30ff3d6a8c`;
- run: `31244058225`;
- validation: **PASS**;
- Android build / `assembleDebug`: **PASS**;
- artifact: `CUSTOMROM-ADB-S23-PREMIUM`;
- APK: `CUSTOMROM-ADB-S23-PREMIUM-debug.apk`;
- SHA-256: `3f766dda89f90fe9ae0f64101e6cdaa41aca0aa750cf59f7ba309d6d03863732`.

O artifact foi baixado fora da Actions. SHA-256 recalculado localmente e coincidente. Teste de integridade ZIP do APK: sem erros.

## Incidentes desta rodada

- v3: substituição textual frágil não encontrou o bloco esperado; nenhum source inválido foi persistido;
- v4: validator PASS, Kotlin build FAIL por escaping de newline/variáveis shell; nenhum source inválido foi persistido;
- v5/v5.1: escaping corrigido; implementação persistida somente após validator + build PASS;
- workflow legado também executa em paralelo e escreve `ci/s23-premium-build-proof.json`; o gate contextual ganhou comprovante isolado para evitar confusão entre fotografias.

## Blueprint

**CONGELADO por decisão explícita do proprietário.** Não atualizar sem nova autorização. Aprendizados operacionais vão para bloco ativo, Estado Oficial, Registro de Alterações e este PROJECT_STATE.

## Limites preservados

Nesta rodada não houve:

- alteração da `main`;
- merge ou PR;
- release/deploy;
- instalação automática;
- desativação física automática na TayTech;
- root/remount/AVB/flash;
- alteração de ROM, MCU ou firmware CAN.

## Próximo gate físico

No S23 → TayTech:

1. executar `O que inicia junto com a central` e validar scroll com 45+ packages;
2. tocar Rádio/Launcher e confirmar detalhe no mesmo contexto, sem navegação automática para Apps;
3. confirmar que criticidade ALTA informa consequência, mas oferece controle manual user 0;
4. testar conscientemente disable + enable em um package escolhido pelo proprietário e verificar estado/ledger;
5. testar novas jornadas de serviços persistentes, UsageStats, batterystats e launchers;
6. observar crash/ANR/logcat do próprio CUSTOMROM.
