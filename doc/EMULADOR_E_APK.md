# Emulador Android e APK do DevSOS

Este guia explica como iniciar o emulador, gerar/instalar o APK e trabalhar com recarga rápida durante o desenvolvimento.

## Requisitos

- Windows, Node.js/npm e dependências do projeto em `mobile/`.
- Android SDK instalado em `%LOCALAPPDATA%\Android\Sdk` (ou configurado em `ANDROID_HOME`/`ANDROID_SDK_ROOT`), incluindo `platform-tools` e `emulator`.
- Um AVD criado no Android Studio. O padrão deste projeto é `Medium_Phone_API_37.0`.
- JDK 17. O script procura uma pasta `jdk-17*` em `%LOCALAPPDATA%\Programs\Eclipse Adoptium`; se não encontrar, usa `JAVA_HOME`.

Na primeira configuração, abra Android Studio e conclua o Setup Wizard, incluindo as licenças e os componentes Android SDK/Android Emulator. No Device Manager, crie um AVD se `Medium_Phone_API_37.0` não existir.

## Abrir o APK release

No PowerShell, a partir da raiz do repositório:

```powershell
.\doc\atualizar-e-abrir.ps1 -Modo Release
```

O script sincroniza as dependências, aplica as versões recomendadas pelo Expo, atualiza o projeto Android nativo, compila o APK, inicia o AVD se necessário, instala o app e abre a tela principal.

APK gerado em:

```text
mobile\android\app\build\outputs\apk\release\app-release.apk
```

Para usar outro AVD:

```powershell
.\doc\atualizar-e-abrir.ps1 -Modo Release -AvdName "Nome_Do_AVD"
```

## Desenvolver com recarga rápida

Para alterações em JavaScript/TypeScript, prefira:

```powershell
.\doc\atualizar-e-abrir.ps1 -Modo Dev
```

O Expo inicia o app em modo de desenvolvimento e mantém o Metro ativo. Salvar arquivos JS/TS atualiza a tela com Fast Refresh, sem recompilar o APK a cada edição. Mantenha esse terminal aberto enquanto desenvolve.

Se adicionar ou atualizar uma dependência nativa (por exemplo, um módulo Expo), execute novamente o script em modo `Dev` ou `Release`: mudanças nativas precisam de novo prebuild e compilação. O script não usa `expo prebuild --clean`, para preservar arquivos/códigos nativos existentes.

## O que o script automatiza

- Sincroniza `package.json` e `package-lock.json` com `npm install`.
- Usa `npx expo install --fix` para alinhar dependências à versão do Expo; depois valida com `npx expo install --check`.
- Executa o prebuild Android, que também faz o Metro empacotar o JavaScript durante a compilação release.
- No modo `Release`, compila, instala e abre o APK com ADB.
- No modo `Dev`, delega a compilação/abertura ao `expo run:android` e deixa o Fast Refresh ativo.
- Para no primeiro erro e mostra o comando que falhou. Erros de código, incompatibilidades ou configurações incompletas precisam ser corrigidos; o script não consegue deduzir nem reescrever código com segurança.

## Backend

O app usa o endereço definido em `mobile/src/config.js`. Para o emulador Android padrão, o endereço do backend executado na mesma máquina costuma ser `http://10.0.2.2:8080`. Confirme que o backend está iniciado e que `API_BASE_URL` aponta para o host/porta corretos antes de testar login, feed ou chat.

## Problemas comuns

- **JDK incompatível:** use JDK 17. O script escolhe automaticamente um `jdk-17*` em `%LOCALAPPDATA%\Programs\Eclipse Adoptium` ou usa `JAVA_HOME`.
- **AVD não encontrado:** confira o nome no Android Studio > Device Manager ou passe `-AvdName`.
- **ADB/emulator não encontrados:** instale Android SDK Platform-Tools e Android Emulator pelo SDK Manager, ou configure `ANDROID_HOME`.
- **Erro de dependência Expo:** execute o script novamente; se `expo install --fix` não conseguir resolver, leia a incompatibilidade exibida e ajuste a dependência manualmente.
- **Falha de API:** verifique se o backend está rodando e o endereço em `mobile/src/config.js` pode ser alcançado pelo emulador.
