<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.1 -->
# CUSTOMROM TAYTECH — protocolo obrigatório de ADB

## Objetivo

Usar ADB como primeira camada de engenharia: observar, medir e otimizar o Android sem antecipar root, flash ou modificação estrutural.

## Princípio central

**Primeiro medir. Depois isolar. Só então alterar.**

ADB não transforma um comando em seguro apenas porque ele é fácil de executar. Em multimídia automotiva, um pacote Android pode ser a interface de uma dependência de MCU/CAN/áudio/suspensão.

## Sessão ADB — preflight

Antes de qualquer bloco:

1. confirmar que o alvo conectado é a TayTech correta;
2. registrar data/hora e build observada;
3. confirmar objetivo do bloco;
4. classificar comandos VERDE/AMARELO/VERMELHO;
5. confirmar Guardrails aplicável;
6. não executar comando AMARELO sem baseline e rollback.

## Baseline mínimo de desempenho

Executar somente os comandos suportados pela build e preservar a saída:

```sh
getprop
cat /proc/meminfo
cat /proc/swaps
dumpsys meminfo
ps -A
top -n 1
df -h
```

Complementos úteis quando suportados:

```sh
getprop ro.hardware
getprop ro.board.platform
getprop ro.product.board
cat /proc/cpuinfo
```

O agente deve interpretar a saída antes de pedir nova coleta. Não transformar o proprietário em executor de uma lista infinita de comandos.

## Investigação de pacote/processo

Quando um candidato aparecer:

```sh
dumpsys package <pacote>
dumpsys meminfo <pacote>
ps -A | grep <termo>
```

`logcat` deve ser filtrado por hipótese quando possível, não coletado indefinidamente sem objetivo.

## Alterações permitidas na fase inicial

### Force stop

```sh
am force-stop <pacote>
```

Uso: teste temporário de dependência/consumo. Não prova que o pacote pode ser removido ou desativado permanentemente.

### Disable reversível

```sh
pm disable-user --user 0 <pacote>
```

Rollback obrigatório:

```sh
pm enable <pacote>
```

Só usar depois de identificar o pacote, registrar motivo, baseline e risco.

### Ajustes de animação

Podem ser testados como alteração reversível de experiência quando o objetivo for percepção de fluidez. Registrar valores anteriores e novos.

## Fora da fase inicial

Não usar como rotina de debloat:

```sh
pm uninstall --user 0 <pacote>
```

Não executar root, remount, fastboot, flash, erase, partição ou AVB sob este protocolo. Essas ações pertencem a `ROM_SAFETY_RULES.md` e exigem autorização específica.

## Pacotes protegidos por presunção

Até prova contrária, tratar como críticos pacotes/serviços ligados a:

- `canbus`;
- `jancar`;
- MCU;
- car/vehicle info;
- launcher OEM quando consumidor de dados automotivos;
- áudio, DSP, amplifier;
- rádio;
- câmera/reverse;
- Bluetooth automotivo;
- ACC/power/sleep/wake;
- HVAC/climate;
- sensores/parking;
- update de firmware/MCU/CAN enquanto sua função não estiver delimitada.

“Protegido por presunção” significa investigar primeiro; não significa intocável para sempre.

## Método de debloat

Para cada candidato:

1. **Identidade:** nome do pacote, APK, versão, caminho e função provável.
2. **Atividade:** processo residente? CPU? PSS? serviços/receivers?
3. **Dependências:** quem envia/recebe intents, binds ou dados relevantes?
4. **Teste temporário:** `force-stop` quando útil.
5. **Observação:** interface, logs e funções do carro.
6. **Decisão:** manter, investigar ou propor `disable-user`.
7. **Disable:** somente se autorizado pelo bloco.
8. **Reboot:** quando necessário para simular uso real.
9. **Medição:** repetir RAM/CPU e comportamento.
10. **Validação física:** funções aplicáveis do veículo.
11. **Rollback:** executar imediatamente se houver regressão material.
12. **Registro:** GitHub + Notion.

## Regra de causalidade

Não desativar uma lista grande de pacotes desconhecidos e depois medir. Isso destrói a capacidade de saber o que causou ganho ou regressão.

Mudanças podem ser agrupadas apenas quando os pacotes são claramente da mesma função, a dependência está mapeada e o rollback é simples.

## Evidência de desempenho

“RAM livre” isolada não define fluidez. Avaliar em conjunto:

- `MemAvailable`;
- swap/ZRAM e pressão de memória;
- CPU em repouso e durante a lentidão;
- processos que acordam/reiniciam;
- uso de armazenamento/I/O quando houver evidência disponível;
- tempo de abertura/boot quando viável;
- comportamento real da UI.

## Saída padrão de cada bloco ADB

Registrar:

- objetivo;
- estado de entrada;
- comandos executados;
- saídas importantes;
- interpretação;
- alteração, se houve;
- antes/depois;
- rollback;
- validação física;
- resultado `PASS`, `PARTIAL`, `FAIL` ou `INCONCLUSIVE`;
- próximo passo único.
