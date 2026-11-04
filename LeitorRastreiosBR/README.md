# Leitor de Rastreios BR v1.4 — Importar rotas Excel e reenviar

Aba **CÂMERA** (primeira): scanner QR/barras, zoom, OCR de nome, histórico, bip, limpar lista, apagar último e exportar Excel. Nenhum botão inferior mudou. Foram adicionados os atalhos do campo de texto: **Backspace** e **Enter**, e seta **→ ROTA**.

Aba **ROTA** (segunda): seta **← VOLTAR**; **IMPORTAR EXCEL** abre o seletor nativo de arquivos; **+ NOVO EXCEL** adiciona outra rota independente sem apagar as anteriores; **SELECIONAR ROTA** permite trocar entre arquivos importados. São aceitos **.xlsx** (primeira planilha) e CSV. O importador local lê códigos **BR/SPXBR**, elimina duplicatas dentro da rota e limita 10.000 códigos e 12 MB por arquivo. Os dados da rota não são enviados para nenhum servidor.

**Intervalo entre o Enter e o próximo código:** botões **−** e **+**, em passos de 0,5 segundo, de 0,5 até 60 segundos. Inicialmente 3,0 s. O intervalo pode ser ajustado enquanto roda.

**INICIAR** envia um código por vez no campo já selecionado, faz Enter e aguarda o intervalo escolhido. **PAUSAR** interrompe imediatamente após a operação atual. **CANCELAR** interrompe e guarda o progresso para continuar do próximo código, sem limpar a rota. Fechar o teclado, trocar de campo/aplicativo ou voltar à aba da câmera pausa o reenvio; ele nunca recomeça sozinho. Se a rota concluir, a execução não reinicia nem duplica automaticamente; para uma nova execução, importe outra cópia da planilha. A memória do progresso persiste no armazenamento privado do aplicativo.

O indicador de progresso mostra **quantos códigos receberam Enter pelo teclado**, não quantas baixas foram confirmadas pelo aplicativo de destino. Android InputMethodService e InputConnection não oferecem confirmação da baixa. Se o aplicativo receptor perde o foco após um Enter, a operação pausa. **Nunca use em campos de senha.**

**Limitações:** a importação aparece no seletor nativo Android e pode ocultar temporariamente o teclado; após escolher o arquivo, retorne ao campo original. A leitura de `.xls` antigo não é suportada. Arquivos grandes são recusados por segurança. A sessão de baixa do aplicativo receptor precisa ser testada manualmente para determinar o intervalo adequado. Caso uma digitação parcial falhe, revise o campo antes de retomar. Os dados locais podem ser apagados se o aplicativo for desinstalado.

**Código:** branch `feature/v1-4-reenvio-excel` no GitHub. **APK:** abra a execução bem-sucedida do GitHub Actions e baixe o artefato `LeitorRastreiosBR-debug`, contendo o arquivo `app-debug.apk`. Esta é uma versão de depuração para testes, não assinatura estável de loja.
