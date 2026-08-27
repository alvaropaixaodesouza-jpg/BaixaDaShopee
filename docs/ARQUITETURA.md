# Arquitetura e decisões do MVP

## Componentes

1. `MainActivity`: configura o nome fixo, importa planilhas, seleciona a entrega, vincula casas, abre a rota e exporta a lista limpa.
2. `DeliveryKeyboardService`: teclado Android baseado em `InputMethodService`; insere texto apenas no campo que estiver com foco.
3. `DeliveryStore`: fila e estado local em `SharedPreferences`, compartilhados pela tela e pelo teclado dentro do mesmo aplicativo.
4. `HouseStore`: memória permanente e editável das casas, independente da rota do dia.
5. `CameraActivity`: captura interna, revisão e confirmação antes de publicar a imagem.
6. `SpreadsheetImporter`: leitor local de CSV e XLSX, sem biblioteca externa e sem enviar a planilha para a internet.
7. `MediaStore`: grava fotos na coleção de imagens do Android com nomes associados ao rastreio.
8. `ProfileManager`: salva perfis Baixa/Ocorrência e alvos em JSON local.
9. `FloatingAssistantService`: mostra a fila, os controles de mapeamento e os alvos sobre a tela escolhida pelo usuário.
10. `AutomationAccessibilityService`: executa a sequência somente depois do comando Play e mantém Stop disponível.
11. `OccurrenceManager`: mantém definições editáveis separadas da rota do dia.
12. `BarcodeReader`: usa o modelo de QR Code incorporado ao APK.

## Separação dos dados

`Delivery` guarda o código, os dez campos da planilha original, ocorrência, coordenada de destino, GPS da fotografia, foto do pacote e um `houseId`. `House` guarda apelido, moradores, endereço, link de navegação, observações, GPS visitado e fachada. A coordenada importada nunca substitui o GPS capturado na fotografia. Limpar a rota apaga apenas objetos `Delivery`; a memória de `House` continua no aparelho.

## Automação assistida

O serviço de acessibilidade fica inativo durante a edição. Somente o botão `Play` inicia a sequência de `dispatchGesture`. Os marcadores passam a aceitar o toque através deles, permitindo interação manual com o aplicativo abaixo. O perfil pode parar por ciclos, por tempo ou pelo botão `Parar`; a confirmação final permanece manual.

## Estado de uma entrega no teclado

Os três marcadores `tracking_used`, `numeric_used` e `name_used` pertencem à entrega atualmente selecionada. Um toque bem-sucedido insere o texto por `InputConnection.commitText` e liga o marcador correspondente. Quando os três estão ligados, `current_index` avança e os marcadores são zerados.

O índice representa somente a posição de trabalho na fila. Ele não significa que a entrega foi concluída na Shopee.

## O que foi deliberadamente excluído

- confirmação automática de entrega;
- mecanismos de anti-detecção ou ocultação da automação;
- GPS falso ou substituição da posição real da fotografia;
- leitura ou alteração do banco de dados da Shopee;
- captura silenciosa de tela ou de teclado;
- envio de planilhas, endereços ou fotos para servidores;
- preenchimento automático de um campo de documento sem orientação oficial.

Essas exclusões diminuem o risco de baixa errada, bloqueio de conta e exposição de dados pessoais.
