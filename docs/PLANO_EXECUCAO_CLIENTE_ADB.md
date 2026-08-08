# Plano de execução — CUSTOMROM ADB / Cockpit Android

## Direção vigente

O objetivo não é um MVP artificialmente pequeno. O produto deve aproveitar o comportamento ADB já comprovado no APK de referência e evoluir para um **cockpit completo de engenharia Android**, sem reescrever subsistemas maduros apenas por preferência arquitetural.

A regra é: **preservar o que funciona; expandir agressivamente o que aumenta autonomia, observabilidade, repetibilidade, evidência e integração com o fluxo CUSTOMROM.**

## Núcleo funcional desejado

### 1. Connection Orchestrator

Estado explícito por alvo:

- conectado;
- reconectando;
- aguardando rede;
- endpoint inválido;
- autenticação preservada;
- novo pareamento necessário.

Ordem de recuperação:

1. transporte ainda vivo;
2. último endpoint conhecido;
3. `IP:5555` quando previamente validado;
4. descoberta mDNS `_adb-tls-connect` para alvo já pareado;
5. último IP conhecido com nova porta descoberta;
6. conexão manual;
7. pareamento novamente somente quando a autenticação realmente se perdeu.

Regras:

- uma tentativa por alvo de cada vez;
- backoff limitado e observável;
- nenhuma falha transitória apaga identidade/chave;
- foreground service somente durante sessão/tarefa que realmente exija continuidade;
- registrar estratégia que efetivamente conectou.

### 2. Device Workspace

Cada dispositivo conhecido pode possuir:

- nome humano;
- modelo/build/board/ABI;
- endpoints conhecidos;
- estratégia preferida;
- status de pareamento;
- último acesso;
- sessões recentes;
- arquivos recentes;
- receitas favoritas;
- tags/contexto.

A TayTech é o alvo principal atual, mas a arquitetura não deve depender de um único aparelho.

### 3. Terminal Workspace

O terminal deve ser uma ferramenta de engenharia, não apenas uma caixa de texto.

Recursos:

- sessão persistente;
- entrada simples e multilinha;
- executar seleção;
- fila serial de execuções;
- interromper execução longa;
- timeout configurável;
- histórico por alvo e sessão;
- busca no histórico;
- favoritos/snippets;
- repetir execução;
- timestamps opcionais;
- saída selecionável;
- copiar trecho ou tudo;
- salvar saída;
- exportar execução;
- distinguir erro do comando de erro do transporte;
- reabrir stream sem refazer pareamento quando possível.

### 4. Command Recipes

Receitas versionadas e independentes do terminal livre.

Cada receita contém:

- identificador estável;
- nome humano;
- objetivo;
- nível de risco;
- comandos;
- timeout;
- arquivo de saída esperado;
- tags;
- possibilidade de compartilhamento/exportação.

O catálogo inicial deve cobrir diagnóstico geral, memória/ZRAM, processos, armazenamento, pacotes, serviços, rede/ADB, logcat e snapshot completo.

### 5. Diagnóstico CUSTOMROM

Tela orientada a tarefas:

- Estado geral da central;
- Memória / ZRAM / swap;
- CPU e processos;
- Armazenamento;
- Aplicativos e serviços;
- Rede e ADB;
- Logcat por janela de tempo;
- Snapshot completo;
- Inspeção de pacote selecionado;
- Diagnóstico antes/depois.

A tela deve mostrar o que será coletado e o risco antes de executar.

### 6. Session Timeline

Uma sessão é a unidade principal de investigação.

Registrar:

- início/fim;
- alvo;
- conexão e reconexões;
- comandos;
- saídas;
- receitas executadas;
- erros;
- arquivos puxados/enviados;
- screenshots;
- logcat;
- marcadores humanos;
- observações;
- comparação com outra sessão.

### 7. Live Capture

Modo de reprodução de problema:

- iniciar captura;
- coletar logs/métricas selecionadas;
- adicionar marcadores humanos durante o problema;
- encerrar;
- consolidar tudo em uma sessão.

Exemplos de marcador: abriu HVAC, interface travou, engatou ré, abriu Spotify, perdeu áudio.

### 8. Compare Mode

Comparar duas fotografias/sessões sem reduzir a análise a “RAM livre”.

Comparáveis quando disponíveis:

- MemAvailable;
- swap/ZRAM;
- CPU em repouso;
- processos;
- serviços;
- armazenamento;
- tempo de execução de rotina;
- erros/logcat;
- lista de pacotes/estado;
- indicadores customizados da sessão.

Preservar os dados brutos ao lado dos deltas.

### 9. File Workbench

A camada de arquivos existente deve ser preservada quando funcional e ampliada para evidência:

- pull/push;
- checksum;
- renomear para nome humano;
- marcar como evidência;
- anexar à sessão;
- comparar arquivos de texto;
- compartilhar;
- salvar no workspace CUSTOMROM.

### 10. APK Workbench

Quando suportado pela base existente:

- listar APK/path/version;
- pull de APK;
- instalar APK;
- calcular hash;
- associar APK a uma sessão;
- comparar metadados antes/depois.

Não alterar funcionalidades maduras apenas para reimplementá-las.

### 11. Logcat Workbench

- captura livre;
- captura com duração;
- filtros salvos;
- filtros por PID/pacote/tag;
- marcadores da sessão;
- detecção visual de crash/ANR quando possível;
- salvar e anexar ao Evidence Pack.

### 12. Evidence Pack / integração com análise

Formato portátil:

`CUSTOMROM_SESSION_YYYYMMDDTHHMMSSZ.zip`

Conteúdo mínimo:

- `manifest.json`;
- `resumo.md`;
- `checksums.sha256`;
- `attachments/` com terminal, logs, relatórios, screenshots e outros arquivos selecionados.

Metadados:

- alvo;
- modelo/build;
- estratégia de conexão;
- endpoint;
- intervalo da sessão;
- investigação;
- reconexões/erros;
- lista de evidências com SHA-256.

A exportação deve permitir compartilhar pelo Android, inclusive para ChatGPT, sem exigir copiar centenas de linhas.

### 13. Safety Layer

Classificação operacional integrada:

- VERDE — somente leitura;
- AMARELO — alteração reversível;
- VERMELHO — estrutural/destrutivo.

Receitas VERDES são validadas automaticamente contra padrões de comandos mutáveis. Para ações AMARELAS, exibir rollback quando conhecido. VERMELHO nunca deve ficar escondido atrás de uma receita genérica.

### 14. Perfis/contextos

Perfis de investigação podem reorganizar ações sem duplicar o motor:

- desempenho;
- aplicativo/pacote;
- boot;
- áudio;
- rede/ADB;
- CAN/automotivo;
- customizado.

### 15. Command Palette

Busca única por:

- comando;
- receita;
- sessão;
- arquivo;
- dispositivo;
- ferramenta.

Não substituir navegação normal; complementar usuários avançados.

## Etapas de implementação

### Etapa A — referência e mapa técnico

- preservar APK original + SHA-256;
- mapear apenas superfícies necessárias;
- identificar recursos já existentes de reconexão, mDNS, shell, arquivos e preferências;
- evitar versionar decompilação integral proprietária.

### Etapa B — patch/rebuild reproduzível

- localizar APK/ZIP automaticamente;
- decodificar com Apktool;
- aplicar patches próprios e auditáveis;
- reconstruir;
- alinhar;
- assinar com chave de desenvolvimento;
- verificar assinatura;
- produzir relatório e hashes.

### Etapa C — defaults de estabilidade

Ativar por padrão, quando presentes na versão de referência:

- reconexão de últimos alvos Wi-Fi;
- autofetch de parâmetros de conexão;
- autofetch de informações de pareamento;
- continuidade ADB em background quando suportada.

### Etapa D — conexão determinística

Evoluir a lógica de reconexão para o Connection Orchestrator, sem substituir o motor ADB se não necessário.

### Etapa E — terminal/sessões

Transformar o shell em workspace persistente, com fila, cancelamento, histórico, saída e integração com sessão.

### Etapa F — receitas e diagnóstico

Integrar catálogo versionado e tela de diagnóstico.

### Etapa G — Evidence Pack

Portar para Android o formato já especificado e testado em Python.

### Etapa H — Live Capture / Compare / workbenches

Adicionar capacidades avançadas progressivamente, preservando ferramentas maduras existentes.

### Etapa I — validação física

Em Android real:

- instalação/assinatura;
- pareamento;
- reconexão;
- `:5555`;
- mDNS;
- background/foreground;
- perda/retorno de Wi-Fi;
- shell;
- receitas;
- exportação;
- arquivos/logcat;
- regressões/crash/ANR.

## Gate comercial do APK de referência

O APK contém indicação de limitação comercial da versão gratuita. Esse mecanismo de terceiros não será neutralizado.

Se ele bloquear uma capacidade necessária do produto CUSTOMROM, a solução é substituir **a menor camada funcional necessária** por implementação própria/open source e manter as demais partes úteis. O resultado final pode ser um produto nosso sem esse limite, sem depender de quebrar o mecanismo comercial original.

## Critério de conclusão desta fase

A fase só é considerada concluída quando houver:

1. build reproduzível;
2. APK assinado gerado;
3. instalação no S23;
4. pareamento/reconexão comprovados;
5. sessão de shell comprovada;
6. pelo menos uma receita executada;
7. Evidence Pack produzido e compartilhável;
8. ausência de regressões críticas nas superfícies preservadas;
9. estado GitHub + Notion sincronizado.
