# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** aplicativo próprio CUSTOMROM ADB compilado e verificado; próximo gate é validação física no S23/TayTech.  
**Papel da `main`:** linha principal de governança, documentação, evidência e ferramentas seguras.

## Estado

A etapa de criar uma alternativa operacional própria ao fluxo manual do cliente ADB de referência chegou a uma build Android real.

O aplicativo nativo já possui no código e passou pela compilação com:

- pareamento ADB por código;
- identidade ADB persistente;
- conexão direta e tentativa por `:5555`;
- descoberta/reconexão via `_adb-tls-connect` por mDNS;
- terminal e execução de shell;
- classificação VERDE / AMARELO / VERMELHO;
- receitas de diagnóstico CUSTOMROM;
- sessão e organização de evidências;
- geração de SHA-256;
- exportação em ZIP;
- compartilhamento pelo Android.

Essas capacidades estão **implementadas e compiladas**, mas ainda não devem ser chamadas de PASS de integração até serem exercitadas no S23 contra a TayTech real.

## Fotografia humana atual

**CUSTOMROM ADB nativo compilado + artifact real baixado + integridade conferida + CI estabilizada + aprendizado fechado + Notion sincronizado. Falta instalar no S23 e provar a comunicação real com a TayTech.**

## Build nativa aprovada pela CI

Base conhecida e protegida:

- Kadb `2.1.1`;
- Coroutines `1.10.2`;
- compileSdk `36`;
- minSdk `29`;
- targetSdk `35`;
- AGP `9.3.1`;
- Gradle `9.5.0`;
- JDK 17.

Resultado integrado:

- contrato estático: **PASS**;
- build Android: **PASS**;
- artifact: `CUSTOMROM-ADB-native`;
- APK: `CUSTOMROM-ADB-native-debug.apk`;
- SHA-256: `19a038ec37c5d2619df08cd8b928aba0a1dcb2d0284c1bab218736aa8ca0b3ae`;
- fotografia técnica testada: `6b8581a7ded59fc13928afc110ba8bd6c38275b5`;
- run técnico: `31234887262`.

O artifact foi baixado fora da GitHub Actions. O SHA-256 do APK foi recalculado e coincidiu exatamente com `sha256.txt` e com `ci/native-build-proof.json`. O contêiner do APK também passou em teste de integridade ZIP.

## Aprendizado fechado

A sequência de builds revelou e fechou três falhas reutilizáveis:

1. Kadb `2.1.3` exigia compileSdk 37, enquanto a plataforma numérica necessária não estava disponível no fluxo do runner usado;
2. `MainActivity` usa `runBlocking` diretamente e precisava declarar Coroutines no classpath do app;
3. builds sobrepostas podiam disputar `ci/native-build-proof.json`.

Prevenção executável:

- `tools/validate_native_customrom.py` protege a matriz Kadb/Coroutines/SDK conhecida;
- `.github/workflows/build-customrom-adb-native.yml` serializa builds por branch e sincroniza a `main` antes de publicar o comprovante;
- história/evidência em `docs/incidents/2026-08-07-build-nativo-e-comprovante-concorrente.md`.

## Validação física ainda pendente

A primeira prova no hardware deve cobrir, nesta ordem:

1. instalar o APK no S23;
2. abrir o app e parear a TayTech por código quando necessário;
3. conectar e confirmar shell simples;
4. fechar/reabrir o app e verificar reconexão sem refazer pareamento;
5. confirmar o caminho `:5555` quando disponível;
6. confirmar recuperação por mDNS quando aplicável;
7. executar uma receita VERDE;
8. gerar o pacote de evidência;
9. compartilhar/exportar o pacote;
10. observar crash/ANR/logcat durante o fluxo.

**Não declarar PASS físico antes dessa prova.**

## Diagnóstico da lentidão da TayTech

O diagnóstico de memória/processos continua sendo o objetivo seguinte do projeto, mas agora a intenção é fazê-lo pelo próprio cockpit CUSTOMROM assim que a comunicação física do novo app for comprovada.

Antes de desativar qualquer pacote da central, continua obrigatório obter baseline suficiente e preservar funções automotivas por presunção.

## Rollback

Nesta etapa não houve alteração de ROM, sistema, MCU, CAN ou pacotes da TayTech.

Se o APK nativo apresentar regressão no teste físico, o rollback imediato é simplesmente parar/remover a build de desenvolvimento do S23 e continuar usando o cliente ADB de referência enquanto a falha é corrigida.

## Notion sync

**SINCRONIZADO neste checkpoint.**

Foram atualizados:

- `01 — Estado Oficial`;
- `Bloco 00 — Evoluir o cliente ADB com mínimo de mudanças`, agora em **Aguardando validação física**;
- `05 — Registro de Alterações do Notion`, com `CR-003`;
- banco `Aprendizados`, com a prevenção permanente da matriz de build e da concorrência do comprovante.

## Ações vermelhas executadas?

**Não.** Nenhum root, fastboot, flash, remount, partição, AVB, firmware, MCU ou CAN foi alterado. Nenhuma otimização ADB foi aplicada à TayTech para produzir esta build.

## Codex Engineering Guardrails

`code-work` foi usado como gate desta alteração. A correção foi conduzida por reprodução do erro, causa raiz, mudança mínima, CI real, artifact real e verificação independente do hash antes de fechar o checkpoint.

## Próximo passo

**Instalar `CUSTOMROM-ADB-native-debug.apk` no S23 e executar a primeira validação física controlada contra a TayTech.**
