# Mapa cirúrgico do APK de referência — Bugjaeger 5.0

## Artefato

- SHA-256: `65d2e6f73a62bc5ae4cdcf9a8c9271ff0bab499eca9d9464ca37425931ba015b`
- tamanho observado: ~26 MB;
- 4 arquivos DEX;
- recursos Android tradicionais + Compose/AndroidX;
- bibliotecas nativas para múltiplas ABIs.

Este documento registra somente nomes/superfícies necessários ao trabalho. Não contém decompilação integral proprietária.

## Conexão/reconexão

Símbolos confirmados em DEX:

- `eu.sisik.hackendebug.MainActivity`;
- `MainActivity$reconnectLastWifiConnections...`;
- `MainActivity$saveConnectDataForReconnect...`;
- `MainActivity$handleReconnectLastWifiConnections...`;
- `eu.sisik.hackendebug.connection.MdnsSdResolver`;
- `TargetConnectionsManager`;
- `AdbDeviceHolder`;
- `AdbClient`;
- `AdbClient2`.

Strings operacionais confirmadas:

- `Reconnecting target `;
- `Should reconnect last connection: `;
- `Reconnect targets`;
- `Fetched connection params from network: `;
- `Fetched pairing params from network: `.

Serviços ADB Wireless confirmados:

- `_adb-tls-pairing`;
- `_adb-tls-connect`.

## Preferências relevantes

Chaves confirmadas em resources:

- `key.reconnect.last.wifi.targets`;
- `key.autofetch.connection.params`;
- `key.autofetch.pairing.info`;
- `key.start.adb.server.foreground`.

Descrições correspondentes observadas:

- tentar reconectar alvos previamente conectados depois que o app for encerrado;
- recuperar automaticamente IP/porta para conexão Wi-Fi;
- recuperar automaticamente endereço/porta de pareamento;
- manter servidor ADB rodando quando o app não está em foreground para evitar desconexões/interrupções longas.

### Consequência técnica

A primeira intervenção de estabilidade deve testar **ativação/persistência dessas capacidades existentes** antes de criar uma segunda implementação de reconexão.

## Shell/comandos

Símbolos confirmados:

- `AdbShellRepository`;
- `AdbShellState`;
- `AdbShellViewModel`;
- `ShellConnection`;
- `AdbCommandProcessor`;
- `CommandsFragment`;
- `ShellSession`.

Strings relevantes:

- `Starting shell with target `;
- `Interactive shell`;
- `Raw ADB shell`;
- `Run shell command`;
- `Type in command here...`;
- `Result of command will be shown here`;
- aviso de que o usuário já está dentro de shell remoto e não deve prefixar com `adb shell`;
- aviso de que comandos longos podem bloquear comandos subsequentes e precisam de mecanismo de interrupção.

### Consequência técnica

A evolução de terminal deve preservar o backend de shell inicialmente, mas tratar:

- ciclo de vida do stream;
- comando longo/cancelamento;
- histórico;
- saída persistente;
- separação entre erro do comando e erro do transporte.

## Layouts confirmados

- `activity_main.xml`;
- `activity_shell.xml`;
- `activity_fastboot_shell.xml`;
- `commands_fragment.xml`;
- `custom_command_dialog.xml`;
- `dialog_connect.xml`;
- `dialog_pair.xml`;
- `dialog_qr_code_pairing.xml`;
- `devices.xml`;
- `device_fragment.xml`;
- `file_manager_fragment.xml`;
- `logcat_fragment.xml`;
- `main_toolbar.xml`;
- menus de shell/commands.

Também existem variantes `layout-v22` para shell/commands.

### Consequência técnica

É possível aplicar uma primeira modificação de UX sem tocar no APK inteiro. As telas de conexão, shell, commands, arquivos e logcat são superfícies separáveis.

## Recursos existentes que vale preservar

Pelo inventário estático e strings:

- pareamento por código;
- pareamento por QR;
- conexão Wi-Fi;
- raw ADB;
- shell interativo;
- lista de comandos;
- file manager;
- logcat;
- screenshot;
- package/APK tooling;
- backup/restore;
- fastboot tooling.

Não há motivo para reimplementar esses módulos até existir defeito concreto ou incompatibilidade com a build própria.

## Gate comercial

String confirmada:

`Free version only allows maximum %1$d commands per session`

O gate não faz parte dos patches CUSTOMROM. Se ele impedir a operação, a camada limitada deverá ser substituída por código próprio/open source, preservando as demais capacidades úteis.

## Hipóteses prioritárias para runtime

1. ativar as quatro preferências de reconexão/autofetch/background pode eliminar a maior parte da instabilidade percebida;
2. a reconexão já implementada pode estar desabilitada por default ou depender de ciclo de vida específico;
3. a manutenção do ADB server em foreground pode evitar quedas ao alternar entre Settings/ChatGPT/Files;
4. `MdnsSdResolver` pode absorver porta dinâmica sem novo pareamento;
5. o shell pode ser preservado e apenas receber uma UX própria de sessão/cancelamento/exportação.

## Próximo mapa após rebuild

Depois de Apktool/JADX no runner, extrair somente:

- chamadas das quatro preferências;
- ordem real dentro de `reconnectLastWifiConnections`;
- gatilhos em `onCreate`/`onResume`/receivers;
- ciclo de vida de `AdbShellRepository`;
- IDs/views usados por `activity_main`, `activity_shell`, `dialog_connect`, `dialog_pair`, `commands_fragment`.

Isso será suficiente para o primeiro patch comportamental sem decompilar/documentar o aplicativo inteiro.
