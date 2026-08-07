<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.2 -->
# CUSTOMROM TAYTECH — índice técnico obrigatório

Este arquivo é o mapa de leitura para qualquer agente. Deve ser lido depois de `AGENTS.md`.

## Comandos humanos mínimos

- `Liste os blocos ativos do CUSTOMROM.`
- `Leia o bloco X e siga-o.`

Quando um bloco do Notion for citado, ele funciona como roteador operacional. O agente deve recuperar sozinho Central Oficial, Estado Oficial, Roadmap, decisões, aprendizados, códigos internos, linha de trabalho, fotografia, evidências e regras relacionadas.

## Sequência obrigatória

1. bloco do Notion citado, quando houver;
2. `AGENTS.md`;
3. `SKILLS.md`;
4. `MOBILE_WORKFLOW.md` antes de instruir qualquer ação prática ao proprietário;
5. `TESTING_RULES.md`;
6. `ADB_RULES.md` para diagnóstico/otimização Android;
7. `ROM_SAFETY_RULES.md` para fronteiras estruturais;
8. `LEARNING_RULES.md` quando aplicável;
9. `PROJECT_STATE.md`;
10. fontes vivas: GitHub + aparelho + ADB/logs.

## Arquivos operacionais

| Área | Arquivo | Aplicação |
|---|---|---|
| Autoridade e escopo | `AGENTS.md` | todo trabalho |
| Entrada rápida | `START_HERE.md` | novo agente/chat |
| Operação pelo celular | `MOBILE_WORKFLOW.md` | todo comando, coleta, envio de evidência ou ação prática |
| Testes e evidência | `TESTING_RULES.md` | todo checkpoint técnico |
| ADB e debloat | `ADB_RULES.md` | shell, pacotes, processos, RAM/CPU, ajustes |
| ROM e hardware crítico | `ROM_SAFETY_RULES.md` | root, bootloader, AVB, partições, MCU/CAN, flash |
| Aprendizado fechado | `LEARNING_RULES.md` | defeito, quase falha, descoberta reutilizável |
| Estado vivo | `PROJECT_STATE.md` | linha de trabalho, fotografia, baseline, mudanças e próximo passo |
| Política da linha de trabalho | `ci/branch-policy.json` | papel e nível de risco esperado |
| Verificação automática | `scripts/ci/verify-repository.mjs` | integridade e governança |
| Memória operacional | Notion — Central/Blocos/Decisões/Aprendizados | missão, contexto e continuidade |

## Codex Engineering Guardrails — gate contínuo

O Guardrails é obrigatório em todo trabalho técnico.

1. tentar o plugin **Codex Engineering Guardrails**;
2. se não carregar, usar `code-verification` para leitura/diagnóstico/auditoria;
3. usar `code-work` antes da primeira escrita autorizada;
4. se o modo mudar de leitura para escrita, trocar para `code-work` antes da alteração;
5. antes de fechar checkpoint, confirmar que a verificação final está coberta pelo modo correto.

Falha de catálogo ou descoberta não prova indisponibilidade. Só declarar indisponível se plugin e skill aplicável falharem.

Se um trecho começou sem Guardrails, ele permanece **ainda não verificado pelo Guardrails** até ser revisado sob a skill correta.

## Regra de consistência entre linhas de trabalho

`AGENTS.md`, `SKILLS.md`, `MOBILE_WORKFLOW.md`, `TESTING_RULES.md`, `ADB_RULES.md`, `ROM_SAFETY_RULES.md`, `LEARNING_RULES.md` e `START_HERE.md` são canônicos na `main`.

Branches operacionais futuras devem manter cópia idêntica desses arquivos, salvo decisão explícita registrada. `PROJECT_STATE.md` e `ci/branch-policy.json` são específicos da branch.

A CI deve validar **marcador de versão válido + igualdade com a `main`**, nunca depender de um número histórico fixo de versão.

## Sincronização GitHub ↔ Notion

Atualize o Notion em checkpoint que mude o estado real: baseline, decisão, alteração, falha, aprendizado, CI, validação física ou encerramento de bloco.

Antes do próximo bloco funcional, GitHub + `PROJECT_STATE.md` + bloco do Notion devem estar coerentes.

## Responsabilidade do agente

O proprietário define objetivo e limites. O agente escolhe a verificação proporcional, interpreta evidência, mantém rollback e resolve referências internas. O proprietário não deve receber uma lista de dependências técnicas para reconstruir contexto já registrado.

Toda ação prática deve chegar já preparada para celular, com local de execução, bloco copiável, consequência, destino do resultado, risco e forma de devolver a evidência.