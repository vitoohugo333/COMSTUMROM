# Estado oficial — CUSTOMROM ADB S23 Premium Acionável

**Atualizado em:** 2026-08-08, horário de Brasília  
**Linha de trabalho:** `refactor/customrom-adb-s23-premium`  
**Estado:** Functional Action Graph implementado, contrato estático permanente PASS, Android build PASS, artifact baixado e hash conferido. Validação física desta nova APK no S23 → TayTech permanece pendente.

## Fotografia humana atual

**A linguagem visual premium aprovada foi preservada. O CUSTOMROM deixou de tratar o log como fim natural das receitas: o fluxo novo tenta interpretar o que foi encontrado, transformar packages/processos/configurações em objetos navegáveis, oferecer próximos passos e só então deixar a evidência técnica disponível sob demanda.**

O Galaxy S23 continua sendo o controlador. A TayTech continua sendo o alvo remoto via Wireless ADB.

## Evidência física que originou este ciclo

O proprietário validou a versão premium operacional no S23 e aprovou fortemente UI, navegação, conexão e novas funções. As capturas de uso real, porém, mostraram uma lacuna funcional clara: receitas como `top` e o diagnóstico de lentidão abriam grandes blocos de saída técnica como resultado principal e repetiam a evidência em uma área `Saída técnica`.

Decisão de produto decorrente:

`INTENÇÃO → COLETA → INTERPRETAÇÃO → OBJETOS ACIONÁVEIS → PRÓXIMOS PASSOS → AÇÃO ESCOLHIDA → VERIFICAÇÃO → HISTÓRICO/ROLLBACK/EVIDÊNCIA`

Log continua preservado, mas passa a ser **evidência secundária** quando existe uma próxima ação humana natural.

## Functional Action Graph v1

Novo arquivo: `FunctionalActionEngine.kt`.

O engine recebe `recipeId + raw output` e devolve:

- título humano;
- resumo;
- achados estruturados;
- lista de próximas ações;
- destino da ação;
- risco da próxima ação;
- acesso separado à evidência técnica.

Destinos atualmente suportados:

- package específico;
- filtro da tela Apps;
- outra receita;
- outra tela;
- Terminal.

O engine **não executa alterações sozinho**. A escolha continua pertencendo ao usuário e qualquer ação AMARELA passa pelo gate normal de confirmação/rollback.

## Jornadas acionáveis implementadas

### Por que a central está lenta?

Interpreta quando disponível:

- `MemTotal`;
- `MemAvailable`;
- swap/ZRAM;
- principais owners de CPU expostos por `dumpsys cpuinfo`.

Depois oferece ações como:

- investigar package consumidor diretamente em Apps;
- abrir Apps filtrado por `Rodando`;
- cruzar com wakelocks/alarmes;
- repetir a mesma coleta para comparação posterior.

Nenhum consumidor é automaticamente classificado como bloat ou desativado.

### O que inicia junto com a central?

A receita `boot-servicos` deixa de terminar apenas no dump. Packages extraídos da evidência viram atalhos para a tela Apps, onde o usuário vê criticidade, confiança, razões e ações permitidas.

### Quais apps estão falhando?

`falhas-crashes` e `logcat-curto` tentam extrair packages relacionados a crashes/ANRs/eventos e oferecem investigação contextual. Presença no log **não é tratada como prova de culpa**.

### Quem acorda a central?

`wakelocks-alarmes` transforma possíveis owners em packages navegáveis e permite cruzar a evidência com jobs.

### O que trabalha em segundo plano?

`jobs-agendados` liga jobs a packages quando possível e permite abrir o detalhe do app ou cruzar com wakelocks.

### Personalizações reversíveis

Depois de ações como animações, rotação e stay-on, o resultado oferece uma ação de **verificação**, em vez de apenas confirmar que o comando executou.

## Diagnóstico — nova apresentação

A tela Diagnóstico ganhou perguntas explícitas:

- **O que inicia junto com a central?**
- **Quais apps estão falhando?**
- **Quem acorda a central?**
- **O que trabalha em segundo plano?**

O último resultado agora prioriza resumo humano. A saída bruta fica oculta por padrão e aparece apenas ao tocar em **Ver evidência técnica**.

Existe uma nova área **O que você pode fazer agora**, preenchida com ações derivadas do resultado.

## Apps continua sendo a superfície de decisão

O inventário e a inteligência já existentes foram preservados:

`Todos | Rodando | Usuário | Sistema | Desativados | Protegidos | Candidatos | Alterados`

Cada package continua recebendo:

- criticidade `PROTEGIDO | ALTA | MÉDIA | BAIXA | DESCONHECIDA`;
- confiança;
- razões observáveis;
- estado sistema/usuário;
- enabled/disabled/running;
- caminho do APK quando disponível.

Ações permanecem:

- **Analisar** — VERDE;
- **Parar temporariamente** — AMARELO;
- **Desativar reversivelmente** — AMARELO;
- **Restaurar** — somente quando o ChangeLedger comprova que o CUSTOMROM realizou a desativação.

Packages PROTEGIDO/ALTA continuam sem stop/disable no fluxo comum.

## Resultado humano / timeout / segurança preservados

Continuam válidos:

- `exit=0` não aparece como “saída zero”;
- sucesso sem stdout tem estado próprio;
- command error ≠ transport error;
- timeout padrão de shell = 45 s;
- timeout cancela a task, reseta transporte e tenta recuperar conexão;
- VERDE = leitura;
- AMARELO = interação ou mudança reversível;
- VERMELHO = estrutural/destrutivo e bloqueado no fluxo comum.

Nenhuma mudança foi feita em ROM, MCU, CAN, partições, root, AVB ou firmware.

## Catálogo preservado

As **44 receitas** existentes foram mantidas. O Bloco 04 muda principalmente o que acontece **depois** da coleta, não remove capacidades anteriores.

## Contrato de regressão permanente

`tools/validate_native_customrom.py` agora exige explicitamente:

- `FunctionalActionEngine` presente;
- `FunctionalActionGraph` ligado à Activity;
- boot/crash/wake/jobs com jornadas acionáveis;
- área `O que você pode fazer agora`;
- evidência técnica secundária;
- receitas usando `showDialog = false` no caminho comum, impedindo retorno ao padrão de abrir dump bruto automaticamente;
- botão explícito para ver evidência técnica.

No run final o validador imprimiu:

- `functional_action_graph=present`;
- `raw_evidence=secondary`;
- `actionable_boot_crash_wake_jobs=present`.

## Build final do Bloco 04

- contrato estático: **PASS**;
- Android build: **PASS**;
- Gradle assembleDebug: **BUILD SUCCESSFUL**;
- artifact: `CUSTOMROM-ADB-S23-PREMIUM`;
- APK: `CUSTOMROM-ADB-S23-PREMIUM-debug.apk`;
- source commit testado: `b9a417954d69abb11d2d7458adfbaaf9a8c3f5ef`;
- run: `31242479522`;
- APK SHA-256: `2e2dc2c948632039501a39999f80b7e867f6495fbb948c5518e926abfd00d69e`.

O artifact foi baixado fora da Actions, o SHA-256 foi recalculado e coincide com `sha256.txt` e com `ci/s23-premium-build-proof.json`. `unzip -t` do APK terminou sem erros.

## Blueprint Premium UI/UX

**Congelado por decisão explícita do proprietário.**

A atualização operacional que havia sido acrescentada ao Blueprint foi revertida no Notion. O Blueprint não deve receber novas alterações sem nova autorização explícita. Aprendizados futuros vão para blocos, Estado Oficial, Registro de Alterações e Aprendizados.

## Validação física pendente

Esta APK ainda precisa ser validada S23 → TayTech.

Gate recomendado:

1. instalar/atualizar a APK no S23;
2. executar **Por que a central está lenta?** e confirmar que o resultado abre resumo + ações, não dump como fim da jornada;
3. tocar num package sugerido e confirmar navegação para Apps;
4. executar **O que inicia junto com a central?** e confirmar que packages extraídos são navegáveis;
5. executar **Quais apps estão falhando?**, **Quem acorda a central?** e **O que trabalha em segundo plano?**;
6. confirmar que **Ver evidência técnica** continua disponível;
7. não executar disable automático; qualquer teste AMARELO físico continua exigindo escolha explícita e rollback conhecido;
8. observar crash/ANR/logcat do próprio CUSTOMROM.

## Rollback

A `main` continua fora desta implementação. A build anterior premium operacional permanece referência de rollback se esta nova jornada regredir no S23.

## Próximo passo único

**Instalar a APK do Functional Action Graph no S23 e validar se cada diagnóstico agora leva naturalmente a uma próxima decisão humana, mantendo a evidência técnica em segundo plano.**