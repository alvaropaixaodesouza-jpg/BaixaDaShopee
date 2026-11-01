# Leitor de Rastreios BR

Aplicativo Android independente, na pasta `LeitorRastreiosBR/` do repositório `BaixaDaShopee`.

## Funções
- Lê QR Code e códigos de barras pela câmera (CameraX + ML Kit).
- Reconhece rastreios iniciados em BR e SPXBR; filtro desativável para outras etiquetas.
- Registra horário, código e tipo. Não repete código na mesma lista.
- Mantém listas anteriores no armazenamento local e permite selecionar/criar listas.
- Exporta arquivo Excel de verdade (.xlsx) para a pasta escolhida pelo usuário.
- Fornece teclado Android com câmera, modo lote, enviar e Enter; tenta preencher o código e avançar no aplicativo de destino.

## Gerar APK
O workflow `Leitor de Rastreios BR APK` compila automaticamente a cada push nesta branch.
Na aba Actions, abra a execução mais recente e baixe o artefato `LeitorRastreiosBR-debug`; dentro dele está `app-debug.apk`.

## Uso
No aplicativo, toque **Abrir câmera e escanear**, conceda a permissão de câmera e aponte para as etiquetas. Depois toque **Concluir** e **Salvar planilha Excel**.
Para o teclado, entre em **Ativar teclado**, selecione **Teclado Rastreios BR** e abra um campo de texto em outro aplicativo. Toque em QR / BARRAS para uma leitura ou LOTE para uma sequência. O teclado tenta inserir código e Enter.

## Limites
A aceitação de Enter depende do aplicativo de destino: nenhum teclado Android consegue garantir navegação universal entre campos e telas. Em telas que perdem o foco, reabra o campo e toque em ENVIAR. A câmera abre em tela separada e não fica embutida no teclado.
Os rastreios ficam apenas no celular. O histórico pode ser apagado quando o usuário desinstala ou limpa os dados do aplicativo. Faça exportações frequentes.
O APK gerado é **debug para testes**, não versão de publicação da Play Store.
