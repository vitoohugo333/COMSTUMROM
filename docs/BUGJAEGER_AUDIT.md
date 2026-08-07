# Auditoria do Bugjaeger e estratégia de substituição

## Objetivo humano
Usar o APK enviado como referência técnica para entender o que funciona bem no pareamento e na operação ADB, sem depender de anúncios, limites ou decisões de produto de terceiros.

## Artefato analisado
- arquivo: `Bugjaeger Mobile ADB - USB OTG_5.0_APKPure.apk`
- SHA-256: `65d2e6f73a62bc5ae4cdcf9a8c9271ff0bab499eca9d9464ca37425931ba015b`
- formato: APK Android válido
- conteúdo observado: 4 arquivos DEX e bibliotecas nativas para arm64-v8a, armeabi-v7a, x86 e x86_64

## Descobertas confirmadas
- biblioteca ADB nativa observada: `libadb-sixo.so`;
- biblioteca nativa adicional: `libbugjaeger.so`;
- SDK Google Mobile Ads presente nos DEX;
- referências a banner, interstitial e rewarded ads presentes;
- Firebase Analytics e Crashlytics presentes;
- o aplicativo possui lógica própria de comandos, sessões e shell.

## Limite
Não remover anúncios, premium gates ou limites pagos do APK proprietário. O aplicativo oficial oferece monetização/premium e versão sem anúncios; contornar isso não faz parte do projeto.

## Estratégia aprovada
Construir `CUSTOMROM ADB`, um aplicativo Android próprio, mínimo e orientado ao nosso fluxo.

## Bases open source selecionadas
1. `cgutman/AdbLib` — implementação Java do protocolo ADB, BSD-3-Clause.
2. `tytydraco/LADB` — referência de Wireless Debugging e pareamento por código.
3. `mouldybread/adb-auto-enable` — referência MIT para automatizar retorno à porta 5555.
4. `Jolanrensen/ADBPlugin` — exemplo de execução de séries de comandos em dispositivo remoto usando AdbLib.

## Primeira fatia executável
A primeira build própria deve:
- conectar à TayTech por `IP:5555`;
- gerar/persistir chave ADB própria;
- abrir shell remoto;
- aceitar comando simples e bloco multi-linha sem limite artificial;
- exibir saída integral;
- permitir copiar e salvar saída;
- ter uma interface com linguagem humana e foco no uso por celular.

## Fase seguinte
- pareamento Android 11+ por código;
- descoberta automática do alvo;
- push/pull de arquivos;
- scripts salvos;
- logcat filtrado;
- tela de diagnóstico CUSTOMROM;
- integração futura com automação da porta 5555.

## Critério de sucesso da primeira build
No S23, conectar à TayTech e executar `getprop ro.product.model`, recebendo a resposta completa sem limite de comandos e sem dependência do Bugjaeger.