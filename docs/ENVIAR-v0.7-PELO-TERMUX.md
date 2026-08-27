# Enviar a versão 0.7 pelo Termux

Baixe `BaixaDaShopee-source-v0.7.0.zip` para a pasta `Download` do celular e execute:

```sh
cd ~/BaixaDaShopee-upload
unzip -o /storage/emulated/0/Download/BaixaDaShopee-source-v0.7.0.zip -d .
git add .
git commit -m "Versão 0.7: nomes, interface e painel"
git push
```

O envio inicia automaticamente o fluxo `Gerar APK`. Na aba **Actions**, abra a execução mais recente e baixe o artefato `BaixaDaShopee-v0.7`.

Instale o APK por cima da versão atual. Não desinstale o aplicativo, pois isso apagaria a memória local.
