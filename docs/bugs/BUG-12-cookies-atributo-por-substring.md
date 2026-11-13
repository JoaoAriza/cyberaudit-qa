# BUG-12 — Cookies: atributo lido por substring, CSRF tratado como sessão, agregado sempre MEDIUM

Encontrado em 2026-11-07 ao revisar o feedback de um cliente (o laudo dele trazia um
cookie de sessão HIGH e um de visitante MEDIUM). O cliente não contestou os cookies; os
defeitos saíram da revisão do código. Formato conforme `../template-bug.md`. Colar como
issue no GitHub quando for abrir.

```markdown
## Resumo

Cinco defeitos no `CookieSecurityService` e no bloco de cookies do `ScoreService`:

1. **Flag por substring.** `contains("secure")`/`contains("httponly")`/`contains("samesite=…")`
   no Set-Cookie inteiro: um cookie chamado `secure_pref`, ou com "secure" no valor, saía
   com Secure. `SameSite` com espaço em volta do `=` saía `UNKNOWN`, que não contava como
   problema — e valor inválido também.
2. **CSRF como sessão.** `XSRF-TOKEN` e `csrftoken` caíam na heurística de sessão (por
   "token" e por "csrf") e saíam HIGH por falta de HttpOnly. Esses cookies existem para o
   JavaScript ler (double-submit); HttpOnly quebraria a proteção. Todo site Laravel padrão
   (`XSRF-TOKEN` sem HttpOnly, por design) perdia 10 pontos.
3. **`connect.sid` não era sessão.** A sessão padrão do Express (e `sid`) saía MEDIUM.
4. **Cookie que não é de sessão custava ponto.** Preferência/consentimento sem flag saía
   MEDIUM, -5 — o mesmo peso de uma sessão sem SameSite.
5. **Agregado fixo em MEDIUM.** O `INSECURE_COOKIES` saía MEDIUM mesmo quando o desconto
   aplicado era o de HIGH (-10): o laudo dizia MEDIUM na lista e HIGH no card do cookie.

## Severidade

P1 — desconto errado no score nos dois sentidos: -10 em todo site Laravel padrão (defeito
2) e flag reportada presente quando falta (defeito 1).

## Ambiente

- Commit/versão testada: Backend `2a18641`
- Ambiente: docker-compose de teste, backend 8081, alvo `cookies-mock` (alias novo do
  WireMock, cinco Set-Cookie). Nunca produção do CyberAudit (Regra 1).
- Ferramenta que encontrou: revisão de código

## Passos para reproduzir

1. qa-pro, `GET /scan?url=http://cookies-mock:8080` (passivo).
2. Ler `cookieIssues` e o `INSECURE_COOKIES` em `score.issues`.

## Resultado esperado

| Set-Cookie | Esperado |
|---|---|
| `PHPSESSID=…; Path=/; Secure` | HIGH (sem HttpOnly, sem SameSite) |
| `XSRF-TOKEN=…; Path=/; Secure; SameSite=Lax` | fora da lista |
| `secure_pref=1; Path=/` | LOW, `secure: false` |
| `visitante=1; Path=/` | LOW |
| `connect.sid=…; Path=/; HttpOnly; SameSite=Lax` | HIGH (sem Secure) |

`INSECURE_COOKIES` HIGH, título "2 cookie(s) com problemas de segurança", nota -10.

## Resultado obtido

`XSRF-TOKEN` HIGH; `secure_pref` MEDIUM com `secure: true`; `visitante` MEDIUM;
`connect.sid` MEDIUM; `INSECURE_COOKIES` MEDIUM, "5 cookie(s)", nota -10.

## Caso de teste relacionado

SCAN-09 (novo, P1).

## Causa raiz

O parser nunca separou nome, valor e atributos (RFC 6265 §5.2); a heurística de sessão
casava "token" antes de olhar se era token de CSRF; e o tipo do cookie só mudava a
severidade de Secure/HttpOnly, nunca a do resto.

## Correção

Corrigido no Backend em 2026-11-12:

- atributos lidos como atributo: nome antes do `=`, sem caixa e sem espaço; SameSite fora
  de Strict/Lax/None conta como ausente (RFC 6265bis §5.6.7);
- tipo do cookie: CSRF (`csrf`/`xsrf`) antes de sessão; `sid`, `connect.sid` e sufixos
  `.sid`/`_sid`/`-sid` como sessão (no meio da palavra, como em `inside_banner`, não);
- severidade por tipo: sessão sem Secure/HttpOnly HIGH, sem SameSite MEDIUM, SameSite=None
  sem Secure HIGH (como antes); CSRF não precisa de HttpOnly; qualquer problema em cookie
  que não é de sessão é LOW — continua no card, sem desconto;
- agregado: severidade do pior cookie (HIGH com -10, MEDIUM com -5); o "N cookie(s)" do
  título conta só os que custam ponto.

Verificado ao vivo no docker-compose (backend 8081) em 2026-11-12: na imagem anterior ao
fix saiu o "Resultado obtido" acima; depois do rebuild, a tabela do "Resultado esperado"
inteira — `INSECURE_COOKIES` HIGH, "2 cookie(s) com problemas de segurança", nota -10.

## Teste de regressão

- Backend: `CookieAtributosTest` (19 — atributos, sessão, CSRF, não-sessão, agregado e o
  site Laravel padrão) — 14 vermelhos antes do fix. Suíte inteira verde (757).
- QA: SCAN-09, a escrever pelo dono.
```

## Decisões que valem registrar

- **O agregado HIGH aciona o piso de risco.** Com a severidade do pior cookie, um cookie de
  sessão sem HttpOnly (HIGH, como o card já dizia) passa a travar o `riskLevel` em MEDIUM —
  o mesmo piso que a etapa D tirou do HSTS. Para o cliente que motivou a rodada (sessão PHP
  sem HttpOnly), o risco sai MEDIUM mesmo com nota alta. Se isso for rígido demais, a
  alavanca é rebaixar "sessão sem HttpOnly" para MEDIUM (amplifica XSS, não é falha
  sozinha) e manter HIGH só para sessão sem Secure — decisão do dono.
- **LOW para tudo que não é sessão.** A heurística de sessão é por nome. Um cookie de
  autenticação com nome fora do padrão (`uid`, `conta`) sai LOW e não custa ponto — antes
  saía MEDIUM, -5. Alargar a heurística com fragmentos soltos ("user", "id") traria falso
  positivo de volta; ficou como está.

## O que o fix NÃO resolve

- `wordpress_logged_in_*` não é reconhecido como sessão ("logged_in" não casa com
  "login"). O WordPress já manda esse cookie com HttpOnly, então o efeito prático é pequeno.
- O compliance (LGPD Art. 49) lê a flag crua: um cookie de CSRF com outro problema (sem
  Secure, por exemplo) entra na lista com `httpOnly: false` e dispara "Cookies sem flag
  HttpOnly". E cookie LOW continua disparando os itens de compliance.
- Achado lateral, registrado à parte: o item "Cookies sem SameSite" do compliance nunca
  dispara (`BUG-13-compliance-samesite-nunca-dispara.md`).
