---
status: Aceito
date: 2026-10-29
decision-makers: Maykon Tardoche
---

# ADR-0007: Guardar senhas com Argon2id (m=19 MiB, t=2, p=1) via Spring Security e Bouncy Castle

## Contexto e problema

A API passa a guardar senhas de usuários. A senha precisa ficar só como hash forte, lento e
irreversível, gerado por biblioteca consolidada. O algoritmo e os parâmetros ficam gravados em
cada hash, e trocá-los depois exige rehash no login de cada usuário. Por isso a escolha é cara de
reverter.

O Spring Security 7.1.2 oferece `Argon2PasswordEncoder`, `BCryptPasswordEncoder` e
`Pbkdf2PasswordEncoder`. O Argon2 depende do Bouncy Castle (`bcprov-jdk18on`), que fica fora do
BOM do Boot. O padrão do Spring para Argon2 (`defaultsForSpringSecurity_v5_8()`) usa 16 MiB de
memória e 2 iterações (inspecionado com `javap` no jar 7.1.2), abaixo do mínimo da OWASP.

A política de senha aceita de 15 a 128 caracteres, inclusive acentos e espaços.

## Drivers da decisão

1. Resistência a ataque offline com GPU/ASIC se o banco vazar.
2. Recomendação vigente de referência reconhecida (OWASP Password Storage Cheat Sheet).
3. Implementação pronta e mantida, sem criptografia própria.
4. Custo por hash compatível com login em menos de 1 s (p95) local.
5. Senhas longas aceitas inteiras, sem truncamento silencioso.

## Alternativas consideradas

1. **Argon2id** (m=19 MiB, t=2, p=1) via `Argon2PasswordEncoder` + Bouncy Castle.
2. **bcrypt** (custo 10+) via `BCryptPasswordEncoder`, sem dependência nova.
3. **PBKDF2-HMAC-SHA256** (600 000 iterações) via `Pbkdf2PasswordEncoder`.

## Decisão

Escolhida: **Argon2id com m=19 MiB, t=2, p=1**, porque é a primeira escolha da OWASP (driver 2),
é resistente a GPU pelo custo de memória (driver 1) e não trunca a senha (driver 5).

O que passa a valer:
- encoder construído explicitamente com `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` (sal
  de 16 bytes, hash de 32 bytes, paralelismo 1, 19 456 KiB, 2 iterações), nunca o padrão do
  Spring;
- o hash completo (com parâmetros e sal) fica em `users.password_hash`;
- o tempo por hash é medido na máquina de desenvolvimento quando o encoder entrar, com meta de
  50 a 300 ms. Fora dessa faixa, os parâmetros são revistos antes de seguir.

## Consequências

- **Positivas:** ataque offline caro por tentativa; formato autodescritivo, que permite subir os
  parâmetros no futuro com rehash no login.
- **Negativas:**
  - dependência fora do BOM (`bcprov-jdk18on`), com versão mantida à mão e pelo Dependabot;
  - ~19 MiB de memória por login simultâneo: rajadas de login pesam na heap;
  - cada tentativa de login custa CPU no servidor, o que abre espaço para DoS.
- **Riscos e mitigação:**
  - DoS pelo hash caro → limite de tentativas por origem antes do hash e senha de no máximo 128
    caracteres;
  - vulnerabilidade no Bouncy Castle → alertas do Dependabot e `dependency-review` na CI.
- **Passa a ser obrigatório:** nenhuma senha em claro em log, resposta ou exportação; o hash
  também nunca sai da API.

## Confirmação

- Teste de unidade: o hash gerado começa com `$argon2id$v=19$m=19456,t=2,p=1$` e confere com a
  senha original.
- Medição do tempo por hash registrada quando o encoder entrar.
- Revisão de código: outro `PasswordEncoder` ou o padrão do Spring para Argon2 é recusado.

## Prós e contras das alternativas

### bcrypt

- Prós: sem dependência nova; amplamente usado.
- Contras: trunca a senha em 72 bytes (uma senha de 128 caracteres acentuados passa disso); pouco
  custo de memória, mais barato de atacar com GPU; a OWASP o indica só para sistemas legados.
- Drivers: 1 pior, 2 pior, 5 reprovado.

### PBKDF2

- Prós: sem dependência nova; aprovado pelo FIPS.
- Contras: sem custo de memória, o mais barato de paralelizar em GPU; a OWASP o indica quando há
  exigência FIPS, que este projeto não tem.
- Drivers: 1 pior, 2 pior.

## Links

- ADRs relacionados: ADR-0006 (autenticação que consome o hash)
- Referências: [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
  [Spring Security — Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
