---
status: Aceito
date: 2026-10-28
decision-makers: Maykon Tardoche (responsável pelo projeto)
---

# ADR-0005: Negar por padrão toda rota não pública antes do roteamento, respondendo 401 até para rota inexistente

## Contexto e problema

A fundação exige negação por padrão: toda rota fora de uma lista pública explícita exige autenticação. Também pede 404 para rota inexistente e 405 para método não suportado. As duas exigências se chocam para quem chega **sem credencial**: o filtro de segurança roda antes do roteamento, então decidir 404 nesse ponto exigiria consultar o roteador antes de autenticar, e isso revela quais rotas existem.

A fundação ainda não tem mecanismo de login (Fase 2). Portanto, hoje, toda rota não pública responde 401. O MVP pede autorização em todas as rotas de recurso e testes de IDOR/BOLA.

## Drivers da decisão

1. Rota nova nasce privada: esquecer de proteger não pode abrir a API.
2. Não permitir enumerar rotas sem credencial.
3. Simplicidade: usar o comportamento padrão do Spring Security, sem consultar o roteador no filtro.
4. Manter o 404/405 útil para cliente autenticado.

## Alternativas consideradas

1. **Negar antes de rotear:** sem credencial, toda rota fora da lista pública → 401. Com credencial, 404/405 normais.
2. **Rotear antes de negar:** consultar os handlers no filtro e devolver 404 para rota inexistente mesmo sem credencial.
3. **Lista de rotas protegidas** (allow by default), protegendo cada rota explicitamente.

## Decisão

Escolhida: **negar antes de rotear**, porque atende os drivers 1 a 3 com o comportamento nativo (`anyRequest().authenticated()`) e mantém o 404/405 para quem está autenticado (driver 4).

O que passa a valer:
- lista pública **explícita** e mínima: `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`;
- rota pública nova só com teste que atualize a lista fixada;
- sem credencial, rota inexistente → 401 `unauthorized` (formato do ADR-0004);
- esse refinamento (401 antes de 404/405) fica registrado no `docs/arquitetura/api-contract.md`.

## Consequências

- **Positivas:** fechado por padrão; nenhuma enumeração de rotas sem credencial; nenhuma lógica própria no filtro.
- **Negativas:**
  - cliente sem credencial que erra a URL recebe 401 em vez de 404, o que confunde na depuração (mitigado pelo `detail` em pt-BR e pela documentação);
  - testes de 404/405 precisam simular um usuário autenticado (`@WithMockUser`);
  - toda rota pública futura (ex.: `/auth/login` na Fase 2) exige mudança deliberada na lista e no teste.
- **Riscos e mitigação:** alguém abrir rota pública demais (ex.: `/actuator/**`) → teste que fixa a lista pública exatamente.
- **Passa a ser obrigatório:** nenhum `permitAll()` fora da lista; `anyRequest().authenticated()` é a última regra da cadeia.

## Confirmação

- Teste parametrizado: as rotas públicas são exatamente as da lista; privada e inexistente sem credencial → 401.
- O `security-reviewer` e o `architect-reviewer` recusam `permitAll()` novo sem o teste atualizado.

## Prós e contras das alternativas

### Rotear antes de negar

- Prós: 404 "correto" para qualquer cliente; depuração mais fácil.
- Contras: revela a superfície da API sem credencial; exige lógica própria no filtro, consultando o `HandlerMapping`.
- Drivers: 2 reprovado, 3 ruim.

### Lista de rotas protegidas

- Prós: fácil de entender no começo.
- Contras: rota esquecida nasce aberta, o que viola o driver 1 e é a origem clássica de BOLA.
- Drivers: 1 reprovado.

## Links


- ADRs relacionados: ADR-0003 (Spring Security vem do Boot), ADR-0004 (formato do 401)
- Referência: [Spring Security — Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
