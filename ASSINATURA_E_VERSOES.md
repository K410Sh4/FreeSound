# Assinatura persistente e versões APK

## Gerar APK de teste

Acesse Actions > Android APK + versões > Run workflow. Se testes e compilação passarem, baixe o APK pelo artefato da execução. O workflow também tentará publicar uma GitHub Release.

## Gerar APKs que podem atualizar uns aos outros

Não publique o keystore privado neste repositório público.

1. Em um computador com JDK, crie um keystore: keytool -genkeypair -v -keystore freesound-release.jks -alias freesound -keyalg RSA -keysize 3072 -validity 10000
2. Salve backup seguro e permanente do .jks, alias e senhas. Perder a chave impede atualizar instalações anteriores.
3. Codifique em Base64. PowerShell: [Convert]::ToBase64String([IO.File]::ReadAllBytes("freesound-release.jks")). Linux: base64 -w0 freesound-release.jks.
4. Em GitHub > Settings > Secrets and variables > Actions > New repository secret, configure:
   - ANDROID_KEYSTORE_BASE64 — keystore .jks em Base64;
   - ANDROID_KEYSTORE_PASSWORD — senha do keystore;
   - ANDROID_KEY_ALIAS — alias;
   - ANDROID_KEY_PASSWORD — senha da chave.
5. Execute o workflow novamente. O APK release será assinado e anexado à versão v1.0.N.

Não é possível instalar uma release com assinatura diferente por cima de um APK debug existente. Para atualizações futuras sem desinstalação, preserve o mesmo applicationId, a mesma chave de assinatura e incremente o versionCode.

## Histórico

- main: código atual e histórico de commits.
- v1.0.N: tag e Release numeradas por GitHub Actions.
- Artifacts: APKs de teste por 90 dias.
- Releases: APKs arquivados até remoção.

Se a publicação de Release falhar, verifique Settings > Actions > General > Workflow permissions e conceda permissão de escrita para GitHub Actions. Se as políticas da conta impedirem, os APKs dos Artifacts permanecem disponíveis.
