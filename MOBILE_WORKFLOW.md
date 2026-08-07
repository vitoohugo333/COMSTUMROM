<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.2 -->
# CUSTOMROM TAYTECH — operação pelo celular

Este projeto é operado principalmente pelo celular/tablet. O agente deve reduzir ao mínimo digitação, troca de telas e interpretação técnica exigida do proprietário.

## Prioridade zero — conexão ADB previsível

Antes de qualquer bloco longo de diagnóstico, priorizar uma conexão ADB previsível.

- Se a TayTech estiver usando **Depuração sem fio** com porta dinâmica, tentar primeiro o modo TCP/IP clássico na porta **5555**.
- No Bugjaeger, a ação preferida é **Commands → Connect through WiFi**, que executa internamente o equivalente a `adb tcpip 5555` no alvo selecionado.
- Depois, reconectar usando `IP_DA_TAYTECH:5555` e validar com um comando simples no Shell.
- Considerar essa configuração **temporária até prova de persistência**. Em muitos aparelhos ela dura até reinício, desativação das opções de desenvolvedor ou mudança do modo ADB.
- Não usar root, remount, `build.prop`, scripts de boot ou alteração de ROM apenas para tornar 5555 permanente nesta fase.

## Formato obrigatório de qualquer ação prática

Toda instrução executável deve ser entregue nesta ordem:

1. **Objetivo humano** — o que vamos descobrir ou mudar, em uma frase.
2. **Onde executar** — por exemplo: `Bugjaeger → TayTech → Shell`.
3. **O que copiar e colar** — preferir um bloco único quando for seguro; evitar sequências longas de comandos separados.
4. **Onde o resultado ficará** — informar pasta e nome do arquivo quando houver saída persistente.
5. **O que vai acontecer** — explicar a consequência prática antes da execução.
6. **Risco** — VERDE, AMARELO ou VERMELHO, traduzido em linguagem simples.
7. **Como conferir** — um comando curto ou caminho visual para verificar que a ação ocorreu como esperado.
8. **Como devolver a evidência** — dizer exatamente qual arquivo, captura ou texto o proprietário deve enviar ao agente.
9. **O que não fazer** — somente quando houver uma fronteira relevante no mesmo contexto.

## Otimização de comandos para celular

- Preferir comandos prontos para **copiar e colar**.
- Agrupar somente comandos que pertencem ao mesmo objetivo e tenham o mesmo nível de risco.
- Quando houver muita saída, salvar automaticamente em arquivo em vez de exigir seleção/cópia manual de centenas de linhas.
- Usar nomes legíveis de pastas e arquivos.
- Não pedir que o proprietário renomeie arquivos tecnicamente, monte comandos, substitua placeholders ou descubra caminhos que o agente já consegue definir.
- Se for necessário um valor variável, explicar visualmente onde encontrá-lo e fornecer o comando final já montado sempre que possível.
- Depois de cada bloco, parar para interpretar antes de ampliar a coleta ou aplicar nova mudança.

## Pasta padrão de trabalho ADB nesta TayTech

Para esta central, usar explicitamente o armazenamento interno emulado:

`/storage/emulated/0/CUSTOMROM/`

Subpastas padrão:

- `/storage/emulated/0/CUSTOMROM/diagnosticos/` — saídas de leitura, inventários e medições;
- `/storage/emulated/0/CUSTOMROM/backups/` — cópias de segurança permitidas;
- `/storage/emulated/0/CUSTOMROM/logs/` — logs capturados;
- `/storage/emulated/0/CUSTOMROM/experimentos/` — evidências de testes reversíveis autorizados.

Criar arquivos nessa pasta não altera ROM, sistema, MCU ou CAN. É apenas armazenamento de evidência no espaço compartilhado da própria TayTech quando o Shell remoto está conectado ao aparelho-alvo.

## Convenção de nomes humanos

Evitar nomes como `dump01.txt`, `xpto.log` ou SHA como título principal.

Preferir:

- `01_estado_inicial_da_central.txt`
- `02_memoria_em_repouso.txt`
- `03_processos_mais_pesados.txt`
- `antes_de_desativar_nome-do-app.txt`
- `depois_de_desativar_nome-do-app.txt`

A data/hora pode ser incluída automaticamente quando acrescentar valor, mas não deve tornar o nome incompreensível.

## Regra de consequência explícita

Antes de qualquer comando, o proprietário deve saber qual destas situações se aplica:

- **Só observa:** não muda o funcionamento da central.
- **Cria evidência:** apenas grava um arquivo de diagnóstico no armazenamento compartilhado.
- **Muda temporariamente:** a alteração desaparece ao reverter ou reiniciar, conforme o caso.
- **Muda configuração reversível:** exige rollback explícito já fornecido.
- **Muda estrutura:** exige autorização específica e plano de recuperação antes de qualquer execução.

## Regra de linguagem

Termos técnicos podem aparecer quando necessários, mas sempre acompanhados da tradução operacional. O proprietário deve conseguir executar e entender o efeito sem saber programação, Git, Android internals ou shell.

## Modelo de entrega

**O que vamos fazer:** descobrir quanta memória realmente sobra com a central parada.

**Onde:** Bugjaeger → TayTech → Shell.

**Cole isto:**
```sh
comando-pronto
```

**Vai salvar em:** `/storage/emulated/0/CUSTOMROM/diagnosticos/arquivo_legivel.txt`.

**Consequência:** só lê informações e grava o relatório; não desativa nem altera nenhum aplicativo.

**Risco:** VERDE — sem mudança funcional.

**Depois:** abra o arquivo indicado no Bugjaeger e envie-o ao agente.

Esse modelo deve ser adaptado ao caso concreto, não repetido mecanicamente quando uma instrução mais curta for igualmente clara.