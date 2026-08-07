# Auditoria do Bugjaeger 5.0 — direção CUSTOMROM

## Objetivo humano

Usar o APK fornecido como referência técnica e preservar tudo que já funciona. A evolução será **cirúrgica**: reconexão, continuidade da sessão, shell operacional e interface reduzida.

## Artefato analisado

- arquivo: `Bugjaeger Mobile ADB - USB OTG_5.0_APKPure.apk`;
- SHA-256: `65d2e6f73a62bc5ae4cdcf9a8c9271ff0bab499eca9d9464ca37425931ba015b`;
- APK Android válido;
- 4 DEX;
- bibliotecas nativas para arm64-v8a, armeabi-v7a, x86 e x86_64;
- biblioteca ADB nativa observada: `libadb-sixo.so`.

## Descobertas relevantes para a evolução mínima

O APK já contém componentes que indicam uma arquitetura madura para o nosso objetivo:

- `AdbClient` / `AdbClient2`;
- `AdbDeviceHolder`;
- `AdbShellRepository`;
- `AdbCommandProcessor`;
- `TargetConnectionsManager`;
- `MdnsSdResolver`;
- suporte a `_adb-tls-pairing` e `_adb-tls-connect`;
- `saveConnectDataForReconnect`;
- `reconnectLastWifiConnections`;
- `handleReconnectLastWifiConnections`.

Também existem preferências internas para:

- reconectar alvos Wi-Fi anteriores;
- buscar automaticamente parâmetros de conexão;
- manter o servidor ADB em execução quando o app deixa o foreground.

Isso muda a estratégia: **não precisamos inventar um novo mecanismo de reconexão antes de provar que o existente não pode ser aproveitado.**

## Superfície de UI já separada

Foram encontrados layouts próprios para:

- `activity_main.xml`;
- `activity_shell.xml`;
- `commands_fragment.xml`;
- `custom_command_dialog.xml`;
- `dialog_connect.xml`;
- `dialog_pair.xml`;
- `dialog_qr_code_pairing.xml`;
- `devices.xml`;
- `file_manager_fragment.xml`;
- `logcat_fragment.xml`.

Portanto, existe uma superfície clara para reconstruir a experiência sem tocar em todos os subsistemas.

## Direção aprovada

1. manter o núcleo ADB quando ele já cumpre o requisito;
2. tornar a TayTech o alvo principal do fluxo;
3. priorizar reconexão ao último alvo;
4. usar `:5555` como fast-path quando disponível;
5. usar mDNS `_adb-tls-connect` para recuperar endpoints dinâmicos;
6. preservar o pareamento enquanto a identidade/chave continuar válida;
7. manter/reabrir shell após pequenas interrupções;
8. reduzir home/conexão/shell para o fluxo CUSTOMROM;
9. deixar ferramentas secundárias intactas e deslocá-las para **Mais** se necessário.

## O que não vamos mexer sem evidência

- protocolo ADB nativo;
- fastboot;
- flashing;
- screencap/server;
- backup;
- APK manager;
- file manager que já funcione;
- recursos da TayTech, ROM, MCU ou CAN.

## Limite de distribuição e monetização

O projeto não remove nem neutraliza mecanismos comerciais do aplicativo proprietário. O artefato original é preservado como referência; decompilação integral não deve ser publicada no repositório.

## Documento operacional

O plano de implementação está em `docs/PLANO_EXECUCAO_CLIENTE_ADB.md`.
