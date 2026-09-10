# DevSOS — API do Backend (Perfis, Feed e Autenticação)

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
  }
}
```

Erros: `400` (validação de formulário), `400` (e-mail já cadastrado).

---

### 2.2 `POST /api/auth/login` — Entrar (código 200)

Corpo de envio:

```json
{ "email": "bruna@dev.com", "senha": "senha12345" }
```

Resposta — `200 OK`: mesma estrutura do `register` (token + expiração + perfil).

Erros: `400` "E-mail ou senha inválidos." (credenciais erradas — o atacante não
descobre se o e-mail existe sozinho), `400` (validação).

> **Como usar o token:** inclua em toda requisição protegida o cabeçalho
> `Authorization: Bearer <token>`.

---

### 2.3 `GET /api/users/{id}` — Buscar perfil público (aberto)

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

### 2.4 `PATCH /api/users/{id}/technologies` — Atualizar tecnologias dominadas

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

### 2.5 `GET /api/posts` — Listar feed paginado (posts `OPEN`, aberto)

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

### 2.6 `POST /api/posts` — Criar publicação (código 201)

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
| `mediaUrl` | `string` | Não | Deve começar com `http(s)://` |
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
| `401` | Sem token (ou token inválido/expirado) |
| `400` | Validação de formulário (campo ausente/curto) |
| `400` | Regra de negócio (`FREE` com recompensa, `PAID` sem recompensa) |
| `404` | Usuário autenticado não existe mais |

---

## 3. Como rodar

### Pré-requisitos
- Java 25
- PostgreSQL 16 (container: `devsos-db` na porta `5433`) — veja `database/schema.sql`

```bash
# dentro de backend/
mvn spring-boot:run          # ou: ./mvnw spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

> O banco usa `spring.jpa.hibernate.ddl-auto=none`: **a DDL não é gerada pela
> aplicação** — rode primeiro o `database/schema.sql` e, se estiver subindo
> sobre um banco antigo, aplique também `database/migrations/v2_auth_password_hash.sql`.

### Autenticação local

| Propriedade | Default (dev) | Observação |
|-------------|---------------|------------|
| `devsos.jwt.secret` | chave fixa de dev | em produção, defina `DEV_SOS_JWT_SECRET` |
| `devsos.jwt.expiracao-segundos` | `3600` (1h) | `DEV_SOS_JWT_EXPIRACAO` para sobrescrever |

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

---

## 7. Roadmap

- [x] Cadastro de usuários (`POST /api/auth/register`) + login (`POST /api/auth/login`)
- [x] Autenticação JWT e substituição do `authorId` manual pelo usuário da sessão
- [ ] Refresh token / logout forçado (revogação)
- [ ] Endpoints da "corrida" (`sessions`): aceitar socorro, sala de chat
- [ ] Upload real de prints (S3/Cloudinary) em vez de `mediaUrl`
- [ ] Limite de tamanho do body no `POST /api/posts`
- [ ] Flyway para versionar a DDL junto do deploy
- [ ] Integração com OpenAPI/Swagger (UI em `/swagger-ui`)