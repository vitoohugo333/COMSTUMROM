# Plano de execução — cliente ADB CUSTOMROM

## Direção aprovada

**Mudança mínima, efeito máximo.** O APK 5.0 analisado já possui um núcleo ADB maduro. O projeto não vai reescrever subsistemas que já funcionam sem evidência de necessidade.

O foco é exclusivamente:

1. reconexão automática ao alvo já pareado;
2. continuidade da sessão quando app/rede oscilam;
3. entrada e saída de comandos mais operacional;
4. interface CUSTOMROM reduzida ao fluxo real;
5. preservação de recursos existentes fora desse caminho.

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
- histórico local curto.

### 3. UI CUSTOMROM

Home mínima:

- alvo principal **TayTech**;
- estado da conexão sempre visível;
- Terminal como ação principal;
- Arquivos como ação secundária;
- Diagnóstico CUSTOMROM como atalho;
- demais ferramentas existentes em **Mais**.

Meta de UX: quando a TayTech estiver acessível, chegar ao shell conectado em **até dois toques**.

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

### Etapa 5 — build e prova

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
6. não cria regressões nas funções preservadas.
