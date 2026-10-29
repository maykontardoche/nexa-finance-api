---
status: Aceito
date: 2026-10-28
decision-makers: Maykon Tardoche (responsável pelo projeto)
---

# ADR-0003: Construir a API sobre Spring Boot 4.1.x, com starters modulares e Maven Wrapper

## Contexto e problema

O legado não tem framework: `MainApp` de console + JDBC puro + `ConnectionFactory` estática. A fundação exige, a partir de agora, um conjunto de comportamentos transversais:
- API REST sob `/api/v1`;
- negação por padrão;
- erros RFC 9457;
- health;
- OpenAPI gerado a partir do código;
- configuração por ambiente e perfil.

O build precisa rodar sem Maven instalado, e hoje o Maven não está no PATH. O plano inicial sugeria Spring Boot 3.x, mas o Spring Boot 3.5 saiu do suporte OSS em 30/06/2026, e o 4.1 tem suporte OSS até 31/07/2027 (ver Links). A última versão mostrada em spring.io em 2026-10-28 era a 4.1.2.

## Drivers da decisão

1. Cobertura nativa dos RFs transversais: segurança, Problem Details, Actuator, OpenAPI, Flyway e Testcontainers.
2. Suporte ativo durante toda a vida do MVP.
3. Familiaridade e valor de portfólio no mercado Java.
4. Build reproduzível sem ferramenta global.

## Alternativas consideradas

1. **Spring Boot 4.1.x**, com os starters modulares e o Maven Wrapper.
2. **Spring Boot 3.5.x**, a linha do MVP original.
3. **Quarkus**, framework Jakarta/MicroProfile com foco em startup rápido.
4. **Manter Java puro + JDBC**, adicionando um servidor HTTP leve à mão.

## Decisão

Escolhida: **Spring Boot 4.1.x**, porque cobre todos os RFs transversais com recursos nativos (driver 1) e é a linha suportada (driver 2).

O que passa a valer:
- parent `spring-boot-starter-parent` na última patch 4.1.x;
- **só starters modulares do Boot 4**, sem os "classic", com os nomes conferidos no BOM do Boot 4.1.2;
- build pelo **Maven Wrapper** versionado (`mvnw`, `.mvn/wrapper`), com `distributionSha256Sum`;
- Maven segue como ferramenta de build, porque trocar por Gradle não traz ganho para os drivers.

## Consequências

- **Positivas:** Spring Security, `ProblemDetail`, Actuator, springdoc 3.x, Flyway e `@ServiceConnection` com Testcontainers sem código próprio; build com `./mvnw` só com o JDK.
- **Negativas:**
  - Boot 4 é recente, então há menos material de referência que para o 3.x e os starters mudaram de nome;
  - bibliotecas de terceiros podem demorar a suportar o 4.1 (o springdoc exige a linha 3.1.x/3.2.x);
  - o framework é pesado para uma API pequena, com startup na casa de segundos.
- **Riscos e mitigação:** nome de starter errado ou biblioteca incompatível → `./mvnw -B verify` na CI acusa; matriz de compatibilidade do springdoc consultada antes de fixar a versão.
- **Passa a ser obrigatório:** atualizar a minor do Boot com o fim do suporte OSS de cada linha (o Dependabot propõe).

## Confirmação

- O `pom.xml` tem parent `spring-boot-starter-parent` 4.1.x, nenhum `*-classic` e `mvnw` versionado.
- A CI roda `./mvnw -B verify`.

## Prós e contras das alternativas

### Spring Boot 3.5.x

- Prós: mais material, ecossistema estável.
- Contras: fora do suporte OSS desde 06/2026; migrar para o 4 seria obrigatório logo em seguida.
- Drivers: 1 bom, 2 reprovado.

### Quarkus

- Prós: startup e memória menores; dev mode excelente.
- Contras: menos aderente ao MVP e ao material do curso; abordagem de segurança e erro diferente do que o MVP descreve; sem ganho para os drivers 1 e 3.
- Drivers: 1 bom, 2 bom, 3 médio.

### Manter Java puro + JDBC

- Prós: sem dependência de framework.
- Contras: reimplementar segurança, Problem Details, health, OpenAPI e configuração à mão contradiz "biblioteca antes de código próprio".
- Drivers: 1 e 3 reprovados.

## Links


- ADRs relacionados: ADR-0002 (Java 25), ADR-0004 (erro), ADR-0005 (negação por padrão)
- Referências:
  - [spring.io/projects/spring-boot](https://spring.io/projects/spring-boot)
  - [Modularizing Spring Boot](https://spring.io/blog/2025/10/28/modularizing-spring-boot)
  - [springdoc FAQ](https://springdoc.org/faq.html)
  - [Datas de suporte do Spring Boot (HeroDevs, 04/2026)](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026)

> Nota 2026-10-28 (sem mudar a decisão):
> - **Gradle** também foi considerado como ferramenta de build. Foi descartado porque o projeto já é Maven (`pom.xml`), o wrapper resolve o build sem Maven instalado do mesmo jeito e a troca não melhora nenhum driver.
> - Contra o **Quarkus**, quem decide é o **driver 3** (aderência ao material do curso), não os drivers 1 e 2, em que os dois empatam.
