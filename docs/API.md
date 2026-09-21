# DevSOS — API do Backend (Perfis, Feed, Autenticação e Corridas)

> Público-alvo: desenvolvedores (front, mobile, back).
> Versão do contrato: Spring Boot 4.1.1 · Java 25 · PostgreSQL 16
> Base URL (local): `http://localhost:8080`

---

## 1. Convenções

- **Formato de todos os payloads:** JSON (`Content-Type: application/json`).
- **Datas:** sempre `Instant` (UTC, com `Z` no final), ex.: `2026-09-09T18:48:19Z`.
- **Paginação:** parâmetros `?page=0&size=10` (base 0) no `PagedModel` do Spring.
- **IDs:** UUIDv4 (string de 36 caracteres).
- **Erros:** envelope único `ApiError` (ver seção 4).
- **Autenticação:** rotas protegidas exigem o cabeçalho
  `Authorization: Bearer <token>` (JWT obtido em `/api/auth/login` ou
  `/api/auth/register`). Sem token → `401`; token presente nas rotas públicas
  é simplesmente ignorado.

---

## 2. Autenticação (JWT)

### 2.1 `POST /api/auth/register` — Criar conta (código 201)

Corpo de envio:

```json
{
  "nome": "Bruna Fullstack",
  "email": "bruna@dev.com",
  "senha": "senha12345",
  "githubUsername": "bruna-full"
}
```

| Campo | Tipo | Obrigatório | Regras |
|-------|------|------------|--------|
| `nome` | `string` | Sim | 2–120 caracteres |
| `email` | `string` | Sim | formato e-mail; único no sistema |
| `senha` | `string` | Sim | 8–100 caracteres; guardada só como hash BCrypt |
| `githubUsername` | `string` | Não | máx. 60 caracteres |

Resposta — `201 Created`:

```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9....",
  "expiraEmSegundos": 3600,
  "usuario": {
    "id": "85fcf0d5-bece-44f8-afa3-a7ee0ed07292",
    "nome": "Bruna Fullstack",
    "email": "bruna@dev.com",
    "bio": "",
    "githubUsername": "bruna-full",
    "avatarUrl": "",
    "saldoPontos": 0,
    "mediaAvaliacoes": 0.0,
    "tecnologiasDominadas": [],
    "createdAt": "2026-09-10T01:09:30.536151Z"
  },
  "refreshToken": "YSHqoDRKg_KUAc-9xUBd5zfhFVJDSaL0RE4oKWjhvR4",
  "refreshExpiraEmSegundos": 604800
}
```

| Campo | Tipo | Significado |
|-------|------|-------------|
| `token` | `string` | **Access JWT** (curto, 1h). Vai em `Authorization: Bearer <token>`. |
| `expiraEmSegundos` | `number` | Vida útil do access JWT. |
| `usuario` | `object` | Perfil autenticado. |
| `refreshToken` | `string` | **Refresh token opaco** (43 chars, base64url). Renova o access sem pedir a senha (ver 2.3) e revoga sessão (ver 2.4). **Guarde-o com segurança e rotacione a cada uso.** |
| `refreshExpiraEmSegundos` | `number` | Vida útil do refresh token (padrão 7 dias). |

Erros: `400` (validação de formulário), `400` (e-mail já cadastrado).

---

### 2.2 `POST /api/auth/login` — Entrar (código 200)

Corpo de envio:

```json
{ "email": "bruna@dev.com", "senha": "senha12345" }
```

Resposta — `200 OK`: mesma estrutura do `register` (access JWT + expiração
+ perfil + refresh token + expiração do refresh).

Erros: `400` "E-mail ou senha inválidos." (credenciais erradas — o atacante não
descobre se o e-mail existe sozinho), `400` (validação).

> **Como usar o token:** inclua em toda requisição protegida o cabeçalho
> `Authorization: Bearer <token>`.

---

### 2.3 `POST /api/auth/refresh` — Renovar o access sem relogar (código 200)

Quando o access JWT expira (1h), o cliente troca o refresh token por um par
novo — não precisa da senha de novo. **Rota pública** (sem JWT).

Corpo de envio:

```json
{ "refreshToken": "YSHqoDRKg_KUAc-9xUBd5zfhFVJDSaL0RE4oKWjhvR4" }
```

Resposta — `200 OK`: mesmo formato do login/register (access JWT novo +
perfil + **refresh token NOVO**).

**Rotações e segurança:**

| Situação | Comportamento |
|----------|---------------|
| Refresh válido | 200 + par novo; o token usado **morre** (rotaciona, com rastro `replaced_by`) |
| Token inexistente | `400 "Refresh token inválido."` |
| Token **reutilizado** (já foi consumido/revogado) | `400` + **revoga TODAS as sessões do usuário** (suspeita de roubo — um token que vazou é usado por dois agentes) |
| Token expirado | `400` (idem acima) |

> O refresh token é **opaco** e vive só no banco como **hash SHA-256**
> (tabela `refresh_tokens`, migração V2). O valor cru existe apenas na
> resposta e no cliente — vazamento do banco não libera tokens utilizáveis.

---

### 2.4 `POST /api/auth/logout` — Revogar sessões (código 204)

**Exige JWT** (o servidor precisa saber DE quem revogar). Não devolve corpo.

| Body | Efeito |
|------|--------|
| `{}` ou sem body | **Logout forçado**: revoga TODOS os refresh tokens do usuário autenticado (todas as suas "sessões"/dispositivos) |
| `{ "refreshToken": "..." }` | Revoga só aquele refresh token (este dispositivo) |

Erros: `401` (sem token), `400 "Refresh token não encontrado."`.

> **Limite didático:** o access JWT é stateless — não dá para revogá-lo antes
> de expirar (até 1h). Após o logout, quem tentar renovar um access vencido
> toma `400`, porque o refresh não existe mais. É por isso que o padrão de
> mercado combina JWT curto + refresh revogável.

---

### 2.5 `GET /api/users/{id}` — Buscar perfil público (aberto)

Parâmetros:

| Nome | Tipo | Obrigatório | Descrição |
|------|------|------------|-----------|
| `id` | `UUID` | Sim | Identificador do usuário |

Resposta — `200 OK`

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "nome": "Ana Dev",
  "email": "ana@dev.com",
  "bio": "Especialista em backend Java",
  "githubUsername": "ana-dev",
  "avatarUrl": "https://github.com/ana-dev.png",
  "saldoPontos": 120,
  "mediaAvaliacoes": 4.50,
  "tecnologiasDominadas": ["java", "spring"],
  "createdAt": "2026-09-09T18:42:15.181948Z"
}
```

Erros: `404` se o usuário não existir. Rota pública (não precisa de token).

---

### 2.6 `PATCH /api/users/{id}/technologies` — Atualizar tecnologias dominadas

> **Obriga JWT.** O `id` atualizado é o do **usuário logado** (o `{id}` do path
> é aceito por compatibilidade de URL, mas o dono vem do token) — ninguém altera
> o perfil de terceiros.

Cabeçalhos: `Content-Type: application/json` + `Authorization: Bearer <token>`

Corpo de envio:

```json
{
  "tecnologiasDominadas": ["Java", "Spring Boot", "PostgreSQL", "Docker"]
}
```

| Campo | Tipo | Obrigatório | Regras |
|-------|------|------------|--------|
| `tecnologiasDominadas` | `array<string>` | Sim | máx. 30 itens; cada item não nulo |

Resposta — `200 OK` (perfil atualizado; tecnologias normalizadas em minúsculas)

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "nome": "Ana Dev",
  "email": "ana@dev.com",
  "bio": "Especialista em backend Java",
  "githubUsername": "ana-dev",
  "avatarUrl": "https://github.com/ana-dev.png",
  "saldoPontos": 120,
  "mediaAvaliacoes": 4.50,
  "tecnologiasDominadas": ["java", "spring boot", "postgresql", "docker"],
  "createdAt": "2026-09-09T18:42:15.181948Z"
}
```

Erros: `401` (sem token), `400` (body inválido), `404` (usuário não existe).

---

### 2.7 `GET /api/posts` — Listar feed paginado (posts `OPEN`, aberto)

Query params (opcionais):

| Nome | Default | Descrição |
|------|---------|-----------|
| `page` | `0` | Página (base 0) |
| `size` | `10` | Itens por página |
| `sort` | `createdAt,desc` | Ordenação |

Resposta — `200 OK` (estrutura `PagedModel` do Spring)

```json
{
  "content": [
    {
      "id": "28dc7009-0312-425a-908e-53c521752b8f",
      "titulo": "Bug no Spring Data JPA",
      "descricao": "Meu repository nao encontra registros com join fetch, alguem ajuda?",
      "mediaUrl": "https://i.imgur.com/erro.png",
      "tags": ["java", "spring", "jpa"],
      "tipo": "FREE",
      "recompensaValor": 0.0,
      "status": "OPEN",
      "autorNome": "Ana Dev",
      "autorAvatarUrl": "https://github.com/ana-dev.png",
      "createdAt": "2026-09-09T18:48:19.840365Z"
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 2.8 `POST /api/posts` — Criar publicação (código 201)

> **Obriga JWT.** Desde a iteração de autenticação, o **autor** do post é o
> usuário logado (definido pelo token) — o campo `authorId` **deixou de existir**
> no contrato e é ignorado se enviado.

Cabeçalhos: `Content-Type: application/json` + `Authorization: Bearer <token>`

Corpo de envio:

```json
{
  "titulo": "Bug no Spring Data JPA",
  "descricao": "Meu repository nao encontra registros com join fetch, alguem ajuda?",
  "mediaUrl": "https://i.imgur.com/erro.png",
  "tags": ["java", "spring", "jpa"],
  "tipo": "FREE",
  "recompensaValor": 0
}
```

| Campo | Tipo | Obrigatório | Regras |
|-------|------|------------|--------|
| `titulo` | `string` | Sim | 3–160 caracteres |
| `descricao` | `string` | Sim | 10–5000 caracteres |
| `mediaUrl` | `string` | Não | Pode ser a URL devolvida em `POST /api/uploads` (print no storage do DevSOS) ou `http(s)://…` externo |
| `tags` | `array<string>` | Não | máx. 10 itens |
| `tipo` | `enum` | Sim | `FREE` ou `PAID` |
| `recompensaValor` | `number` | Não | ≥ 0; **`FREE` ⇒ obrigatoriamente `0`; `PAID` ⇒ > `0`** |

Resposta — `201 Created` (com cabeçalho `Location: /api/posts/{id}`)

```json
{
  "id": "d1d2b270-b7b9-452f-b980-2e807845783c",
  "titulo": "Bug no Spring Data JPA",
  "descricao": "Meu repository nao encontra registros com join fetch, alguem ajuda?",
  "mediaUrl": "https://i.imgur.com/erro.png",
  "tags": ["java", "spring", "jpa"],
  "tipo": "FREE",
  "recompensaValor": 0.0,
  "status": "OPEN",
  "autorNome": "Bruna Fullstack",
  "autorAvatarUrl": "https://github.com/bruna-full.png",
  "createdAt": "2026-09-10T01:12:11.168989Z"
}
```

Erros:

| HTTP | Caso |
|------|------|
| `413` | Corpo maior que o limite (`devsos.posts.max-body-bytes`, default **64 KiB**) — barrado na porta, antes mesmo do JWT |
| `401` | Sem token (ou token inválido/expirado) |
| `400` | Validação de formulário (campo ausente/curto) |
| `400` | Regra de negócio (`FREE` com recompensa, `PAID` sem recompensa) |
| `404` | Usuário autenticado não existe mais |

---

### 2.9 Corridas (`sessions`) — a dinâmica "Uber" do DevSOS

> **Todas as rotas de corrida exigem JWT.** O usuário logado é o **helper** no
> aceite e o "participante" no restante. Corridas de terceiros são invisíveis
> (o endpoint devolve `400` "Você não participa desta corrida.").

#### O ciclo de vida de uma corrida

```
   POST /api/sessions      PATCH → ACTIVE       PATCH → COMPLETED
        │                       │                    │
        ▼                       ▼                    ▼
     [MATCHED] ──────────▶ [ACTIVE] ──────────▶ [COMPLETED]
        │                       │
        └────────── PATCH → CANCELLED ◀─────────┘
                    (post volta a OPEN)
```

- **MATCHED** — aceite: o helper pega o post (que sai do feed).
- **ACTIVE** — atendimento em curso (tanto faz quem dispara).
- **COMPLETED** — só o **helper** conclui; transfere a recompensa e resolve o post.
- **CANCELLED** — qualquer participante cancela; o post volta ao feed.

Regras extras: 1 corrida ativa por post (barrada no Service **e** no banco por
índice único); não dá para aceitar o próprio post; só dá para CONCLUIR partindo
de ACTIVE (transição de MATCHED direto é `400`).

#### 2.9.1 `POST /api/sessions` — Aceitar socorro (código 201)

Corpo de envio (o `helper` vem do token):

```json
{ "postId": "b1b072c6-e366-4af8-b0a5-37d0abcad78c" }
```

Resposta — `201 Created` (Status de um post: entra `IN_PROGRESS`, sai do feed):

```json
{
  "id": "076c04cf-4a93-4c89-b6ec-d2a73729a1c4",
  "postId": "b1b072c6-e366-4af8-b0a5-37d0abcad78c",
  "postTitulo": "Projeto travado na fila do RabbitMQ",
  "recompensaValor": 30.00,
  "status": "MATCHED",
  "chatRoomId": "2cc00dd9-5021-484b-a070-d4e404d8b3ee",
  "authorId": "ba84f93c-7b95-4872-8eb1-342b5116d1bc",
  "authorNome": "Carlos Dev",
  "helperId": "f8f4baef-9c56-4f52-8e46-bec223abafc6",
  "helperNome": "Diana Java",
  "completedAt": null,
  "createdAt": "2026-09-10T01:30:27.089800Z"
}
```

> `chatRoomId`: sala de chat criada automaticamente no aceite. Os dois
> participantes conversam nela pelo **chat em tempo real** (seção 2.11) e o
> histórico fica disponível em `GET /api/sessions/{id}/messages` (seção 2.11.3).

Erros:

| HTTP | Caso |
|------|------|
| `401` | Sem token |
| `400` | Post não está `OPEN` / já tem corrida ativa / você é o autor |
| `404` | Post (ou usuário logado) não existe |
| `409` | Corrida de concorrência: outro helper aceitou no mesmo instante (índice único) |

#### 2.9.2 `PATCH /api/sessions/{id}` — Avançar a corrida (código 200)

> **Obriga JWT.** Só participantes da corrida (autor ou helper).

Corpo de envio:

```json
{ "status": "ACTIVE" }
```

| `status` | Quem pode | Transição permitida | Efeito |
|----------|-----------|--------------------|---------|
| `ACTIVE` | qualq. participante | de `MATCHED` | mantém o post fora do feed |
| `COMPLETED` | **só o helper** | de `ACTIVE` | transfere pontos, resolve o post, grava `completedAt` |
| `CANCELLED` | qualq. participante | de `MATCHED`/`ACTIVE` | post volta a `OPEN` |

`COMPLETED` em post **PAID**:
- `autor.saldo_pontos -= recompensa` e `helper.saldo_pontos += recompensa`
  (a própria DDL garante saldo não negativo — `CHECK`).
- Se o autor não tiver saldo: `400` "O autor não tem saldo suficiente...".
- Post `FREE` não movimenta pontos (recompensa = 0).

Erros: `401`, `400` (não participa / transição ilegal / status alvo inválido ou
já atual), `404`.

#### 2.9.3 `GET /api/sessions` — Minhas corridas (código 200)

Onde você participa como **autor** ou **helper** — paginado
(`?page=0&size=10&sort=createdAt,desc`). Exige JWT. Formato: `PagedModel`
com itens iguais ao do aceite.

#### 2.9.4 `GET /api/sessions/{id}` — Detalhe de uma corrida (código 200)

Exige JWT e participação. Terceiros recebem `400` "Você não participa desta
corrida." (a existência da corrida não é revelada).

---

### 2.10 Avaliações mútuas (`reviews`) — pós-corrida

> **Todas as rotas exigem JWT.** O **avaliador** é o usuário logado e o
> **avaliado** é SEMPRE o outro lado da corrida (autor ↔ helper) — o cliente
> não escolhe quem avaliar (isso impede que se avalie estranhos).

#### 2.10.1 `POST /api/reviews` — Avaliar a corrida (código 201)

Requisitos: corrida `COMPLETED`, você participa dela e ainda não avaliou
(1 review por pessoa por corrida).

Corpo de envio:

```json
{
  "sessionId": "076c04cf-4a93-4c89-b6ec-d2a73729a1c4",
  "nota": 5,
  "comentario": "Resolveu o deadlock rapidinho, muito claro."
}
```

| Campo | Tipo | Obrigatório | Regras |
|-------|------|------------|--------|
| `sessionId` | `UUID` | Sim | Corrida concluída |
| `nota` | `number` | Sim | inteiro 1–5 |
| `comentario` | `string` | Não | máx. 1000 caracteres |

Resposta — `201 Created` (média do avaliado já recalculada pelo trigger do banco):

```json
{
  "id": "380c8f31-21ea-4566-983c-da888e571512",
  "sessionId": "076c04cf-4a93-4c89-b6ec-d2a73729a1c4",
  "reviewerId": "f8f4baef-9c56-4f52-8e46-bec223abafc6",
  "reviewerNome": "Diana Java",
  "reviewedId": "ba84f93c-7b95-4872-8eb1-342b5116d1bc",
  "reviewedNome": "Carlos Dev",
  "nota": 5,
  "comentario": "Resolveu o deadlock rapidinho, muito claro.",
  "mediaAvaliacoesDoAvaliado": 5.00,
  "createdAt": "2026-09-10T01:31:36.022734Z"
}
```

Erros: `401`, `400` (corrida não concluída / você não participa / já avaliou /
validação), `404` (corrida não existe).

#### 2.10.2 `GET /api/reviews` — Avaliações que EU recebi (código 200)

Paginado (`?page=0&size=10&sort=createdAt,desc`). Exige JWT. Devolve o
histórico da minha reputação (mesmo formato do item acima).

---

### 2.11 Chat em tempo real (WebSocket/STOMP)

Quando existe uma corrida (`sessions`), os dois participantes conversam numa
**sala** identificada pelo `chatRoomId` (criado no aceite). O chat tem duas
metades:

| Metade | Tecnologia | Para quê |
|--------|------------|----------|
| **Tempo real** | WebSocket (SockJS + STOMP) | trocar mensagens ao vivo na sala |
| **Histórico** | REST (`GET /api/sessions/{id}/messages`) | carregar mensagens anteriores |

#### 2.11.1 Conectar (handshake SockJS)

O backend expõe um endpoint SockJS em `/ws-devsos`. O token JWT vai como
parâmetro de consulta (o servidor identifica você pelo JWT, nunca pelo que o
cliente digita):

```
ws://localhost:8080/ws-devsos/{serverId}/{sessionId}/websocket?token=<JWT>
```

Em STOMP, o primeiro frame é o `CONNECT` (com `accept-version:1.2` e
`heart-beat:0,0`). O servidor responde `CONNECTED` com `user-name` = id do
usuário do token.

> **SockJS transporta STOMP dentro de array JSON.** No transporte
> "raw websocket", o cliente envia `["<frame STOMP>\\u0000"]` como JSON e
> recebe do servidor `o` (aberto), `a[...]` (frames), `\n` (batimento) e
> `c[...]` (fechamento). Quem usar a biblioteca `@stomp/stompjs` não precisa
> se preocupar com isso — o `webSocketFactory` cuida do envelope.

#### 2.11.2 Assinar e enviar

| Ação | Frame STOMP | Destino |
|------|-------------|---------|
| Ouvir a sala | `SUBSCRIBE` | `/topic/chat/{chatRoomId}` |
| Mandar mensagem | `SEND` | `/app/chat/{chatRoomId}` |

Corpo do `SEND` — o cliente manda **só** `content` e `type`:

```json
{ "content": "oii, roda com -Xmx512m", "type": "CHAT" }
```

O servidor preenche `senderId`, `senderNome` e `timestamp`, **persiste** a
mensagem e republica no tópico `/topic/chat/{chatRoomId}` para **todos que
assinaram** (inclusive o remetente):

```json
{
  "senderId": "f8f4baef-9c56-4f52-8e46-bec223abafc6",
  "senderNome": "Diana Java",
  "content": "oii, roda com -Xmx512m",
  "timestamp": "2026-09-10T12:03:28.686Z",
  "type": "CHAT"
}
```

- `senderId` / `senderNome`: dono do JWT da conexão (nunca confiável no corpo).
- `timestamp`: hora do servidor (UTC) em que a mensagem chegou — os relógios de
  celular não contam.
- `type`: `CHAT` (texto comum) ou `CODE_SNIPPET` (trecho de código).

**Autorização:** quem não participa da corrida (não é o autor do post nem o
helper) recebe um frame `ERROR` com a mensagem `Você não participa desta sala
de chat.` e a conexão é encerrada (`1002`) — valendo tanto para `SUBSCRIBE`
quanto para `SEND`.

#### 2.11.3 Histórico — `GET /api/sessions/{id}/messages`

> **Obriga JWT.** Só participantes da corrida (autor ou helper).

Lista as mensagens da sala em ordem cronológica (mesmo formato do frame):

```
GET /api/sessions/{id}/messages
```

```json
[
  { "senderId": "ba84f93c-...", "senderNome": "Carlos Dev", "content": "opa, bora", "timestamp": "2026-09-10T12:03:28.683Z", "type": "CHAT" },
  { "senderId": "f8f4baef-...", "senderNome": "Diana Java", "content": "@Bean MeterRegistry", "timestamp": "2026-09-10T12:03:28.690Z", "type": "CODE_SNIPPET" }
]
```

| HTTP | Caso |
|------|------|
| `200` | Histórico devolvido (vazio se ninguém falou ainda) |
| `401` | Sem token |
| `404` | Sessão não existe |
| `400` | Você não participa (`Você não participa desta corrida.`) |

> **Quando usar REST e quando usar WebSocket?** O WebSocket serve para o que
> acontece **agora** (bolha nova aparecendo sem dar F5). O REST serve para o que
> **já aconteceu** (entrar na sala e carregar o que já foi dito — quem recarrega
> a página não perde o histórico). No DevSOS a jornada típica é: abrir a sala →
> `GET /api/sessions/{id}/messages` para o histórico → assinar o tópico para as
> mensagens novas em tempo real.

### 2.12 `POST /api/uploads` — Subir o print do problema (código 201)

> **Obriga JWT.** Antes, `mediaUrl` só aceitava um link colado de fora (ex.:
> Imgur). Agora o DevSOS tem **storage próprio** (um MinIO local em dev —
> S3-compatível): o print é enviado como arquivo e vira um link da própria API.

Formato: `multipart/form-data`, campo de arquivo chamado **`arquivo`**.

```bash
curl -X POST http://localhost:8080/api/uploads \
  -H "Authorization: Bearer <token>" \
  -F "arquivo=@./print.png"
```

| Requisito | Valor |
|-----------|-------|
| Content-Type aceito | `image/png`, `image/jpeg`, `image/webp`, `image/gif` |
| Arquivo | máx. **5 MB** (definido em `spring.servlet.multipart.max-file-size`) |
| Autenticação | JWT obrigatório |

Resposta — `201 Created`:

```json
{
  "mediaUrl": "http://localhost:8080/api/uploads/ecebf0f0-05a1-4680-a842-75fd8364945a.png"
}
```

A `mediaUrl` devolvida pode ir no campo `mediaUrl` do `POST /api/posts` — o
mesmo contrato de sempre. Ela aponta para `GET /api/uploads/{chave}`, que
**serve o arquivo direto do storage** (sem expor o MinIO). Quem criou o post
não precisa guardar o arquivo: o "print" segue vivo no bucket.

#### 2.12.1 `GET /api/uploads/{chave}` — Baixar a imagem (público)

> **Público** (como o feed): qualquer um vê a imagem de um post — `GET` tem
> `permitAll` no `SecurityConfig`; quem **subir** (`POST`) precisa de JWT.

- `200` — bytes da imagem + `Content-Type` certo + `Cache-Control: max-age=2592000`
- `401` — sem token (somente no `POST`)
- `400` — chave fora do padrão (não é `uuid.ext`)
- `415` — content-type fora da lista de imagens (no `POST`)
- `413` — arquivo acima de 5 MB (no `POST`)
- `503` — storage não configurado/minio fora do ar

**Como a segurança da imagem funciona?** A extensão vem do **content-type**
declarado, nunca do nome original do arquivo (renomear `virus.exe` para
`virus.png` não engana: se o content-type não é imagem, responde 415). E
ninguém "sobrescreve" arquivo: a chave é um **UUID gerado no servidor**
(impossível prever/colidir). Veja `StorageService`.

---

## 3. Como rodar

### Pré-requisitos
- Java 25
- PostgreSQL 16 — container `devsos-db` na porta `5433` (a DDL é aplicada sozinha pelo Flyway no boot, sem script manual)

```bash
# dentro de backend/
mvn spring-boot:run          # ou: ./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

> O banco usa `spring.jpa.hibernate.ddl-auto=none`: **a DDL não é gerada pelo
> Hibernate** — ela é versionada e aplicada automaticamente pelo **Flyway** no
> boot (`backend/src/main/resources/db/migration/`). Em banco novo/vazio o
> backend cria tudo do zero. Num banco antigo (criado à mão antes do Flyway,
> como o `devsos-db` local) ele faz *baseline*: valida o schema atual, assume a
> versão 1 e passa a rodar apenas as próximas migrações por cima — os dados
> existentes não são tocados.

### Configuração local (variáveis de ambiente)

| Propriedade | Default (dev) | Observação |
|-------------|---------------|------------|
| `devsos.jwt.secret` | chave fixa de dev | em produção, defina `DEV_SOS_JWT_SECRET` |
| devsos.jwt.expiracao-segundos | 3600 (1h) | DEV_SOS_JWT_EXPIRACAO para sobrescrever |
| devsos.jwt.refresh-expiracao-segundos | 604800 (7 dias) | DEV_SOS_JWT_REFRESH_EXPIRACAO para sobrescrever |
| `devsos.storage.endpoint` | (vazio = storage desligado, upload 503) | MinIO local: `http://localhost:9000` — `DEV_SOS_STORAGE_ENDPOINT` |
| `devsos.storage.access-key` / `secret-key` | (vazio) | MinIO dev: `devsos` / `devsos123` (nunca commit em produção) |
| `devsos.storage.bucket` | `devsos` | criado sozinho no boot (`StorageBootstrap`) |
| `devsos.storage.region` | `us-east-1` | qualquer valor para MinIO |

Storage desligado (`endpoint` vazio) ≠ banco: o backend sobe normal, só o
`POST /api/uploads` responde **503** até você apontar um MinIO/S3.

---

## 4. Contrato de erro padronizado

Todo erro retorna `ApiError`:

```json
{
  "timestamp": "2026-09-09T18:48:25.972Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Requisição inválida. Verifique os campos informados.",
  "fieldErrors": {
    "titulo": "O título deve ter entre 3 e 160 caracteres",
    "descricao": "A descrição deve ter entre 10 e 5000 caracteres"
  }
}
```

| Campo | Significado |
|-------|-------------|
| `timestamp` | Momento do erro (UTC) |
| `status` | Código HTTP numérico |
| `error` | Frase curta do status |
| `message` | Mensagem amigável / causa |
| `fieldErrors` | Erros de validação **por campo** (usado pelo front nos inputs) |

Mapa de exceções → HTTP (veja `GlobalExceptionHandler`):

| Exceção | HTTP |
|---------|------|
| `ResourceNotFoundException` | `404` |
| `RegraDeNegocioException` | `400` |
| `MethodArgumentNotValidException` | `400` + `fieldErrors` |
| `ConstraintViolationException` | `400` |
| `HttpMessageNotReadableException` | `400` |
| `MethodArgumentTypeMismatchException` | `400` |
| `MissingServletRequestParameterException` | `400` |
| `NoResourceFoundException` | `404` |
| `DataIntegrityViolationException` | `409` (rede de segurança do banco) |
| **Sem token / token inválido** (filtro de segurança) | `401` |
| **Token válido, sem permissão** (filtro de segurança) | `403` |
| Qualquer outra `Exception` | `500` (mensagem neutra) |

> Os casos `401`/`403` não passam pelo `GlobalExceptionHandler`: são gravados
> pelo `RestAuthenticationHandler` (via `SecurityFilterChain`) com o MESMO
> formato `ApiError`.

---

## 5. Guia Arquitetural — como a requisição viaja

```
                ┌──────────┐    JSON    ┌──────────────┐   chamada   ┌───────────────┐
  Cliente  ───▶ │ Controller│ ─────────▶ │   Service    │ ─────────▶ │  Repository   │
  (App/Web)     │ (Garçom)  │  {DTO de   │  (Chef de    │            │  (Estoquista) │
                └────▲──────┘  Request}  │   cozinha)   │            └──────┬────────┘
                     │                  └──────┬────────┘                   │
                     │          regras de      │                            │ JPQL/SQL
                     │          negócio        │                            ▼
                     │      (DTO → Entity)     │                     ┌──────────────┐
                     └─────────────────────────┼─────────────────────▶│   Banco      │
                     JSON (DTO de Response)    │                     │  PostgreSQL  │
                                               │                     └──────────────┘
```

### 5.1 Passo a passo de um `POST /api/posts`

1. **Filtro de segurança** (`JwtAuthenticationFilter`)
   - Lê `Authorization: Bearer <token>`; valida assinatura/expiração com o
     `JwtService` e coloca o `IdUsuarioLogado` no `SecurityContext`.
   - Token ausente/inválido → `401` (sem chegar ao Service).

2. **Controller** (`PostController.criarPost`)
   - `@AuthenticationPrincipal` injeta o `IdUsuarioLogado` (o **autor**).
   - Converte o JSON para `PostCreateRequestDTO`, `@Valid` valida (senão `400`).

3. **Service** (`PostService.criar`)
   - Busca o autor no `UserRepository` (404 se não existir).
   - **Valida regra de negócio** (recompensa FREE/PAID).
   - Cria a `PostEntity`, persiste via `PostRepository.saveAndFlush()`.
   - Converte a entidade em `PostResponseDTO` e devolve ao Controller.

4. **Controller**
   - Envolve em `ResponseEntity.status(201).location(...)` e devolve o JSON.

### 5.2 O que acontece com a Entidade JPA?

A entidade **nunca sai do backend**:

- A **entrada** é padrão pelo `DTO` (o cliente não preenche `id`/`status`/datas).
- A **saída** é padronizada pelo `DTO` (`PostResponseDTO`, `UserProfileResponseDTO`).

Isso é a essência do **Princípio da Responsabilidade Única (SRP)**:

| Camada | Responsabilidade única |
|--------|------------------------|
| Controller | **Ligar/montar** HTTP (status code, headers, JSON) |
| DTO | **Formato** de entrada/saída + validação de formulário |
| Service | **Regras de negócio** e orquestração |
| Entity | **Mapeamento** do banco (objeto relacional) |
| Repository | **Persistência** (SQL/JPQL) |

Cada uma faz UMA coisa; mudar uma não derruba as outras (ex.: mudar o banco
não muda a versão do JSON).

---

## 6. Stack e decisões

| Decisão | Motivo |
|---------|--------|
| Spring Boot 4.1.1 / Java 25 | Versões atuais com suporte ativo (OOS até 2027) |
| Injeção por construtor | Imutabilidade, testes fáceis, falha cedo |
| `record` para DTOs | Imutáveis, menos código, sem setter |
| `@Getter/@Setter` (sem `@Data`) | Evita `equals/hashCode` e `toString` perigosos em JPA |
| `@Enumerated(STRING)` | Valor legível no banco + `CHECK` na DDL |
| `PagedModel` | Padrão Spring para paginação estável |
| `open-in-view=false` | Evita sessão JPA aberta durante renderização (N+1 escondidos) |
| **Spring Security (stateless)** | Sem `JSESSIONID`: cada requisição se autentica pelo JWT |
| **BCrypt** | Hash de senha lento e com salt automático (padrão de mercado) |
| **jjwt 0.12.6** | Geração/validação de tokens (HS384) |
| **AWS SDK v2 (`s3`) + MinIO** | Protecol S3 é o padrão "de fábrica" de cloud storage: em dev usamos MinIO (S3-compatível, roda no Docker), e ir pra AWS/R2/Spaces depois é só trocar o endpoint — código não muda (`forcePathStyle` porque MinIO usa path-style) |

---

## 7. Roadmap

- [x] Cadastro de usuários (`POST /api/auth/register`) + login (`POST /api/auth/login`)
- [x] Autenticação JWT e substituição do `authorId` manual pelo usuário da sessão
- [x] Endpoints da "corrida" (`sessions`): aceitar socorro, máquina de estados,
      transferência de pontos e avaliações mútuas (`reviews`)
- [x] Chat em tempo real da sala (WebSocket/STOMP + histórico em `chat_messages`)
- [x] Refresh token / logout forçado (revogação)
- [x] Upload real de prints (MinIO local em dev, S3-compatível — troca de cloud sem trocar código) em vez de `mediaUrl` solta
- [x] Limite de tamanho do body no `POST /api/posts` (`MaxRequestBodySizeFilter`, 413 em `devsos.posts.max-body-bytes` = 64 KiB default)
- [x] Flyway para versionar a DDL junto do deploy (migrações em `backend/src/main/resources/db/migration/`, aplicadas no boot)
- [x] Integração com OpenAPI/Swagger (UI em `/swagger-ui`)

---

## 8. OpenAPI/Swagger (vitrine interativa da API)

O backend publica a spec **OpenAPI 3** e uma **UI interativa** (Swagger UI) —
um "console de teste" navegável da própria API, sem precisar de `curl`:

| O que | Onde |
|---|---|
| UI interativa | `GET http://localhost:8080/swagger-ui/index.html` |
| Spec JSON | `GET http://localhost:8080/v3/api-docs` |
| Spec YAML | `GET http://localhost:8080/v3/api-docs.yaml` |

### Como testar pela UI

1. Abra `/swagger-ui/index.html`.
2. Em `POST /api/auth/register` (ou `/login`) clique em **Try it out**, preencha
   o corpo e **Execute** — a resposta vem com `accessToken` + `refreshToken`.
3. Copie o `accessToken`, clique em **Authorize** (canto superior direito),
   cole como `Token` e feche — a partir daí todos os endpoints protegidos do
   "cadeado" usam esse JWT (não precisa copiar `Authorization: Bearer` na mão).
4. Endpoints sem cadeado (register/login/refresh, `GET` do feed e do perfil)
   são públicos no Swagger igual ao `SecurityConfig` — a cobrança de token na
   UI espelha a regra real.

### Config

Metadados (título/descrição) e o esquema `bearerAuth` ficam em
`backend/src/main/java/com/devsos/infrastructure/web/OpenApiConfig.java`
(dependência `springdoc-openapi-starter-webmvc-ui` v3, a linha com suporte ao
Spring Boot 4). Nada de anotação em cada rota: a exigência de JWT é GLOBAL na
spec e os endpoints públicos removem com `@SecurityRequirements(value = {})`
— espelhando o filtro do `SecurityConfig`.

> Os paths do Swagger são **públicos por conveniência de dev**. Para expor em
> produção, restrinja `/v3/api-docs/**` e `/swagger-ui/**` no `SecurityConfig`
> (ou atrás de autenticação).