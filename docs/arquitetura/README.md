# Arquitetura

> As decisões que justificam esta arquitetura estão em [`../adr/`](../adr/README.md).

## Visão geral

A Nexa Finance API é um **monólito modular** Spring Boot 4.1 sobre Java 25, com PostgreSQL 18 como
banco único. Ela roda localmente: no host, com o JDK, ou inteira em contêiner pelo Docker Compose.
Não há deploy público nem integração externa em tempo de execução. Os usuários são avaliadores e
desenvolvedores que chamam a API diretamente ou pelo Swagger UI.

Descrição do diagrama de contexto: o cliente HTTP (navegador no Swagger UI, `curl` ou, no futuro,
a interface visual) chama a API. A API usa o PostgreSQL e envia e-mails por SMTP para uma
caixa de e-mail local (Mailpit), que o avaliador abre no navegador e que não repassa nada para
fora. No desenvolvimento e na CI, o
GitHub Actions constrói, testa e verifica o código, e o Testcontainers sobe um PostgreSQL
descartável para os testes.

```mermaid
flowchart LR
  user[Avaliador ou desenvolvedor] -->|HTTP| api[Nexa Finance API<br/>Spring Boot 4.1 / Java 25]
  api -->|JPA + Flyway| db[(PostgreSQL 18)]
  api -->|SMTP, depois do commit| mail[Mailpit<br/>caixa de e-mail local]
  user -->|navegador :8025| mail
  ci[GitHub Actions] -.->|build, testes, fumaça, varreduras| api
```

## Fluxo de uma requisição

Descrição: toda requisição passa primeiro pelo filtro de observação, que cria ou continua o trace
W3C. Em seguida passa pela cadeia do Spring Security. As rotas públicas (health e documentação)
seguem direto. As demais exigem autenticação e, sem credencial, recebem 401 antes do roteamento
(ADR-0005). Requisições autenticadas chegam aos controllers sob `/api/v1`. Qualquer erro, seja da
segurança, do controller ou de um filtro, sai no formato RFC 9457 montado por um único
componente (ADR-0004).

```mermaid
flowchart LR
  c[Cliente] --> obs[ServerHttpObservationFilter<br/>traceId / traceparent]
  obs --> sec[SecurityFilterChain<br/>negação por padrão]
  sec -- público --> act["/actuator/health"]
  sec -- público --> doc["springdoc<br/>/v3/api-docs, /swagger-ui"]
  sec -- autenticado --> mvc["DispatcherServlet<br/>@RestController em /api/v1"]
  sec -- 401 / 403 --> h[ProblemDetailsSecurityHandlers]
  h --> adv[ApiExceptionHandler]
  mvc -- exceção --> adv
  obs -. falha fora do MVC .-> err[ProblemDetailsErrorController]
  adv --> pf[ProblemFactory<br/>RFC 9457 pt-BR + traceId]
  err --> pf
  act --> db[(PostgreSQL)]
```

## Componentes

| Componente | Responsabilidade | Onde vive |
|---|---|---|
| `NexaFinanceApplication` | Bootstrap; exclui o usuário em memória do Spring Security | `br.com.fintech` |
| `RequiredConfiguration` + `MissingConfigurationFailureAnalyzer` | Recusa o startup sem `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, nomeando só a variável | `br.com.fintech.config` |
| `ApiVersioningConfig` | Prefixo `/api/v1` em todo `@RestController` do projeto | `br.com.fintech.config` |
| `SecurityConfig` | Lista pública explícita por método e caminho (`PUBLIC_ROUTES`), resto autenticado, stateless, method security | `br.com.fintech.config` |
| `LocaleConfig` | Locale fixo pt-BR para erros e Bean Validation | `br.com.fintech.config` |
| `OpenApiConfig` | Metadados do OpenAPI 3.1, schema `ProblemDetail` e 401/500 em toda operação | `br.com.fintech.config` |
| `WebServerConfig` | Tomcat repassa `%2F`/`%5C` ao firewall do Spring (erro no contrato, não em HTML) | `br.com.fintech.config` |
| `ProblemType` | Catálogo estável de `type` (`urn:nexa-finance:problem:<slug>`) | `br.com.fintech.shared.web` |
| `ProblemFactory` | Único ponto que monta o corpo de erro (`instance`, `traceId`, `errors[]`) | `br.com.fintech.shared.web` |
| `ApiExceptionHandler` | Converte erros do MVC e da segurança em RFC 9457; loga 5xx com stack só no servidor | `br.com.fintech.shared.web` |
| `ProblemDetailsSecurityHandlers` | 401/403 dos filtros, delegados ao `ApiExceptionHandler` | `br.com.fintech.shared.web` |
| `ProblemDetailsErrorController` | Erros fora do MVC (filtros, container), mesmo formato e headers de segurança | `br.com.fintech.shared.web` |
| `AppProperties` | Configuração própria (`app.*`): `APP_BASE_URL` validada no startup e remetente dos e-mails | `br.com.fintech.config` |
| `AsyncConfig` | `@Async` com o contexto do trace propagado para a thread do executor | `br.com.fintech.config` |
| `MailNotifier` + `MailMessage` + `MailLinks` | E-mail em texto pt-BR só depois do commit e em segundo plano; falha de SMTP vira WARN sem destinatário nem conteúdo; links absolutos sobre `APP_BASE_URL` | `br.com.fintech.shared.mail` |
| `RequestOriginResolver` + `RequestOrigin` | IP truncado (/24 IPv4, /48 IPv6, sem confiar em `X-Forwarded-For`) e navegador/sistema aproximados; o user-agent bruto não é guardado | `br.com.fintech.shared.web` |
| Flyway | Migrations em `src/main/resources/db/migration`, aplicadas no startup; único dono do schema (ADR-0008) | dependência |
| `User` + `UserRepository` | Conta do usuário (e-mail normalizado e único, só o hash da senha) | `br.com.fintech.user.domain` |
| `Session`, `EmailToken`, `SecurityEvent` + repositórios | Sessões com o hash da credencial de renovação (ADR-0006), tokens de e-mail de uso único (só o hash) e atividade de segurança | `br.com.fintech.auth.domain` |

## Convenções

- **Camadas e direção de dependência:** cada módulo de domínio (Fases 2–4) terá `api` →
  `application` → `domain` ← `infrastructure`. Os módulos dependem de `shared`, nunca o
  contrário; `domain` não conhece Spring Web nem JDBC.
- **Persistência:** Spring Data JPA sobre o schema do Flyway, com `ddl-auto=validate` e
  `open-in-view=false` (ADR-0008). Entidades de módulos diferentes se referenciam pelo id
  (`user_id`), não por associação JPA; a exclusão do usuário apaga o que é dele por
  `ON DELETE CASCADE` no banco.
- **Tratamento de erro:** só pelo `ProblemFactory`; slug novo entra no `ProblemType`, nas
  mensagens pt-BR e no [`api-contract.md`](api-contract.md) (ADR-0004).
- **Autorização:** sempre no servidor; rota pública só pela `PUBLIC_ROUTES` (método + caminho), com teste (ADR-0005).
- **Logs e auditoria:** `traceId`/`spanId` em todo log de requisição; JSON (ECS) no contêiner;
  nunca senha, token, header `Authorization` ou corpo de requisição em log. O driver do
  PostgreSQL roda com `logServerErrorDetail=false`, para que uma violação de UNIQUE não leve o
  valor duplicado (e-mail, hash) para a exceção e o log.
- **E-mail:** publicado com `MailNotifier.sendAfterCommit` dentro da transação do caso de uso
  (fora de transação é erro). Envio depois do commit e assíncrono: operação desfeita não manda
  e-mail e o tempo de resposta não depende do SMTP. O log do envio identifica o e-mail só pelo
  tipo (`kind`), com o `traceId` da requisição de origem.

## Contrato de API

Registro de endpoints e convenções: [`api-contract.md`](api-contract.md). Documento gerado em
tempo de execução: `/v3/api-docs` (OpenAPI 3.1) e `/swagger-ui.html`.
