# Leitor de Rastreios BR v1.3 — Zoom e nome do destinatário

**Teclado com câmera:** na área da câmera, abra e feche dois dedos para aplicar zoom de acordo com o limite físico da câmera. O nível de zoom aparece sobre o vídeo. Do lado direito, cada linha exibe **código de rastreio | primeiro nome**, ou **Nome pendente** se o texto da etiqueta não foi reconhecido.

**OCR local:** ML Kit Latin Text Recognition (modelo integrado) tenta reconhecer o campo rotulado `Destinatário`, `Recebedor`, `Nome`, bem como rua/endereço, bairro e CEP. É uma tentativa: é necessário enquadrar texto e código na mesma imagem; texto pequeno, borrado, sobreposto ou etiquetas com campos diferentes podem falhar. O sistema não inventa nomes nem vincula o texto de uma etiqueta diferente ao código escaneado. O destinatário completo, o primeiro nome, a rua, o bairro e o CEP são salvos no armazenamento local e exportados como colunas para Excel.

O código de rastreio continua sendo escaneado e, havendo campo ativo, tenta ser preenchido seguido de Enter após 650 ms, independentemente de o OCR conseguir reconhecer o nome. A câmera fica dentro do teclado, sem abrir o aplicativo principal. O botão **SALVAR PLANILHA** abre apenas o seletor de arquivos Android. Os botões **LIMPAR LISTA** (confirmação dupla), **APAGAR** (último rastreio), **QR / BARRAS** (pausa/retoma) e o bip/vibração continuam disponíveis.

**Privacidade:** os dados pessoais da etiqueta ficam apenas no armazenamento privado do aplicativo, até o usuário exportar a planilha. Não capturamos nem enviamos vídeos ou imagens ao servidor. Evite compartilhar planilhas que contenham nomes e endereços desnecessariamente.

**Primeiro uso:** abra o aplicativo pelo ícone para autorizar a câmera e ativar o teclado. Depois selecione Teclado Rastreios BR em qualquer campo editável do app de baixas.

**Compilar:** workflow GitHub Actions `Leitor Rastreios BR APK v1.3`, branch `feature/v1-3-zoom-ocr-etiqueta`. Baixe o artefato `LeitorRastreiosBR-debug` (ZIP contendo `app-debug.apk`). O GitHub Actions testa o parser OCR antes do build.

**Observação:** compile e teste no dispositivo para verificar foco/zoom, OCR e comportamento do aplicativo de destino diante do Enter. Para atualizar, o pacote Android precisa ter assinatura igual à versão anterior; se diferente, exporte primeiro as planilhas e reinstale com cuidado, pois desinstalar apaga os registros locais.
