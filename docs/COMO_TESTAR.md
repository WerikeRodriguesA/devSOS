# DevSOS — Como testar

Guia prático de uso e teste do DevSOS, do "tudo desligado" até um fluxo
completo (cadastrar → publicar → aceitar socorro → conversar → avaliar).

> **Resumo de 10 segundos:** rode `.env\devsos-up.ps1`, abra
> **http://localhost:5173** no navegador e use as abas na ordem
> Autenticação → Feed → Corridas → Chat → Avaliações. O painel **Requisições**
> (à direita) mostra cada chamada que o Dev Client faz — é o "ônibus" do teste.

---

## 1. Subir o ambiente (uma vez)

O script local `.env\devsos-up.ps1` sobe **banco + backend + Web Client**
(ou só o que faltar). Não vai para o GitHub — é exclusivo da sua máquina.

```powershell
# na raiz do repositório
.env\devsos-up.ps1            # sobe tudo
.env\devsos-up.ps1 -Rebuild   # recompila o JAR do backend antes de subir
.env\devsos-up.ps1 -Stop      # derruba backend + Web Client + Expo
```

Ao final ele imprime um resumo com as portas. Você deve ver **três serviços de pé**:

| Serviço | Endereço | Para quê |
|---|---|---|
| **Backend (API)** | `http://localhost:8080` | as regras de negócio (REST + chat) |
| **Web Client (teste)** | `http://localhost:5173` | **a forma mais rápida de testar tudo** |
| Mobile (Expo/Metro) | `http://localhost:8081` | o app React Native (terceiro passo) |

> **Subiu tudo no primeiro run?** O script compila o backend (`mvn package`) e
> instala as dependências do Web Client (`npm install`) se ainda não existirem —
> os dois demoram alguns minutos só na primeira vez.
>
> **Janela do Web Client não abriu?** Rode num terminal separado:
> `cd dev-client; npm run dev` — o Vite abre em `http://localhost:5173`.

### Só o backend não subiu?

Se alguma porta ficar de fora, o script loga em `.env\logs\backend.out.log` /
`.env\logs\backend.err.log`. As causas mais comuns:

- **Porta 8080 já ocupada** → outro backend rodando; rode `.env\devsos-up.ps1 -Stop` e suba de novo.
- **Docker parado** → o Postgres (`devsos-db`, porta 5433) não levanta; inicie o Docker Desktop.
- **JAR travado no Windows** → backend rodando segura o `.jar` no rebuild; `-Stop` resolve (ou `-Rebuild` que já faz isso).

---

## 2. Testar pelo Swagger UI (console interativo)

Sem instalar nada, a própria API entrega uma UI de teste em:

**http://localhost:8080/swagger-ui/index.html**

1. Abra o link — você verá todos os endpoints do backend (register/login,
   posts, corridas, avaliações) com o botão **Try it out**.
2. Em `POST /api/auth/register` preencha um corpo (nome, e-mail, senha **mín.
   8** e githubUsername) e **Execute**. A resposta **201** traz `accessToken`
   e `refreshToken`.
3. Clique em **Authorize** no topo, cole o `accessToken` e feche.
4. Agora testa os endpoints do "cadeado" — por exemplo `POST /api/posts` ou
   `POST /api/sessions` — sem precisar copiar cabeçalho nenhum.

> O cadastro/login/refresh e os `GET` de feed/perfil aparecem **sem** cadeado
> (são públicos de verdade). O Swagger espelha a regra do backend.

---

## 3. Testar pelo Web Client (recomendado)

No navegador, **http://localhost:5173**. O cabeçalho mostra a URL do backend
(`http://localhost:8080`, com botão **Aplicar** para trocar) e o estado da
sessão. À direita, o painel **Requisições** registra **todas** as chamadas
(método, URL e status) — use-o para conferir que cada ação bateu certo.

### Aba 1 — Autenticação

1. **Cadastro**: nome, e-mail, senha (**mín. 8**) e GitHub (opcional) → **Criar
   conta**. O JWT já vem na resposta, então você fica logado na hora. O badge
   do cabeçalho troca para `logado: Seu Nome`.
2. **Login**: `carlos@dev.com` / `senha12345` (usuário que já existe) ou a
   conta que você acabou de criar.
3. **Sair** (botão no cabeçalho ou na aba): limpa o token. A partir daqui,
   criar post / aceitar socorro retorna **401** — teste o 401 e faça login
   de novo para continuar.
4. **Buscar perfil**: mostra seus pontos, média de avaliações e GitHub.

### Aba 2 — Feed (publicações de ajuda)

1. Crie um post: título (3–160), tipo `FREE` ou `PAID`, recompensa, tags e
   descrição (10–5000). **Publicar** → o card aparece no feed (201 no log).
2. **Recarregue** o feed para ver posts de outros usuários.
3. No card de um post **que não é seu**, clique **Aceitar socorro** →
   `POST /api/sessions { postId }` cria a corrida e o próprio card mostra o
   retorno (sessão + `chatRoomId`) com o botão **Abrir chat**.

> Regra de negócio para testar: o **autor do post não pode aceitar o próprio
> socorro** (o banco barra via trigger). Use DUAS contas/abas para o fluxo
> completo — por exemplo, crie o post numa aba e logue com outra para aceitar.

### Aba 3 — Corridas (estado de uma ajuda)

1. **Minhas corridas** lista as suas como autor OU helper (para ver os dois
   papéis, use as DUAS contas — uma publica, a outra aceita).
2. Avance a máquina de estados nos botões do card:
   - `MATCHED` → **ACTIVE** (transfere pontos/comptetência) ou **Cancelar**.
   - `ACTIVE` → **COMPLETED** (só o **helper** pode concluir) ou **Cancelar**.
   - `COMPLETED` → **Avaliar corrida** (leva para a aba Avaliações).
3. **Detalhar (GET)** mostra o JSON completo da corrida.

### Aba 4 — Chat (tempo real + histórico)

O chat é **por sala** (`chatRoomId`). Para abrir uma sala do zero, preencha o
`sessionId` e o `chatRoomId` (ambos aparecem na corrida/aceite) e clique
**Abrir sala**:

1. Carrega o **histórico** via `GET /api/sessions/{id}/messages`.
2. Conecta o **WebSocket** (badge "WebSocket CONECTADO").
3. Digite e envie (ou `Ctrl+Enter`) — mensagens novas caem **em tempo real**
   na bolha, do seu lado (direita).
4. Alterne o tipo para `CODE_SNIPPET` e envie um trecho de código — aparece
   formatado como `<code>`.

> Para ver o tempo real, faça login na MESMA sala com o OUTRO usuário (janela
> anônima ou outra aba) — as mensagens de um aparecem no outro sem recarregar.

### Aba 5 — Avaliações (reputação)

1. **Avaliar corrida concluída**: clique **trazer corridas concluídas** → use
   uma corrida COMPLETED → nota 1–5 + comentário → **Avaliar**. A resposta
   mostra a nova média do avaliado.
2. **Avaliações que EU recebi** mostra o que os outros te avaliaram.
3. **Editar a MINHA avaliação** (issue #20): no histórico recebido (ou no
   endpoint `PATCH /api/reviews/{id}`), mude nota/comentário — a média do
   avaliado reajusta na hora.
4. **Apagar a MINHA avaliação** (`DELETE /api/reviews/{id}`) — 204 e a média
   do avaliado reajusta.
5. **Proteção de dono**: tente editar/apagar a avaliação de OUTRO dev →
   `400` "Você só pode editar/apagar a sua própria avaliação."

### Checklist rápido de regras (bom para dar aula/validar)

| Ação | Esperado no painel de Requisições |
|---|---|
| Cadastro com e-mail repetido | `400` com `fieldErrors`/mensagem |
| Login errado | `400` "E-mail ou senha inválidos" |
| Publicar sem estar logado | `401` |
| Post sem descrição (min 10) | `400` com `fieldErrors` |
| Aceitar o PRÓPRIO post | `400` (trigger do banco) |
| Aceitar post já com corrida ativa | `400` (índice único) |
| Concluir corrida sendo o autor | `400` (só o helper conclui) |
| Editar/apagar avaliação de OUTRO dev | `400` "Você só pode editar/apagar a sua própria avaliação." |
| Abrir chat de corrida que não é sua | erro do WebSocket `Você não participa…` |
| Corpo acima de 64 KiB no POST /api/posts | `413` Payload Too Large |

---

## 4. Testar pelo app mobile (Expo)

Depois de validar no Web Client, teste no celular:

1. Garanta o IP do backend em `mobile/src/config.js` (o script sugere o valor
   ao subir). **Nunca use `localhost`** — no celular isso é o próprio celular.
   Veja `mobile/docs/TROUBLESHOOTING_REDE.md`.
2. Com o `devsos-up.ps1` de pé, a janela do Expo mostra um **QR Code**; no
   celular (mesma rede Wi-Fi), instale o **Expo Go** e escaneie.
3. Fluxo esperado: Login → Feed → "+ Postar" → com outra conta "Aceitar
   Socorro" → chat da sala (histórico + tempo real + envio de código).

---

## 5. Testar a API direto (curl / PowerShell)

Para conferir um endpoint sem abrir o navegador:

```powershell
# register
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register `
  -ContentType 'application/json' `
  -Body '{"nome":"Teste","email":"t1@dev.com","senha":"senha123","githubUsername":"t1"}'

# login → pega o token
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType 'application/json' -Body '{"email":"t1@dev.com","senha":"senha123"}'
$r.token        # o JWT

# chamada autenticada (autor vindo do token, não do corpo)
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/posts `
  -Headers @{Authorization="Bearer $($r.token)"} `
  -ContentType 'application/json' `
  -Body '{"titulo":"Bug CORS","descricao":"Fica 401 quando chamo do front?","tags":["spring"],"tipo":"FREE","recompensaValor":0,"mediaUrl":""}'
```

Toda a documentação de rotas/JSONs está em [`docs/API.md`](API.md).

---

## 6. Endpoint por aba (mapa rápido)

| Aba do Dev Client | Endpoint(s) que ela exercita |
|---|---|
| Autenticação | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/users/{id}` |
| Feed | `GET /api/posts` (feed), `POST /api/posts`, `POST /api/sessions` (aceitar) |
| Corridas | `GET /api/sessions`, `GET /api/sessions/{id}`, `PATCH /api/sessions/{id}/status` |
| Chat | `GET /api/sessions/{id}/messages` + WebSocket `/ws-devsos`/`/topic/chat/{room}` `/app/chat/{room}` |
| Avaliações | `POST /api/reviews`, `GET /api/reviews` |