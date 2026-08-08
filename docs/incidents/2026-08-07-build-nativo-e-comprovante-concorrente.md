# Aprendizado fechado — build nativo e comprovante concorrente

**Fotografia humana:** o aplicativo CUSTOMROM ADB passou a gerar APK real na GitHub Actions com toolchain estável, e o comprovante de build deixou de disputar o mesmo arquivo entre execuções sobrepostas.

**Data local do projeto:** 2026-08-07

## 1. Sintoma

A primeira sequência de builds do aplicativo nativo falhou em camadas diferentes:

- Kadb 2.1.3 exigiu `compileSdk 37`;
- o runner não encontrou a plataforma numérica `platforms;android-37` pelo SDK Manager disponível;
- após voltar para a linha estável Kadb 2.1.1/API 36, a compilação chegou ao Kotlin e falhou porque `MainActivity` usa `kotlinx.coroutines.runBlocking` diretamente sem declarar Coroutines no classpath de compilação do app;
- duas execuções próximas também podiam tentar reescrever `ci/native-build-proof.json` e conflitar durante `git pull --rebase`.

## 2. Causa imediata

Havia três causas imediatas independentes:

1. dependência ADB mais nova puxando uma exigência de SDK de compilação ainda inadequada ao runner;
2. dependência direta ausente para uma API usada diretamente por `MainActivity`;
3. o workflow criava e commitava o comprovante antes de sincronizar a `main`, permitindo conflito no mesmo arquivo quando builds se sobrepunham.

## 3. Causa estrutural

O projeto ainda não tinha uma matriz de toolchain conhecida e protegida para o aplicativo nativo. Também tratava o arquivo de comprovante como se houvesse sempre uma única execução da CI por vez.

## 4. Falha de detecção

O verificador estático validava capacidades do app, mas ainda não protegia a combinação `Kadb + Coroutines + compileSdk + targetSdk`. A CI também não possuía serialização por branch para essa build.

## 5. Correção / decisão

Foi fixada e comprovada a seguinte base para o APK de validação:

- backend ADB: `com.flyfishxu:kadb:2.1.1`;
- Coroutines direta: `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`;
- `compileSdk = 36`;
- `minSdk = 29`;
- `targetSdk = 35`;
- AGP `9.3.1`;
- Gradle `9.5.0`;
- JDK 17.

A CI passou a:

- cancelar execução antiga quando uma nova build da mesma branch começa;
- sincronizar a `main` antes de escrever `ci/native-build-proof.json`;
- publicar o comprovante somente depois da sincronização;
- continuar anexando SHA-256 e metadados ao artifact.

## 6. Prevenção permanente

`tools/validate_native_customrom.py` agora falha se a base validada for alterada silenciosamente em qualquer destes pontos:

- Kadb 2.1.1;
- Coroutines 1.10.2;
- compileSdk 36;
- targetSdk 35.

Qualquer upgrade futuro dessa matriz deve ser deliberado e voltar a provar compilação real antes de substituir a base conhecida.

O workflow `.github/workflows/build-customrom-adb-native.yml` também serializa a build por `github.ref` e usa sincronização antes da publicação do comprovante.

## 7. Prova

Última execução integrada aprovada:

- resultado do contrato: `success`;
- resultado da build Android: `success`;
- artifact: `CUSTOMROM-ADB-native`;
- arquivo: `CUSTOMROM-ADB-native-debug.apk`;
- SHA-256 do APK: `19a038ec37c5d2619df08cd8b928aba0a1dcb2d0284c1bab218736aa8ca0b3ae`;
- fonte testada: `6b8581a7ded59fc13928afc110ba8bd6c38275b5`;
- run técnico: `31234887262`.

O artifact foi baixado fora da Actions e o SHA-256 recalculado localmente; o valor coincidiu exatamente com `sha256.txt` e com `ci/native-build-proof.json`. O contêiner ZIP do APK também passou em verificação de integridade.

## 8. Alcance do aprendizado

**Aprendizado fechado.** Esta prova encerra a camada de compilação/empacotamento do aplicativo nativo. Ela **não** comprova ainda comportamento físico no S23 nem conexão ADB real com a TayTech. Pareamento, reconexão, receitas, exportação e compartilhamento ainda precisam de validação física no aparelho antes de serem classificados como PASS de integração.

Nenhuma ação ADB, root, flash, partição, MCU, CAN ou firmware foi executada para obter esta prova.
