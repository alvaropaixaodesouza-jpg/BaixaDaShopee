# Baixas de Pacote — Android 0.8.0

Aplicativo Android local com banco de dados estruturado e teclado personalizado (**Teclado das baixas**) para organizar filas de entrega e agilizar a conferência e preenchimento de dados de rastreio, pessoas e fotos no fluxo de trabalho do entregador.

- **ID do aplicativo**: `com.alvaro.baixashopee`
- **Nome do app**: Baixas de Pacote
- **Nome do teclado**: Teclado das baixas
- **Versão**: 0.8.0 (Build 9)
- **Privacidade**: 100% local e offline (não requer permissão de internet nem envia dados a servidores).

---

## 📱 Navegação em 4 Áreas

A partir da versão 0.8.0, o aplicativo organiza todas as funções em quatro abas principais:

### 1. 📦 ROTA
- Visualização da fila do dia em tempo real com indicador duplo de progresso: **Pessoa X de Y** e **Pacote W de Z**.
- Organização e ordenação flexível da fila:
  - **Manual / Ordem de importação**;
  - **Nome A-Z** (agrupamento estrito por destinatário literal; nomes inteiramente em maiúsculas são movidos para o bloco final da triagem);
  - **Bairro** (com agrupamento inteligente de variantes como Cabuçu, Bom Jesus / Bom Jesus dos Pobres e Praia do Sol).
- Ações rápidas na entrega: vincular casa salva, abrir no mapa e detalhes do pacote.

### 2. 📷 FOTOS
- Processamento de fotos em lote (até 50 fotos por vez) com proteção de memória e anti-duplicação por hash MD5.
- **Identificação Offline**: Leitura automática de código de barras (1D e 2D) com fallback para OCR de texto via Google ML Kit incorporado.
- Associação automática das fotos aos pacotes da rota.
- Galeria de fotos pendentes com miniaturas e diálogo para atribuição manual de fotos não identificadas.

### 3. 📂 ARQUIVO
- **Entregas Concluídas**: Histórico de baixas com data/hora e atalho para **Desfazer**, retornando a entrega para a fila ativa caso haja engano.
- **Memória de Casas Salvas**: Lista de residências com apelidos, moradores e fotos de fachada de referência.
- **Exportação Excel**: Exportação da base de casas em formato `.csv` com codificação UTF-8 com BOM, compatível diretamente com Microsoft Excel e planilhas sem corromper acentos.
- Relatórios de entrega em PDF individual.

### 4. ⚙️ AJUSTES
- Guia passo a passo para **Ativar** e **Selecionar** o **Teclado das baixas**.
- Configuração do nome padrão do entregador / recebedor.
- Ajuste de **altura do teclado** (220dp a 420dp).
- Configuração do tempo de expiração da cópia temporária de fotos no MediaStore (padrão de 5 minutos).
- Configurações do painel flutuante de autoclique assistido e diagnóstico do sistema.

---

## ⌨️ Teclado das baixas

O teclado foi redesenhado com layout fixo, estável e ergonômico:

- **Indicador de Progresso Fixo**: Exibe a pessoa atual e o total de pessoas, além do pacote atual e total de pacotes (`Pessoa X/Y • Pacote W/Z`).
- **Preenchimento em 1 Toque**:
  - Código de rastreio completo (`SPX...`);
  - Código numérico (somente os algarismos);
  - Nome configurado do recebedor.
- **Fotos Rápidas no MediaStore (TTL 5 min)**:
  - Botões **Pacote** e **Casa**: criam uma cópia temporária da foto nos arquivos Recentes da Galeria do sistema (MediaStore). Isso permite selecionar a foto no aplicativo de entrega facilmente, sem alterar ou apagar o arquivo original, expirando automaticamente após 5 minutos.
- **Navegação Segura**:
  - Botões **Voltar** e **Próxima** para mover na fila.
  - Botão **Desfazer**: reverte a última entrega concluída diretamente pelo teclado.
  - Alternância rápida para o teclado alfanumérico padrão do sistema.

---

## 🗄️ Banco de Dados Local (Room)

O aplicativo utiliza o **Android Room** com migração automática e não-destrutiva a partir de versões anteriores:
- Entidades separadas para rotas, destinatários, pacotes, casas salvas, histórico de entregas concluídas e fotos.
- Preservação integral de todas as casas e moradores já cadastrados em versões legadas.

---

## 📥 Importação e Exportação

- **Formatos aceitos**:
  - Planilhas `.xlsx` modernas (busca automática da aba de rastreios);
  - Arquivos `.csv` (delimitadores `;`, `,` ou tabulação);
  - Texto colado (códigos linha a linha ou formato `código; nome; endereço`).
- **Estrutura de rota reconhecida**: `AT ID | Sequence | Stop | SPX TN | Destination Address | Bairro | City | Zipcode/Postal code | Latitude | Longitude`.
- **Exportação**:
  - Exportação de casas em CSV (UTF-8 BOM Excel).
  - Exportação de relatório PDF individual em `Documentos/BaixaDaShopee/Entregas/CODIGO`.

---

## 🛠️ Como Instalar e Testar

### Pelo Android Studio
1. Clone o repositório e abra no Android Studio com JDK 17+.
2. Certifique-se de ter o Android SDK 36 e Build-Tools 36.0.0 instalados.
3. Conecte o dispositivo via depuração USB e execute `Run` (ou gere o APK em `Build > Build Bundle(s) / APK(s) > Build APK(s)`).

### Execução de Testes Unitários
Para rodar a suíte completa de testes de regras de negócio:
```bash
./gradlew testDebugUnitTest
```

---

## 🔒 Privacidade e Segurança
Todos os dados e imagens são mantidos exclusivamente no armazenamento interno do aparelho. Nenhuma informação de rastreio, cliente ou localização é transmitida para a rede.
