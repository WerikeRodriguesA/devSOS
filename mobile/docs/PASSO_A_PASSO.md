# DevSOS Mobile — Passo a passo

Como subir, conectar e gerar o **APK** do app React Native (Expo).

---

## 1. Pré-requisitos

| Ferramenta | Versão mínima | Para quê |
|------------|--------------|----------|
| Node.js | 18 (recomendado 20+) | rodar o Metro (bundler do RN) |
| npm | 9+ | gerenciar dependências |
| Git | — | versionar o projeto |
| Expo Go | app (Play Store) | testar no celular físico |
| Android Studio / Emulador | — | alternativa de teste / gerar APK |
| Backend DevSOS | rodando em `:8080` | o app não tem dados próprios |

O backend precisa estar de pé e acessível pela rede (veja
`docs/TROUBLESHOOTING_REDE.md` para o problema clássico de `localhost`).

---

## 2. Criando o projeto do zero (scaffold)

Este repositório já traz o app pronto na pasta `mobile/`. Se quiser criá-lo
manualmente para entender o que cada arquivo faz:

```bash
# 1) Sobe a estrutura base do Expo (template em branco)
npx create-expo-app@latest devsos-mobile --template blank

# 2) Entra na pasta
cd devsos-mobile

# 3) Instala as dependências usadas pelo nosso código
npm i axios sockjs-client stompjs
npm i expo-status-bar

# 4) Copie a pasta src/ deste projeto + o App.js + index.js para dentro dela
#    (ou rode `npm run start` e ajuste o config.js)

# 5) Garante que as versões do Expo/React estão no mesmo SDK que o Metro
npx expo install --fix
```

> Importante: `expo`, `react` e `react-native` **devem casar com o mesmo SDK**.
> O comando `npx expo install --fix` alinha automaticamente. Se o mapa de
> versões do seu SDK for outro, ele cuidará do ajuste.

---

## 3. Configurando o endereço do backend

Abra `src/config.js` e troque o IP pelo IP **da sua máquina** na rede Wi-Fi:

```js
export const API_BASE_URL = 'http://192.168.0.10:8080'; // SEU IP
export const WS_BASE_URL = API_BASE_URL; // WebSocket usa o mesmo host
```

- **Emulador Android**: use `http://10.0.2.2:8080` (alias interno que aponta
  para o localhost da máquina).
- **Celular físico**: use o IP da máquina na rede local (o mesmo Wi-Fi).
- Nunca use `localhost` — no celular isso significa o próprio celular.

Mais detalhes e testes em `docs/TROUBLESHOOTING_REDE.md`.

---

## 4. Rodando o app

```bash
cd mobile
npm start          # ou: npx expo start
```

O Expo sobe o Metro e mostra um QR Code:

| Como testar | Comando/ação |
|-------------|--------------|
| **Celular físico** | instale o app **Expo Go** e escaneie o QR Code |
| **Emulador Android** | aperte `a` no terminal (ou `npm run android`) |
| **Abrir em outra porta** | `npx expo start --port 8081` |

O app abre na tela de **Login**. Use um usuário criado no backend
(`POST /api/auth/register`) ou os já existentes (ex.:
`carlos@dev.com` / `senha12345`).

Fluxo esperado:
1. Login → Feed.
2. Tocar "+ Postar" → criar um post (aparece no feed).
3. Em outro usuário, tocar "Aceitar Socorro" em um post → cai dentro da
   **sala de chat**.
4. Dentro da sala: o histórico carrega via REST e novas mensagens chegam em
   tempo real pelo WebSocket. O botão "Código" envia `CODE_SNIPPET`.

---

## 5. Gerando o APK

### Opção A — Expo (apk via EAS, sem Android Studio)

```bash
npm i -g eas-cli
# faça login uma única vez
eas login

# primeiro build (cria as credenciais)
eas build --platform android --profile preview

# apk pronto nos builds do Expo EAS (menu "Builds")
```

O profile `preview` gera um **APK** instalável direto (não precisa da Play
Store). Para a Store, o profile `production` (AAB) é o caminho.

### Opção B — Build local com Android Studio

```bash
cd mobile
npx expo prebuild --platform android   # gera a pasta android/
cd android
./gradlew assembleRelease              # gera app/build/outputs/apk/release/
```

> No Windows, use `gradlew.bat assembleRelease`.

---

## 6. Endpoints usados pelo app

| Onde | Endpoint | Exige JWT |
|------|----------|-----------|
| `services/api.js` | `POST /api/auth/login` | Não |
| `services/api.js` | `GET /api/posts` | Não |
| `services/api.js` | `POST /api/posts` | Sim |
| `services/api.js` | `POST /api/sessions` | Sim |
| `services/api.js` | `GET /api/sessions/{id}/messages` | Sim |
| `services/socket.js` | WS `/ws-devsos` (SockJS+STOMP) | Sim (query `token`) |

Vale a analogia: os `services` são os "Controllers clientes" da API — cada
função conversa com uma rota REST ou com o canal STOMP.