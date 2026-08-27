# Baixa da Shopee — Android 0.5.0

Aplicativo Android local com um teclado personalizado para organizar uma fila de entregas e inserir, no campo atualmente selecionado:

- o código de rastreio completo;
- o mesmo código contendo somente os algarismos;
- um nome fixo configurado pelo entregador.

O projeto não acessa contas nem confirma entregas. O painel assistido pode executar um toque ou deslize mapeado por vez, depois de o usuário ativar a acessibilidade e autorizar o pacote Android correto. A conferência e a confirmação final continuam manuais. Ele não solicita permissão de internet.

## Fluxo do teclado

Cada entrega permanece selecionada até os três dados serem inseridos. Cada botão muda para verde depois de usado. Quando os três ficarem verdes, a fila avança automaticamente para a próxima entrega.

O nome da pessoa e o endereço aparecem na lista e no teclado somente quando existem. Eles podem vir da planilha importada ou da memória da casa; o aplicativo não mostra mais mensagens de “nome não encontrado”.

Esse controle evita o erro mais perigoso do fluxo: o código completo de uma entrega ficar combinado com o código numérico da encomenda seguinte. Os botões **Voltar** e **Próxima** permitem corrigir a posição da fila. O botão **Teclado** retorna ao teclado normal do celular.

## Importação

O aplicativo aceita:

- `.xlsx` moderno, lendo a primeira aba;
- `.csv` separado por ponto e vírgula, vírgula ou tabulação;
- códigos colados, um por linha;
- linhas coladas no formato `código; nome; endereço`.

O formato original de rota é reconhecido sem mudar a estrutura: `AT ID | Sequence | Stop | SPX TN | Destination Address | Bairro | City | Zipcode/Postal code | Latitude | Longitude`. `Sequence` é tratado como o nome da pessoa e `SPX TN` como o código de rastreio. Também continuam aceitos títulos como `Código de rastreio`, `Rastreio`, `Tracking`, `AWB`, `Nome`, `Destinatário`, `Endereço`, `Número`, `Bairro` e `Cidade`. Códigos repetidos são removidos mantendo a primeira ocorrência.

O botão **Exportar código + nome + endereço** gera um CSV enriquecido com os dez campos da rota, situação/ocorrência, memória da casa, GPS da fotografia, fotos e PDF.

## Memória de casas e rota do dia

O aplicativo agora separa dois conjuntos de dados:

- **rota do dia**: códigos, posição do teclado e foto de cada pacote;
- **memória de casas**: apelido editável, moradores, endereço completo, link do mapa, observações e foto de referência da fachada.

Use **Vincular casa** na entrega selecionada para escolher uma casa já salva ou cadastrar uma nova. Ao importar outra rota, um endereço completo e específico pode ser reconhecido e vinculado automaticamente. Endereços genéricos sem número não são vinculados automaticamente, evitando que várias casas sejam confundidas.

O botão **Limpar rota** apaga somente a fila atual. Ele preserva a memória das casas e não remove fotos da galeria. Atualizar o APK por cima da versão instalada também preserva os dados; desinstalar o aplicativo apaga a memória interna.

## Fotos

Na tela principal, toque numa entrega e use:

- **Foto do pacote**;
- **Foto da fachada**.

O aplicativo usa uma câmera interna com prévia, **Tirar outra** e **Usar foto**. As imagens confirmadas são gravadas em `Imagens/Baixa da Shopee` com o código de rastreio no nome do arquivo. Quando há localização, latitude, longitude e horário também são gravados no EXIF quando o aparelho permite. A foto do pacote fica ligada à entrega atual; a foto da fachada fica ligada à casa permanente.

Quando o par **pacote + fachada** fica completo, a tela seleciona automaticamente a próxima entrega para fotografar. Se a casa já tiver fachada salva, basta registrar o pacote novo. A seleção das fotos é independente da posição do teclado; **Começar o teclado nesta entrega** é a única ação da tela que muda a posição da fila do teclado.

## Mapa e capturas da tela

Em cada casa é possível salvar o link exato do Google Maps ou Waze. O botão **Abrir no mapa** prioriza o link da casa, depois Latitude/Longitude do arquivo original e, por último, pesquisa o endereço.

## QR Code offline

O botão **Escanear QR Code offline** abre a câmera interna. Depois da fotografia, o modelo incorporado ao APK lê o QR Code e procura o conteúdo na rota carregada. Ao encontrar o código de rastreio, seleciona diretamente o pacote no aplicativo e no teclado. O modelo não precisa ser baixado durante o percurso.

Para relacionar códigos, nomes e endereços que aparecem somente no aplicativo oficial, use capturas longas em blocos de aproximadamente 5 a 8 entregas, repetindo uma entrega entre dois blocos consecutivos. Depois da leitura, importe no aplicativo o CSV enriquecido no formato `código; nome; endereço`. A leitura automática das próprias capturas dentro do celular ainda não faz parte desta versão.

## Localização e relatório PDF

Ao confirmar uma foto, o aplicativo tenta obter uma posição atual ou recente do GPS. Se a permissão for aceita e houver sinal, a entrega guarda latitude, longitude, precisão aproximada, data e horário. O endereço textual continua vindo da memória da casa ou da planilha; obter o nome da rua a partir do GPS pode exigir internet.

O botão **Gerar PDF desta entrega** cria um relatório em `Documentos/BaixaDaShopee/Entregas/CODIGO`. A pasta recebe o PDF e cópias organizadas das fotos do pacote e da fachada. O relatório contém código, casa, pessoas, endereço, coordenada de destino, GPS real da fotografia, ocorrência, horário e imagens. Ele não substitui o comprovante nem a confirmação do aplicativo oficial.

## Painel flutuante assistido

O botão **Painel flutuante assistido** abre as autorizações de sobreposição e acessibilidade. Existem perfis editáveis de **Baixa assistida** e **Ocorrência assistida**. A barra permite adicionar alvo de toque, adicionar deslize A→B, remover o último alvo, arrastar pontos, editar atraso/duração e carregar outra configuração. Cada pressão em **▶** executa somente o próximo passo; no fim do ciclo, o painel para para conferência manual. O perfil fica preso ao pacote Android detectado durante o mapeamento.

Cada entrega possui o menu `⋮` para editar nome/endereço, colocar ou remover ocorrência, abrir o perfil de Baixa/Ocorrência e excluir somente aquele item da rota. As definições de ocorrência são editáveis e ficam salvas para as próximas rotas.

As cores do painel estão centralizadas em `colors.xml`: `overlay_bg_color`, `primary_accent_color`, `secondary_accent_color`, `target_circle_color` e `text_primary_color`.

O Android isola os aplicativos. Portanto, o teclado não consegue obter automaticamente uma foto tirada dentro do app da Shopee nem preencher um campo de câmera que não aceite a galeria. Isso depende do fluxo oferecido pelo aplicativo oficial.

## Como instalar para teste

### Pelo Android Studio

1. Instale uma versão atual do Android Studio com JDK 17.
2. No SDK Manager, instale o Android SDK 36 e Build-Tools 36.0.0.
3. Abra esta pasta como projeto.
4. Aguarde a sincronização do Gradle.
5. Conecte o celular com depuração USB e pressione **Run**.

Para gerar um APK, use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Pelo GitHub Actions

O arquivo `.github/workflows/build-apk.yml` compila o APK ao enviar o projeto para um repositório GitHub. Na aba **Actions**, execute **Gerar APK** e baixe o artefato `BaixaDaShopee-debug`.

## Como ativar no celular

1. Abra **Baixa da Shopee**.
2. Salve o nome que está autorizado a ser inserido no fluxo de entrega.
3. Importe a planilha ou cole os códigos.
4. Toque em **Ativar teclado** e habilite **Baixa da Shopee** nas configurações do Android.
5. Volte, toque em **Escolher teclado** e selecione **Baixa da Shopee**.
6. No aplicativo oficial, toque no campo correto antes de usar cada botão do teclado.
7. Cadastre/vincule casas conforme os endereços forem identificados.
8. Confira código, pessoa, endereço e fotos antes da confirmação final.

## Privacidade e uso autorizado

Os dados ficam nas preferências privadas do aplicativo e as fotos ficam na pasta de imagens do aparelho. Não há envio para servidor. Use somente informações verdadeiras e procedimentos autorizados pela transportadora, pela plataforma e pelo destinatário. A foto da fachada salva é uma referência: use como comprovante atual somente se o procedimento da operação permitir. Se um campo estiver rotulado como documento de identidade, confirme com a supervisão qual dado deve ser registrado; este projeto não trata um código de rastreio como documento pessoal.

## Próximas melhorias recomendadas

- leitor de código de barras para selecionar automaticamente a entrega fotografada;
- histórico diário separado por rota;
- importação e leitura de capturas diretamente no aparelho;
- exportação e restauração de uma cópia de segurança da memória de casas;
- importação com escolha manual das colunas quando os títulos forem desconhecidos;
- modo configurável para exigir dois ou três botões antes do avanço;
- exportação de relatório de conferência sem declarar a entrega como concluída.
