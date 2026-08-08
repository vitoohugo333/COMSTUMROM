# CUSTOMROM ADB — mapa de capacidades

Este documento separa capacidades por origem para evitar confundir comportamento já existente no APK de referência com código novo.

## Preservar da base quando funcional

- pareamento por código;
- descoberta mDNS;
- conexão ADB remota;
- shell;
- push/pull de arquivos;
- captura/logcat;
- APK manager;
- screenshots;
- demais ferramentas que não criarem regressão.

## CUSTOMROM-owned

- defaults de reconexão/autofetch;
- Device Workspace;
- Session Timeline;
- Command Recipes;
- diagnóstico CUSTOMROM;
- Evidence Pack;
- Safety Layer;
- Live Capture;
- Compare Mode;
- perfis/contextos;
- Command Palette;
- integração de exportação para compartilhamento Android/ChatGPT;
- verificadores determinísticos de receitas e evidência;
- pipeline reprodutível de rebuild/assinatura.

## Substituir somente se necessário

- execução de comandos, caso o gate comercial da base impeça a operação desejada;
- reconexão, caso a lógica existente não possa ser tornada previsível;
- exportação, caso a base não ofereça pontos seguros de extensão.

A substituição deve sempre buscar a menor superfície funcional possível.
