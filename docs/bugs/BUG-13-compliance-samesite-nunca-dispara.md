# BUG-13 — Compliance: "Cookies sem SameSite" nunca dispara

Achado lateral da etapa E (BUG-12), em 2026-11-12, por leitura de código. **✅ Corrigido em
2026-11-23** (HANDOFF §12, Etapa M), junto com duas cópias da mesma regra que divergiam do
card do cookie. Formato conforme `../template-bug.md`.

```markdown
## Resumo

`ComplianceMappingService.hasCookiesWithoutSameSite` procurava cookie com `sameSite` nulo ou
vazio. O `CookieSecurityService` nunca produz isso: SameSite ausente sai como a string
`"MISSING"`. O item "Cookies sem atributo SameSite — vulneráveis a CSRF" (LGPD Art. 49)
nunca aparecia no laudo, nem para um cookie de sessão sem SameSite.

Na mesma leitura, mais duas cópias da regra divergiam do card do cookie:
- o item "Cookies sem flag HttpOnly" do compliance olhava só a flag e contava o token CSRF,
  que o card isenta desde o BUG-12 (é feito para o JavaScript ler);
- os avisos explicativos do card na tela (Frontend) refaziam a conta pelas flags: o aviso
  de SameSite nunca aparecia (procurava vazio), aparecia com `SameSite=None` mesmo quando
  o cookie tinha Secure, e o aviso de HttpOnly aparecia no token CSRF.

## Severidade

P2 — itens de compliance omitidos ou a mais no laudo, e avisos errados no card. Não mexe na
nota: o score já descontava o SameSite ausente pelo `INSECURE_COOKIES`.

## Ambiente

- Commit/versão testada: Backend `260074a` (fix do BUG-20), Frontend `067dad2`
- Ambiente: docker-compose de teste, backend 8081, tela 8090; alvos `cookies-mock` (local).
  Nunca produção.
- Ferramenta que encontrou: revisão de código; reproduzido ao vivo em 2026-11-23

## Passos para reproduzir

1. qa-pro, pt-BR: `GET /scan?url=http://cookies-mock:8080/&active=false` (cinco Set-Cookie,
   três sem SameSite, um deles o `PHPSESSID`).
2. qa-pro, pt-BR: `GET /scan?url=http://cookies-mock:8080/so-csrf&active=false` (um
   `XSRF-TOKEN` sem nenhuma flag; stub novo `env/wiremock/mappings/cookies-csrf.json`).
3. Em cada um, comparar `compliance.lgpdItems` (item `Art. 49`) com `cookieIssues`.

## Resultado esperado

O Art. 49 lista um item de cookie quando, e só quando, algum card de cookie lista o
problema correspondente.

## Resultado obtido

1. Os cards de `PHPSESSID`, `secure_pref` e `visitante` dizem "sem SameSite"; o Art. 49
   traz Secure e HttpOnly, **sem o item de SameSite**.
2. O card do `XSRF-TOKEN` diz "sem Secure; sem SameSite"; o Art. 49 traz Secure e
   **HttpOnly** (a mais), **sem SameSite** (a menos).

## Causa raiz

Contrato implícito divergente. O `CookieSecurityService` decide o que está errado em cada
cookie, mas só entregava o veredito como texto (`issues`). Quem precisava da resposta (o
compliance, os avisos da tela) refazia a conta pelas flags, cada um com a sua regra: o
compliance esperava `null` onde o serviço grava `"MISSING"` e não conhecia a isenção do
token CSRF; a tela tinha a mesma falha no SameSite e no HttpOnly.

## Correção

Decisões do dono (2026-11-23):

- **Régua:** o compliance segue o card. Conta todo cookie em que o card lista o problema,
  inclusive valor de SameSite inválido e cookie de preferência, como os itens de Secure e
  HttpOnly já faziam.
- **HttpOnly do token CSRF:** corrigido junto, por ter a mesma raiz e os mesmos métodos
  (exceção à regra "lateral vira bug próprio").
- **Card da tela:** corrigido junto. O Backend manda o veredito em códigos e a tela desenha
  a partir deles.

O que mudou:

- Backend:
  - `CookieFinding.problems` (campo novo na API, só acrescenta): os códigos dos problemas,
    na ordem do `issues`. São as chaves do catálogo: `COOKIE_SEM_SECURE`,
    `COOKIE_SEM_HTTPONLY`, `COOKIE_SEM_SAMESITE`, `COOKIE_SAMESITE_NONE_SEM_SECURE`.
  - `CookieSecurityService` monta o código e o texto do mesmo veredito.
  - `ComplianceMappingService`: os três itens de cookie do Art. 49 aparecem quando algum
    cookie traz o código em `problems`. Saíram os três métodos que refaziam a conta.
- Frontend: `src/cookieAvisos.ts` decide os avisos do card a partir de `problems`. Scan
  gravado no histórico antes do campo existir usa a regra das flags, agora com o
  `"MISSING"` e com `SameSite=None` só sem Secure.
- A nota do laudo não muda. O placar do compliance só muda se o SameSite era a única falha
  do Art. 49: o item vira FAIL.

## Teste de regressão

- Backend: `ComplianceCookiesTest` (44). O `ScanResult` sai do `CookieSecurityService` real,
  a partir do Set-Cookie. Um `CookieFinding` montado à mão foi como a divergência passou.
  - Casos nomeados: sessão sem SameSite, valor inválido, cookie de preferência, token CSRF
    sem flags e só sem Secure, os cinco cookies do `cookies-mock`; controles: sessão
    completa, sessão sem HttpOnly, `SameSite=None` sem Secure.
  - Regra geral, 17 Set-Cookie: o item do compliance aparece se, e só se, o card lista o
    problema.
  - Os 17 de novo: cada código em `problems` tem o texto dele em `issues`, na mesma ordem.
  - Contra o código antigo (antes de os testes de código existirem): 16 de 27 vermelhos;
    os controles verdes nos dois. Os 17 de código usam o campo novo e não compilam contra o
    código antigo.
  - `PesoLowTest` passou a montar o `CookieFinding` com `problems`.
  - Suíte do Backend: 859 → 903, verde.
- Frontend: `cookieAvisos.test.ts` (8). Vitest: 45 → 53; `tsc -b` limpo.
- Ao vivo (8081, containers `backend` e `frontend` rebuildados):

  | Alvo (qa-pro, pt-BR) | Art. 49 antes | Art. 49 depois |
  |---|---|---|
  | `cookies-mock:8080/` | Secure, HttpOnly | Secure, HttpOnly, **SameSite** |
  | `cookies-mock:8080/so-csrf` | Secure, **HttpOnly** | Secure, **SameSite** |

  Também conferido depois do fix:
  - O mesmo em inglês ("Cookies without the SameSite attribute — exposed to CSRF").
  - `problems` na API: `PHPSESSID` `["COOKIE_SEM_HTTPONLY","COOKIE_SEM_SAMESITE"]`,
    `XSRF-TOKEN` `["COOKIE_SEM_SECURE","COOKIE_SEM_SAMESITE"]`, `connect.sid`
    `["COOKIE_SEM_SECURE"]`.
  - Nota igual antes e depois (22 e 32); placar do compliance igual (60 e 53): o Art. 49
    já era FAIL pelos outros itens.
  - Na tela (8090, qa-pro): o card do `XSRF-TOKEN` mostra os avisos "Secure ausente" e
    "SameSite ausente/None", sem o de HttpOnly; o Art. 49 lista Secure e SameSite.
  - qa-free: `compliance` e `cookieIssues` vêm nulos (trava de plano), antes e depois.
  - RestAssured do dono: 44 / 1 falha / 2 pulados. A falha é o PLAN-02, já conhecida
    (BUG-20, Etapa L).
- Caso de QA: **SCAN-14** (P2, 🔲): contrato no HANDOFF §12, Etapa M; ficha 20.
```

## Lateral

O painel "O que está em jogo" chamou o `XSRF-TOKEN` de "cookie de sessão". O
`ImpactLabelService` tem a sua própria lista de nomes de sessão. Registrado como
**BUG-21**. ✅ Corrigido depois, na Etapa N.
