# Leitor de Rastreios BR — v1.1 (câmera no teclado)

**Novo fluxo:** escolha o teclado Rastreios BR em qualquer campo de texto. A câmera fica aberta **dentro do teclado**, à esquerda, com até seis códigos recentes à direita. Não há teclado alfabético. QR e barras são detectados automaticamente.

Ao reconhecer um código válido, ele é gravado na lista local e colocado na fila de envio. Se houver um campo selecionado, o teclado tenta inserir o código e, após **650 ms**, enviar a tecla Enter. Dependendo do aplicativo de destino, Enter poderá fechar o campo, enviar formulário, ou não ter efeito; é necessário testar no aplicativo de baixas.

**Botões:** QR / BARRAS (pausar/retomar leitura); ENVIAR LOTE (enviar pendências, sem repetir códigos já enviados); APAGAR (remover último registro local — não desfaz registro no app de destino); SALVAR PLANILHA (abre seletor Android, permitindo memória interna ou cartão SD compatível com o seletor).

Para conceder permissão de câmera, abra o aplicativo principal e toque em ABRIR CÂMERA E ESCANEAR pelo menos uma vez. Depois volte ao aplicativo de baixas e selecione o teclado Rastreios BR. O teclado não captura vídeo nem faz upload; usa processamento local ML Kit. A câmera fecha quando o teclado é ocultado. Não use o teclado em campos de senha.

O histórico é mantido automaticamente no armazenamento privado do aplicativo. O arquivo Excel .xlsx é gerado apenas quando **SALVAR PLANILHA** for acionado. Crie novas listas no aplicativo principal. O filtro BR/SPXBR pode ser desativado para etiquetas de outros padrões.

## APK
Consulte o workflow `Leitor de Rastreios BR APK` na aba Actions, branch `feature/camera-dentro-teclado`; baixe o artefato `LeitorRastreiosBR-debug` e extraia `app-debug.apk`. O APK de testes não é a versão assinada para distribuição na Play Store.

## Limitações
A câmera embutida em InputMethodService e o Enter entre aplicativos exigem validação em celular real. Se o aplicativo de destino alterar o foco ou bloquear eventos de teclado, códigos podem ficar pendentes; toque ENVIAR LOTE quando o campo estiver pronto. O Android pode restringir a câmera do teclado em alguns dispositivos e políticas. O botão APAGAR remove apenas o último rastreio da lista local.
