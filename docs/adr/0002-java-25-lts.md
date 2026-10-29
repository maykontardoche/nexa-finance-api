---
status: Aceito
date: 2026-10-28
decision-makers: Maykon Tardoche (responsável pelo projeto)
---

# ADR-0002: Fixar Java 25 LTS como versão única do projeto e da CI

## Contexto e problema

O projeto declara Java 17 (`pom.xml:10-11`), mas o único JDK da máquina de desenvolvimento é o Temurin 25.0.3 LTS (`java -version` executado em 2026-10-28). A fundação exige uma única versão de Java, igual no ambiente local e na CI. O plano inicial deixava a escolha entre 17 e 21 em aberto, e o responsável confirmou que não há exigência acadêmica de versão.

O framework escolhido (ADR-0003, Spring Boot 4.1.x) suporta Java 17 a 26 ([HeroDevs, 04/2026](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026)).

## Drivers da decisão

1. Uma versão só, igual local e na CI.
2. Nada a instalar além do que a máquina de dev já tem.
3. Janela de suporte longa (LTS).
4. Compatibilidade com framework e bibliotecas (springdoc 3.x, Testcontainers, Flyway).

## Alternativas consideradas

1. **Java 25 LTS**: a LTS mais recente, já instalada.
2. **Java 21 LTS**: a LTS anterior, com mais tempo de mercado.
3. **Manter Java 17**: o estado atual do `pom.xml`.

## Decisão

Escolhida: **Java 25 LTS**, porque é a única opção que atende os drivers 1 e 2 sem instalar outro JDK, com a janela de suporte mais longa.

Vale para `java.version` no `pom.xml`, `actions/setup-java` na CI (distribuição Temurin) e as imagens base `eclipse-temurin:25-*` no Dockerfile. Recursos de preview da linguagem não são usados.

## Consequências

- **Positivas:** máquina de dev, CI e contêiner na mesma versão; acesso a virtual threads e às melhorias de linguagem até o Java 25.
- **Negativas:**
  - quem clona precisa de JDK 25, mais novo que o de muitas máquinas (o caminho só com Docker do README contorna isso);
  - bibliotecas atrasadas em relação ao bytecode 25 podem exigir uma atualização antes da hora.
- **Riscos e mitigação:** plugin ou biblioteca incompatível com Java 25 → `./mvnw -B verify` na CI acusa; trocar para a versão compatível, sem baixar o Java.
- **Passa a ser obrigatório:** subir a versão de Java só com ADR novo que substitua este.

## Confirmação

- `java.version` = 25 no `pom.xml`.
- `java-version: '25'` no `ci.yml`.
- Imagens `eclipse-temurin:25-*` no `Dockerfile`.
- O `architect-reviewer` confere os três juntos.

## Prós e contras das alternativas

### Java 21 LTS

- Prós: mais tempo de mercado, compatibilidade ampla.
- Contras: exige instalar outro JDK ou configurar toolchain na máquina de dev; suporte termina antes.
- Drivers: 1 ok, 2 ruim, 3 médio, 4 bom.

### Manter Java 17

- Prós: zero mudança no `pom.xml`.
- Contras: também não está instalado; é a LTS mais antiga ainda aceita pelo framework; nada do legado a preservar justifica isso.
- Drivers: 2 ruim, 3 ruim.

## Links


- ADRs relacionados: ADR-0003 (Spring Boot 4.1.x, que define a faixa de Java suportada)
- Compatibilidade confirmada na prática: o projeto compila, testa e roda com Java 25 sobre o Spring Boot 4.1.2
