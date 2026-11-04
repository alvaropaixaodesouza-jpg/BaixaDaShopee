# Leitor de Rastreios BR v1.6 — Correções após vídeo de uso real

## Correções aplicadas
1. **Backspace no teclado**: se houver seleção de texto, substitui a seleção inteira por vazio com `InputConnection.commitText("", 1)`. Sem seleção, remove um caractere anterior ao cursor. Não interfere no botão inferior **APAGAR**, que remove o último pacote escaneado da lista da câmera.
2. **Botão Excel duplicado removido**: a aba ROTA fica com **IMPORTAR EXCEL** e **LIMPAR LISTA**. Pode importar outros arquivos repetindo o mesmo botão Importar Excel e alternar entre rotas existentes em Selecionar Rota.
3. **Limpar lista da rota**: ao tocar LIMPAR LISTA duas vezes em até oito segundos, remove apenas o arquivo/rota selecionada, apagando seus BRs e progresso. Outras rotas importadas e a lista de pacotes escaneados na aba da câmera permanecem preservadas.
4. **Cancelar duas vezes**: primeiro toque interrompe o envio e guarda o progresso; segundo toque em até oito segundos **reinicia o índice da rota para 0**, de modo que o próximo INICIAR começa no primeiro BR novamente. Não dispara envio automaticamente após reiniciar.
5. **Continuidade ao esconder e reabrir o teclado**: após o primeiro INICIAR o estado da execução fica registrado. Se o aplicativo receptor ocultar temporariamente o teclado após Enter, ele entra em estado **Aguardando campo**. Quando o mesmo aplicativo e campo compatível reaparecem dentro de três minutos, o teclado tenta retomar sozinho, respeitando o intervalo configurado contado do último Enter. O progresso de envio e o alvo são preservados mesmo se o serviço do teclado for recriado.
6. **Proteção de campo**: não reenvia se retornar em outro aplicativo, campo de senha/URL, campo incompatível ou campo com texto não selecionado que poderia concatenar BRs. Nesses casos pede intervenção manual.

## Reenvio
Abra o campo do aplicativo de destino, selecione o teclado Rastreios BR, toque → ROTA. Importe Excel `.xlsx`, CSV ou texto com códigos BR. Ajuste o intervalo com +/− (padrão 3 s). Toque INICIAR. O sistema envia BR, tecla Enter, aguarda o intervalo configurado e envia o próximo quando o mesmo campo estiver disponível. PAUSAR interrompe imediatamente e exige INICIAR para voltar. CANCELAR duas vezes reinicia a rota.

A lista mostra quantos **códigos tiveram Enter enviado pelo teclado**; isso **não comprova que a baixa foi aceita no sistema de destino**. A automação só pode operar com o campo de texto ativo, não controla o navegador ou a tela por fora dele. Se for necessário alternar outras telas, a automação pode ser bloqueada por segurança.

## Exportação/Importação
O Excel do aplicativo contém apenas uma coluna "Código BR". O importador identifica BRs mesmo em planilhas antigas com outras informações, porém armazena só os códigos no sistema de rotas. Mantém a ordem, remove duplicatas dentro da mesma rota e suporta até 10 mil códigos e 12 MB por arquivo. O Android pode abrir seu seletor padrão por um instante durante a importação. Não usamos servidores externos.

## Compilar
O fluxo GitHub Actions na branch `feature/v1-6-continuidade-teclado` executa testes e compila o APK debug. Baixe o artefato `LeitorRastreiosBR-debug` na execução concluída.

**Aviso de atualização:** exporte as listas antes de desinstalar versões antigas; desinstalar pode apagar registros locais. A funcionalidade foi compilada e precisa de teste real no navegador/app receptor, pois o teclado Android não consegue garantir que cada Enter tenha sido processado.
