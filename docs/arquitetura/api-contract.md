# API Contract — Nexa Finance API

Convenções e endpoints da **Nexa Finance API**. Atualizado junto com o código sempre que um
endpoint é criado ou alterado.

> Fonte gerada: OpenAPI 3.1 do springdoc em `/v3/api-docs` (interface em `/swagger-ui.html`),
> só com as rotas `/api/**`. Este registro manda nas **convenções** e nos endpoints de plataforma,
> que o OpenAPI não documenta; o OpenAPI manda no detalhe de campo de cada endpoint de negócio.

## Convenções

- **Estilo e prefixo:** REST; todo `@RestController` de `br.com.fintech` responde sob `/api/v1`
  (`ApiVersioningConfig`). Ficam fora do prefixo: Actuator, springdoc e a página de erro
  do container (`/error`).
- **Versionamento:** `/v1` no caminho. Breaking change vai para `/v2`; ver *Compatibilidade*.
- **Erro:** `application/problem+json` (RFC 9457, ADR-0004), montado só pelo `ProblemFactory`.
  Ver *Modelo de erro* abaixo.
- **Idioma:** `title`, `detail` e mensagens de validação em pt-BR, com locale fixo (o
  `Accept-Language` é ignorado).
- **Rastreio:** toda requisição tem trace W3C. O `traceparent` recebido é respeitado; o trace-id
  volta no `traceId` do erro e aparece no log da requisição.
- **Status de recusa:** 400 entrada malformada ou inválida · 401 sem credencial · 403 sem
  permissão · 404 não encontrado · 405 método não suportado (com `Allow`) · 415 `Content-Type`
  não suportado · 500 erro inesperado.
- **Formatos, paginação, filtros, idempotência:** definidos quando o primeiro endpoint de
  negócio entrar (Fase 2+).

## Modelo de erro

```json
{ "type": "urn:nexa-finance:problem:validation-error", "title": "Requisição inválida",
  "status": 400, "detail": "Um ou mais campos são inválidos.", "instance": "/api/v1/...",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "email", "message": "deve ser um endereço de e-mail bem formado" } ] }
```

| Campo | Regra |
|---|---|
| `type` | URN `urn:nexa-finance:problem:<slug>` do catálogo abaixo. **O cliente decide por ele**, nunca pelo texto |
| `title` | resumo pt-BR da classe do problema; não muda entre ocorrências |
| `status` | espelha o status HTTP |
| `detail` | explicação pt-BR para humano. Mudar o texto não é breaking change |
| `instance` | caminho da requisição que falhou |
| `traceId` | 32 hex do trace W3C; informe ao suporte para achar o log |
| `errors` | **só** em `validation-error`: todas as violações (`field` + `message`), de campos do corpo **e** de parâmetros (`?n=0` → `field: "n"`). Ordenadas por `field`; erro do objeto inteiro vem primeiro, com `field: null`. Valor que não converte para o tipo (ex.: `?idade=abc`) traz `message: "valor inválido"`, nunca a mensagem do conversor |

O corpo nunca traz stack trace, mensagem de exceção, SQL, nome de classe ou caminho de arquivo. A causa fica só no log do servidor, com o mesmo `traceId`.

### Catálogo de `type`

Slug existente nunca muda de significado. Slug novo entra no `ProblemType`, em
`messages_pt_BR.properties` e nesta tabela, na mesma passada.

| Slug | Status | Quando |
|---|---|---|
| `bad-request` | 400 | corpo ilegível (JSON malformado) ou parâmetro inválido. Também cobre qualquer 4xx sem slug próprio (ex.: 406, 413), mantendo o status real |
| `validation-error` | 400 | campo do corpo ou parâmetro que viola as regras, ou valor que não converte para o tipo; traz `errors[]` |
| `unauthorized` | 401 | sem credencial válida |
| `forbidden` | 403 | autenticado, sem permissão |
| `not-found` | 404 | rota ou recurso inexistente (inclui exceção do domínio com `@ResponseStatus(NOT_FOUND)`) |
| `method-not-allowed` | 405 | método não suportado na rota; header `Allow` lista os aceitos |
| `unsupported-media-type` | 415 | `Content-Type` não aceito pela rota |
| `internal-error` | 500 | erro inesperado, com detalhe genérico. Também cobre qualquer 5xx sem slug próprio, mantendo o status real |

## Autenticação

**Fechado por padrão** (ADR-0005). Ainda não existe mecanismo de login (Fase 2), então hoje só as
rotas públicas respondem.

- **Públicas (só `GET` e `HEAD`):** `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui.html`,
  `/swagger-ui/**`. Detalhe em *Plataforma*.
- **Todo o resto exige autenticação**, inclusive escrita (`POST`, `PUT`, `DELETE`…) nas rotas públicas.
- **Sem credencial válida → 401 `unauthorized` antes do roteamento**, inclusive para rota
  inexistente. *Refinamento:* 404, 405, 400 e 415 só aparecem para requisição
  autenticada, o que evita enumeração de rotas.
- **Autenticado sem permissão → 403 `forbidden`**, com mensagem genérica.
- Por enquanto, a resposta 401 não traz `WWW-Authenticate`: o esquema (ex.: `Bearer`) entra com o
  mecanismo de login na Fase 2.

---

## Plataforma

Fora do prefixo `/api/v1` e do OpenAPI. São as únicas rotas públicas (ADR-0005), só para leitura.

| Método | Endpoint | Descrição | Acesso |
|--------|----------|-----------|--------|
| GET, HEAD | `/actuator/health` | Estado agregado, incluindo a conectividade com o banco. **200** `{"status":"UP"}` · **503** `{"status":"DOWN"}`. O corpo tem **só** `status`, sem detalhes nem componentes. Com o banco fora, o DOWN sai em ~2 s (`connection-timeout` do Hikari). Medido em 2026-10-29: UP p50 6 ms / p95 11 ms (n=20), DOWN p50 2,008 s (n=5), local. | pública |
| GET, HEAD | `/v3/api-docs` | Documento OpenAPI **3.1** (JSON) das rotas `/api/**`, com o schema `ProblemDetail` (corpo de erro padrão); toda operação documenta 401 e 500 com esse schema. | pública |
| GET, HEAD | `/v3/api-docs/swagger-config` | Configuração lida pela interface do Swagger. | pública |
| GET, HEAD | `/swagger-ui.html` → `/swagger-ui/index.html` | Interface navegável (Swagger UI). O primeiro responde **302** para o segundo. | pública |

Nenhum outro endpoint do Actuator existe na web (`/actuator`, `/actuator/env`, `/actuator/beans`…):
sem credencial → 401; com credencial → 404 `not-found`.

---

## Deprecações

| Endpoint / campo | Depreciado em | Sunset | Substituto | Uso ainda medido? |
|---|---|---|---|---|

## Compatibilidade — changelog de breaking changes

| Data | Endpoint | Mudança | Estratégia | Consumidores avisados |
|---|---|---|---|---|
