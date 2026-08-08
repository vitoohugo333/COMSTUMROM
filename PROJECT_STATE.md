# Estado oficial — CUSTOMROM ADB S23 Premium

**Atualizado em:** 2026-08-08, horário de Brasília  
**Linha de trabalho:** `refactor/customrom-adb-s23-premium`  
**Estado:** APK premium S23-first gerado em CI real, artifact baixado e integridade conferida; validação física S23 → TayTech permanece pendente.

## Fotografia humana atual

**O CUSTOMROM ADB agora possui uma interface própria pensada para rodar no Galaxy S23 e controlar remotamente a TayTech. A primeira APK desta linha foi compilada com sucesso, baixada do GitHub Actions e teve o SHA-256 recalculado fora da Actions com coincidência exata.**

## Direção de produto confirmada

O Galaxy S23 é a superfície principal da interface. A TayTech é o alvo remoto ADB.

A experiência foi reorganizada em cinco destinos:

- **Comandos** — biblioteca executável com busca, favoritos, risco e detalhes;
- **Terminal** — shell dedicado multilinha, saída selecionável, copiar, limpar e interrupção;
- **Apps** — inventário visual de packages, análise, force-stop, disable reversível e restore;
- **Diagnóstico** — receitas, snapshot, resumo humano e evidência técnica bruta;
- **Sessões** — linha do tempo, Evidence Pack, ZIP e compartilhamento.

A conexão deixa de ser formulário permanente e passa a ser um estado global compacto. IP, porta, pairing e descoberta ficam sob demanda.

## Segurança operacional

- comandos são classificados em VERDE / AMARELO / VERMELHO;
- ações VERMELHAS ficam bloqueadas no fluxo comum;
- ações AMARELAS exigem confirmação contextual;
- packages com indícios de CAN, Jancar, MCU, HVAC, câmera, DSP, rádio, Bluetooth automotivo e outras integrações recebem **PROTEGIDO POR PRESUNÇÃO**;
- packages protegidos recebem análise no fluxo comum, sem oferecer stop/disable diretamente;
- nenhuma ROM, MCU, CAN, partição ou package da TayTech foi alterado durante esta implementação.

## Backend preservado

A nova superfície continua sobre a base funcional atual:

- Kadb `2.1.1`;
- Coroutines `1.10.2`;
- pairing por código;
- identidade ADB persistente;
- conexão direta;
- tentativa em `:5555`;
- descoberta `_adb-tls-connect` por mDNS;
- reconexão;
- shell;
- receitas existentes;
- sessões e Evidence Pack;
- exportação pelo Android.

## Build verificada

- validação de contrato: **PASS**;
- Android build: **PASS**;
- artifact: `CUSTOMROM-ADB-S23-PREMIUM`;
- APK: `CUSTOMROM-ADB-S23-PREMIUM-debug.apk`;
- source commit da APK: `aeb2156b6be7fc2f44fa875e8b19466b617ca724`;
- run: `31238639725`;
- SHA-256 da APK: `becea89b2cafdb30373bb258df2e075d83eede378e7c2e6d2d90a80539d822e6`.

O artifact foi baixado após a CI. O SHA-256 foi recalculado localmente e coincidiu exatamente com `sha256.txt`. O APK também passou em teste de integridade ZIP sem erros.

## Arquivos principais introduzidos nesta linha

- `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumMainActivity.kt`;
- `apps/customrom-adb-native/app/src/main/java/com/customrom/adb/PremiumModels.kt`;
- launcher da branch aponta para `PremiumMainActivity`;
- workflow isolado `build-customrom-adb-s23-premium.yml`;
- workflow nativo da branch tornou-se branch-aware sem escrever na `main`.

## Validação física ainda pendente

A build não recebe PASS de integração física antes de ser exercitada no Galaxy S23 contra a TayTech real.

Prova física recomendada:

1. instalar a APK no S23;
2. abrir e avaliar Comandos, Terminal, Apps, Diagnóstico e Sessões;
3. confirmar teclado/insets e navegação inferior;
4. parear/conectar a TayTech;
5. testar reconexão e mDNS;
6. executar receita VERDE;
7. carregar inventário de apps;
8. testar apenas uma ação AMARELA não automotiva e reversível quando autorizada;
9. gerar/compartilhar Evidence Pack;
10. observar crash/ANR/logcat.

## Rollback

A `main` não foi alterada por esta linha de implementação. Se a nova APK apresentar regressão física, basta remover/parar a build de desenvolvimento do S23 e continuar usando o artifact anterior enquanto a branch é corrigida.

## Codex Engineering Guardrails

`code-work` foi o gate da implementação. O trabalho preservou o backend funcional, manteve fronteiras de risco, compilou em CI real e conferiu o artifact baixado antes de fechar este checkpoint.

## Próximo passo

**Instalar `CUSTOMROM-ADB-S23-PREMIUM-debug.apk` no Galaxy S23 e validar visual e funcionalmente contra a TayTech.**
