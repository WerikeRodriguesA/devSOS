# devSOS — Documentação Técnica do Banco de Dados

> Público-alvo: desenvolvedores e DBA's do projeto.
> Versão do script: `database/schema.sql` · PostgreSQL 13+

---

## 1. Visão Geral

O `devSOS` é uma rede social em que devs publicam problemas de código no feed
(estilo Instagram) e especialistas "aceitam o socorro" (estilo Uber), iniciando
uma sala de chat para resolver o bug.

O banco de dados é **relacional** e possui 4 tabelas:

| Tabela     | Papel no domínio                                  |
|------------|---------------------------------------------------|
| `users`    | Todos os devs: quem pede ajuda e quem ajuda       |
| `posts`    | Os problemas publicados no feed                   |
| `sessions` | A "corrida": vincula um post a um helper + chat   |
| `reviews`  | Avaliação mútua (nota 1–5) após sessão concluída  |

**Fluxo do domínio:** `users` → publica `posts` → `sessions` vincula um helper
→ sessão termina → `reviews` avaliação → `users.media_avaliacoes` é recalculada.

---

## 2. Diagrama de Relacionamentos

```
users 1 ────< N posts 1 ────< 1 sessions >──── 1 users (helper)
                            1        │
                            │        └──── >──── N chat_messages (session_id)
                            │
                            └──── >──── 1 reviews (session_id)
          users <──── reviewers/reviewed (1:N reviews)
          users <──── chat_messages (sender_id 1:N)
```

Resumo das cardinalidades:

- Um **user** pode ter **N posts** (`posts.author_id → users.id`).
- Um **post** pode ter **N sessions** ao longo da vida (embora apenas uma
  session `ACTIVE` deva existir por vez — regra de aplicação).
- Uma **session** pertence a **1 post** e a **1 helper** (um user).
- Uma **session** pode gerar até **2 reviews** (autor ↔ helper), garantido pela
  UNIQUE em `(session_id, reviewer_id)`.

---

## 3. Dicionário de Dados

### 3.1 Tabela `users`

Cadastro de todos os usuários (quem pede ajuda e quem presta ajuda).

| Coluna                  | Tipo             | Nulo  | Default             | Restrições / Observações                                    |
|-------------------------|------------------|-------|---------------------|-------------------------------------------------------------|
| `id`                    | `UUID`           | Não   | `gen_random_uuid()` | Chave primária                                              |
| `nome`                  | `VARCHAR(120)`   | Não   | —                   | Nome de exibição                                            |
| `email`                 | `VARCHAR(255)`   | Não   | —                   | **UNIQUE** — usado em login                                 |
| `bio`                   | `TEXT`           | Não   | `''`                | Autodescrição do dev                                        |
| `github_username`       | `VARCHAR(60)`    | Não   | `''`                | **UNIQUE** — sem o `@`                                      |
| `avatar_url`            | `TEXT`           | Não   | `''`                | URL do avatar                                               |
| `saldo_pontos`          | `INTEGER`        | Não   | `0`                 | **CHECK ≥ 0** — pontos ganhos ao ajudar                     |
| `media_avaliacoes`      | `NUMERIC(3,2)`   | Não   | `0`                 | **CHECK 0–5** — recalculado por trigger nas reviews          |
| `tecnologias_dominadas` | `TEXT[]`         | Não   | `ARRAY[]::TEXT[]`   | Array padrão do PostgreSQL; sem `NOT NULL` de elementos      |
| `created_at`            | `TIMESTAMPTZ`    | Não   | `now()`             | UTC                                                         |
| `updated_at`            | `TIMESTAMPTZ`    | Não   | `now()`             | Atualizado automaticamente pelo trigger `trg_users_touch`   |

**Constraints definidas:**

- `users_email_uniq` (UNIQUE): impede emails duplicados.
- `users_github_uniq` (UNIQUE): impede github duplicado.
- `users_saldo_check` (CHECK): saldo de pontos nunca negativo.
- `users_avaliacao_chk` (CHECK): média de avaliações dentro de 0–5.

---

### 3.2 Tabela `posts`

Os problemas publicados no feed.

| Coluna             | Tipo             | Nulo   | Default           | Restrições / Observações                               |
|--------------------|------------------|--------|-------------------|--------------------------------------------------------|
| `id`               | `UUID`           | Não    | `gen_random_uuid()` | Chave primária                                         |
| `author_id`        | `UUID`           | Não    | —                 | **FK → `users.id`** com `ON DELETE CASCADE`            |
| `titulo`           | `VARCHAR(160)`   | Não    | —                 | Título curto do problema                               |
| `descricao`        | `TEXT`           | Não    | —                 | Detalhes do bug, stack trace, etc.                     |
| `media_url`        | `TEXT`           | Não    | `''`              | URL de print/imagem do erro                            |
| `tags`             | `TEXT[]`         | Não    | `ARRAY[]::TEXT[]` | Ex.: `['java','spring','postgres']`                    |
| `tipo`             | `TEXT`           | Não    | `'FREE'`          | **CHECK `IN ('FREE','PAID')`**                         |
| `recompensa_valor` | `NUMERIC(10,2)`  | Não    | `0`               | **CHECK ≥ 0** e regra `posts_clean_reward`             |
| `status`           | `TEXT`           | Não    | `'OPEN'`          | **CHECK `IN ('OPEN','IN_PROGRESS','RESOLVED','CANCELLED')`** |
| `created_at`       | `TIMESTAMPTZ`    | Não    | `now()`           | UTC                                                    |
| `updated_at`       | `TIMESTAMPTZ`    | Não    | `now()`           | Trigger `trg_posts_touch`                              |

**Constraints definidas:**

- `posts_tipo_check`: `tipo` só pode ser `FREE` ou `PAID`.
- `posts_status_check`: `status` segue o ciclo de vida do socorro.
- `posts_recompensa_chk`: recompensa nunca negativa.
- `posts_clean_reward`: **regra de negócio** — post `FREE` só pode ter
  recompensa `0`; post `PAID` obrigatoriamente precisa de recompensa `> 0`.

---

### 3.3 Tabela `sessions` (a "corrida" / chamada)

Vincula um `post` a um `helper` e guarda a sala de chat.

| Coluna          | Tipo            | Nulo   | Default           | Restrições / Observações                              |
|-----------------|-----------------|--------|-------------------|--------------------------------------------------------|
| `id`            | `UUID`          | Não    | `gen_random_uuid()`| Chave primária                                        |
| `post_id`       | `UUID`          | Não    | —                 | **FK → `posts.id`** com `ON DELETE CASCADE`           |
| `helper_id`     | `UUID`          | Não    | —                 | **FK → `users.id`** com `ON DELETE CASCADE`           |
| `status`        | `TEXT`          | Não    | `'MATCHED'`       | **CHECK `IN ('MATCHED','ACTIVE','COMPLETED','CANCELLED')`** |
| `chat_room_id`  | `UUID`          | Não    | `gen_random_uuid()`| Sala do chat em tempo real (redireciona para `chat_messages`) |
| `created_at`    | `TIMESTAMPTZ`   | Não    | `now()`           | UTC                                                   |
| `updated_at`    | `TIMESTAMPTZ`   | Não    | `now()`           | Trigger `trg_sessions_touch`                          |
| `completed_at`  | `TIMESTAMPTZ`   | Sim    | —                 | Populado quando `status = 'COMPLETED'`                |

**Regras aplicadas:**

- `sessions_status_check`: ciclo de vida da corrida.
- **Trigger `trg_sessions_no_self_help`**: impede que o autor do post aceite o
  próprio socorro (`helper_id = posts.author_id` → erro `23514`). Isso é feito
  em `TRIGGER` (não `CHECK`) porque a regra depende de outra tabela (`posts`),
  e `CHECK` no PostgreSQL **só pode validar a própria linha**.
- Dois helpers nunca "pegam" o mesmo post graças a um **único post só pode ter
  uma sessão `MATCHED`/`ACTIVE` ativa** — garantido no nível de aplicação com
  `SELECT ... FOR UPDATE` (ver seção 6).

  **Dica arquitetural:** para garantir isso no próprio banco, adicione um
  índice **parcialmente único**:

  ```sql
  CREATE UNIQUE INDEX uniq_sessions_post_active
      ON sessions (post_id)
      WHERE status IN ('MATCHED', 'ACTIVE');
  ```

  See why we did not include it by default in seção 5.2.

---

### 3.4 Tabela `reviews`

Avaliação mútua após uma sessão concluída.

| Coluna        | Tipo        | Nulo | Default           | Restrições / Observações                              |
|---------------|-------------|------|-------------------|--------------------------------------------------------|
| `id`          | `UUID`      | Não  | `gen_random_uuid()` | Chave primária                                       |
| `session_id`  | `UUID`      | Não  | —                 | **FK → `sessions.id`** com `ON DELETE CASCADE`        |
| `reviewer_id` | `UUID`      | Não  | —                 | **FK → `users.id`** com `ON DELETE CASCADE` (quem dá) |
| `reviewed_id` | `UUID`      | Não  | —                 | **FK → `users.id`** com `ON DELETE CASCADE` (quem recebe) |
| `nota`        | `SMALLINT`  | Não  | —                 | **CHECK `BETWEEN 1 AND 5`**                           |
| `comentario`  | `TEXT`      | Não  | `''`              | Comentário opcional (`''` = vazio)                    |
| `created_at`  | `TIMESTAMPTZ`| Não | `now()`           | UTC                                                   |

**Constraints definidas:**

- `reviews_um_por_sessao` (UNIQUE `(session_id, reviewer_id)`): cada usuário só
  avalia **uma vez** por sessão.
- `reviews_avaliador_diff` (CHECK): ninguém se auto-avalia.
- **Trigger `trg_reviews_rating`**: recalcula automaticamente
  `users.media_avaliacoes` do avaliado **em INSERT, UPDATE e DELETE** (V4 —
  issue #20). A função `fn_recalc_user_rating` é sensível ao `TG_OP`: usa
  `NEW.reviewed_id` quando a linha nasce/é alterada e `OLD.reviewed_id` quando
  é apagada (na V1 só rodava em INSERT).

---

### 3.5 Tabela `chat_messages` (mensagens da sala do chat)

Persiste o que foi dito na sala de chat de uma corrida (o chat em tempo real
roda por WebSocket/STOMP, mas todo envio é gravado aqui — o "histórico").

| Coluna         | Tipo            | Nulo   | Default           | Restrições / Observações                        |
|----------------|-----------------|--------|-------------------|--------------------------------------------------|
| `id`           | `UUID`          | Não    | `gen_random_uuid()` | Chave primária                                  |
| `session_id`   | `UUID`          | Não    | —                 | **FK → `sessions.id`** com `ON DELETE CASCADE` — é por ela que autorizamos quem lê o histórico (participantes) |
| `chat_room_id` | `UUID`          | Não    | —                 | Sala usada pelo WebSocket em `/topic/chat/{chat_room_id}` (denormalizada para busca rápida) |
| `sender_id`    | `UUID`          | Não    | —                 | **FK → `users.id`** com `ON DELETE CASCADE` (quem escreveu) |
| `tipo`         | `TEXT`          | Não    | `'CHAT'`          | **CHECK `IN ('CHAT','CODE_SNIPPET','JOIN','LEAVE')`** |
| `conteudo`     | `TEXT`          | Não    | `''`              | Texto digitado / trecho de código                |
| `created_at`   | `TIMESTAMPTZ`   | Não    | `now()`           | UTC — ordem do histórico por esta coluna         |

**Índices:**

- `idx_chat_room` (`chat_room_id, created_at`): histórico da sala em ordem.
- `idx_chat_session` (`session_id, created_at`): consultas por corrida.

**Regra de autorização (aplicação):** `sender_id` nunca vem do cliente — o
servidor preenche com o usuário do JWT da conexão, e só `author`/`helper` da
sessão conseguem enviar ou ler.

### 3.6 Tabela `refresh_tokens` (revogação / logout forçado)

Criada na migração `V2__refresh_tokens.sql`. Suporta a renovação do access
JWT sem pedir senha de novo e, principalmente, a **revogação** (logout /
logout forçado) — algo que um JWT stateless sozinho não permite.

| Coluna         | Tipo            | Nulo   | Default           | Restrições / Observações                        |
|----------------|-----------------|--------|-------------------|--------------------------------------------------|
| `id`           | `UUID`          | Não    | `gen_random_uuid()` | Chave primária                                  |
| `user_id`      | `UUID`          | Não    | —                 | **FK → `users.id`** com `ON DELETE CASCADE` (dono do token) |
| `token_hash`   | `CHAR(64)`      | Não    | —                 | **SHA-256 hex** do token cru — o valor cru NUNCA vai para o banco; índice **UNIQUE** para lookup O(1) |
| `created_at`   | `TIMESTAMPTZ`   | Não    | `now()`           | UTC — emissão do token |
| `expires_at`   | `TIMESTAMPTZ`   | Não    | —                 | TTL (padrão 7 dias, `devsos.jwt.refresh-expiracao-segundos`) |
| `revoked_at`   | `TIMESTAMPTZ`   | —      | —                 | Quando preenchido ⇒ token inutilizável (revogado) |
| `replaced_by`  | `UUID`          | —      | —                 | Rastro da **rotação**: id do token que o substituiu no `/refresh` |

**Índices:**

- `uq_refresh_tokens_hash` (`token_hash`): lookup único por token.
- `idx_refresh_tokens_user` (`user_id, created_at DESC`): revogar tudo de um
  usuário / auditoria.

**Regras de negócio (serviço `RefreshTokenService`):**

- Cada `/refresh` **rotaciona**: o token usado é revogado e nasce um novo.
- Reuso de token já revogado = suspeita de roubo ⇒ revoga a família inteira
  do usuário (é o "logout forçado" automático).
- `POST /api/auth/logout` sem body revoga TODOS os tokens do usuário do JWT;
  com `{"refreshToken": ...}` revoga só aquele dispositivo.

---

## 4. Tipos de Dados — Justificativa

| Tipo            | Onde usado        | Por quê                                                                 |
|-----------------|-------------------|--------------------------------------------------------------------------|
| `UUID`          | Todas as PKs/FKs  | Geração distribuída, impossibilidade de adivinhação, sem "ordenamento" por ID. |
| `TEXT[]`        | `tags`, `tecnologias_dominadas` | Arrays nativos = 1 tabela a menos; combinados com índice `GIN`. |
| `NUMERIC(10,2)` | `recompensa_valor` | Dinheiro: precisão exata (nunca usar `FLOAT` para dinheiro).              |
| `INTEGER`       | `saldo_pontos`    | Pontos são contagens inteiras.                                            |
| `SMALLINT`      | `reviews.nota`    | 1–5 cabe em 2 bytes = menor custo de armazenamento.                       |
| `TIMESTAMPTZ`   | Criado/atualizado | Fuso horário embutido; sempre armazenado em UTC.                         |

---

## 5. Decisões Arquiteturais

### 5.1 Por que `UUID` em vez de `SERIAL`/`BIGSERIAL`?

1. **Segurança/privacidade:** com `SERIAL`, o post `#7` vaza o tamanho da base
   e permite "chutes" de IDs (um `POST /posts/123` provável de existir).
   O `UUID` (128 bits aleatórios) é impossível de adivinhar.
2. **Distribuição futura:** se o app crescer, split de bancos (sharding) ou
   migração para multi-região é viável porque cada registro já nasce com um ID
   globalmente único, sem depender de uma sequência central.
3. **Segurança em APIs:** é comum expor o ID em URLs (ex.: `POST /posts/:id`).
   Com UUID, não há enumeração de recursos.

**Custo:** UUIDs consomem 16 bytes (vs 8 do `BIGINT`) e índices em UUIDs
aleatórios têm mais "page splits". Para um MVP é perfeitamente aceitável;
padrão recente da indústria (~2024+), inclusive o novo UUIDv7 combina
ordenabilidade com aleatoriedade se a performance virar problema.

> **Sugestão futura:** UUIDv7 (ordenável) surge como padrão para placeholders
> onde a ordenação importa. Para o MVP, `gen_random_uuid()` (UUIDv4) resolve.

### 5.2 Estratégia de índices — feed otimizado

O feed é a tela mais quente do app. Os índices foram pensados para os padrões
típicos de consulta:

| Índice                     | Consulta que otimiza                                                       |
|----------------------------|----------------------------------------------------------------------------|
| `idx_posts_feed`           | Feed principal: `WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 20`. |
| `idx_posts_author`         | Perfil do usuário: "posts que eu publiquei".                               |
| `idx_posts_tags` (GIN)     | "Praça de ajuda" filtrando por tags (`WHERE tags && ARRAY['java']`).        |
| `idx_posts_recompensa` (parcial) | Ranking de posts pagantes (`WHERE tipo='PAID' ORDER BY recompensa DESC`). |
| `idx_posts_status_tipo`    | Telas de "meus posts ativos", filtros combinados.                          |
| `idx_sessions_fila` (parcial) | Fila de "socorros disponíveis" — só registros `MATCHED`.                 |
| `idx_users_tecnologias` (GIN) | Busca "especialistas em Spring/PostgreSQL" por tecnologia.               |

**Por que índices parciais (`WHERE`)?** Eles são menores e mais rápidos que
índices completos porque indexam apenas os registros relevantes. Ex.: a fila de
`sessions` só precisa indexar linhas `MATCHED`.

**Por que `GIN`?** O índice `GIN` é o adequado para colunas de array no
PostgreSQL — permite operações como "contém", "sobrepõe" e "contém todos".

### 5.3 Por que `TEXT` + `CHECK` em vez de tipo `ENUM`?

1. `ENUM` do PostgreSQL torna **caro/de moroso adicionar novo valor** (ex.:
   novo `status` exige `ALTER TYPE` + rewrite da tabela).
2. `TEXT + CHECK` é flexível para o MVP: mudar a lista de valores = recriar a
   constraint.
3. Tratamento em Java é idêntico (`String`).

> **Trade-off:** um `ENUM` armazena em 4 bytes e tem tipagem mais estrita.
> Para valores que se estabilizam (ex.: tipo de pagamento), ENUM pode valer a pena.

### 5.4 Estratégia de `ON DELETE`

| FK                   | Regra          | Justificativa                                                     |
|----------------------|----------------|--------------------------------------------------------------------|
| `posts.author_id`    | `CASCADE`      | Apagar o user apaga os posts dele (perfil cancelado).              |
| `sessions.post_id`   | `CASCADE`      | Post removido ⇒ corridas daquele post somem (histórico de chat?).   |
| `sessions.helper_id` | `CASCADE`      | Helper removido ⇒ sessões dele apagadas.                           |
| `reviews.*`          | `CASCADE`      | Consistência: review órfã não faz sentido.                         |

> **Atenção para o produto:** `ON DELETE CASCADE` em `sessions` apaga o
> histórico de chats e reviews de uma sessão quando um post é excluído. Se
> forem dados auditáveis, use `ON DELETE SET NULL` (com colunas `FK` anuláveis)
> ou soft-delete (`deleted_at`). Recomendo avaliar **soft-delete** em `posts`
> na primeira iteração de produto.

### 5.5 Triggers — o que elas garantem

1. **`fn_touch_updated_at`**: `updated_at` sempre reflete a última modificação;
   a aplicação não precisa lembrar de setá-lo — evita bugs de "stale cache".
2. **`fn_no_self_help`**: regra de negócio crítica (helper ≠ autor) garantida
   **no banco**, não apenas na UI.
3. **`fn_recalc_user_rating`**: `media_avaliacoes` sempre consistente; a leitura
   do perfil não precisa de `AVG` a cada request.

### 5.6 Sobre o índice único para "um helper por post"

A garantia de que **dois especialistas não pegam o mesmo post** é em camadas
(decidida na issue #13 — ver seção 6):

1. **Aplicação (camada principal):** o aceite lê o post com **lock pessimista**
   (`SELECT ... FOR UPDATE` via `SessionService.aceitarSocorro`) — o 2º helper
   espera o 1º e relê o post `IN_PROGRESS` → ganha `400` amigável.
2. **Banco (blindagem extra):** o índice único parcial — garantia matemática no
   PostgreSQL para o que escapar do service:

```sql
CREATE UNIQUE INDEX uniq_sessions_post_active
    ON sessions (post_id)
    WHERE status IN ('MATCHED', 'ACTIVE');
```

> O índice está ativo na V1. A política "1 helper por post" foi fechada no
> MVP; se um dia a regra mudar (múltiplos helpers), o índice deve ser
> removido junto com a regra do service. Detalhes na seção 6.

---

## 6. Concorrência — o "Duplo Aceite" (dois helpers, um post) — issue #13

O cenário perigoso: dois especialistas clicam "Aceitar Socorro" no MESMO
milissegundo. A estratégia definitiva é **em camadas**, decidida na #13:

### 6.1 Barreira 1 — Lock pessimista (OPÇÃO B, escolhida)

O caminho do aceite (`SessionService.aceitarSocorro`) busca o post com
`@Lock(LockModeType.PESSIMISTIC_WRITE)` (`findByIdComLock` no repositório),
que vira `SELECT ... FOR UPDATE`:

```sql
BEGIN;                                  -- TX do helper A
SELECT ... FROM posts WHERE id = $1 FOR UPDATE;  -- A trava a linha do post
-- checagens: status OPEN, helper ≠ autor, sem corrida MATCHED/ACTIVE
INSERT sessions (status 'MATCHED'); UPDATE posts SET status='IN_PROGRESS';
COMMIT;                                 -- libera a linha
```

O helper B que chegou atrasado fica **bloqueado no SELECT FOR UPDATE**
(espera o A terminar). Ao reler, vê o post já `IN_PROGRESS` → o Service
responde `400` amigável ("Este post já está em atendimento..."). O "perdedor"
descobre a derrota **na leitura**, com uma mensagem clara — não precisa
interpretar um erro de constraint.

**Trade-off escolhido (Opção B vs Opção A):**
- **A favor do lock** (escolhido): serializa as transações — o comportamento
  é deterministico (o 2º vê o post mudado e ganha 400), a mensagem é amigável
  e é fácil de raciocinar (uma única leitura travada).
- **Custo**: toda chamada de aceite segura um lock de linha de post na
  transação. No volume do MVP é desprezível; se o app escalar muito, dá para
  trocar por `SKIP LOCKED` ou pela Opção A pura (índice único) + mensagem 409.

### 6.2 Barreira 2 e 3 — Blindagem no banco (cinto de segurança)

Mesmo que tudo acima falhe (ex.: acesso direto ao banco), a DDL ainda impede:

```sql
CREATE UNIQUE INDEX uniq_sessions_post_active
    ON sessions (post_id)
    WHERE status IN ('MATCHED', 'ACTIVE');

-- trigger trg_sessions_no_self_help: veta "aceitar o próprio post"
```

A 2ª sessão ativa no mesmo post falha com `23505 (unique_violation)`; o
`GlobalExceptionHandler` traduz para **409** com mensagem neutra. Na prática,
com o lock da 6.1 o 409 virou raridade (só se algo escapar do service).

### 6.3 Prova com estresse automatizado

`backend/src/test/java/.../DuploAceiteConcorrenciaTest.java` (perfil `test`,
banco separado `devsos_test`):

- Cria 1 post OPEN + 8 helpers e dispara o aceite dos 8 ao MESMO tempo
  (`CountDownLatch`), cada um na sua thread/transação;
- Asserts: **exatamente 1 vencedor**; os 7 restantes retornam `400` (regra)
  ou `409` (índice); zero "outros" erros;
- Saída real da rodada: `vencedores=1 400(regra/lock)=7 409(indice)=0 outros=0`.

Rodar: `mvn test -Dtest=DuploAceiteConcorrenciaTest` (com o Postgres de pé e
o banco `devsos_test` criado via `CREATE DATABASE devsos_test`).

---

## 7. Como a DDL versionada é aplicada

Desde a PR do Flyway, o banco **não é mais criado na mão**: o backend roda as
migrações sozinho no boot. A única coisa que você precisa é um PostgreSQL com
o banco de dados criado (vazio):

```bash
docker run -d --name devsos-db \
  -e POSTGRES_PASSWORD=devsos \
  -e POSTGRES_DB=devsos \
  -p 5433:5432 postgres:16
```

No primeiro boot o Flyway cria a tabela `flyway_schema_history` e aplica todas
as migrações de `backend/src/main/resources/db/migration/` (hoje apenas o
`V1__schema_inicial.sql`, que contém o schema completo).

### Banco de dev antigo (já tinha tabelas antes do Flyway)

O `devsos-db` local foi criado aplicando o `schema.sql` à mão, então o app sobe
com `spring.flyway.baseline-on-migrate=true` e `baseline-version=1`: o Flyway
valida o schema existente contra o V1, registra esse *baseline* na versão 1 e
**não re-executa nada** — só as migrações futuras (V2 em diante) rodam. Os
dados existentes permanecem intactos.

> **Regra daqui pra frente:** qualquer mudança de schema = nova migração
> `V{n+1}__descricao.sql` em `backend/src/main/resources/db/migration/`.
> Atualize também `database/schema.sql` (snapshot documental do estado final)
> e este arquivo.

### Verificação

```sql
\dt+                            -- lista tabelas e tamanhos
\d users                        -- detalha a estrutura de uma tabela
SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank;
```

---

## 8. Roadmap / Pendências (MVP)

- [ ] Decidir política de múltiplos helpers ⇒ ativar `uniq_sessions_post_active`.
- [ ] Decidir entre `ON DELETE CASCADE` e **soft-delete** para `posts`.
- [x] Documentação de API (`docs/API.md`) expondo os endpoints REST sobre este schema.
- [ ] Avaliar migração para UUIDv7 se a escrita no feed for intensa.
- [x] Tabela `chat_messages` (histórico das salas de chat) — criada em `v4_chat_messages.sql`.
- [x] DDL versionada via Flyway (`spring-boot-flyway` + `db/migration/`, aplicadas no boot).
- [x] Tabela `refresh_tokens` (V2) — rotação de refresh token + revogação/logout forçado.

---

*Documentação gerada em conjunto com o `database/schema.sql`. Qualquer mudança
de schema deve atualizar este arquivo.*