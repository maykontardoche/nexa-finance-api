---
status: Aceito
date: 2026-10-28
decision-makers: Maykon Tardoche
---

# ADR-0001: Usar PostgreSQL como banco único da aplicação, no lugar do Oracle

## Contexto e problema

A base acadêmica usa Oracle via JDBC puro:
- o `pom.xml` declarava o driver `ojdbc8`;
- a configuração apontava para `jdbc:oracle:thin:@//HOST:PORT/SERVICENAME`.

O schema não era versionado no repositório, e o banco era um Oracle remoto da FIAP, já desativado.

A fundação exige um banco que suba localmente com um comando, sem conta externa, e setup em até 15 minutos seguindo só o README. O plano inicial do MVP mandava confirmar se o Oracle era requisito do curso. Não há exigência acadêmica de banco específico, então o Oracle sai. Não há dados a migrar: o legado é descartado.

## Drivers da decisão

1. Onboarding do avaliador: o banco sobe local, sem conta nem licença.
2. Testes de integração com banco real e descartável no CI e no Windows.
3. Suporte maduro no ecossistema escolhido (Spring Boot, Flyway, Testcontainers).
4. Peso operacional da imagem e do processo de dev.

## Alternativas consideradas

1. **PostgreSQL 18** em contêiner, com Testcontainers nos testes.
2. **Oracle Database Free** em contêiner, mantendo o contexto do curso.
3. **Manter o Oracle remoto da FIAP**: o estado atual.

## Decisão

Escolhida: **PostgreSQL**, porque é o único que atende os drivers 1 e 2 sem custo de licença nem imagem pesada, e não há exigência de Oracle.

Vale para desenvolvimento, teste, CI e qualquer ambiente futuro. A versão da imagem é fixada (`postgres:18.6`) no `compose.yaml` e nos testes. O schema evolui só por migração Flyway (`db/migration`). SQL específico de PostgreSQL é permitido quando traz ganho real, porque não há meta de portabilidade.

## Consequências

- **Positivas:** banco local com `docker compose up -d db`; Testcontainers sem configuração especial; nenhum segredo de terceiro no projeto.
- **Negativas:**
  - perde-se a continuidade com o conteúdo Oracle do curso (PL/SQL, prefixos `T_FIN_*`);
  - todo o schema é reescrito do zero na Fase 2;
  - Docker passa a ser pré-requisito para os testes de integração.
- **Riscos e mitigação:**
  - incompatibilidade entre a versão do Flyway e o Postgres 18 → conferido no scaffold: o Flyway 12.4 reconhece o PostgreSQL 18;
  - avaliador da FIAP espera Oracle → não há essa exigência no curso.
- **Passa a ser obrigatório:** nenhuma dependência de driver Oracle; toda mudança de schema via migração versionada.

## Confirmação

- O `pom.xml` não contém `ojdbc` nem `com.oracle`.
- Os testes de integração usam `PostgreSQLContainer`.
- A revisão de código rejeita schema criado fora de `db/migration`.

## Prós e contras das alternativas

### Oracle Database Free em contêiner

- Prós: mantém o dialeto do curso; tem imagem oficial.
- Contras: imagem de vários GB e startup lento (pior para o driver 1 e para o CI); termos de licença próprios; nenhum ganho para o produto.
- Drivers: 1 ruim, 2 aceitável, 3 bom, 4 ruim.

### Manter o Oracle remoto da FIAP

- Prós: zero mudança.
- Contras: avaliador sem acesso; credencial vazada no histórico; banco provavelmente desativado; impossível rodar em CI.
- Drivers: 1, 2 e 4 reprovados.

## Links

- ADRs relacionados: ADR-0003 (Spring Boot), que dá a integração com Flyway e Testcontainers
- Compatibilidade: Flyway 12.4 com PostgreSQL 18, conferida no scaffold do projeto
