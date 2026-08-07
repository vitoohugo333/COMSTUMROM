<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.1 -->
# CUSTOMROM TAYTECH — regras permanentes de testes e evidência

## Princípio

O proprietário autoriza objetivos; o agente responde pela prova técnica. Teste não é uma etapa opcional oferecida ao proprietário.

## Camadas de prova

### 1. Integridade documental

Para mudanças de governança:

- arquivos obrigatórios presentes;
- marcadores de versão válidos;
- JSON válido;
- scripts com sintaxe válida;
- ausência de segredo em texto claro;
- `PROJECT_STATE.md` coerente com a branch e o checkpoint.

### 2. Diagnóstico ADB somente leitura

Antes de otimização, obter baseline suficiente para responder ao risco real. Quando suportado pelo aparelho:

- `getprop` e identificação de build/board;
- `/proc/meminfo`;
- `/proc/swaps` e ZRAM quando existente;
- `dumpsys meminfo`;
- `ps -A`;
- `top` em repouso e sob carga relevante;
- `df -h`;
- inventário de pacotes/serviços relevantes;
- `logcat` focado quando houver comportamento anômalo.

A ausência de um comando em determinada build não é falha do produto; deve ser substituída por evidência equivalente e registrada.

### 3. Alteração ADB reversível

Cada mudança AMARELA deve possuir:

1. estado anterior documentado;
2. pacote/configuração alvo identificado;
3. comando exato executado;
4. rollback exato;
5. medição antes/depois quando o objetivo for desempenho;
6. reinício quando relevante;
7. validação funcional Android;
8. validação física automotiva aplicável.

Mudanças múltiplas só podem ser agrupadas quando a relação causal e o rollback permanecerem claros. Em dúvida, reduzir o lote.

### 4. APK/UI

APK alterado deve ser comparado ao original preservado. Verificar, conforme o caso:

- identidade/pacote/versão;
- permissões;
- services, receivers e providers;
- assinatura e implicações de permissões `signature`/`privileged`;
- instalação/upgrade/rollback em ambiente seguro;
- logs de crash/ANR;
- integração com serviços Jancar/MCU/CAN quando aplicável;
- validação física no aparelho.

### 5. ROM/firmware

Nenhuma prova puramente estática autoriza flash. Antes de qualquer ação VERMELHA devem existir, conforme aplicável:

- identificação inequívoca do hardware/board;
- dump ou pacote original preservado;
- mapa de partições;
- estado de A/B e `super` quando existentes;
- estado de AVB/dm-verity;
- método de recuperação testável;
- energia estável e janela segura;
- compatibilidade exata da imagem;
- rollback definido;
- autorização explícita específica.

### 6. Validação física automotiva

Quando uma alteração puder tocar dependências do veículo, verificar as funções existentes/aplicáveis, incluindo:

- boot e retorno de suspensão;
- ACC/sleep-wake;
- áudio e volume;
- rádio;
- Bluetooth;
- comandos de volante;
- HVAC;
- câmera de ré;
- sensores/overlay de estacionamento;
- informações CAN e estados do veículo.

Não marcar PASS em integração física sem validação no aparelho.

## Classificação de resultado

- **PASS:** critérios cobertos por evidência fresca e sem risco material pendente.
- **PARTIAL:** parte comprovada, mas ainda falta camada importante de prova.
- **FAIL:** evidência direta mostra que requisito ou segurança não foi atendido.
- **INCONCLUSIVE:** evidência insuficiente ou conflitante.

## Falhas

Classificar como:

- defeito da personalização;
- dependência OEM descoberta;
- problema de comando/ADB;
- limitação de permissão;
- problema de ambiente/ferramenta;
- falha preexistente comprovada;
- resultado inconclusivo.

Nunca repetir silenciosamente até funcionar. Nunca apagar evidência válida para “limpar” o resultado.

## Critério de conclusão

Um bloco só encerra quando:

1. critérios de aceite estão ligados a evidência;
2. fotografia do GitHub reportada é a testada;
3. rollback está conhecido para mudanças reversíveis;
4. validação física está registrada ou explicitamente pendente;
5. `PROJECT_STATE.md` e Notion estão sincronizados;
6. o aprendizado foi classificado conforme `LEARNING_RULES.md`.
