# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** fundação de governança e Central Oficial concluídas; `Bloco 01 — Baseline ADB de desempenho` aberto. Nenhuma otimização ADB foi aplicada ainda por este projeto.  
**Papel da `main`:** governança canônica, documentação, evidência e ferramentas seguras. Não existe branch funcional separada neste checkpoint.

## Objetivo atual

Começar pela camada de menor risco e maior retorno: **diagnóstico e otimização reversível via ADB**, medindo por que uma central com 4 GB de RAM apresenta lentidão antes de considerar ROM, root ou flash.

## Repositório

- oficial: `vitoohugo333/COMSTUMROM`;
- branch atual: `main`;
- estado de entrada desta fundação: repositório vazio;
- branch nova: não criada;
- publicação/deploy: não aplicável.

## Governança criada

Arquivos canônicos:

- `START_HERE.md`;
- `AGENTS.md`;
- `SKILLS.md`;
- `TESTING_RULES.md`;
- `ADB_RULES.md`;
- `ROM_SAFETY_RULES.md`;
- `LEARNING_RULES.md`;
- `PROJECT_STATE.md`;
- `ci/branch-policy.json`;
- `scripts/ci/verify-repository.mjs`;
- `.github/workflows/ci-autonomous.yml`.

## Codex Engineering Guardrails

- plugin primeiro;
- skill direta como fallback;
- `code-verification` para auditoria/diagnóstico;
- `code-work` para mudança autorizada;
- neste bootstrap documental, `code-work` foi carregado diretamente como fallback antes da primeira escrita.

## Baseline documental já disponível

### Sistema principal

Informações visíveis na tela de sistema fornecida pelo proprietário:

- build Android/sistema: `JCRK01-V1.0.60R8-251023_1036`;
- MCU: `JCMM40-0-2025.07.23_15:06`;
- versão da camada/app CAN exibida: `1.0.3853.2026-06-17-09-33.060a51b6ee`.

### CAN / veículo

Logs fornecidos registram:

- CAN box respondendo como `H1H2PAF23A-240409`;
- configuração do aplicativo CAN: `Hiworld-Peugeot-208-2023~Present (Brazil)-All`;
- stack Android observada com componentes `Jancar*` e pacote/processo `canbus`.

### Arquivos `.iap`

A auditoria inicial classificou os `.iap` recebidos como firmware da camada CAN/HiWorld, não como ROM Android. Nenhum `.iap` foi instalado pelo projeto.

## Estado físico/ADB

- o proprietário informou que o Wireless ADB da central funciona;
- o celular/tablet de controle está conectado à central pelo Bugjaeger;
- **nenhum comando de diagnóstico do Bloco 01 foi executado ainda**;
- desempenho atual relatado: lentidão perceptível mesmo com uso leve; causa ainda não confirmada.

## Limites vigentes

- começar somente com comandos VERDES de leitura;
- nenhum `disable-user` antes de baseline e análise;
- nenhum `pm uninstall --user 0` nesta fase;
- nenhum root, fastboot, flash, remount, alteração de partição, AVB, MCU ou CAN firmware;
- funções automotivas permanecem protegidas por presunção até mapa de dependências.

## Notion sync

**CONCLUÍDO neste checkpoint.**

Central criada: `CUSTOMROM TAYTECH — Central Oficial do Projeto`.

Estrutura criada:

- `00 — Instrução Operacional`;
- `01 — Estado Oficial`;
- `02 — Roadmap Mestre`;
- `03 — Governança e Autoridade`;
- `04 — Handoff para Novo Chat`;
- `05 — Registro de Alterações do Notion`;
- banco `Blocos de Execução`;
- banco `Decisões`;
- banco `Aprendizados`;
- `Fundação — Governança e Central Oficial` marcada como PASS;
- `Bloco 01 — Baseline ADB de desempenho` em execução;
- decisões `D-001` a `D-004` registradas;
- alteração estrutural de memória registrada como `CR-001`.

## Verificação da governança

- criação remota dos arquivos confirmada pelo conector GitHub;
- fotografia da `main` confirmada pelo histórico remoto;
- `scripts/ci/verify-repository.mjs` teve sintaxe validada localmente e passou numa simulação determinística do contrato de arquivos, JSON, marcadores e segredo em texto claro;
- o conector disponível nesta sessão não expôs uma execução `push` do GitHub Actions para confirmação; portanto **não afirmar CI remota verde neste checkpoint**.

## Evidência necessária para o primeiro diagnóstico

O Bloco 01 começa com uma coleta curta, em repouso, e para para interpretação antes de ampliar comandos:

```sh
getprop ro.hardware
getprop ro.board.platform
getprop ro.product.board
cat /proc/meminfo
cat /proc/swaps
```

## Aprendizado

- **Aprendizado fechado de governança:** o CUSTOMROM herdou do VETTA a prevenção contra CI acoplada a uma versão histórica fixa; a regra é marcador de governança válido e, em branches futuras, paridade com a `main`.
- Nenhum aprendizado permanente novo do aparelho ainda; o baseline ADB ainda não começou.

## Próximo passo único

Executar a **Coleta A** do `Bloco 01 — Baseline ADB de desempenho` no Shell do Bugjaeger e analisar o resultado antes de qualquer comando AMARELO.
