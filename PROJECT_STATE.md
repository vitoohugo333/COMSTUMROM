# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** fundação concluída; operação mobile-first formalizada; **Bloco 01 — Descobrir por que a central está lenta** pronto para a primeira coleta. Nenhuma otimização ADB foi aplicada ainda.  
**Papel da `main`:** linha principal de governança, documentação, evidência e ferramentas seguras.

## Onde estamos

Estamos no ponto **antes de qualquer otimização da TayTech**. A central continua no estado original em relação às ações deste projeto. O próximo passo é apenas criar uma fotografia de diagnóstico por ADB.

## Fotografia humana atual

**Governança pronta + operação pelo celular padronizada + diagnóstico inicial ainda não executado.**

Referências técnicas ficam em segundo plano e só são apresentadas ao proprietário quando acrescentarem rastreabilidade útil.

## Objetivo atual

Descobrir por que uma central anunciada com 4 GB de RAM apresenta lentidão, começando pela camada de menor risco: leitura ADB e evidência salva em arquivo legível.

## Repositório e linha de trabalho

- repositório oficial: `vitoohugo333/COMSTUMROM`;
- linha de trabalho humana: **Linha principal de governança e diagnóstico**;
- nome técnico da branch: `main`;
- nenhuma branch nova foi criada;
- publicação/deploy: não aplicável.

## Governança ativa

Arquivos canônicos incluem:

- `START_HERE.md`;
- `AGENTS.md`;
- `SKILLS.md`;
- `MOBILE_WORKFLOW.md`;
- `TESTING_RULES.md`;
- `ADB_RULES.md`;
- `ROM_SAFETY_RULES.md`;
- `LEARNING_RULES.md`;
- `PROJECT_STATE.md`;
- política e verificador de governança.

## Operação mobile-first

Regra permanente:

- instruções práticas chegam preparadas para celular/tablet;
- o agente informa **o que fazer, onde tocar, o que colar, onde o resultado ficará, o que acontecerá, o risco e o que deve ser enviado de volta**;
- quando a saída for grande, ela deve ser gravada automaticamente em arquivo em vez de exigir cópia manual de centenas de linhas;
- nomes humanos vêm antes de SHA, package name, código interno ou nome técnico;
- o proprietário não deve montar comandos, substituir placeholders ou interpretar identificadores técnicos para acompanhar o projeto.

A pasta padrão de evidências ADB é `/sdcard/CUSTOMROM/`, conforme `MOBILE_WORKFLOW.md`.

## Codex Engineering Guardrails

- plugin primeiro;
- skill direta como fallback;
- `code-verification` para auditoria/diagnóstico;
- `code-work` para escrita autorizada;
- a formalização mobile-first foi feita sob `code-work` direto como fallback.

## Baseline documental disponível

Informações já documentadas:

- sistema/build: `JCRK01-V1.0.60R8-251023_1036`;
- MCU: `JCMM40-0-2025.07.23_15:06`;
- camada/app CAN exibida: `1.0.3853.2026-06-17-09-33.060a51b6ee`;
- CAN box observada: `H1H2PAF23A-240409`;
- configuração CAN observada: HiWorld para Peugeot 208 Brasil;
- componentes Jancar/canbus observados nos logs;
- arquivos `.iap` tratados como firmware CAN/HiWorld, não como ROM Android.

## Estado físico/ADB

- Wireless ADB funciona, segundo validação anterior do proprietário;
- Bugjaeger está conectado à TayTech;
- **nenhum comando do diagnóstico inicial foi executado ainda**;
- nenhuma desativação, remoção, root, flash ou alteração de firmware foi feita por este projeto.

## Limites vigentes

- primeira ação: somente observação + criação de arquivo de evidência no armazenamento compartilhado;
- nenhum `disable-user` antes do diagnóstico;
- nenhum `pm uninstall --user 0` nesta fase;
- nenhum root, fastboot, flash, remount, partição, AVB, MCU ou CAN firmware;
- funções automotivas são protegidas por presunção até mapa de dependências.

## Notion sync

**CONCLUÍDO para a regra mobile-first e para o primeiro bloco operacional.**

O bloco foi renomeado para **Bloco 01 — Descobrir por que a central está lenta** e agora contém instrução pronta para celular, destino do relatório, consequência, risco, conferência e forma de envio.

## Verificação da governança

- `MOBILE_WORKFLOW.md` foi adicionado à governança canônica;
- `SKILLS.md` passou a exigir sua leitura antes de instruções práticas;
- o verificador determinístico passou a exigir a presença desse arquivo;
- CI remota ainda não deve ser chamada de verde sem execução fresca visível.

## Aprendizado

**Aprendizado fechado de operação:** o proprietário trabalha principalmente pelo celular/tablet; portanto, comandos e estados técnicos precisam ser empacotados em blocos humanos, copiáveis e com destino/consequência explícitos.

## Próximo passo

No Bugjaeger, executar **um único bloco copiável** que cria o arquivo:

`/sdcard/CUSTOMROM/diagnosticos/01_estado_inicial_da_central.txt`

Esse relatório será enviado ao agente para decidir a próxima coleta. Nenhuma otimização ocorre antes dessa interpretação.
