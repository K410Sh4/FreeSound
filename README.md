# FreeSound — aplicativo Android

Aplicativo nativo Kotlin para pesquisar, ouvir, salvar e organizar músicas com informações de licença abertas, respeitando os termos de cada catálogo.

## Recursos
- Pesquisa em Wikimedia Commons, Internet Archive e Openverse.
- Filtro **Mais populares** usando a contagem real de downloads do Internet Archive (não ranking global).
- Capas oficiais dos catálogos ou imagens embutidas em arquivos locais, com cache e fallback.
- Pré-escuta de fontes com URLs HTTPS.
- Download via Android DownloadManager apenas para arquivos de origens autorizadas com indicação de licença CC, CC0 ou domínio público.
- Biblioteca offline, importação de áudios do aparelho, favoritos, links oficiais e créditos.
- Catálogo de sites oficiais de outros serviços, sem métodos não autorizados de extração.
- Sem WebView, anúncios, login, rastreadores ou quebra de DRM.
- Android 10+ (minSdk 29); interface Android nativa.

A licença declarada pelo catálogo não é garantia definitiva dos direitos de composição e gravação. Uso pessoal não autoriza automaticamente todos os downloads.

## Builds / APK

- [GitHub Actions — APKs por execução](https://github.com/K410Sh4/FreeSound/actions/workflows/android.yml)
- [GitHub Releases — versões arquivadas](https://github.com/K410Sh4/FreeSound/releases)

Cada envio para main ou execução manual do workflow inicia testes e compilação. Os APKs ficam em Actions > Android APK + versões > execução > Artifacts. Após sucesso, o workflow também tenta publicar v1.0.N em Releases, com APK anexado.

Sem segredos de assinatura, o aplicativo compila no modo debug. Builds diferentes poderão usar certificados diferentes, exigindo desinstalação antes da nova versão e perdendo dados locais. Configure uma chave persistente em ASSINATURA_E_VERSOES.md.

### Build local

Abra no Android Studio com Android SDK 35, Gradle 8.9 e JDK 17. O JAR do Gradle Wrapper não acompanha este repositório. Use Gradle 8.9 local ou regenere o wrapper e rode:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

APK: app/build/outputs/apk/debug/app-debug.apk

## Versões

APP_BUILD_NUMBER recebe github.run_number. A versão será 1.0.N, code N, tag v1.0.N. Cada commit fica no histórico Git. Artifacts expiram após 90 dias; Releases ficam arquivadas até exclusão.

## Limites

- API Wikimedia exige User-Agent com contato adequado em produção; substitua o placeholder.
- A checagem de licença é baseada em metadados, não é auditoria jurídica.
- Openverse é usado para descoberta; downloads são feitos no site original.
- Spotify e YouTube não oferecem extração irrestrita de MP3 por este app.
- Reprodução apenas em primeiro plano: ao minimizar o app, a música pausa.
- APK não foi validado em aparelho físico nesta entrega.

Código sob licença MIT (LICENSE); licenças de músicas são independentes.
