# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** fundação de governança criada; nenhuma otimização ADB aplicada ainda por este projeto.  
**Papel da `main`:** governança canônica, documentação, evidência e ferramentas seguras. Não existe branch funcional separada neste checkpoint.

## Objetivo atual

Começar pela camada de menor risco e maior retorno: **diagnóstico e otimização reversível via ADB**, medindo por que uma central com 4 GB de RAM apresenta lentidão antes de considerar ROM, root ou flash.

## Repositório

- oficial: `vitoohugo333/COMSTUMROM`;
- branch atual: `main`;
- estado de entrada desta fundação: repositório vazio;
- branch nova: não criada;
- publicação/deploy: não aplicável.

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
- **nenhum comando de diagnóstico deste novo bloco foi executado ainda sob a governança criada aqui**;
- desempenho atual relatado: lentidão perceptível mesmo com uso leve; causa ainda não confirmada.

## Limites vigentes

- começar somente com comandos VERDES de leitura;
- nenhum `disable-user` antes de baseline e análise;
- nenhum `pm uninstall --user 0` nesta fase;
- nenhum root, fastboot, flash, remount, alteração de partição, AVB, MCU ou CAN firmware;
- funções automotivas permanecem protegidas por presunção até mapa de dependências.

## Evidência necessária para o primeiro diagnóstico

Coletar um baseline curto da central em repouso pelo Bugjaeger e usar a saída para decidir o próximo comando, em vez de pedir uma lista extensa de coletas cegas.

## Notion sync

`PENDENTE` neste ponto da criação do repositório. Deve ser resolvido no mesmo bloco, criando a Central Oficial, Estado, Roadmap, Governança, Handoff, bancos de Blocos/Decisões/Aprendizados e o primeiro bloco ADB.

## Aprendizado

Nenhum aprendizado permanente novo do aparelho neste bootstrap; a governança foi adaptada das proteções maduras do VETTA, incluindo Guardrails contínuo, sincronização GitHub ↔ Notion, aprendizado fechado e verificação de governança sem versão histórica fixa.

## Próximo passo único

Concluir a Central Oficial no Notion e, depois da sincronização, iniciar **Bloco 01 — Baseline ADB de desempenho**, com coleta somente leitura.
