---
status: Aceito
date: 2026-10-29
decision-makers: Maykon Tardoche
supersedes: ADR-0007
---

# ADR-0009: Guardar senhas com Argon2id m=19 MiB, t=4, p=1, o dobro de iterações do mínimo da OWASP

## Contexto e problema

O [ADR-0007](0007-argon2id-para-hash-de-senha.md) escolheu o Argon2id com m=19 MiB, t=2, p=1, o
mínimo da OWASP. Ele fixou uma meta de 50 a 300 ms por hash na máquina de desenvolvimento e
previu a revisão dos parâmetros se a medição caísse fora dessa faixa. O algoritmo, a biblioteca
e a normalização da senha continuam valendo; só os parâmetros mudam.

A medição com o `CredentialHasher` real ficou abaixo da faixa. Foram n=30 amostras depois de 5 de
aquecimento, em duas rodadas, num AMD Ryzen 7 7735HS (16 threads) com JDK 25.0.3:
- `hashPassword`: p50 de 23,7 a 23,9 ms e p95 de 25,9 a 26,2 ms;
- `passwordMatches`: p95 de 30,8 a 32,3 ms.

Na mesma máquina foram comparadas as alternativas (n=20 depois de 3 de aquecimento):

| Parâmetros | p50 | p95 |
|---|---|---|
| m=19 MiB, t=2, p=1 | 24,3 ms | 30,9 ms |
| m=19 MiB, t=4, p=1 | 43,8 ms | 55,8 ms |
| m=46 MiB, t=2, p=1 | 66,2 ms | 77,0 ms |
| m=64 MiB, t=3, p=1 | 136,0 ms | 148,0 ms |

Com o `CredentialHasher` real configurado em t=4, foram duas rodadas de n=30 depois de 5 de
aquecimento:
- `hashPassword`: p50 de 44,4 a 48,4 ms e p95 de 54,6 a 61,0 ms;
- `passwordMatches`: p95 de 47,9 a 52,8 ms.

O contêiner da aplicação tem limite de 1 GB (`compose.yaml`) e heap de cerca de 768 MB
(`MaxRAMPercentage=75`). Cada hash simultâneo ocupa `m` de memória.

## Drivers da decisão

1. Custo do ataque offline acima do mínimo, aproveitando a folga do orçamento de login (p95 < 1 s).
2. Memória por login compatível com o contêiner de 1 GB.
3. Tempo por hash dentro da faixa de 50 a 300 ms.

## Alternativas consideradas

1. **m=19 MiB, t=4, p=1**: dobra as iterações e mantém a memória.
2. **Manter m=19 MiB, t=2, p=1**: o mínimo da OWASP, abaixo da faixa medida.
3. **m=46 MiB, t=2, p=1**: a outra configuração mínima da OWASP, com mais memória.
4. **m=64 MiB, t=3, p=1**: a segunda opção recomendada pela RFC 9106, para memória limitada (lá
   com p=4), e o custo mais alto medido.

## Decisão

Escolhida: **m=19 MiB, t=4, p=1**, porque dobra o custo de cada tentativa offline sem aumentar a
memória por login (drivers 1 e 2). Ela fica dentro da faixa no p95 (55,8 ms), embora o p50 (43,8 ms)
fique um pouco abaixo dela. Com o hasher real, o p95 de `hashPassword` ficou entre 54,6 e 61,0 ms.

O que passa a valer:
- o encoder é `new Argon2PasswordEncoder(16, 32, 1, 19456, 4)`;
- o restante do ADR-0007 continua: Argon2id via Spring Security e Bouncy Castle, senha
  normalizada em NFKC antes do hash, hash completo em `users.password_hash`.

## Consequências

- **Positivas:** cada tentativa de adivinhação offline custa o dobro, com a mesma memória.
- **Negativas:**
  - login e troca de senha gastam cerca de 20 ms a mais de CPU por hash;
  - numa máquina mais lenta que a de desenvolvimento, o tempo pode passar de 100 ms. Ainda cabe
    no orçamento de 1 s, mas pesa mais em rajadas.
- **Riscos e mitigação:** DoS pelo hash mais caro → o limite de tentativas por origem continua
  antes do hash.
- **Passa a ser obrigatório:** hashes novos começam com `$argon2id$v=19$m=19456,t=4,p=1$`. Como
  ainda não existe hash gravado, não há rehash a fazer.

## Confirmação

- Teste de unidade: o hash gerado começa com `$argon2id$v=19$m=19456,t=4,p=1$`.
- Revisão de código: outro parâmetro no `Argon2PasswordEncoder` exige um ADR novo.

## Prós e contras das alternativas

### Manter m=19 MiB, t=2

- Prós: login mais barato; continua no mínimo recomendado.
- Contras: fica abaixo da faixa medida e desperdiça a folga do orçamento de login.
- Drivers: 1 pior, 2 igual, 3 reprovado.

### m=46 MiB, t=2

- Prós: mais resistente a hardware dedicado, pelo custo de memória.
- Contras: 46 MiB por login simultâneo num contêiner de 1 GB; com 10 logins ao mesmo tempo,
  cerca de 460 MB só de hash.
- Drivers: 1 melhor, 2 pior, 3 igual.

### m=64 MiB, t=3

- Prós: o maior custo de memória e de tempo entre as opções medidas.
- Contras: cerca de 140 ms e 64 MiB por login; com 10 logins simultâneos, cerca de 640 MB, perto
  do heap inteiro do contêiner.
- Drivers: 1 melhor, 2 reprovado, 3 igual.

## Links

- ADRs relacionados: ADR-0007 (substituído; algoritmo e biblioteca continuam), ADR-0006 (autenticação)
- Referências: [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
  [RFC 9106 §4](https://www.rfc-editor.org/rfc/rfc9106#section-4)
