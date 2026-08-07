# Estado oficial — CUSTOMROM TAYTECH (`main`)

**Atualizado em:** 2026-08-07, horário de Brasília  
**Estado:** governança pronta; operação mobile-first ativa; porta ADB `5555` confirmada em funcionamento na sessão atual; **Bloco 00 — Evoluir o cliente ADB com mínimo de mudanças** em execução.  
**Papel da `main`:** linha principal de governança, documentação, evidência e ferramentas seguras.

## Onde estamos

Antes de iniciar o baseline de desempenho da TayTech, foi identificado um gargalo operacional: o aplicativo ADB usado no celular cria atrito por estabilidade/UX e pela forma de reconexão.

A porta `5555` foi configurada e o proprietário confirmou que conseguiu conectar. **Persistência após reboot ainda não foi provada.**

O APK Bugjaeger 5.0 fornecido foi auditado como referência. A estratégia foi refinada: não criar um novo motor ADB sem necessidade; preservar o que já funciona e modificar somente reconexão, continuidade da sessão e interface operacional.

## Fotografia humana atual

**ADB funcional em 5555 + APK de referência mapeado + plano de evolução mínima pronto + primeira build modificada ainda não produzida.**

## Evidência relevante do APK

O artefato analisado contém:

- `AdbClient` / `AdbClient2`;
- `AdbDeviceHolder`;
- `AdbShellRepository`;
- `AdbCommandProcessor`;
- `TargetConnectionsManager`;
- `MdnsSdResolver`;
- `_adb-tls-pairing` e `_adb-tls-connect`;
- rotinas de salvamento/reconexão de alvos Wi-Fi anteriores;
- preferências para busca automática de parâmetros de conexão e execução ADB em background;
- layouts separados para conexão, pareamento, shell, comandos, arquivos e logcat.

Isso sustenta a decisão de **evolução cirúrgica**, não reconstrução ampla.

## Objetivo atual

Produzir uma primeira build que:

1. preserve o pareamento por código;
2. trate a TayTech como alvo principal;
3. reconecte automaticamente quando o alvo já estiver pareado;
4. priorize `IP:5555` quando disponível;
5. use mDNS `_adb-tls-connect` como fallback para endpoint dinâmico;
6. preserve/reabra shell após oscilações pequenas;
7. simplifique home, conexão e terminal;
8. mantenha ferramentas estáveis fora desse caminho intactas.

## O que não será refeito sem evidência

- protocolo ADB nativo;
- fastboot/flashing;
- screencap/server;
- backup;
- APK manager;
- file manager funcional;
- ROM, MCU ou CAN.

## Repositório e linha de trabalho

- repositório oficial: `vitoohugo333/COMSTUMROM`;
- linha de trabalho humana: **Linha principal de governança e diagnóstico**;
- branch técnica: `main`;
- nenhuma branch nova foi criada;
- publicação/deploy: não aplicável.

## Documentos da execução

- `docs/BUGJAEGER_AUDIT.md` — superfície já confirmada no APK;
- `docs/PLANO_EXECUCAO_CLIENTE_ADB.md` — arquitetura mínima, etapas e critérios;
- `apps/customrom-adb/README.md` — workspace da evolução.

## Notion sync

**CONCLUÍDO para a nova direção.**

- D-007 foi marcada como substituída;
- D-008 registra a evolução mínima do cliente ADB;
- o Bloco 00 foi renomeado e detalhado com etapas, escopo e critérios.

## Codex Engineering Guardrails

`code-work` está ativo para esta operação de escrita. O contrato aplicado é: preservar comportamento comprovado, alterar apenas superfícies necessárias e exigir evidência fresca antes de chamar a build de funcional.

## Testes/CI

Nenhuma build modificada foi compilada ainda. Portanto, reconexão automática, shell alterado e UI CUSTOMROM permanecem **não verificados em runtime**.

## Próximo passo

**Etapa 1 — mapa cirúrgico da implementação:** decompilar o artefato de trabalho localmente e isolar somente MainActivity/conexão, mDNS, holder de dispositivo, repositório de shell e os cinco layouts principais. Em seguida aplicar a primeira mudança de reconexão e shell antes de compilar.
