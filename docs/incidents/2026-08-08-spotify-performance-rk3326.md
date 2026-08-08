# Incidente — Spotify lento na TayTech RK3326

Data: 2026-08-08
Estado: diagnóstico estático confirmado + diagnóstico runtime automatizado no CUSTOMROM; validação física da nova receita ainda pendente.
Escopo: somente leitura. Nenhuma alteração automática em Spotify, Google Play Services, ROM, MCU, CAN, áudio ou packages.

## Pergunta

Por que o Spotify funciona mal na TayTech? É uma versão/arquitetura incompatível?

## Veredito atual

**ABI incompatível não é a causa.** O Spotify instalado usa `armeabi-v7a` e a central oferece `armeabi-v7a,armeabi`.

A hipótese principal passa a ser **pressão/concorrência de recursos e peculiaridades da plataforma**: SoC RK3326 em userspace 32-bit, framework com combinação `release=13` + `sdk=30`, Google Play Services pesado em capturas anteriores e cliente Spotify moderno/complexo. A causa final de cada episódio de lentidão deve ser medida durante o sintoma, não presumida.

## Evidência confirmada

### APK analisado

- arquivo fornecido: `com.spotify.music__base.apk`
- SHA-256: `a347eac03923a63793bcfe0e179ab0a021490ac54fa541846ad0bdd10e0b4ae3`
- package: `com.spotify.music`
- versionName: `9.1.72.1891`
- versionCode: `144716725`
- minSdk: `24`
- targetSdk: `37`
- primaryCpuAbi observado no PackageManager: `armeabi-v7a`
- secondaryCpuAbi: `null`
- flag observada: `LARGE_HEAP`

### Plataforma TayTech

Capturas fornecidas mostram:

- `ro.board.platform=rk3326`
- `ro.product.cpu.abi=armeabi-v7a`
- `ro.product.cpu.abilist64=[]`
- `ro.build.version.release=13`
- `ro.build.version.sdk=30`
- RAM total: 4 GiB
- fotografia CUSTOMROM: `MemAvailable=765064 kB`
- fotografia CUSTOMROM: `SwapTotal=1007660 kB`, `SwapFree=561196 kB`

A combinação `release=13` + `sdk=30` é não padrão e deve ser tratada como sinal de framework OEM profundamente customizado. Não é, isoladamente, prova de que todo problema do Spotify vem do framework.

### Concorrência de CPU

Em captura OMEGAS anterior:

- `com.google.android.gms.persistent`: aproximadamente `89%` de CPU na janela reportada;
- outros processos pesados coexistiam na mesma central.

Regra: uma captura em que outro app está saturando a CPU prova **contenção do ambiente**, não culpa direta do Spotify.

### GC do Spotify

Logs reais:

- ciclo de GC total `651.905 ms`, com `paused 257 us`;
- ciclo de GC total `123.347 ms`, com `paused 194 us`.

**Aprendizado obrigatório:** duração total do ciclo de GC não equivale à pausa stop-the-world da UI. Não relatar `651.905 ms` como “pausa de 652 ms”. Pode representar trabalho/pressão de GC, mas o campo `paused` deve ser respeitado.

### SIGQUIT / dump de stack

O log mostra:

- `dumpstate: libdebuggerd_client: started dumping process 16248`;
- em seguida o `Signal Catcher` do Spotify reage ao sinal 3.

**Aprendizado obrigatório:** esse SIGQUIT foi induzido pelo `dumpstate` e não deve ser classificado como ANR. Só classificar ANR quando houver evidência explícita como `ANR in com.spotify.music` / `am_anr` equivalente.

### Kills históricos

Há `am_kill` do Spotify com descrição `empty for 1800s`.

**Aprendizado obrigatório:** descarte de processo vazio em background é reclaim do sistema; não deve ser apresentado ao usuário como crash do Spotify.

## Correção de produto implementada

Foi criada a receita VERDE:

`spotify-diagnostico` — **Por que o Spotify está lento?**

Ela coleta em um único fluxo:

1. versão, minSdk/targetSdk e ABI do Spotify;
2. release/SDK/patch/ABI da central;
3. PID e meminfo do Spotify;
4. RAM e swap/ZRAM do sistema;
5. CPU do Spotify, GMS, system_server, SurfaceFlinger e audioserver;
6. `gfxinfo` do Spotify;
7. foco/rota de áudio e MediaSession;
8. histórico de exit info;
9. estado térmico;
10. logs recentes filtrados por Spotify, áudio, codecs, LMK, Choreographer e Bluetooth A2DP.

O `FunctionalActionEngine` interpreta a coleta e separa:

- ABI compatível/incompatível;
- framework não padrão;
- memória apertada e swap;
- CPU concorrente;
- janky frames;
- GC sem confundir total com pausa;
- ANR/crash apenas por padrões explícitos;
- reclaim de processo vazio;
- indícios de desconexão A2DP.

## Jornada humana

Após interpretar, o CUSTOMROM oferece ações contextuais:

- abrir detalhe do Spotify;
- investigar Google Play Services;
- cruzar com CPU do sistema;
- cruzar com áudio;
- cruzar com renderização;
- ver crashes/ANRs;
- repetir a coleta com Spotify aberto durante o sintoma.

Nenhuma dessas leituras executa otimização destrutiva automaticamente.

## GitHub

- branch: `refactor/customrom-adb-s23-premium`
- workflow de aplicação/build: `.github/workflows/build-customrom-adb-s23-premium.yml`
- apply script: `tools/apply_spotify_diagnostic.py`
- source commit gerado pelo gate após validação + `assembleDebug`: `674082197509a7eefa0a15dba486f6288aaa600e`
- catálogo após aplicação: 63 receitas

## Gate de fechamento

Para fechar o incidente como comprovado no dispositivo:

1. instalar/atualizar o APK do CUSTOMROM no Galaxy S23;
2. conectar à TayTech;
3. abrir o Spotify e reproduzir o sintoma;
4. executar **Por que o Spotify está lento?** durante a lentidão;
5. conferir o resumo humano e a evidência técnica;
6. se necessário, executar as ações de correlação sugeridas;
7. exportar a sessão/Evidence Pack;
8. só então decidir qualquer mudança de package, GMS ou versão do Spotify.

Até esse teste, a implementação recebe **PASS de compilação/contrato** e **PENDENTE de validação física/runtime na TayTech**.
