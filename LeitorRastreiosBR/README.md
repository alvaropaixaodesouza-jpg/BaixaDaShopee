# Leitor de Rastreios BR v1.7 — Quebra de linha corrigida

**Correção após análise do vídeo de 51,7 s:** o teclado Android simulava a tecla Enter com `InputConnection.sendKeyEvent(KEYCODE_ENTER)`. O aplicativo de destino pode interceptar essa tecla, interpretá-la como ação ou não inserir `\n`. Depois da primeira tentativa, a regra antiga `safeToType()` recusava qualquer campo com texto, fazendo o reenvio parar em 1/9.

A aba ROTA agora tem um botão de **MODO: QUEBRA DE LINHA ↵** (padrão), que pode ser alternado para **MODO: ENTER DO APLICATIVO**. A preferência é salva.

### Modo QUEBRA DE LINHA — para testar em campos de mensagem e editores de texto
- Para cada BR: `commitText(codigo, 1)`, `commitText("\n", 1)`, verifica se o campo terminou com `codigo + "\n"` e aguarda o intervalo configurado.
- O próximo BR é permitido quando o cursor está no fim de uma linha vazia, mesmo que as linhas anteriores tenham outros BRs. Não precisa apagar a primeira linha.
- Se o campo bloquear a quebra de linha ou o cursor estiver no meio do texto, pausa em vez de repetir códigos.
- **Não envia a mensagem do WhatsApp**, apenas compõe um texto com um BR por linha; o usuário decide se deve enviar depois.

### Modo ENTER DO APLICATIVO — para sistemas de baixas
- Insere o BR e dispara `KEYCODE_ENTER`. A ação que o campo realiza depende do aplicativo receptor.
- Só passa para o próximo quando o campo estiver livre de texto ou o texto anterior estiver selecionado; evita concatenar códigos acidentalmente.
- O contador indica eventos enviados, **não confirma baixa no sistema**.

O teclado ainda oferece **Importar Excel, Limpar Lista, Selecionar Rota, Iniciar, Pausar, Cancelar duas vezes para reiniciar do primeiro BR e tempo ajustável**. A câmera permanece na primeira aba. A preferência de aba e o progresso da rota são preservados após recriação do teclado. Os arquivos XLSX e CSV são interpretados localmente, importando apenas códigos BR.

### Testes
GitHub Actions compila o APK debug e roda testes JUnit, incluindo casos de campo com linhas existentes, nova linha não inserida, seleção de texto e cursor no meio do campo.

### Limitações
Nem todo campo Android aceita quebras de linha. Um campo configurado para apenas uma linha ou para envio imediato pode rejeitar `\n`; a ferramenta deve pausar e avisar. Para baixas reais, teste com poucos códigos antes de usar em produção. Não é possível garantir que o aplicativo receptor processou a baixa a partir do sucesso do Enter no teclado.

**Instalação:** antes de desinstalar, exporte suas listas. APK debug assinado por outra chave pode exigir remoção da versão anterior e perder os dados locais. Preferir o link de artefatos do GitHub se o anexo do ChatGPT falhar.
