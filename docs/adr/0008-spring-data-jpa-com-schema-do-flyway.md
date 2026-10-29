---
status: Aceito
date: 2026-10-29
decision-makers: Maykon Tardoche
---

# ADR-0008: Persistir com Spring Data JPA sobre o schema do Flyway, só validado pelo Hibernate

## Contexto e problema

A API ganha as primeiras tabelas (usuários, sessões, tokens de e-mail e eventos de segurança), e
as próximas fases trazem contas, lançamentos e transferências. Até aqui o acesso ao banco era só
o JDBC com HikariCP, trazido pelo `spring-boot-starter-flyway`, com o Flyway aplicando migrations
no startup e nenhuma tabela (`src/main/resources/db/migration` vazio).

É preciso decidir como o código lê e grava essas tabelas e quem é o dono do schema. A escolha
vale para todos os módulos de domínio e é cara de trocar depois que existirem dezenas de
entidades e consultas.

## Drivers da decisão

1. Um único dono do schema, revisável em SQL e versionado.
2. Produtividade para os módulos seguintes (CRUD, paginação, consultas por dono).
3. Divergência entre código e banco detectada no startup, não em produção.
4. Nenhuma consulta escondida fora da transação ou na camada web.
5. Suporte nativo do Boot 4.1, sem biblioteca externa.

## Alternativas consideradas

1. **Spring Data JPA (Hibernate 7.4) com o schema do Flyway**: `ddl-auto=validate` e
   `open-in-view=false`.
2. **Spring Data JDBC / `JdbcClient`**, com o schema do Flyway e mapeamento explícito.
3. **Spring Data JPA com o Hibernate gerando o schema** (`ddl-auto=update`).

## Decisão

Escolhida: **Spring Data JPA com o schema do Flyway, só validado pelo Hibernate**, porque mantém o
SQL revisável como único dono do schema (driver 1) e dá repositórios, paginação e consultas
derivadas para as próximas fases (driver 2).

O que passa a valer:
- schema criado e alterado **só** por migration Flyway (`V<n>__<descricao>.sql`); migration
  aplicada não se edita;
- `spring.jpa.hibernate.ddl-auto=validate`: entidade que não bate com o banco impede o startup;
- `spring.jpa.open-in-view=false`: nada de carregamento preguiçoso durante a renderização da
  resposta;
- entidades e repositórios ficam no módulo dono do dado (`<modulo>.domain` e
  `<modulo>.infrastructure`); o JDBC e o HikariCP continuam por baixo, trazidos pelos starters.

## Consequências

- **Positivas:**
  - schema revisável em SQL antes de aplicar (linhas afetadas, lock e rollback de cada migration);
  - erro de mapeamento aparece no startup e nos testes;
  - repositórios e paginação prontos para as próximas fases.
- **Negativas:**
  - cada mudança de schema exige duas edições coerentes (migration e entidade);
  - o JPA esconde SQL: N+1 e carregamento preguiçoso precisam de atenção na revisão;
  - startup e testes mais lentos (inicialização do Hibernate);
  - `validate` não confere tudo: CHECK, índices parciais e `ON DELETE CASCADE` só existem na
    migration e precisam de teste contra o banco.
- **Riscos e mitigação:**
  - N+1 → consultas com `JOIN FETCH` ou projeções onde a lista importa, e revisão de banco;
  - regra que só existe na migration → testes de integridade contra o Postgres do Testcontainers.
- **Passa a ser obrigatório:** nenhum `ddl-auto` diferente de `validate` em nenhum perfil;
  nenhuma consulta montada por concatenação de entrada.

## Confirmação

- Teste de integração sobe o contexto contra o Postgres do Testcontainers com `validate` e
  confere que não há migration pendente.
- Testes de integridade (UNIQUE, CHECK e CASCADE) rodam contra o banco real.
- Revisão de código: `ddl-auto` diferente de `validate` ou `open-in-view` ligado é recusado.

## Prós e contras das alternativas

### Spring Data JDBC / `JdbcClient`

- Prós: SQL explícito, sem estado de sessão nem carregamento preguiçoso; startup mais leve.
- Contras: mapeamento manual de linhas e de relacionamentos; nenhuma validação automática de
  entidade contra o banco no startup.
- Drivers: 1 igual, 2 pior, 3 pior, 4 melhor.

### JPA com o Hibernate gerando o schema

- Prós: nenhuma migration para escrever no começo.
- Contras: schema implícito, sem revisão nem rollback; o `update` nunca remove nem renomeia e
  diverge entre ambientes; conflita com o Flyway já adotado.
- Drivers: 1 reprovado, 3 reprovado.

## Links

- ADRs relacionados: ADR-0001 (PostgreSQL como banco único), ADR-0003 (starters modulares do Boot 4)
- Referências: [Spring Boot — Data Access: Use Spring Data JPA and Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
  [Spring Data JPA — Reference](https://docs.spring.io/spring-data/jpa/reference/)
