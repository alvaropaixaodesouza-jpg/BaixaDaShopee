# Baixa da Shopee 0.7.0

## Correção da planilha com nomes

Os dois arquivos não são internamente iguais, apesar de terem as mesmas colunas visíveis.

- `(1)26-08-2026 ALVARO PAIXAO DE SOUZA.xlsx` contém 12 entregas e a coluna `Sequence` usa `-`; portanto, esse arquivo realmente não contém nomes.
- `lista_corrigida_app_com_bairro-1.xlsx` contém 33 entregas com nomes reais na coluna `Sequence`. Ele foi salvo com tags XML prefixadas, como `<x:c>` e `<x:v>`, e com textos diretos `t="str"`.
- O leitor anterior aceitava somente `<c>` e `<v>`. Por isso interpretava a segunda planilha como vazia.
- O novo leitor ignora o prefixo técnico e aceita as duas serializações do XLSX.

Antes de substituir a rota, o aplicativo agora informa quantas entregas e quantos nomes foram reconhecidos.

## Tela principal

- Cabeçalho com estado de Sobreposição e Acessibilidade.
- Botão `▶ Painel` com autorização guiada.
- Uma única rolagem para nome, QR, importação, entrega selecionada e lista completa.
- Menu `•••` ao lado da importação para ferramentas menos usadas.
- Cartões e botões arredondados, cores mais suaves e seleção visual da entrega.
- Menu de cada pacote limitado a edição, ocorrência e exclusão.

## Painel flutuante

- Reabre sempre numa posição visível.
- Largura limitada ao espaço disponível da tela.
- Play/Stop, toque, deslize, remover, configurações, dados, mover/recolher e fechar.
- Somente Play inicia os gestos.
- Durante a execução, a edição fica bloqueada e a tela abaixo continua interativa.
- Tocar no controle de mover recolhe/expande; arrastar move o painel.
- Alvos também ficam limitados à área da tela.
- Três predefinições vazias e editáveis: Baixar, Ocorrência e Tirar de ocorrência.
- Perfis podem ser salvos, renomeados, duplicados, limpos, excluídos, importados e exportados.

## Autorização no Samsung

O aplicativo abre primeiro a autorização de sobreposição e depois tenta abrir diretamente `Baixa da Shopee — Automação`. A ativação continua exigindo um toque humano porque o Android não permite que um aplicativo conceda a si mesmo acesso de acessibilidade.

Se o Samsung bloquear o serviço, a ajuda abre as informações do aplicativo para o usuário escolher `⋮ > Permitir configurações restritas`. O quadrado que o Samsung pode colocar na borda é um atalho do sistema e não substitui o painel com os botões.
