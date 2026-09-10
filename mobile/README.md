# devSOS Mobile

App React Native (Expo) do DevSOS — o "cliente" do backend Java/Spring.

- **REST**: axios (feed, posts, sessões, histórico de chat).
- **Tempo real**: WebSocket via sockjs-client + @stomp/stompjs (salas de chat
  por corrida, assinando `/topic/chat/{chatRoomId}`).
- **Arquitetura em camadas**: `services`, `hooks`, `components`, `screens`.
- **Conta**: perfil com pontos/média de avaliação, "Minhas corridas",
  avaliação mútua (1–5 + comentário) e logout local.

## Estrutura

```
mobile/
├── App.js                    # entrada + "navegação manual" (MVP)
├── index.js                  # registerRootComponent (expo)
├── app.json / package.json   # config e dependências
├── src/
│   ├── config.js             # IP/URL do backend (o "application.properties")
│   ├── services/
│   │   ├── api.js            # axios + token + chamadas REST (feed, posts,
│   │   │                     #   sessions, usuários, reviews)
│   │   └── socket.js         # SockJS + STOMP (chat em tempo real)
│   ├── hooks/
│   │   ├── AuthContext.js    # contexto global de login/logout (singleton)
│   │   └── useChatMessages.js# ciclo de vida do chat (histórico + socket)
│   ├── components/
│   │   ├── Avatar.js         # foto ou iniciais (fallback sem URL)
│   │   ├── PostCard.js       # card do feed (tags, descrição, aceitar)
│   │   ├── MessageBubble.js  # balão de mensagem (texto vs. código)
│   │   ├── RatingStars.js    # estrelas 1–5 (exibir ou avaliar)
│   │   └── AvaliarCorridaModal.js  # formulário de avaliação mútua
│   └── screens/
│       ├── LoginScreen.js
│       ├── FeedScreen.js     # + acesso ao Perfil
│       ├── CreatePostScreen.js
│       ├── ChatScreen.js     # volta para a tela de origem
│       └── PerfilScreen.js   # perfil + abas Corridas/Avaliações + Sair
└── docs/
    ├── PASSO_A_PASSO.md          # como rodar + gerar o APK
    ├── TROUBLESHOOTING_REDE.md   # problemas de rede (localhost vs IP)
    └── EXECUTIVA_FRONTEND.md     # o papel do front (analogia do restaurante)
```

## Começando rápido

```bash
cd mobile
npm install
# edite src/config.js com o IP da sua máquina na rede
npm start
```

O código segue uma didática em comentários com **analogias de backend** — uma
ajuda para devs de Java que estão migrando para mobile.
Veja `docs/EXECUTIVA_FRONTEND.md` para o mapa React ↔ Backend.

> Backend: `../backend` (Spring Boot). As rotas usadas estão em
> `services/api.js` e `services/socket.js`.