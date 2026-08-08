# CUSTOMROM ADB — workspace de evolução

Este diretório concentra os componentes próprios do **cockpit de engenharia Android remota** que estamos construindo em torno das capacidades ADB já comprovadas no APK de referência.

## Princípio

**Preservar o que funciona; ampliar onde existe ganho operacional real.**

Não existe obrigação de reduzir o aplicativo a poucas telas nem de manter a UX original. Também não existe razão para reescrever protocolo, pareamento, arquivos ou ferramentas maduras sem evidência de necessidade.

## Capacidades-alvo

- Device Workspace persistente;
- reconexão automática/mDNS/5555;
- Terminal Workspace com sessões e histórico;
- Command Recipes versionadas;
- Session Timeline;
- Evidence Pack estruturado;
- Compare Mode antes/depois;
- Live Capture;
- File Workbench;
- APK Workbench enxuto;
- Logcat Workbench;
- Safety Layer VERDE/AMARELO/VERMELHO;
- Profiles/Contextos;
- Command Palette;
- Home adaptativa;
- integração de exportação com ChatGPT/GitHub/Notion via arquivos e Android Share Sheet.

## O que já existe neste workspace

### Receitas

`recipes/recipes.json`

Catálogo inicial de diagnósticos somente leitura, incluindo estado geral, memória/ZRAM, processos, pacotes, serviços, logcat, rede/ADB e snapshot completo.

### Evidence Pack

`schemas/evidence-pack.schema.json`

Contrato para uma sessão exportável com alvo, conexão, execuções, risco, status, arquivos e checksums.

## Pipeline de modificação

Fora deste diretório:

- `tools/bugjaeger_mod/patch_defaults.py`;
- `tools/bugjaeger_mod/build_mod.sh`;
- `.github/workflows/build-customrom-adb-mod.yml`.

O pipeline localiza o APK/ZIP fornecido, registra hash, decodifica com Apktool, aplica somente patches próprios, recompila e permite produzir uma build assinada de desenvolvimento.

## Patches iniciais

O primeiro patch explora capacidades já presentes no APK e ativa por padrão quando localizadas:

- reconectar últimos alvos Wi-Fi;
- buscar automaticamente parâmetros de conexão;
- buscar automaticamente parâmetros de pareamento;
- manter servidor ADB em foreground/background conforme a opção já existente;
- nome humano `CUSTOMROM ADB`.

Esse patch **não toca em premium gates, anúncios ou monetização** e não altera o protocolo ADB.

## Critério para a primeira build útil

A primeira build deve provar em runtime:

1. instalação/assinatura;
2. pareamento funcional;
3. reconexão ao alvo conhecido;
4. `:5555` quando disponível;
5. fallback mDNS quando necessário;
6. shell funcional;
7. recuperação após foreground/background e oscilação de rede;
8. ausência de regressões materiais nas ferramentas preservadas.

Depois dessa prova, as camadas próprias de sessão, receitas, Evidence Pack e comparação podem ser integradas progressivamente sem ficar limitadas a um redesign superficial.

## Referências internas

- `docs/BUGJAEGER_AUDIT.md`;
- `docs/PLANO_EXECUCAO_CLIENTE_ADB.md`;
- `docs/VISAO_PRODUTO_CUSTOMROM_ADB.md`.

## Limite de distribuição

Código proprietário decompilado não deve ser publicado integralmente neste repositório. O repositório guarda documentação, código próprio, scripts, schemas, receitas e patches reproduzíveis aplicados ao artefato fornecido pelo proprietário.
