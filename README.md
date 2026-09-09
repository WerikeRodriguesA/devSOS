# devSOS

Rede social para desenvolvedores: feed de problemas de código (estilo
Instagram) + dinâmica de chamadas (estilo Uber). Quem sabe ajuda quem precisa,
com chat ao vivo e avaliação mútua.

## Estrutura do repositório

```
.
├── README.md
├── database/
│   └── schema.sql        # DDL completo (PostgreSQL 13+)
└── docs/
    ├── TECNICA.md        # Documentação para desenvolvedores/DBA
    └── EXECUTIVA.md      # Documentação sem termos técnicos
```

## Como rodar o banco

```bash
createdb devsos
psql -d devsos -f database/schema.sql
```

## Documentação

- [Documentação técnica (devs)](docs/TECNICA.md) — dicionário de dados, índices,
  decisões arquiteturais e estratégia de concorrência.
- [Documentação executiva (leiga)](docs/EXECUTIVA.md) — como o app guarda as
  informações, sem jargão.