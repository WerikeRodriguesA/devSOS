# devSOS

Rede social para desenvolvedores: composta por um **feed de problemas de
código** (estilo Instagram) e **corridas de ajuda** (estilo Uber).

**A proposta:** quem sabe ajuda quem precisa. Um dev publica um bug ou desafio
no feed; outro dev "aceita o socorro" e vira um helper na sessão — com **chat
ao vivo**, **trechos de código** compartilhados e **avaliação mútua** no final.

## Funcionalidades (MVP)

- Feed com cards (tags, descrição, autor, tipo e recompensa).
- Publicar pedido de ajuda e aceitar a corrida de outro perfil.
- Chat em tempo real por corrida (WebSocket + STOMP), com mensagens de texto
  ou bloco de código.
- Autenticação via JWT (login com email e senha, senha com hash BCrypt).
- Histórico de mensagens persistido no banco.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.1 (REST + WebSocket) · JWT |
| Banco | PostgreSQL 16 (Docker), DDL versionada em `database/` |
| Frontend | React Native + Expo (axios, sockjs-client + @stomp/stompjs) |
| Dev Client (interno) | Vite + JS vanilla (REST + SockJS/STOMP) — só para testes |

## Estrutura do repositório

```
.
├── database/                 # DDL e migrações (PostgreSQL)
├── backend/                  # API Spring Boot (perfis, feed, JWT, chat)
├── mobile/                   # App React Native/Expo (testa o MVP no celular)
├── dev-client/               # Dev Client Web (ferramenta interna de teste)
└── docs/                     # Documentação técnica e executiva
```

## Rodar localmente

Banco (Docker) + backend + app mobile: passos completos em
[`docs/API.md`](docs/API.md) e [`mobile/README.md`](mobile/README.md).

> As tabelas são criadas/atualizadas sozinhas pelo backend no boot (migrações
> versionadas com Flyway em `backend/src/main/resources/db/migration/`) — não
> é preciso rodar scripts de banco à mão.

## Documentação

- [Como testar (guia prático)](docs/COMO_TESTAR.md) — do zero até o fluxo
  completo pelo **Web Client** em `http://localhost:5173` (cadastro, feed,
  corrida, chat e avaliações) + como testar no celular e via curl.
- [API REST + WebSocket](docs/API.md) — rotas, JSONs, canais STOMP e arquitetura.
- [Banco (técnica)](docs/TECNICA.md) — dicionário de dados, índices e concorrência.
- [Executiva banco](docs/EXECUTIVA.md) e [Executiva backend](docs/EXECUTIVA_BACKEND.md) — sem jargão.
- [Mobile](mobile/README.md) — app Expo e como gerar o APK (`mobile/docs/`).
- [Dev Client Web](dev-client/README.md) — ferramenta interna de teste no
  navegador (abas, chat em tempo real e log de requisições).