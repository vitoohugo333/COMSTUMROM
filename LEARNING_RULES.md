<!-- COMSTUMROM_GOVERNANCE_VERSION: 2026-08-07.1 -->
# CUSTOMROM TAYTECH — regras permanentes de aprendizado técnico

Um bloco que revelar defeito, quase falha, dependência OEM ou conhecimento reutilizável deve deixar prevenção executável e contexto suficiente para o próximo agente.

## Ciclo obrigatório

1. sintoma;
2. causa imediata;
3. causa estrutural;
4. falha de detecção;
5. correção ou decisão;
6. prevenção permanente;
7. prova;
8. alcance do aprendizado.

## Onde registrar

- `docs/incidents/`: história completa e evidência;
- regra especializada aplicável (`ADB_RULES.md`, `ROM_SAFETY_RULES.md` etc.): prevenção resumida;
- teste/script: prevenção executável quando fizer sentido;
- `SKILLS.md`: índice quando surgir nova área permanente;
- `PROJECT_STATE.md`: somente estado atual e pendência;
- Notion/Aprendizados: memória operacional e vínculo com o bloco.

## Quando é obrigatório

Registrar quando o problema afetou ou poderia afetar:

- boot, estabilidade ou desempenho;
- pacote/sistema Android;
- CAN/MCU;
- HVAC, áudio, câmera, sensores, volante, ACC ou sleep/wake;
- ADB/permits/root;
- partições, AVB, firmware ou recovery;
- segurança, credenciais ou dados;
- CI/governança/branch;
- ou quando a investigação consumiu esforço relevante e produziu conhecimento reutilizável.

Não transformar hipótese em regra. Não criar diário de comandos. Preservar somente tentativas que ensinem algo.

## Fechamento

Todo bloco técnico declara uma opção:

- `Nenhum aprendizado permanente novo`;
- `Aprendizado fechado`, citando regra e prova;
- `Aprendizado pendente`, explicando o que ainda não foi confirmado.
