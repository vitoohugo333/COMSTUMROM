<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.2 -->
# CUSTOMROM TAYTECH — regras obrigatórias do projeto

O objetivo do projeto é entender, medir, otimizar e personalizar a multimídia TayTech preservando a capacidade de recuperação e, principalmente, as funções automotivas proprietárias. A primeira fase é **ADB e diagnóstico reversível**. ROM, root, bootloader, AVB, MCU, CAN e flash são fronteiras posteriores e separadas.

## Interface humana por blocos autodirecionáveis

O proprietário opera o projeto principalmente pelo celular/tablet. A complexidade técnica deve ser absorvida pelo agente.

Dois comandos humanos devem bastar para retomar a memória operacional:

- **“Liste os blocos ativos do CUSTOMROM.”** O agente consulta o Notion e apresenta nome, objetivo, branch/papel e estado atual em linguagem simples.
- **“Leia o bloco X e siga-o.”** O bloco passa a ser o ponto de entrada. O agente resolve sozinho Central Oficial, Estado Oficial, Roadmap, decisões, aprendizados, códigos internos, branch, commit, governança, evidências ADB e demais dependências.

O proprietário não é responsável por memorizar SHAs, comandos ADB, arquivos, códigos internos ou dependências técnicas já registradas.

## Regra absoluta de linguagem humana

A interface com o proprietário deve ser **humana primeiro e técnica depois**.

Identificadores técnicos continuam existindo para rastreabilidade, mas nunca podem ser apresentados como se fossem o nome do estado ou da ação.

Regras obrigatórias:

- toda fotografia salva deve receber um **nome humano descritivo** antes do SHA;
- todo bloco deve ter nome que explique a ação ou resultado, não apenas código interno;
- toda branch deve ser explicada pelo seu papel quando mencionada pela primeira vez;
- todo comando ADB deve vir acompanhado de uma frase simples dizendo o que ele vai observar ou alterar;
- códigos como SHA, `CR-xxx`, nomes de arquivo, package names e IDs ficam em segundo plano e só aparecem quando acrescentarem rastreabilidade útil;
- nunca responder ao proprietário apenas com SHA, nome de arquivo, código de decisão, número de workflow ou mensagem técnica;
- quando o identificador técnico for necessário, usar o formato: **Nome humano — referência técnica**;
- em resumos de estado, priorizar: **o que é**, **o que aconteceu**, **efeito prático**, **o que ficou intacto** e **próximo passo**.

Exemplo correto:

> **Fotografia: Fundação da governança pronta e sincronizada.** Referência técnica: `6600401…`.

Exemplo incorreto:

> `660040137a54062c284a2cbe831046772a51e564` é a fotografia atual.

O proprietário nunca deve precisar interpretar um identificador técnico para entender o projeto.

## Sequência obrigatória

Quando um bloco do Notion for citado, leia primeiro o bloco e siga suas referências. Antes de qualquer diagnóstico, conclusão ou alteração técnica, leia na branch realmente afetada:

1. `AGENTS.md`;
2. `SKILLS.md`;
3. `TESTING_RULES.md`;
4. `ADB_RULES.md` quando houver ADB, pacotes, processos, memória, CPU ou ajustes Android;
5. `ROM_SAFETY_RULES.md` quando houver root, bootloader, partições, AVB, firmware, MCU, CAN ou flash;
6. `LEARNING_RULES.md` quando houver defeito, quase falha ou descoberta reutilizável;
7. `PROJECT_STATE.md`;
8. GitHub e evidência física/ADB fresca.

## Fontes de verdade

1. ordem explícita mais recente do proprietário;
2. governança vigente da branch-alvo;
3. evidência física/ADB fresca do aparelho e arquivos originais diretamente extraídos;
4. GitHub: branch, commit, arquivos, diff, PR e CI;
5. `PROJECT_STATE.md` da branch-alvo;
6. Notion como memória operacional de missão, decisões, blocos e aprendizados;
7. chat, resumos e capturas apenas como contexto auxiliar.

Todo fato mutável deve indicar a origem e o momento da checagem. Se não puder ser confirmado, declarar **não confirmado** e não completar a lacuna com suposição.

## Ferramentas obrigatórias

- Use o conector GitHub como fonte remota primária do repositório.
- Use o Notion para memória operacional e sincronização de checkpoints relevantes.
- Use o **Codex Engineering Guardrails** durante toda operação técnica: plugin primeiro; se o plugin não carregar, use diretamente `code-verification` para auditoria/diagnóstico e `code-work` para qualquer escrita autorizada.
- Só declarar Guardrails indisponível se plugin e skill diretamente aplicável falharem.

Se o agente perceber que começou sem Guardrails, deve parar no próximo ponto seguro, ativar a skill correta, reler contrato e governança e revisar o trecho já executado antes de continuar.

## Fronteiras de risco

Classifique qualquer ação antes de executá-la ou instruí-la:

### VERDE — somente leitura

Exemplos: `getprop`, `/proc/meminfo`, `dumpsys`, `ps`, `top`, `logcat`, inventário de pacotes, leitura de propriedades e cópia de arquivos permitidos.

Pode ser usada em diagnóstico autorizado sem alterar o aparelho.

### AMARELO — alteração reversível

Exemplos: `force-stop`, `pm disable-user --user 0`, limpeza controlada de cache/dados quando explicitamente necessária, ajustes reversíveis de animação e preferências de usuário.

Exige baseline anterior, alvo identificado, rollback conhecido e validação funcional depois da mudança.

### VERMELHO — estrutural/destrutivo

Exemplos: `pm uninstall --user 0`, root, `adb root`, remount, bootloader unlock, fastboot, flash, erase, escrita em partições, alteração de AVB/dm-verity, recovery, MCU, CAN firmware, `.iap`, downgrade ou atualização de firmware.

Nunca executar nem orientar como próximo comando operacional sem autorização explícita específica e contrato de recuperação correspondente.

## Regra absoluta para funções automotivas

CAN, MCU, câmera de ré, sensores, comandos de volante, áudio/DSP, rádio, ACC, sleep/wake, Bluetooth automotivo e HVAC devem ser tratados como **dependências críticas** quando presentes.

- pacote com nome estranho não é bloat por presunção;
- integração automotiva não pode ser desativada apenas porque o consumo parece alto;
- antes de `disable-user`, mapear processo, pacote, serviços/receivers relevantes e evidência de uso;
- a validação física deve cobrir as funções aplicáveis depois de cada bloco de mudança.

## Regra de baseline antes de otimizar

Nenhum ganho de desempenho pode ser afirmado sem comparação antes/depois.

O baseline mínimo inclui, quando suportado:

- identificação do build, board, SoC e arquitetura;
- RAM total/disponível;
- swap/ZRAM;
- CPU/processos em repouso;
- armazenamento livre e sinais de pressão de I/O disponíveis;
- lista de pacotes/processos relevantes;
- tempo de boot percebido ou medido quando viável;
- estado funcional do veículo antes da mudança.

## Debloat progressivo e reversível

A sequência padrão é:

1. identificar o candidato e sua função provável;
2. medir consumo e dependências;
3. `force-stop` quando isso produzir evidência útil;
4. observar efeitos;
5. somente então `disable-user --user 0`, se autorizado;
6. reiniciar quando necessário;
7. repetir medições;
8. validar funções Android e automotivas aplicáveis;
9. manter rollback documentado com `pm enable <pacote>`;
10. registrar resultado no GitHub/Notion.

Não desativar vários pacotes desconhecidos de uma vez. Uma mudança observável por bloco de prova.

## Autoridade e escopo

Uma autorização para um bloco cobre investigação proporcional, edição documental prevista, testes necessários, pequenas correções indispensáveis ao mesmo objetivo e sincronização do checkpoint.

Exigem autorização separada: criação de branch, merge, release, alteração fora do objetivo, ação destrutiva, flash, root, bootloader, partições, MCU/CAN firmware, exclusão de evidência ou credenciais.

### Criação de branch

Criar branch nova sempre exige autorização explícita do proprietário. Antes de recomendar uma branch, avaliar se a atual realmente não comporta o trabalho e explicar o custo de fragmentar conhecimento.

## Evidência e linguagem

Nunca dizer “ficou mais rápido”, “seguro”, “não usa”, “pode desativar”, “funcionando” ou “sem risco” sem evidência identificável.

O agente deve explicar ao proprietário, em linguagem simples:

- onde está o estado;
- o que foi observado ou alterado;
- por que;
- efeito prático;
- o que ficou intocado;
- evidência antes/depois;
- rollback;
- validação física ainda pendente;
- próximo passo único.

Commit é uma **fotografia salva do projeto**, não um número de versão crescente. Para o proprietário, essa fotografia deve sempre ter um **nome humano**; o SHA é apenas a referência técnica dessa fotografia.

## Sincronização GitHub ↔ Notion

GitHub prova o estado técnico; Notion preserva a memória operacional. Atualize o Notion em checkpoints relevantes: baseline concluído, decisão, alteração, CI, falha classificada, aprendizado, validação física ou encerramento de bloco.

Antes de iniciar o próximo bloco funcional, `branch + PROJECT_STATE.md + bloco do Notion` devem contar a mesma história. Se o Notion falhar, registrar `Notion sync: PENDENTE` no `PROJECT_STATE.md` e resolver antes do próximo bloco funcional.

## Formato final obrigatório para trabalho técnico

- Modo executado:
- Objetivo aprovado:
- Onde estamos:
- Fotografia humana do estado:
- Referência técnica (somente se útil):
- Estado explicado:
- Evidência coletada:
- Alterações feitas:
- Rollback:
- Testes/CI:
- Validação física:
- Notion sync:
- Ações vermelhas executadas?:
- Próximo passo único:
