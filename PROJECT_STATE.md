# Estado oficial — CUSTOMROM ADB S23 Premium Operacional

**Atualizado em:** 2026-08-08, horário de Brasília  
**Linha de trabalho:** `refactor/customrom-adb-s23-premium`  
**Estado:** expansão premium operacional compilada em CI real, artifact baixado e integridade conferida; validação física S23 → TayTech permanece pendente.

## Fotografia humana atual

**A linguagem visual premium aprovada foi preservada. A nova APK aumenta fortemente autonomia, didática e alcance operacional: resultados shell viram estados humanos, Aplicativos ganhou inteligência explicável e rollback, o ADB ganhou timeout explícito, e o catálogo passou a cobrir personalização, performance, crashes, energia, overlays, launcher, input, segurança de boot e atalhos remotos.**

## Superfície principal

O Galaxy S23 continua sendo o controlador. A TayTech continua sendo o alvo remoto ADB.

A navegação permanece:

- **Comandos** — biblioteca pesquisável, favoritos, ações de alto valor e personalização reversível;
- **Terminal** — shell multilinha, classificação de risco, estados humanos, saída técnica sob demanda e interrupção;
- **Apps** — inventário real, filtros, criticidade, confiança, motivos, análise, force-stop, disable/restore e ledger;
- **Diagnóstico** — workflows humanos + evidência técnica;
- **Sessões** — linha do tempo, alterações feitas pelo CUSTOMROM e Evidence Pack.

## Resultado humano padronizado

Toda execução comum passa por um contrato visual único:

`IDLE → QUEUED → RUNNING → SUCCESS_WITH_OUTPUT | SUCCESS_EMPTY | COMMAND_ERROR | TRANSPORT_ERROR | CANCELLED`

Regras principais:

- `exit=0` nunca é apresentado como “saída zero”;
- sucesso sem stdout/stderr aparece como **Concluído — nenhum texto foi retornado pelo comando**;
- erro do shell é separado de erro de transporte ADB;
- timeout é apresentado como **Tempo esgotado**;
- códigos de saída e duração ficam em detalhes técnicos.

## Timeout e recuperação

`AdbRemoteController` agora impõe timeout explícito padrão de **45 s** para shell remoto.

Se uma operação excede o limite:

1. a task é cancelada;
2. a conexão Kadb é resetada;
3. a UI recebe estado `Tempo esgotado`;
4. o controller tenta recuperar a conexão antes da próxima ação.

Esse padrão foi destilado de práticas externas de tooling ADB que exigem timeouts explícitos e contratos observáveis; nenhum código externo foi incorporado.

## Apps Intelligence

A tela Apps agora separa dois conceitos:

### Criticidade do package

- `PROTEGIDO`;
- `ALTA`;
- `MÉDIA`;
- `BAIXA`;
- `DESCONHECIDA`.

### Risco da ação

- `VERDE` — leitura;
- `AMARELO` — interação/mudança reversível;
- `VERMELHO` — estrutural/destrutiva, bloqueada no fluxo comum.

Cada classificação recebe confiança `alta`, `média` ou `baixa` e razões observáveis.

Sinais utilizados incluem:

- package name;
- caminho do APK (`/data/app`, `/system`, `/product`, `/vendor`, `/odm`, `priv-app`, APEX);
- metadados de persistent/boot/shared UID quando inspecionados;
- tokens automotivos como CAN/CAN box/Jancar/HiWorld/MCU/HVAC/DSP/rádio/câmera/ACC/sleep/wake;
- origem sistema/usuário;
- estado disabled/running.

`DESCONHECIDA` nunca é promovida silenciosamente a “segura”.

## Inventário e ações de packages

O inventário rápido consulta:

- todos os packages;
- sistema;
- terceiros/usuário;
- desativados;
- processos correntes.

Filtros previstos na UI:

`Todos | Rodando | Usuário | Sistema | Desativados | Protegidos | Candidatos | Alterados`

A inspeção sob demanda acrescenta `dumpsys package`, PID, `dumpsys meminfo` e serviços relacionados.

Ações:

- **Analisar** — leitura;
- **Parar temporariamente** — `am force-stop --user 0`;
- **Desativar reversivelmente** — `pm disable-user --user 0`;
- **Restaurar** — `pm enable --user 0`, somente quando o ledger comprova que o CUSTOMROM fez a desativação.

Packages `PROTEGIDO`/`ALTA` ficam sem stop/disable no fluxo comum.

## Ledger de alterações

`ChangeLedger` persiste até 500 registros em armazenamento privado do app.

Cada alteração registra:

- package;
- ação;
- estado anterior;
- estado posterior;
- timestamp;
- sessão;
- exit code;
- rollback conhecido.

O Evidence Pack inclui `changes.json`.

## Catálogo expandido

O catálogo passou de **7 para 44 receitas**.

Além da base anterior, agora cobre:

### Performance e saúde

- thermal;
- energia/bateria/device idle;
- armazenamento/mounts;
- fluidez/SurfaceFlinger/gfxinfo;
- diagnóstico composto **Por que a central está lenta?**;
- wakelocks e alarmes;
- jobs agendados;
- crashes/ANRs.

### Sistema e personalização

- tela/resolução/densidade/brilho/rotação/timeout;
- escalas de animação;
- animações 0x e rollback 1x;
- rotação automática on/off;
- manter tela ligada durante alimentação e rollback;
- overlays ativos;
- launcher e apps padrão;
- input devices e teclado;
- acessibilidade, IME e notification listeners;
- USB;
- SELinux/Verified Boot/security patch.

### Conectividade/mídia

- Wi‑Fi/rede/ADB;
- DNS/proxy/conectividade;
- Bluetooth;
- áudio e media sessions.

### Controle remoto prático

- abrir Configurações Android;
- abrir Wi‑Fi;
- abrir Bluetooth;
- abrir lista de aplicativos;
- enviar Home;
- enviar Voltar.

Essas ações ativas são AMARELAS e exigem confirmação.

## Skills externas destiladas

Duas referências ADB foram usadas como matéria-prima, nunca como autoridade:

- `pengdev/claude-adb-skill` — padrão de inspeção por camadas e uso de `uiautomator dump` como evidência UI;
- `hah23255/adb-android-control` — contratos observáveis, health/workflow, classificação de falhas e principalmente timeout explícito para evitar comandos presos.

O que foi incorporado são conceitos adaptados à arquitetura Kadb do CUSTOMROM. Nenhuma dependência Python, CLI externa ou código dessas ferramentas foi adicionado ao APK.

## Segurança operacional reforçada

O Terminal agora reconhece mais sinais de risco, incluindo:

- fastboot/flash/erase/wipe;
- uninstall;
- root/remount/mount rw;
- `dd`, `mkfs`, partition tools;
- AVB/Magisk/SELinux permissivo;
- remoções recursivas;
- force-stop/disable/enable/settings put;
- `am start`/broadcast;
- input remoto;
- uiautomator dump;
- chmod/chown/kill;
- overlay enable/disable.

VERMELHO continua bloqueado no fluxo comum.

## Backend preservado

- Kadb `2.1.1`;
- Coroutines `1.10.2`;
- compileSdk `36`;
- minSdk `29`;
- targetSdk `35`;
- pairing por código;
- identidade ADB persistente;
- `:5555`;
- `_adb-tls-connect` por mDNS;
- reconexão;
- shell;
- ZIP/SHA-256/Evidence Pack.

## Build final desta expansão

- validação de contrato: **PASS**;
- Android build: **PASS**;
- artifact: `CUSTOMROM-ADB-S23-PREMIUM`;
- APK: `CUSTOMROM-ADB-S23-PREMIUM-debug.apk`;
- source commit: `a4d0ac1554ad7aa7f279d1166608ecb67c1712f8`;
- run: `31241041911`;
- SHA-256: `ee3fabbfe64ef9651ddbad7fc7dea4ae38f243d3b68abb8a01bee703f87d3def`.

O artifact foi baixado fora da Actions. O SHA-256 foi recalculado e coincide com `sha256.txt` e com `ci/s23-premium-build-proof.json`. O APK também passou em `unzip -t` sem erros.

## Incidentes do ciclo

Dois falsos negativos do verificador foram corrigidos em vez de contornados:

1. o contrato procurava a frase de `SUCCESS_EMPTY` na Activity, embora a responsabilidade tenha sido corretamente extraída para `PremiumOpsModels`;
2. a regex genérica `flash` confundiu a propriedade somente leitura `ro.boot.flash.locked` com uma ação de flash; o detector passou a reconhecer execução real de comando.

Nenhum dos dois era erro de compilação do produto.

## Validação física ainda pendente

A build não recebe PASS físico antes de ser testada S23 → TayTech.

Gate sugerido:

1. atualizar/instalar a APK no S23;
2. confirmar abertura em `PremiumOpsActivity`;
3. validar estados `Concluído`, `Concluído sem texto`, falha e cancelamento;
4. carregar Apps e conferir classificação/razões em packages reais;
5. analisar um package não crítico;
6. só com autorização física, testar um único force-stop/disable reversível de baixo risco e depois `Restaurar`;
7. executar `Por que a central está lenta?`;
8. testar um atalho remoto de Configurações/Home;
9. gerar Evidence Pack;
10. observar crash/ANR/logcat do próprio CUSTOMROM.

## Rollback

A `main` continua fora desta implementação. Nenhuma ROM, MCU, CAN, partição ou package da TayTech foi alterado durante a construção/CI. Se a nova APK regredir no S23, a build premium anterior permanece referência de rollback enquanto a branch é corrigida.

## Próximo passo

**Validar esta APK expandida no Galaxy S23 contra a TayTech e usar o próprio CUSTOMROM para iniciar o inventário real de packages e o diagnóstico de lentidão.**
