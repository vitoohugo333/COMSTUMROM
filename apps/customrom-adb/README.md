# CUSTOMROM ADB

Aplicativo Android próprio para controlar a TayTech por ADB a partir do celular.

## Princípios
- sem anúncios;
- sem limite artificial de comandos;
- interface mobile-first;
- pareamento por código e conexão simples;
- shell remoto completo;
- evidência salva com nomes humanos;
- nenhuma ação destrutiva escondida;
- integração com a governança CUSTOMROM.

## Primeira fatia
1. conexão por `IP:5555`;
2. autenticação ADB com chave persistida pelo aplicativo;
3. campo de comando/bloco multi-linha;
4. execução remota;
5. saída integral;
6. copiar/salvar resultado.

## Base técnica
A primeira implementação usará o protocolo ADB por TCP como superfície mínima. A biblioteca `cgutman/AdbLib` é a referência inicial por sua licença BSD-3-Clause e por já existir exemplo Android de execução remota. O pareamento TLS do Android 11+ será incorporado em uma fatia separada, usando LADB como referência de comportamento e licença.

## Não é um fork do Bugjaeger
O Bugjaeger serve apenas como referência funcional da experiência que já foi validada no uso real. Nenhum código proprietário, anúncio, premium gate ou mecanismo de monetização será removido ou redistribuído.
