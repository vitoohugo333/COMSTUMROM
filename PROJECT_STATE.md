# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** fundação concluída; operação mobile-first formalizada; **nova prioridade operacional: estabilizar o ADB em uma porta previsível antes do baseline de desempenho**. Nenhuma otimização de performance foi aplicada ainda.  
**Papel da `main`:** linha principal de governança, documentação, evidência e ferramentas seguras.

## Onde estamos

Estamos no ponto **antes de qualquer otimização da TayTech**. A central continua no estado original em relação a desempenho, pacotes, ROM, MCU e CAN.

O problema operacional atual é a porta dinâmica da **Depuração sem fio**. Antes de iniciar o diagnóstico longo, o projeto tentará colocar o ADB da TayTech em **TCP/IP porta 5555**, de forma reversível.

## Fotografia humana atual

**Governança pronta + operação pelo celular padronizada + tentativa de porta ADB estável é o próximo passo.**

## Objetivo atual

1. reduzir o atrito operacional colocando o ADB em uma porta previsível quando a ROM permitir;
2. validar reconexão em `IP_DA_TAYTECH:5555`;
3. somente depois executar o baseline de hardware, RAM, swap, CPU e processos.

## Repositório e linha de trabalho

- repositório oficial: `vitoohugo333/COMSTUMROM`;
- linha de trabalho humana: **Linha principal de governança e diagnóstico**;
- nome técnico da branch: `main`;
- nenhuma branch nova foi criada;
- publicação/deploy: não aplicável.

## Operação mobile-first

Regra permanente:

- instruções práticas chegam preparadas para celular/tablet;
- o agente informa **o que fazer, onde tocar, o que colar, onde o resultado ficará, o que acontecerá, o risco e o que deve ser enviado de volta**;
- quando a saída for grande, ela deve ser gravada automaticamente em arquivo;
- nomes humanos vêm antes de SHA, package name, código interno ou nome técnico;
- o proprietário não deve montar comandos, substituir placeholders ou interpretar identificadores técnicos para acompanhar o projeto.

Pasta padrão de evidências nesta TayTech:

`/storage/emulated/0/CUSTOMROM/`

## Prioridade ADB atual

A ação preferida é usar no Bugjaeger **Commands → Connect through WiFi** com a TayTech já selecionada. Essa função executa internamente o equivalente a:

`adb tcpip 5555`

Se a ROM aceitar:

- o daemon ADB passa a escutar na porta `5555`;
- a próxima conexão é feita por `IP_DA_TAYTECH:5555`;
- a configuração é tratada como temporária até prova de persistência;
- nenhum root, remount, `build.prop`, init script ou alteração de ROM será usado apenas para fixar a porta nesta fase.

## Estado físico/ADB

- Wireless ADB funciona, segundo validação do proprietário;
- Bugjaeger consegue conectar à TayTech;
- a porta da Depuração sem fio muda durante o uso, gerando atrito operacional;
- **porta 5555 ainda não foi validada nesta central**;
- nenhuma desativação, remoção, root, flash ou alteração de firmware foi feita por este projeto.

## Limites vigentes

- tentativa de `adb tcpip 5555`: **AMARELO, reversível**;
- nenhum `disable-user` antes do diagnóstico;
- nenhum `pm uninstall --user 0` nesta fase;
- nenhum root, fastboot, flash, remount, partição, AVB, MCU ou CAN firmware;
- funções automotivas são protegidas por presunção até mapa de dependências.

## Notion sync

**CONCLUÍDO para a mudança de prioridade.**

O `Bloco 01 — Descobrir por que a central está lenta` recebeu um **Passo 0** para estabilizar o ADB em `5555`, e a decisão **D-006 — Porta ADB previsível antes do baseline** foi registrada.

## Codex Engineering Guardrails

- plugin primeiro;
- skill direta como fallback;
- `code-verification` para auditoria/diagnóstico;
- `code-work` para escrita autorizada;
- esta mudança de prioridade e sincronização foi feita sob `code-work` direto como fallback.

## Próximo passo

Na conexão atual da TayTech pelo Bugjaeger, executar **Connect through WiFi** e testar reconexão em `IP_DA_TAYTECH:5555`.

Somente depois de confirmar que o Shell funciona na porta 5555 seguimos para o baseline de desempenho.