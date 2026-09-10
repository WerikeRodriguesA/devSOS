# devSOS

Rede social para desenvolvedores: feed de problemas de código (estilo
Instagram) + dinâmica de chamadas (estilo Uber). Quem sabe ajuda quem precisa,
com chat ao vivo e avaliação mútua.

## Estrutura do repositório

```
.
├── README.md
├── database/
│   ├── schema.sql          # DDL completo (PostgreSQL 13+)
│   └── migrations/         # Migrações incrementais (ex.: v2 autenticação)
├── backend/
│   ├── pom.xml             # Spring Boot 4.1.1 · Java 25
│   └── src/main/           # Core: perfis, feed de posts e autenticação JWT
└── docs/
    ├── TECNICA.md        # Documentação do banco (desenvolvedores/DBA)
    ├── EXECUTIVA.md      # Documentação do banco (leigos)
    ├── API.md            # Documentação da API REST (devs)
    └── EXECUTIVA_BACKEND.md  # O que o app faz (leigos)
```

## Como rodar

### 1. Banco (PostgreSQL via Docker)

```bash
docker run -d --name devsos-db \
  -p 5433:5432 \
  -e POSTGRES_USER=devsos -e POSTGRES_PASSWORD=devsos -e POSTGRES_DB=devsos \
  postgres:16-alpine

docker cp database/schema.sql devsos-db:/tmp/schema.sql
docker exec devsos-db psql -U devsos -d devsos -v ON_ERROR_STOP=1 -f /tmp/schema.sql

# Bancos criados em versões anteriores (leva o login JWT):
docker cp database/migrations/v2_auth_password_hash.sql devsos-db:/tmp/v2.sql
docker exec devsos-db psql -U devsos -d devsos -v ON_ERROR_STOP=1 -f /tmp/v2.sql

# Bancos sem a "corrida" (índice de 1 corrida ativa por post):
docker cp database/migrations/v3_sessions_uma_corrida_por_post.sql devsos-db:/tmp/v3.sql
docker exec devsos-db psql -U devsos -d devsos -v ON_ERROR_STOP=1 -f /tmp/v3.sql
```

### 2. Backend

```bash
cd backend
mvn spring-boot:run
```

A API sobe em `http://localhost:8080` (documentação em `docs/API.md`).

## Documentação

- [API REST](docs/API.md) — rotas, JSONs e guia arquitetural.
- [Banco (técnica)](docs/TECNICA.md) — dicionário de dados, índices e concorrência.
- [Executiva banco](docs/EXECUTIVA.md) e [Executiva backend](docs/EXECUTIVA_BACKEND.md) — sem jargão.