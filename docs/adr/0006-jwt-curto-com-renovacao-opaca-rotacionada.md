---
status: Aceito
date: 2026-10-29
decision-makers: Maykon Tardoche
---

# ADR-0006: Autenticar com JWT HS256 de 15 minutos e renovação opaca rotacionada por sessão

## Contexto e problema

A API passa a ter usuários. Cada rota de negócio precisa saber quem chama, e o usuário precisa:
- continuar conectado por alguns dias sem repetir a senha;
- encerrar uma sessão específica, todas as outras ou todas;
- ter as outras sessões encerradas quando troca ou redefine a senha.

Hoje toda rota não pública responde 401 e não existe credencial nenhuma (ADR-0005,
`src/main/java/br/com/fintech/config/SecurityConfig.java`). A API é um único serviço, sem
consumidor externo de token, e roda só localmente. O Spring Security 7.1 (via Boot 4.1.2) já
traz o resource server com validação de JWT (`spring-security-oauth2-resource-server` e
`-oauth2-jose` 7.1.2 no `dependency:tree`).

A RFC 9700 (OAuth 2.0 Security Best Current Practice, §4.14) pede que a credencial de renovação
seja restrita ao remetente ou **rotacionada**, com detecção de reuso.

## Drivers da decisão

1. Revogação real: encerrar sessão tem efeito verificável na próxima renovação.
2. Rotas protegidas sem consultar o banco a cada requisição.
3. Usar o que o Spring Security já oferece, sem protocolo próprio de validação.
4. Pouca infraestrutura: um serviço, sem servidor de autorização nem par de chaves para distribuir.
5. Roubo de credencial de renovação detectável.

## Alternativas consideradas

1. **JWT HS256 de 15 min + renovação opaca rotacionada por sessão**, com detecção de reuso.
2. **Token opaco em toda requisição**, consultado no banco a cada chamada.
3. **JWT de acesso + JWT de renovação** de longa duração.
4. **JWT RS256** (par de chaves) no lugar do HS256, com o mesmo esquema de renovação.

## Decisão

Escolhida: **JWT HS256 de 15 minutos + credencial de renovação opaca rotacionada**, porque é a
única opção que atende os drivers 1 e 2 juntos com o resource server nativo (driver 3) e sem
infraestrutura nova (driver 4).

O que passa a valer:
- **Credencial de acesso:** JWT assinado com HS256, validade de 15 min, claims `iss`, `sub`
  (id do usuário), `sid` (id da sessão), `iat`, `exp` e `jti`. O segredo vem do ambiente
  (`JWT_SECRET`, base64 de pelo menos 256 bits), é obrigatório e verificado no startup. O
  algoritmo é fixo na validação.
- **Credencial de renovação:** `<id da sessão>.<32 bytes aleatórios em base64url>`. O banco guarda
  só o SHA-256 do segredo, nunca o valor. Validade deslizante de 7 dias.
- **Rotação:** cada renovação troca o segredo da sessão de forma atômica
  (`UPDATE ... WHERE refresh_hash = :anterior`) e devolve um par novo. Credencial já usada que
  reaparece revoga a sessão inteira e gera um evento de segurança.
- **Sessão** é uma linha no banco: logout, troca de senha e encerramento de sessão a revogam.
- O usuário de toda rota protegida vem do `sub` do JWT, nunca de parâmetro.

## Consequências

- **Positivas:**
  - rotas protegidas não consultam o banco para autenticar;
  - logout e encerramento de sessão cortam a renovação na hora;
  - credencial de renovação roubada e usada pelos dois lados é detectada e derruba a sessão;
  - vazamento do banco não entrega credenciais de renovação (só hashes).
- **Negativas:**
  - o JWT continua válido por até 15 min depois de logout, troca de senha ou exclusão da conta;
  - duas abas renovando ao mesmo tempo disparam a detecção de reuso e derrubam a sessão; o
    cliente precisa serializar a renovação;
  - HS256 usa o mesmo segredo para assinar e validar: quem o tiver forja tokens;
  - trocar o segredo invalida todos os JWTs emitidos (todos os usuários renovam).
- **Riscos e mitigação:**
  - segredo fraco ou versionado → mínimo de 256 bits verificado no startup e segredo só no
    ambiente (varredura de segredos na CI);
  - consumidor externo no futuro → migrar para RS256 em ADR novo, sem mudar o esquema de renovação.
- **Passa a ser obrigatório:** nenhum segredo de renovação ou token em claro no banco, em log
  ou em resposta (só na emissão); consultas de dados do usuário sempre filtradas pelo `sub`.

## Confirmação

- Testes de integração: renovação devolve par novo; a credencial anterior é recusada e revoga a
  sessão; credencial de sessão encerrada é recusada; JWT com `alg` diferente, assinatura errada,
  `iss` errado ou expirado é recusado com 401.
- Teste de startup sem `JWT_SECRET` ou com segredo curto falha nomeando só a variável.
- Revisão de código: rota protegida que lê o id do usuário de parâmetro ou corpo é recusada.

## Prós e contras das alternativas

### Token opaco em toda requisição

- Prós: revogação imediata; nenhum segredo de assinatura.
- Contras: uma consulta ao banco por requisição; validação própria em vez do resource server.
- Drivers: 1 ótimo, 2 reprovado, 3 ruim.

### JWT de acesso + JWT de renovação

- Prós: nenhum estado de renovação no banco.
- Contras: o JWT de renovação não pode ser revogado antes de expirar sem uma lista de bloqueio,
  o que recria o estado que a opção queria evitar; reuso não é detectável.
- Drivers: 1 reprovado, 5 reprovado.

### JWT RS256

- Prós: quem valida não consegue emitir; chave pública distribuível.
- Contras: par de chaves para gerar, guardar e rotacionar sem nenhum consumidor externo que se
  beneficie da chave pública.
- Drivers: 4 pior que HS256; demais iguais.

## Links

- ADRs relacionados: ADR-0005 (negação por padrão; a lista pública ganha as rotas de login),
  ADR-0004 (formato dos 401), ADR-0007 (hash de senha)
- Referências: [RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700#section-4.14),
  [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
