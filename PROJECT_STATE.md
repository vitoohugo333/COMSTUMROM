# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-08, horário de Brasília  
**Estado:** CUSTOMROM ADB com cockpit visual premium compilado e verificado; próximo gate é validação visual e funcional no S23/TayTech.  
**Papel da `main`:** linha principal de governança, documentação, evidência e ferramentas seguras.

## Fotografia humana atual

**A primeira interface técnica crua foi rejeitada pelo proprietário. A camada visual foi reconstruída como cockpit premium escuro, responsivo e orientado por contexto. A nova build passou pela CI, o artifact real foi baixado e o SHA-256 foi conferido. Falta instalar no S23 para validar aparência e comportamento em runtime.**

## O que mudou na interface

A interface antiga de formulário único foi substituída por uma arquitetura de produto:

- `Central` — TayTech como alvo principal, estado da conexão, endpoint, ações rápidas e sessão atual;
- `Terminal` — editor multilinha, chip de risco dinâmico, execução, console e cópia de saída;
- `Diagnóstico` — snapshot completo e biblioteca de receitas por cards;
- `Sessões` — resumo, Evidence Pack, exportação, compartilhamento e linha do tempo;
- `Mais` — conexão manual, reconexão e ferramentas técnicas secundárias.

Adicionalmente:

- tema escuro próprio;
- identidade visual CUSTOMROM;
- novo ícone;
- conexão/pareamento manual movidos para painel dedicado;
- navegação inferior em celular;
- navegação lateral automática em telas com `screenWidthDp >= 700`;
- tratamento de system insets para evitar conteúdo sob barras do Android;
- hierarquia visual com cards, estados, badges e CTAs;
- backend ADB, receitas, sessões e Evidence Pack preservados.

## Build premium aprovada pela CI

Base técnica mantida:

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
- SHA-256: `59efbac6565c14842f3040ee58264197660f4e8ba70609bfc48414c71987be55`;
- fotografia técnica testada: `34c6d69ffd405c4939b4f7bfdc95f1b5b9625de3`;
- run técnico: `31236903021`.

O artifact foi baixado fora da GitHub Actions. O SHA-256 do APK foi recalculado e coincidiu exatamente com `sha256.txt` e com `ci/native-build-proof.json`. O contêiner APK também passou em teste de integridade ZIP.

## Falhas encontradas durante a reconstrução visual

A primeira compilação da UI premium encontrou dois erros puramente mecânicos:

1. uso de `ScrollView.LayoutParams`, não resolvido pelo compilador Kotlin;
2. uso da propriedade sintética `singleLine` em `EditText`.

Correções aplicadas:

- `FrameLayout.LayoutParams` no conteúdo do `ScrollView`;
- `setSingleLine(true)` no campo premium.

A build seguinte passou integralmente.

## Capacidades funcionais preservadas

Continuam implementadas e compiladas:

- pareamento ADB por código;
- identidade ADB persistente;
- conexão direta e tentativa por `:5555`;
- descoberta/reconexão `_adb-tls-connect` via mDNS;
- terminal e shell;
- classificação VERDE / AMARELO / VERMELHO;
- receitas de diagnóstico CUSTOMROM;
- sessão e organização de evidências;
- geração de SHA-256;
- exportação ZIP;
- compartilhamento pelo Android.

Essas capacidades não recebem PASS de integração física até serem exercitadas contra a TayTech real.

## Validação física ainda pendente

A próxima prova deve cobrir:

1. instalar a nova APK premium no S23;
2. avaliar visualmente Home, Terminal, Diagnóstico, Sessões e Mais;
3. confirmar que insets, navegação e densidade estão corretos no S23;
4. parear/conectar a TayTech;
5. confirmar reconexão `:5555` e fallback mDNS;
6. executar shell simples e multilinha;
7. executar receita VERDE;
8. gerar e compartilhar Evidence Pack;
9. observar crash/ANR/logcat durante o fluxo;
10. depois testar a adaptação em tela larga/multimídia.

**Não declarar PASS visual ou físico antes dessa prova.**

## Rollback

Nenhuma ROM, sistema, MCU, CAN ou pacote da TayTech foi alterado. Se esta build apresentar regressão, basta remover/parar a build de desenvolvimento do S23 e voltar ao artifact anterior enquanto a falha é corrigida.

## Codex Engineering Guardrails

`code-work` foi usado como gate. A reconstrução preservou o backend funcional, alterou apenas a superfície de produto necessária, reproduziu os erros de compilação, corrigiu as causas específicas e exigiu CI real + artifact real + hash independente antes do checkpoint.

## Próximo passo

**Instalar `CUSTOMROM-ADB-PREMIUM-debug.apk` no S23, abrir e enviar captura da Home. A partir da captura, fazer a auditoria visual real e ajustar densidade, proporção, tipografia, navegação e hierarquia antes de considerar a UI aprovada.**
