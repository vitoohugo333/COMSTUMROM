<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.1 -->
# CUSTOMROM TAYTECH — regras de segurança para ROM, firmware e hardware crítico

## Escopo

Este arquivo passa a ser obrigatório quando o trabalho tocar root, bootloader, fastboot, recovery, partições, AVB/dm-verity, imagens Android, firmware principal, MCU, CAN box ou arquivos `.iap`.

## Princípio

A multimídia não é apenas um tablet Android. Android, vendor/BSP, MCU e CAN podem ter cadeias de atualização, assinaturas e recuperação diferentes. Nunca tratar um arquivo de uma camada como firmware de outra por semelhança de nome.

## Fronteiras separadas

Manter identificadas, quando presentes:

1. **Android / sistema principal** — framework, apps, SystemUI, launcher, `system`, `product`, `vendor`, `odm`, `super` etc.;
2. **boot chain** — bootloader, boot, vbmeta, recovery, AVB/dm-verity;
3. **MCU** — controle de hardware/periféricos e funções automotivas da unidade;
4. **CAN box** — decodificação do veículo e firmware específico do módulo;
5. **APKs proprietários** — interfaces que podem depender de permissões privilegiadas, assinatura OEM e serviços privados.

## Gate obrigatório antes de qualquer escrita estrutural

Não avançar para comando de escrita até documentar, conforme aplicável:

- hardware/SoC/board identificados com evidência;
- versão atual da camada que será alterada;
- origem e hash do arquivo candidato;
- compatibilidade de modelo/board/versão;
- mapa de partições;
- esquema A/B e partições dinâmicas quando existentes;
- estado de bootloader;
- AVB/dm-verity;
- backup/dump original ou pacote stock confiável;
- método realista de recuperação;
- riscos de perda de funções automotivas;
- fonte de energia estável;
- autorização explícita específica do proprietário.

Ausência de método de recuperação é bloqueio material para experimentos destrutivos.

## Firmware MCU/CAN

- Nunca atualizar/downgrade apenas por o nome parecer parecido.
- Compatibilidade deve ser demonstrada pela família exata do hardware e cadeia de atualização.
- `.iap` não é presumido como imagem Android.
- Firmware CAN/MCU permanece fora do escopo de ADB debloat.
- Se o firmware atual for mais novo que um arquivo disponível, downgrade exige justificativa técnica, compatibilidade e autorização separada.

## APKs privilegiados

Modificar/reassinar APK OEM pode quebrar permissões `signature`, `privileged`, shared UID, binder ou integrações privadas mesmo quando a interface abre.

Antes de substituir APK de sistema:

- preservar APK original;
- mapear manifesto, permissões, serviços, receivers/providers e assinatura;
- mapear chamadas/integrações críticas;
- definir caminho de restauração;
- validar fora da cadeia crítica quando possível;
- registrar incompatibilidades de assinatura como risco explícito.

## Operações bloqueadas por padrão

Sem contrato e autorização específicos, não executar:

- `fastboot flashing unlock` ou equivalentes;
- `fastboot flash`, `erase`, `format`;
- escrita direta em `/dev/block/*`;
- `dd` para partições;
- remount de sistema para escrita;
- alteração de `vbmeta`/AVB;
- disable-verity;
- flash de MCU/CAN;
- downgrade de firmware;
- recovery ou boot image de origem incerta.

## Critério de avanço

Passar de ADB reversível para ROM/firmware somente quando o benefício esperado não puder ser atingido de modo suficientemente seguro na camada superior e houver recuperação proporcional ao risco.

A curiosidade técnica, por si só, não é critério de flash.
