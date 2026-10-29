# Runbook — Segredo commitado no histórico do Git

> Quando usar: uma credencial, token ou chave foi commitada e **já foi enviada** ao GitHub.

## 1. Conter (primeiros minutos)

1. **Trate o segredo como comprometido.** Rotacione ou revogue no sistema de origem **antes** de mexer no Git. Reescrever o histórico não desfaz clones, forks, caches nem crawlers.
2. Nunca cole o valor em issue, PR, chat ou log. Cite só o arquivo e a linha.

## 2. Avaliar o alcance

```bash
git ls-remote origin                        # branches e tags publicadas
gh repo view <dono>/<repo> --json visibility,forkCount
git log --all --format='%h %s' -- <arquivo>  # commits que tocam o arquivo
```

Repo público com fork = o segredo já está fora do seu controle. A rotação é o único controle efetivo.

## 3. Backup (obrigatório antes de qualquer escrita remota)

```bash
mkdir -p ../_backup-<repo>-<data>
git bundle create ../_backup-<repo>-<data>/todas-refs.bundle --all
git bundle verify ../_backup-<repo>-<data>/todas-refs.bundle
```

Guarde **fora** do repositório. O bundle contém o segredo: apague-o quando não for mais necessário.

## 4. Reescrever

Escolha a forma conforme o histórico:

| Situação | Como |
|---|---|
| Os commits com o segredo só têm conteúdo descartável e não há commits bons depois | Volte a branch ao último commit limpo: `git push --force-with-lease=<branch>:<sha-atual> origin <sha-limpo>:<branch>` |
| Há commits bons depois do vazamento | `git filter-repo --sensitive-data-removal --invert-paths --path <arquivo>` (git-filter-repo ≥ 2.47) e force-push de todas as branches e tags |

**Peça a confirmação explícita** do dono do repositório mostrando o comando exato. Use sempre `--force-with-lease` com o SHA esperado, nunca `--force` puro.

Depois:
- recrie as branches de trabalho a partir do commit limpo (um merge de branch antiga traz o segredo de volta);
- apague do remoto as **outras branches e tags** que contenham o commit (`git push origin --delete <ref>`, com confirmação) e confira se há PRs abertos/fechados que o referenciem (`gh pr list --state all`; as refs `refs/pull/*` só o GitHub Support remove);
- se for preciso manter uma branch local antiga, instale um `pre-push` local que recuse refs contendo o commit (exemplo no registro abaixo);
- confira com `git merge-base --is-ancestor <sha-vazado> HEAD`, que deve **falhar**.

## 5. Pedir limpeza ao GitHub

Force-push não apaga as *cached views*, as referências de PR nem o acesso direto por SHA. Abra um ticket em <https://support.github.com/contact> pedindo a remoção do commit órfão. O GitHub só atende quando a rotação não resolve o risco. Referência: [Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

## 6. Verificar

**Não confie só no scanner.** Rode antes um **controle positivo**: o scanner precisa acusar o segredo no histórico antigo (backup). Se não acusar, as regras não cobrem esse formato e "0 leaks" não prova nada. Nesse caso, valem as checagens por caminho e por valor.

```bash
git clone <url> /tmp/verificacao && cd /tmp/verificacao
git log --all --format=%h -- <arquivo>              # vazio (repita para cada caminho que continha o segredo)
git merge-base --is-ancestor <sha-vazado> HEAD; echo $?   # 1
# busca pelo VALOR sem imprimi-lo: leia o valor do backup numa variável e conte
V=$(git --git-dir=<backup>.git show <sha-vazado>:<arquivo> | sed -n 's/^<chave>=//p')
git grep -F -c -e "$V" $(git rev-list --all) | wc -l   # 0
unset V
gitleaks git . --redact                               # 0 leaks (só vale se o controle positivo acusou)
```

## 7. Prevenir

- `.env` e arquivos de configuração local no `.gitignore`.
- Push protection e secret scanning do GitHub ativos (padrão em repositório público).
- **gitleaks na CI** (job `secrets` de `.github/workflows/ci.yml`, v8.30.1 fixado por digest), sobre o histórico inteiro de todo PR e push no `main`, com as regras padrão **e** três regras próprias (`.gitleaks.toml`), todas restritas a `.properties`, `.yml`/`.yaml` e `.env`:
  - `credencial-em-arquivo-de-configuracao`: usuário, senha ou segredo literal (`jdbc.password`, `spring.datasource.username`, `DB_PASSWORD`, `export DB_PASS`, `jdbc.user`). Não acusa `${...}`, os valores fictícios do `.env.example`, o `user:` do Compose/k8s nem catálogos de mensagens (`messages*.properties`);
  - `credencial-em-url`: `scheme://usuario:senha@` e `jdbc:oracle:thin:usuario/senha@`;
  - `credencial-em-parametro-de-url`: `?password=` e `&pwd=`.
  - **Controle positivo e negativo registrado** (2026-10-29), com 13 fixtures sintéticos de valores inventados:
    - a configuração padrão dá `no leaks found` no formato do incidente;
    - as regras próprias acusam 7 dos 8 casos verdadeiros: o incidente (2 achados), YAML do Spring (2), `export DB_PASS`, `?password=`, `postgres://u:s@`, `oracle:thin:u/s@` e `prod.env`;
    - não acusam nenhum dos 5 negativos: mensagens, Compose/k8s, `.env.example`, URL Oracle sem credencial e URL em código Java.
  - **Limitação conhecida** (o 8º caso): um valor literal que começa com `$` (sem ser `${`) é descartado pela allowlist global das regras padrão do gitleaks, que o trata como referência de variável. Confirmado: sem `useDefault`, a regra própria acusa. As regras padrão ficam; o caso é raro.
  - Rodar localmente: `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f git /repo --config /repo/.gitleaks.toml --redact`.
- Dependabot (Maven, actions, imagens Docker) e `dependency-review` nos PRs.

---

## Registro de incidentes

### 2026-10-28 — Credencial Oracle da entrega FIAP

| Item | Registro |
|---|---|
| O que vazou | `jdbc.user` e `jdbc.password` reais em `src/main/resources/application.properties` e na cópia de build `target/classes/application.properties` (commit `fba004e`, de 2025). A `jdbc.url` era placeholder (`HOST:PORT/SERVICENAME`), então o host não foi exposto |
| Exposição | Repositório público `maykontardoche/atividade-java`, 0 forks em 2026-10-28 |
| Rotação | **Impossível para o responsável** (conta do ambiente Oracle da FIAP). **2026-10-29:** o responsável informou que o banco Oracle da FIAP **foi desativado** e não será mais disponibilizado. Fonte: declaração do responsável. Não verificado por teste, porque testar login com a credencial vazada continua vetado |
| Decisão | **Risco aceito e encerrado** em 2026-10-29: sem host exposto, com o banco desativado, o repositório antigo apagado e o backup local apagado, não resta nenhum uso possível da credencial nem cópia sob controle do projeto |
| Reescrita | `main` voltou ao `8791983` (commit inicial, só `.gitattributes`). Branch de trabalho recriada a partir dele. |
| Backup | Era `../_backup-atividade-java-2026-10-28/` (bundle + tar) e a branch local `backup/legado-fba004e`. **Apagados em 2026-10-29** por decisão do responsável, com `git reflog expire --expire=now --all` e `git gc --prune=now`. Depois disso, `git cat-file -t fba004e` dá "Not a valid object name": o legado não existe mais na máquina de dev |
| GitHub Support | **Ticket não enviado e dispensado.** O repositório inteiro foi apagado (linha abaixo), o que remove o commit órfão sem depender do Support. O texto fica abaixo só como referência |
| Exclusão do repositório | **2026-10-29:** o responsável apagou `maykontardoche/atividade-java` (0 forks, 0 estrelas). Conferido: `gh repo view` responde "Could not resolve to a Repository". O projeto continua em `maykontardoche/nexa-finance-api` (público, criado do zero, sem o `fba004e`). Links antigos para o repositório deixaram de funcionar, inclusive o que a FIAP tinha da entrega "parte 2" |
| Publicação da `feat/mvp-foundation` | **Resolvida pela exclusão.** As docs citam o SHA do `fba004e`, mas ele não existe em nenhum repositório publicado. Antes do push, gitleaks v8.24.0 rodou sobre `main` e `feat/mvp-foundation`: "no leaks found" (um falso positivo, a senha fictícia de teste, foi marcado com `gitleaks:allow`) |

**Texto do ticket ao GitHub Support** (não enviado: dispensado pela exclusão do repositório; mantido como modelo para um próximo incidente):

> Subject: Remove orphaned commit with exposed credential — maykontardoche/atividade-java
>
> Hello,
> Commit `fba004e79c77ca29240db01025f44f0994de4835` of my public repository
> `maykontardoche/atividade-java` accidentally included a database credential (files
> `src/main/resources/application.properties` and `target/classes/application.properties`).
> I have force-pushed `main` back to `8791983`, so the commit is no longer reachable from any
> branch or tag. The credential belongs to a university-managed database account that I cannot
> rotate myself. Could you please remove cached views and any remaining references to commit
> `fba004e` so it is no longer accessible by SHA? The repository has no forks and no pull
> requests.
> Thank you.
