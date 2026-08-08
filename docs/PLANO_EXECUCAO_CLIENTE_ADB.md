# Plano de execução — cliente ADB CUSTOMROM

## Direção aprovada

**Mudança mínima, efeito máximo.** O APK 5.0 analisado já possui um núcleo ADB maduro. O projeto não vai reescrever subsistemas que já funcionam sem evidência de necessidade.

O foco é exclusivamente:

1. reconexão automática ao alvo já pareado;
2. continuidade da sessão quando app/rede oscilam;
3. entrada e saída de comandos mais operacional;
4. interface CUSTOMROM reduzida ao fluxo real;
5. preservação de recursos existentes fora desse caminho;
6. empacotamento de evidências para uso direto no ChatGPT;
7. rotinas de diagnóstico CUSTOMROM em um toque;
8. sistema de perfis/alvos sem exigir que o usuário entenda IP, porta ou mDNS.

## Evidência do APK analisado

Foram observados no artefato fornecido:

- `AdbClient` e `AdbClient2`;
- `AdbDeviceHolder`;
- `AdbShellRepository`;
- `AdbCommandProcessor`;
- `TargetConnectionsManager`;
- `MdnsSdResolver`;
- serviços `_adb-tls-pairing` e `_adb-tls-connect`;
- rotinas `saveConnectDataForReconnect`, `reconnectLastWifiConnections` e `handleReconnectLastWifiConnections`;
- preferências para reconectar alvos anteriores, buscar parâmetros de conexão automaticamente e manter ADB ativo em segundo plano;
- layouts dedicados para conexão, pareamento, shell, comandos, arquivos e logcat.

Conclusão: **a reconexão já existe; nossa primeira intervenção deve tornar esse comportamento previsível e prioritário, não substituir o motor.**

## Arquitetura mínima

### 1. Connection Keeper

Ordem de tentativa:

1. conexão ainda viva;
2. último endpoint válido;
3. `IP:5555`, quando já validado para o alvo;
4. descoberta mDNS `_adb-tls-connect` do alvo previamente pareado;
5. conexão manual;
6. novo pareamento somente quando a autenticação realmente tiver sido perdida.

Requisitos:

- uma tentativa por alvo de cada vez;
- backoff curto e limitado;
- estado explícito: Conectado / Reconectando / Aguardando rede / Precisa parear;
- não apagar chave/pareamento por falha transitória;
- manter serviço em background apenas quando houver sessão ou tarefa longa.

### 2. Shell Session

- manter sessão enquanto o transporte estiver válido;
- se apenas o stream morrer, reabrir o stream antes de reconstruir toda conexão;
- entrada multilinha;
- fila serial para evitar mistura de saídas;
- interrupção explícita de comando longo;
- saída rolável e selecionável;
- copiar e salvar resultado;
- histórico local curto;
- favoritos/scripts reutilizáveis;
- execução sequencial de um pacote de comandos com marcação de sucesso/falha por etapa.

### 3. UI CUSTOMROM

Home mínima:

- alvo principal **TayTech**;
- estado da conexão sempre visível;
- Terminal como ação principal;
- Arquivos como ação secundária;
- Diagnóstico CUSTOMROM como atalho;
- Exportar sessão como ação direta;
- demais ferramentas existentes em **Mais**.

Meta de UX: quando a TayTech estiver acessível, chegar ao shell conectado em **até dois toques**.

### 4. Evidence Pack — integração com ChatGPT

O app deve conseguir transformar uma sessão técnica em um pacote pronto para compartilhar no chat.

Formato inicial sugerido:

`CUSTOMROM_SESSION_YYYY-MM-DD_HH-mm.zip`

Conteúdo:

- `resumo.md` — alvo, horário, estado da conexão, build, comandos executados e erros;
- `terminal.txt` — saída integral do shell;
- `logcat.txt` — somente quando capturado;
- `device-info.txt` — modelo/build/board/arquitetura;
- `files-index.txt` — lista dos arquivos anexados ao pacote;
- subpasta `attachments/` para screenshots, dumps ou relatórios selecionados.

A exportação deve permitir:

- **Compartilhar** pelo Android diretamente para ChatGPT/arquivos;
- exportar só a última execução;
- exportar uma sessão inteira;
- gerar um Markdown humano para copiar no chat;
- esconder automaticamente informações que não agregam valor quando possível.

### 5. Diagnóstico CUSTOMROM

Tela de rotinas prontas, sem exigir digitação de comandos:

- **Estado geral da central**;
- **Memória e ZRAM**;
- **Processos mais pesados**;
- **Armazenamento**;
- **Pacotes/serviços**;
- **Log de 30 segundos**;
- **Snapshot completo para ChatGPT**.

Cada rotina deve:

1. mostrar o que será lido;
2. executar somente comandos do nível permitido;
3. salvar a saída com nome humano;
4. oferecer **Enviar/Compartilhar** imediatamente.

### 6. Command Recipes

Em vez de depender apenas de um terminal cru, o app terá receitas reutilizáveis:

- nome humano;
- bloco de comandos;
- nível de risco;
- descrição do que faz;
- pasta de saída;
- botão executar;
- botão exportar resultado.

Exemplos:

- `Fotografia inicial da central`;
- `Memória em repouso`;
- `Processos mais pesados`;
- `Capturar logcat por 30 segundos`.

As receitas CUSTOMROM devem ficar separadas de comandos livres.

## Etapas de execução

### Etapa 0 — referência preservada

- manter APK original e hash;
- não versionar decompilação integral proprietária;
- versionar somente documentação, scripts próprios e patches necessários.

### Etapa 1 — mapa cirúrgico

Mapear somente:

- MainActivity / fluxo de conexão;
- MdnsSdResolver;
- AdbDeviceHolder;
- AdbShellRepository;
- layouts main/shell/connect/pair/commands.

Não auditar o app inteiro.

### Etapa 2 — reconexão

- último alvo vira prioridade;
- 5555 vira fast-path quando disponível;
- mDNS recupera endpoint quando a porta TLS mudar;
- retorno foreground e recuperação de Wi-Fi disparam reconexão controlada;
- pareamento não é repetido sem necessidade.

### Etapa 3 — shell operacional

- campo multilinha;
- executar bloco;
- preservar saída e histórico;
- cancelar comando longo;
- salvar/copiar resultado;
- erros de transporte separados de erros do comando.

### Etapa 4 — interface reduzida

Mexer apenas em home, conexão e shell na primeira build. Recursos estáveis ficam intactos.

### Etapa 5 — integração de evidência

Adicionar:

- **Exportar sessão**;
- pacote ZIP/Markdown;
- compartilhamento via Android;
- atalho **Preparar para ChatGPT**;
- primeira coleção de receitas CUSTOMROM.

### Etapa 6 — build e prova

Validar:

- instalação/assinatura própria;
- pareamento por código;
- reconexão já pareada;
- `:5555`;
- fallback mDNS;
- foreground/background;
- perda/retorno de Wi-Fi;
- shell simples e multilinha;
- comandos longos/interrupção;
- arquivos e outras funções preservadas;
- exportação de sessão;
- compartilhamento de pacote;
- crash/ANR/logcat.

## Fora do escopo inicial

Não mexer sem evidência:

- fastboot;
- flashing;
- screencap/server;
- backup;
- APK manager;
- file manager funcional;
- protocolo ADB nativo;
- ROM, MCU ou CAN.

## Critério de conclusão

A primeira build é considerada pronta para validação quando:

1. preserva o pareamento;
2. reconecta automaticamente a um alvo conhecido;
3. usa 5555 como fast-path e mDNS como fallback;
4. mantém ou reabre shell após interrupções pequenas;
5. reduz o caminho até o terminal;
6. exporta uma sessão técnica em formato pronto para compartilhar;
7. possui ao menos uma rotina CUSTOMROM executável em um toque;
8. não cria regressões nas funções preservadas.
