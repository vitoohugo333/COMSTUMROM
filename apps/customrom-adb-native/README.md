# CUSTOMROM ADB native

Aplicativo Android próprio do projeto CUSTOMROM TAYTECH. Ele não depende do gate comercial do APK de referência e usa a biblioteca aberta `flyfishxu/Kadb` como camada ADB.

## Estado atual do código

Implementado no código-fonte:

- identidade criptográfica ADB persistida no armazenamento interno do app;
- pareamento Android Wireless Debugging por código;
- conexão manual;
- fast-path para `IP:5555`;
- reconexão automática ao último alvo;
- fallback por mDNS `_adb-tls-connect._tcp.`;
- sessão ADB reutilizável;
- recuperação do transporte após falha;
- terminal livre e multilinha;
- classificação VERDE / AMARELO / VERMELHO para comandos livres;
- catálogo de diagnósticos CUSTOMROM;
- histórico da sessão;
- saídas de receitas salvas com nomes humanos;
- manifesto da sessão;
- resumo Markdown;
- exportação ZIP com SHA-256;
- compartilhamento Android para outros apps, incluindo o ChatGPT quando disponível no seletor do sistema.

## Alvo operacional

- celular controlador: Samsung S23 / Android atual do proprietário;
- alvo ADB principal: TayTech;
- minSdk: 29;
- targetSdk: 35;
- conexão preferida atual: porta `5555`, com recuperação mDNS.

## Identidade ADB

O Kadb usa armazenamento em memória por padrão. O app configura `OkioFilePrivateKeyStore` antes de qualquer conexão para que a chave usada no pareamento sobreviva a reinicializações do aplicativo. Sem isso, "parear uma vez" não seria garantido.

## Build

Workflow:

`.github/workflows/build-customrom-adb-native.yml`

Artefato esperado:

`CUSTOMROM-ADB-native-debug.apk`

O workflow também grava `ci/native-build-proof.json` tanto em sucesso quanto em falha, permitindo que agentes recuperem o `run_id` e investiguem a compilação sem exigir navegação manual do proprietário.

## Limite da evidência atual

Código implementado não equivale a comportamento comprovado. A fase só passa quando a CI produzir o APK e o proprietário validar no S23:

1. instalação;
2. pareamento;
3. reconexão após fechar/reabrir;
4. conexão 5555;
5. mDNS quando a porta mudar;
6. terminal;
7. receita de diagnóstico;
8. exportação/compartilhamento da sessão.
