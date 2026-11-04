# Leitor de Rastreios BR v1.5 — Excel corrigido, somente rastreios

## O que mudou
- Corrigido o erro **"This parser does not support specification Unknown version 0.0"** na importação de Excel no Android: a versão anterior chamava `DocumentBuilderFactory.isXIncludeAware=false`, não suportado em certas versões do Android.
- Lê planilhas **.xlsx**, arquivos **.csv** e listas **.txt** com códigos BR. Aceita `inlineStr` (Excel exportado pelo próprio aplicativo), `sharedStrings` (planilhas feitas em Excel/LibreOffice) e arquivos de texto com um BR por linha.
- Ignora nomes, endereços, CEP, números de pedido e outras colunas. **Somente códigos que começam com BR** entram na rota. Códigos repetidos são descartados mantendo a ordem do primeiro registro.
- A exportação de Excel agora tem **uma coluna "Código BR"** e uma linha por rastreio. Não coleta nem exporta nomes. O scanner QR/BARRAS continua dentro do teclado, com bip e zoom.
- A segunda aba mostra a lista importada e o próximo BR a enviar. A navegação por setas entre câmera/rota permanece.

## Como usar no WhatsApp ou em aplicativo de baixas
1. Abra o campo onde os códigos devem ser digitados e selecione o teclado Rastreios BR.
2. Toque **→ ROTA** e depois **IMPORTAR EXCEL** (ou **+ NOVO EXCEL** para adicionar outra rota sem apagar as anteriores).
3. Escolha a planilha do celular. O Android volta ao teclado; se preciso, toque no campo novamente.
4. Veja a quantidade e os BRs encontrados. Ajuste o intervalo pelo **−** ou **+** (padrão 3,0 segundos, 0,5 a 60 segundos).
5. Toque **INICIAR**. O teclado insere um BR, envia Enter e espera o intervalo escolhido antes do próximo.
6. **PAUSAR** interrompe, **CANCELAR** para e guarda o progresso. O envio não volta sozinho ao fechar o teclado.

## Restrições importantes
- **Enter enviado não significa baixa concluída**: o aplicativo de destino pode processar Enter de maneira diferente ou perder o foco. Se o campo fechar ou mudar de aplicativo, a execução pausa.
- Não utiliza Accessibility Service para abrir ou controlar outras telas. Precisa manter o campo de texto selecionado.
- Limite de 10 mil BRs por arquivo e 12 MB de entrada. **.xls** antigo não é suportado; salve como `.xlsx`.
- Planilhas importadas, códigos e progresso ficam localmente no aparelho. Não existe upload para servidores.
- Antes de desinstalar versões anteriores, exporte seus dados. APK debug pode ter assinatura diferente.

## Testes e compilação
GitHub Actions no branch `feature/v1-5-importar-br-excel` executa testes automáticos: ler XLSX exportado por este app, XLSX com strings compartilhadas, listas de texto e rejeitar XML potencialmente malicioso. A compilação gera `app-debug.apk` no artefato `LeitorRastreiosBR-debug`.

A importação foi corrigida no código e verificada pelos testes automatizados, mas deve ser validada no Android com uma planilha real do usuário.
