# BUG-21 — "O que está em jogo" chama o token CSRF de cookie de sessão

Achado lateral em 2026-11-23, na conferência na tela do fix do BUG-13 (Etapa M). **✅
Corrigido em 2026-11-23** (HANDOFF §12, Etapa N), junto com a divergência do outro lado: o
card chamava de sessão o analytics do Hotjar. Formato conforme `../template-bug.md`.

```markdown
## Resumo

"É cookie de sessão?" tinha duas respostas no mesmo laudo.

- O painel "O que está em jogo" (rótulo de impacto) lista os "Cookies de sessão" do site
  como indício. O `ImpactLabelService` decidia por uma lista própria de fragmentos, por
  "contém" (`sess`, `sid`, `auth`, `token`, `login`, `usuario`, `user`). Chamava de sessão
  o `XSRF-TOKEN` e o `csrftoken` (tokens anti-CSRF, por "token"), o `inside_banner` (por
  "sid") e o `user_pref` (por "user"). E não via o `jwt`.
- O card do cookie (risco e nota) decide pelo `CookieSecurityService.tipo`, corrigido no
  BUG-12. Ele chamava de sessão o `_hjSession_1873402` (analytics do Hotjar, por
  "session"): o cookie saía HIGH e custava 10 pontos. O painel descartava esse nome por uma
  lista de CDN e analytics que o card não tinha.

O painel limita os indícios a três por origem. No alvo de teste, os três nomes errados
ocupavam as vagas e deixavam de fora o `laravel_session`, que era o único cookie de
sessão.

## Severidade

P3 no registro: era texto errado no painel, e o nível do rótulo não muda, porque vem só
dos campos de formulário. A parte do card, que o dono juntou na correção (Hotjar HIGH e
-10 na nota), é P2. O caso SCAN-15 entra como P2.

## Ambiente

- Commit/versão testada: Backend `0ee80e9` (fix do BUG-13)
- Ambiente: docker-compose de teste, backend 8081 e tela 8090; alvos `cookies-mock`
  (local). Nunca produção.
- Ferramenta que encontrou: conferência na tela, confirmada na API e no código;
  reproduzido ao vivo em 2026-11-23

## Passos para reproduzir

1. qa-pro: `GET /scan?url=http://cookies-mock:8080/so-csrf&active=false` (um `XSRF-TOKEN`
   sem flags).
2. qa-pro: `GET /scan?url=http://cookies-mock:8080/nomes&active=false`. Stub novo
   `env/wiremock/mappings/cookies-nomes.json`, com cinco cookies sem flag nenhuma, nesta
   ordem: `csrftoken`, `inside_banner`, `user_pref`, `_hjSession_1873402` e
   `laravel_session`.
3. Ler `impactIndicators` (na tela, o painel "O que está em jogo") e o `risk` de cada
   cookie em `cookieIssues`.

## Resultado esperado

O painel e o card dão a mesma resposta: só o `laravel_session` é de sessão.

## Resultado obtido

1. `/so-csrf`: `impactIndicators` `[{"source":"COOKIES","detail":"XSRF-TOKEN"}]`.
2. `/nomes`: `impactIndicators` com `csrftoken`, `inside_banner` e `user_pref`, sem o
   `laravel_session`. No card, `_hjSession_1873402` HIGH; o achado `INSECURE_COOKIES` diz
   "2 cookie(s)".

## Causa raiz

Duas cópias da regra "que tipo de cookie é este". O BUG-12 corrigiu a do
`CookieSecurityService` (CSRF antes de sessão; `sid` só como nome inteiro ou sufixo). A
cópia do `ImpactLabelService` ficou com a regra velha, mais a sua lista de CDN e analytics,
que o card nunca teve.

## Correção

Decisões do dono (2026-11-23):

- **Uma regra só, a do card, nos dois.** O que a lista do painel descartava (CDN e
  analytics) também deixa de ser sessão no card.
- **O painel continua vendo só cookie com problema** (`cookieIssues`). Um cookie de sessão
  bem configurado não aparece como indício. Fica como está, fora deste bug.

O que mudou (Backend):

- `ImpactLabelService`: saíram as duas listas. O painel pergunta ao
  `CookieSecurityService.tipo` se o cookie é de sessão.
- `CookieSecurityService.tipo`: cookie que começa com `_hj` (todos os do Hotjar) não é de
  sessão. Do resto da lista do painel, nenhum nome tinha fragmento de sessão pela regra do
  card: já saíam LOW, e nada mudou para eles.
- Na nota: um site com cookie do Hotjar sem flags deixa de perder 10 pontos, se ele era o
  único cookie de sessão "sem flag".

## Teste de regressão

- Backend: `CookieDeSessaoTest` (73). O `CookieFinding` sai do `CookieSecurityService`
  real, a partir do Set-Cookie.
  - Uma tabela de 26 nomes, cada um com a resposta certa ("é sessão?"). O card e o painel
    são conferidos contra ela, cada um no seu teste: 11 de sessão (o `jwt`, o painel não
    via), 6 de token CSRF e preferência, 5 do Hotjar e 4 em que os dois já concordavam.
  - Os 19 nomes da lista antiga do painel: LOW no card e fora do painel.
  - O alvo `/nomes` inteiro: o painel lista só o `laravel_session`.
- `CookieAtributosTest.hotjarNaoCusta`: só cookies do Hotjar, sem flag: nenhum achado e
  nota 100.
- Contra o código antigo: 17 de 93 vermelhos, todos nos nomes do bug.
- Suíte do Backend: 903 → 977, verde. Container `backend` rebuildado.
- Ao vivo (8081, qa-pro):

  | Alvo | Antes | Depois |
  |---|---|---|
  | `/so-csrf`, painel | `XSRF-TOKEN` | vazio |
  | `/nomes`, painel | `csrftoken`, `inside_banner`, `user_pref` | `laravel_session` |
  | `/nomes`, card do `_hjSession_1873402` | HIGH | LOW |
  | `/nomes`, achado `INSECURE_COOKIES` | "2 cookie(s)" | "1 cookie(s)" |

  Também conferido depois do fix:
  - Nota igual (22 no `/nomes`, 32 no `/so-csrf`): o `laravel_session` já custava os 10,
    e o desconto é o do pior cookie, não a soma.
  - O mesmo em inglês ("1 cookie(s) with security problems").
  - Raiz do `cookies-mock` (SCAN-09) igual: painel com `PHPSESSID` e `connect.sid`.
  - Visitante: `impactIndicators` `[{"source":"COOKIES","detail":null}]` e `cookieIssues`
    nulo (trava de plano: só a origem).
  - Na tela (8090, qa-pro): "Cookies de sessão: laravel_session" no painel; o card do
    `_hjSession_1873402` LOW.
  - RestAssured do dono: 44 / 1 falha / 2 pulados. A falha é o PLAN-02, já conhecida
    (BUG-20, Etapa L).
- Caso de QA: **SCAN-15** (P2, 🔲): contrato no HANDOFF §12, Etapa N; ficha 21.
```

## Lateral

Na conferência, `AMP_TOKEN` (Google Analytics) e `s_sess` (Adobe Analytics) saíram HIGH no
card e como sessão no painel. Os dois agora concordam, mas erram juntos. Registrado como
**BUG-22**. ✅ Corrigido depois, na Etapa O.
