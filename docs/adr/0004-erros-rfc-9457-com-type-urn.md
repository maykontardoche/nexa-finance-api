---
status: Aceito
date: 2026-10-28
decision-makers: Maykon Tardoche
---

# ADR-0004: Padronizar toda resposta de erro em RFC 9457 com `type` URN estável e textos pt-BR

## Contexto e problema

O legado propaga `throws Exception` sem distinguir os tipos de erro. A fundação exige:
- formato único compatível com RFC 9457, com `type`, `title`, `status`, `detail`, `instance`, `traceId` e `errors`;
- `title` e `detail` em pt-BR com `type` estável;
- cobertura de 400, 404, 405, 415 e 500;
- nenhum detalhe interno exposto;
- `traceId` correlacionado com o log.

Esse contrato vale para todos os módulos das Fases 2 a 5 e para qualquer frontend futuro. Por isso precisa ser fixado antes do primeiro endpoint de negócio. O Spring Framework 6+ tem suporte nativo a RFC 9457 via `ProblemDetail` e `ResponseEntityExceptionHandler` ([doc oficial](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)).

## Drivers da decisão

1. Um único formato para todo erro, previsível para o cliente.
2. Um identificador estável que o cliente pode usar em código, separado do texto.
3. Uso do recurso nativo do framework, sem envelope próprio.
4. Nenhum vazamento de detalhe interno.

## Alternativas consideradas

1. **RFC 9457 com `type` = URN `urn:nexa-finance:problem:<slug>`**, slugs em inglês, `title` e `detail` pt-BR via `MessageSource`, extensões `traceId` e `errors[]`.
2. **RFC 9457 com `type` = URL que aponta para a documentação** (ex.: página no GitHub).
3. **Envelope próprio** (`{ "code", "message", "details" }`).
4. **RFC 9457 com `type` = `about:blank`** (default do Spring) e o código só no `title`.

## Decisão

Escolhida: **RFC 9457 com `type` URN**, porque dá o identificador estável (driver 2) sem amarrar o contrato a um host ou repositório que pode mudar (o nome do produto é provisório), e usa o `ProblemDetail` nativo (driver 3).

O que passa a valer:
- todo erro HTTP sai como `application/problem+json`, montado por um único ponto (`ProblemFactory`);
- catálogo inicial de slugs: `bad-request`, `validation-error`, `unauthorized`, `forbidden`, `not-found`, `method-not-allowed`, `unsupported-media-type`, `internal-error`;
- slug novo entra no catálogo e no `docs/arquitetura/api-contract.md`;
- slug existente **nunca** muda de significado;
- locale fixo `pt_BR` para mensagens;
- erro inesperado → `internal-error` com detalhe genérico, e a stack só no log do servidor.

## Consequências

- **Positivas:** o cliente trata erros por `type` sem parsear texto; o `traceId` liga a reclamação do usuário ao log; o comportamento é idêntico em todos os módulos.
- **Negativas:**
  - URN não é clicável, então a documentação dos tipos fica no `api-contract.md`, não no próprio `type`;
  - o locale fixo pt-BR impede internacionalizar por `Accept-Language` sem um ADR novo;
  - erros de filtro (segurança) e de container exigem handlers próprios além do `@RestControllerAdvice`.
- **Riscos e mitigação:** algum caminho de erro escapar do formato (ex.: `BasicErrorController`) → error controller próprio + teste parametrizado por status.
- **Passa a ser obrigatório:** proibido devolver erro com corpo fora do formato; proibido `type` ad hoc fora do catálogo.

## Confirmação

- O teste parametrizado do contrato de erro cobre todos os status do catálogo: content-type, campos obrigatórios, `traceId` não nulo, ausência de `Exception`/`at br.`/`SQL` no corpo.
- A revisão de código recusa `ResponseEntity` de erro montado fora do `ProblemFactory`.

## Prós e contras das alternativas

### `type` = URL de documentação

- Prós: a RFC recomenda URI resolvível; o cliente chega na doc do erro.
- Contras: amarra o contrato ao host ou ao nome do repositório; renomear o produto quebraria `type` estáveis.
- Drivers: 2 médio.

### Envelope próprio

- Prós: liberdade total de formato.
- Contras: reinventa um padrão; o Spring, o springdoc e as ferramentas não reconhecem; contraria o formato único exigido.
- Drivers: 1 ok, 3 reprovado.

### `type` = `about:blank`

- Prós: zero configuração.
- Contras: o cliente precisa diferenciar erros pelo texto pt-BR, o que viola o driver 2.
- Drivers: 2 reprovado.

## Links

- ADRs relacionados: ADR-0003 (Spring Boot, que fornece o `ProblemDetail`), ADR-0005 (o 401 usa este formato)
- Referências: [Spring Framework — Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html); [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
