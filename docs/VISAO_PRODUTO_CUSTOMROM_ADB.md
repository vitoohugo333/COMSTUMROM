# CUSTOMROM ADB — visão de produto ampliada

## Tese

O aplicativo não deve ser apenas um terminal ADB melhorado. Ele deve funcionar como **cockpit de engenharia Android remota**, com a TayTech como primeiro alvo importante, sem impedir uso futuro com outros dispositivos.

A base funcional observada no APK de referência continua valiosa: pareamento por código, conexão ADB, shell, arquivos, logcat, instalação, captura e utilitários. O princípio é preservar capacidades comprovadas e construir uma camada de operação muito mais coerente ao redor delas.

A prioridade não é minimizar toques por si só. A prioridade é reduzir atrito, perda de contexto, repetição, ambiguidade e trabalho manual.

## 1. Device Workspace

Cada dispositivo conhecido vira um workspace persistente, não apenas um IP/porta.

Campos mínimos:

- nome humano;
- identidade/fingerprint ADB quando disponível;
- último endpoint válido;
- endpoints históricos;
- estado do pareamento;
- estratégia de conexão que funcionou por último;
- última build/modelo/board observados;
- receitas favoritas;
- sessões recentes;
- pasta de evidências associada.

Estados visíveis:

- Conectado;
- Reconectando;
- Aguardando rede;
- Encontrado via mDNS;
- Endpoint conhecido indisponível;
- Pareamento necessário;
- Autenticação recusada;
- Transporte instável.

## 2. Connection Orchestrator

O aplicativo deve tratar conexão como máquina de estados, não como botão isolado.

Estratégia:

1. reutilizar transporte vivo;
2. último endpoint bem-sucedido;
3. fast-path `:5555` quando validado;
4. `_adb-tls-connect` via mDNS;
5. endpoints históricos recentes;
6. conexão manual;
7. novo pareamento somente quando necessário.

Comportamentos desejados:

- backoff progressivo e limitado;
- cancelamento de tentativa;
- não iniciar duas conexões concorrentes para o mesmo alvo;
- recuperar após troca breve de Wi-Fi;
- recuperar ao voltar do background;
- distinguir falha de rede, autenticação, porta e protocolo;
- telemetria de reconexão na sessão.

## 3. Terminal Workspace

Terminal deixa de ser uma caixa de texto descartável e vira uma área de trabalho.

Recursos:

- múltiplas linhas;
- múltiplas abas/sessões;
- histórico pesquisável;
- favoritos;
- snippets;
- executar seleção;
- executar bloco completo;
- fila serial opcional;
- terminal interativo quando o backend suportar;
- cancelar tarefa longa;
- reconectar stream sem perder texto e saída;
- timestamps opcionais;
- busca na saída;
- copiar trecho ou saída inteira;
- salvar saída;
- marcar uma execução como evidência;
- anexar comentário humano à execução.

## 4. Command Recipes

Receitas são automações auditáveis, versionáveis e orientadas a objetivo.

Cada receita contém:

- nome humano;
- objetivo;
- risco;
- comandos;
- timeout;
- estratégia de coleta;
- arquivos produzidos;
- pré-condições;
- critérios simples de sucesso/falha;
- rollback quando houver alteração;
- tags.

Categorias iniciais:

- Hardware;
- Memória;
- CPU/processos;
- Armazenamento;
- Rede/ADB;
- Pacotes;
- Serviços;
- Logcat;
- Boot;
- Áudio;
- CAN/Jancar;
- HVAC;
- Experimentos reversíveis.

Receitas VERMELHAS nunca devem executar silenciosamente. Elas exigem confirmação e plano de recuperação.

## 5. Session Timeline

Toda investigação relevante vira uma sessão estruturada.

A timeline guarda:

- conexão/reconexão;
- comandos;
- receitas;
- duração;
- resultados;
- erros de transporte;
- arquivos puxados/enviados;
- screenshots;
- logcat;
- notas;
- mudanças reversíveis;
- rollback executado ou pendente.

Isso permite reconstruir exatamente o que aconteceu sem depender de memória do chat.

## 6. Evidence Pack

Uma sessão pode virar pacote portátil para ChatGPT, GitHub, Notion ou armazenamento local.

Estrutura sugerida:

```text
CUSTOMROM_SESSION_YYYY-MM-DD_HH-mm/
  manifest.json
  resumo.md
  terminal/
  reports/
  logcat/
  screenshots/
  attachments/
  checksums.sha256
```

O `manifest.json` segue `apps/customrom-adb/schemas/evidence-pack.schema.json`.

Exportações:

- ZIP completo;
- Markdown resumido;
- somente último comando;
- somente relatório selecionado;
- pacote sem screenshots;
- pacote com sanitização opcional de identificadores/redes.

Compartilhamento via Android Share Sheet deve ser uma saída natural, não um fluxo separado.

## 7. Compare Mode

Uma capacidade especialmente útil para CUSTOMROM: comparar duas fotografias.

Exemplos:

- antes/depois de desativar um pacote;
- antes/depois de reiniciar;
- repouso vs. sob carga;
- ROM original vs. ROM otimizada;
- Wi-Fi instável vs. estável.

Comparações possíveis:

- MemAvailable;
- swap/ZRAM;
- processos presentes/ausentes;
- PSS de processos;
- CPU;
- serviços;
- pacotes;
- armazenamento;
- diferenças textuais em dumps;
- tempo percebido/medido de operações.

Resultado deve indicar dados brutos e interpretação separadamente.

## 8. Live Capture

Modo para reproduzir defeitos:

1. iniciar captura;
2. executar uma ação física/no Android;
3. encerrar;
4. gerar pacote automaticamente.

Pode combinar:

- logcat;
- top periódico;
- memória periódica;
- conectividade;
- screenshots manuais;
- markers de usuário (`agora travou`, `abri HVAC`, `engatei ré`).

## 9. File Workbench

Preservar o file manager que funcionar, mas acrescentar contexto de engenharia:

- favoritos `/storage/emulated/0/CUSTOMROM/`;
- Pull para sessão atual;
- Push com confirmação de destino;
- checksum SHA-256;
- abrir como texto quando possível;
- anexar ao Evidence Pack;
- comparar dois textos;
- marcar como original/referência/modificado.

## 10. APK Workbench

Sem tentar virar um APK editor completo imediatamente.

Recursos úteis:

- instalar APK;
- puxar APK de pacote selecionado;
- listar package name/version/path;
- checksum;
- abrir App Info;
- anexar APK ou metadados à sessão;
- comparar manifesto/versão quando houver ferramentas próprias.

Engenharia reversa profunda continua no repositório/CI, não precisa morar toda dentro do app.

## 11. Logcat Workbench

- captura limitada por tempo;
- filtros salvos;
- filtro por package/PID/tag;
- highlight de crash/ANR/FATAL/Exception;
- markers do usuário;
- exportação direta;
- vincular linhas a uma sessão;
- presets CUSTOMROM: Jancar/CAN, áudio, Bluetooth, HVAC, power/ACC.

## 12. Safety Layer

A camada de segurança não deve bloquear o usuário; deve explicitar consequência e rollback.

Classificação:

- VERDE: leitura;
- AMARELO: mudança reversível;
- VERMELHO: estrutural/recuperação.

Para ações AMARELAS:

- registrar estado anterior;
- mostrar rollback;
- oferecer botão Reverter quando tecnicamente possível;
- anexar mudança à timeline.

Para VERMELHAS:

- exigir confirmação reforçada;
- não misturar com receitas de leitura;
- exigir evidência de recuperação quando aplicável.

## 13. Profiles e Contextos

Perfis são conjuntos de preferências operacionais, não perfis de veículo obrigatórios.

Exemplos:

- TayTech / laboratório;
- TayTech / diagnóstico de desempenho;
- outro Android;
- sessão de APK;
- sessão de logcat.

Cada contexto pode definir:

- alvo padrão;
- receitas favoritas;
- diretório de evidência;
- filtros de log;
- tempo padrão de captura;
- preferências de exportação.

## 14. Command Palette

Busca única para tudo:

- conectar alvo;
- executar receita;
- abrir terminal;
- buscar comando histórico;
- abrir arquivo;
- iniciar logcat;
- exportar sessão;
- abrir pacote;
- tirar screenshot.

O objetivo é potência para usuário frequente sem transformar a home em painel lotado.

## 15. Home adaptativa

Home pode mostrar contexto atual em vez de ser fixa.

Exemplo:

- alvo e conexão;
- sessão ativa;
- últimos erros;
- últimas receitas;
- ações frequentes;
- arquivos recentes;
- botão continuar investigação.

## 16. Backlog criativo posterior

Somente depois da primeira base estável:

- macros condicionais;
- scheduler local de capturas;
- diff visual de dumps;
- monitor leve de CPU/memória;
- bookmarks de processos/pacotes;
- painel CAN/Jancar baseado em logs;
- integração com GitHub para anexar Evidence Pack a issue;
- integração com Notion via compartilhamento/exportação, sem credenciais embutidas;
- importação de recipe packs versionados;
- templates de sessão por hipótese;
- parser local de relatórios para destacar métricas antes do envio ao ChatGPT.

## O que continua fora do objetivo por padrão

- reescrever ADB nativo se o existente funcionar;
- implementar fastboot/flash novamente sem necessidade;
- alterar monetização/gates proprietários do APK de referência;
- embarcar credenciais de GitHub/Notion/OpenAI no APK;
- tocar ROM/MCU/CAN apenas porque o app ganhou capacidade técnica para isso.

## Critério de produto

CUSTOMROM ADB é bem-sucedido quando permite realizar uma investigação Android completa, preservar contexto e evidência, recuperar conexão sem drama e transferir o resultado para outro agente/ferramenta sem reconstrução manual da história.
