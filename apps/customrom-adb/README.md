# CUSTOMROM ADB — workspace de evolução

Este diretório representa a camada própria de UX, patches e integração que vamos construir em torno do comportamento ADB já comprovado no APK analisado.

## Princípio

**Não reescrever por reescrever.** Primeiro preservar o motor que funciona e atacar somente os atritos observados.

## Prioridades

1. reconectar automaticamente à TayTech já pareada;
2. priorizar endpoint conhecido e `:5555` quando disponível;
3. usar mDNS `_adb-tls-connect` como recuperação quando a porta mudar;
4. manter ou reabrir a sessão de shell após oscilações pequenas;
5. simplificar home, conexão e shell;
6. aceitar blocos de comandos e preservar saída/histórico;
7. manter ferramentas existentes fora desse caminho intactas.

## Primeira build

A primeira build não precisa redesenhar o aplicativo inteiro. Ela deve provar:

- pareamento por código preservado;
- reconexão automática ao alvo já conhecido;
- shell conectado em poucos toques;
- entrada multilinha e saída legível;
- recuperação após foreground/background e perda breve de rede;
- ausência de regressão nas funções preservadas.

## Referências

- auditoria: `docs/BUGJAEGER_AUDIT.md`;
- plano de execução: `docs/PLANO_EXECUCAO_CLIENTE_ADB.md`.

## Limite

Código proprietário decompilado não deve ser publicado integralmente neste repositório. O repositório guarda documentação, código próprio, scripts e patches necessários para reproduzir a evolução em artefato fornecido pelo proprietário.
