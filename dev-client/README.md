# Dev Client Web (devSOS)

Ferramenta **interna de desenvolvimento e testes** que exercita o backend
back atual do devSOS (REST + WebSocket/STOMP) direto pelo navegador. **Não** é
o frontend oficial — é um banco de teste: registra/cria usuários, monta
corridas, troca mensagens em tempo real e mostra o "log de requisições" de
cada ação, sem alterar contratos da API.

- Arquitetura/regras do backend: veja `docs/` e `notes/` na raiz do projeto.
- Contrato da API REST: `docs/API.md`.
- **Como testar o projeto usando esta ferramenta (passo a passo): [`docs/COMO_TESTAR.md`](../docs/COMO_TESTAR.md).**

## Pré-requisitos

- Backend do devSOS rodando (ver `README.md` na raiz) com o banco aplicado.
- Node 18+ (recomendado 20+).

## Como rodar

```bash
cd dev-client
npm install
npm run dev
```

Abra **http://localhost:5173**. Por padrão a base URL da API é
`http://localhost:8080`; dá para trocar no campo do cabeçalho (persiste em
`localStorage`, chave `devsos:baseUrl`) — útil para testar contra staging.

### Backend com CORS liberado (obrigatório para o navegador)

O app roda em `http://localhost:5173` e o backend em outra origem, então o
navegador bloqueia sem os headers CORS. Suba o backend com:

```bash
DEV_SOS_CORS_ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173' \
  java -jar backend/target/devsos-backend-0.0.1-SNAPSHOT.jar
```

Sem essa variável o CORS fica **desligado** (nenhuma origem liberada) — esse
é o comportamento de produção. O gating fica em
`backend/src/main/java/com/devsos/infrastructure/security/CorsConfig.java`
(propriedade `devsos.cors.allowed-origins`, origens separadas por vírgula).

### Build de produção (para deploy da ferramenta)

```bash
npm run build        # gera ./dist
npm run preview      # serve o build em localhost:4173
```

## O que cada área faz

| Aba | Fluxos do projeto |
| --- | --- |
| **Autenticação** | Fluxo 1 e 2 — cadastro, login, logout; mostra/decodifica o JWT |
| **Feed** | Fluxo 3 e 4 — listar e criar posts; aceitar socorro (vira corrida) |
| **Corridas** | Fluxo 6 — minhas corridas (MATCHED/ACTIVE/COMPLETED/CANCELLED), transições de estado |
| **Chat** | Fluxo 5/7 — histórico persistido e tempo real via WebSocket (STOMP por SockJS) |
| **Avaliações** | Fluxo 8 — avaliar corrida concluída, ver o que recebi |
| **Requisições** (lateral) | log de cada chamada REST: método, status, duração, envio, resposta/erro |

O **chat** conecta em `/ws-devsos?token=<jwt>` via SockJS + STOMP e assina
`/topic/chat/{sala}`; mensagens `CHAT` e `CODE_SNIPPET` são suportadas. O
backend rejeita quem não participa da sala (quem assina um tópico sem direito
recebe um frame STOMP `ERROR`).

## Nota rápida sobre o código

- `src/api.js` — cliente fetch com cabeçalho `Authorization: Bearer <jwt>`,
  envelope de erro padronizado e `onLog(...)` (alimenta o painel de
  requisições emitindo `devsos:log`).
- `src/session.js` — guarda token/usuário em `localStorage`
  (`devsos:session`), `getSession`, `estaAutenticado`, `onSessionChange`.
- `src/socket.js` — `criarChatSocket({ token, chatRoomId, ... })` espelhando
  o contrato STOMP usado pelo app mobile.
- `src/panes/*` — uma aba por arquivo; todas exportam `init(root, ctx)` e
  muitas exportam `carregar()`. `src/main.js` faz a fiação (abas, badge de
  sessão, toast, e pontes entre abas, ex.: "abrir chat" a partir do feed).