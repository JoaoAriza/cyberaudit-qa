# Matriz de casos de teste — CyberAudit QA

Consolida os 26 casos originais do escopo (`CyberAudit QA — Escopo do projeto de
testes.pdf`, 2026-09-29) com os 12 casos do módulo de pagamento (já catalogados em
`payment-module-requests.md` durante a validação do checkout transparente) e 1 caso
novo (PAY-13), depois que o cartão virou opcional sem CPF. 39 casos: 22 P0, 14 P1, 3 P2.
Contagem atual (2026-11-23, com os casos acrescentados depois, como o SSRF-07, o
SCAN-05 a SCAN-16, o UI-04, o PLAN-03 e o PLAN-04): 57 casos — 21 P0, 26 P1, 10 P2.

P0 cobre segurança e regras de negócio centrais — **se um P0 falhar, é bug a
registrar (ver `template-bug.md`), não teste a ajustar.**

Os códigos HTTP citados são o esperado pelo padrão de mercado ou o que o código do
CyberAudit faz hoje (nos casos PAY-*, confirmado contra o código-fonte real do
Backend). Confirme o que a API retorna antes de fixar nos testes de qualquer forma.

## Legenda de Status

| Status | Significado |
|---|---|
| 🔲 A fazer | ainda não escrito |
| ✅ Passou | automatizado e verde |
| 🐛 Bug | achou um bug real — linkar a issue |
| ⚠️ Parcial | uma ferramenta feita (ex. Postman), outra(s) ainda não |

## Autenticação e 2FA

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| AUTH-01 | Autenticação | Login com credenciais válidas | JWT emitido, acesso liberado | Postman, RestAssured | P0 | ✅ Passou — Postman (2026-10-01) e RestAssured (2026-10-03) |
| AUTH-02 | Autenticação | Senha errada e usuário inexistente | 401 com a mesma mensagem nos dois casos, sem revelar se o usuário existe | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-03) |
| AUTH-03 | Autenticação | JWT expirado, assinatura adulterada, `alg: none` | 401 em todos | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-05) |
| AUTH-04 | 2FA | Sem código TOTP, código inválido, código reutilizado | Acesso negado nos três | RestAssured, Cypress | P0 | 🔲 A fazer — BUG-04 corrigido no Backend (2026-11-01, `bugs/BUG-04-totp-replay.md`); desbloqueado: sem código/inválido → 422 "inválido", válido → 200, mesmo código de novo → 422 "já utilizado". Precisa lib TOTP no pom + conta 2FA dedicada (qa-free) |
| AUTH-05 | API key | Chamada com API key revogada | 401 | Postman, RestAssured | P1 | 🔲 A fazer — BUG-01 corrigido no Backend (2026-10-29, `bugs/BUG-01-apikey-auth-lazyinit.md`); desbloqueado: chave válida → 200 (controle) e revogada → 401 |
| AUTHZ-01 | Controle de acesso | Usuário A consulta scan do usuário B pelo ID | 403 ou 404, nunca os dados | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-08) |

## Planos e limites

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| PLAN-01 | Planos | Usuário Free solicita relatório PDF | Bloqueado (recurso Pro) | RestAssured, Cypress | P0 | ✅ Passou — RestAssured (2026-10-09) e Cypress (2026-10-26; Free: botão PDF desabilitado + title "Exportar PDF requer plano PRO ou superior"; controle positivo Pro habilitado) |
| PLAN-02 | Planos | Exceder a cota de scans do plano | Bloqueado com mensagem clara | RestAssured | P1 | ⚠️ Vermelho desde 2026-11-23 — passou no RestAssured (2026-10-10). Com o fix do BUG-20, o qa-free tem 5 req/min, e o `PlanoCotaScanTest` leva 429 na 6ª chamada seguida ("Tentativa 6 … but was: <429>"). É o contrato novo, não regressão. O ritmo do teste é com o dono (`fichas-da-fila.md`, revisão do PLAN-02) |
| PLAN-03 | Planos | Conta FREE (dona da conta, como todo cadastro) tenta criar API key; controle com conta PRO | FREE → 402 "API Keys requerem plano PRO ou Empresa."; PRO → 201 com `plainKey` (e revogar) | RestAssured | P1 | 🔲 A fazer — BUG-18 corrigido no Backend e no Frontend (2026-11-17, `bugs/BUG-18-free-cria-api-key.md`); contrato no HANDOFF §12, Etapa H |
| PLAN-04 | Planos | Gate de CI (`GET /api-keys/ci`) com a cota do dia gasta e pedindo scan ativo numa conta FREE; controle com conta PRO | Cota gasta → 402 "Limite diário de 10 scans atingido.…"; ativo no FREE → 402 "Scan ativo requer plano PRO ou superior."; PRO → 200 (`threshold=0`) e 422 (`threshold=100`) | RestAssured | P1 | 🔲 A fazer — BUG-19 corrigido no Backend (2026-11-17, `bugs/BUG-19-gate-de-ci-sem-cota.md`); contrato no HANDOFF §12, Etapa I |
| RATE-01 | Rate limit | Exceder o limite por minuto do plano: qa-free (FREE, 5/min) no `GET /scan/verify-check`; controle com o qa-pro logo depois | Chamadas 1–5 → 200; 6ª → 429 `429 TOO_MANY_REQUESTS`; qa-pro → 200 (balde por usuário); 13 s depois → 200 e a seguinte 429 | RestAssured | P1 | 🔲 A fazer — BUG-20 corrigido no Backend (2026-11-23, `bugs/BUG-20-rate-limit-isenta-todo-cadastro.md`); retomado com o qa-free, sem a decisão B; contrato no HANDOFF §12, Etapa L |

## Anti-SSRF

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| SSRF-01 | Anti-SSRF | Alvo `127.0.0.1`, `localhost`, `::1` | Rejeitado | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-11) |
| SSRF-02 | Anti-SSRF | Alvo `169.254.169.254`, `10.x`, `192.168.x` | Rejeitado | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-11) |
| SSRF-03 | Anti-SSRF | Formas alternativas: `2130706433`, `0x7f000001`, `127.1` | Rejeitado | RestAssured | P0 | ✅ RestAssured — decimal/curto (2026-10-11); hex reativado em 2026-11-24, depois do BUG-02 (`bugs/BUG-02-ssrf-hex-nao-normalizado.md`); vermelho conferido no 8099 (200) |
| SSRF-04 | Anti-SSRF | Hostname público que resolve para IP privado | Rejeitado | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-12; hostnames via extra_hosts → IP privado, CI-safe) |
| SSRF-05 | Anti-SSRF | Alvo externo que redireciona para IP interno | Redirecionamento não seguido | RestAssured + WireMock | P1 | ✅ Passou — RestAssured (2026-10-12; redirect 302→interno não seguido, bloqueio no hop) |
| SSRF-06 | Anti-SSRF | Allowlist de teste fora do profile `test` | Allowlist inexistente | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-12; backend 8099 sem profile, juice-shop/wiremock → 403) |
| SSRF-07 | Anti-SSRF | Host disfarçado no `/scan/verify-check` (público): com porta (`127.0.0.1:8081`, `localhost:8081`, `169.254.169.254:80`, `[::1]:443`) e com delimitador (`127.0.0.1/`, `127.0.0.1#`, espaço no fim) | Rejeitado (403), igual ao host sem porta | RestAssured | P1 | ✅ RestAssured (2026-11-24, `SsrfVerifyCheckTest`) — BUG-05 corrigido no Backend (2026-10-29, `bugs/BUG-05-ssrf-host-com-porta.md`); as 7 variantes → 403, controle `juice-shop:3000` → 200; vermelho conferido no 8099 (`127.0.0.1:8081` e `127.0.0.1#` → 200) |

## Domínio e motor de scan

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| DOM-01 | Domínio | Varredura ativa em domínio não verificado | Recusada | Postman, RestAssured | P0 | ✅ Passou — RestAssured (2026-10-05) e Postman (2026-10-06) |
| SCAN-01 | Motor | Varredura passiva no alvo controlado | Módulos esperados com status OK | RestAssured | P0 | 🔲 A fazer — BUG-03 corrigido no Backend (2026-10-29, `bugs/BUG-03-scan-http-porta-nao-padrao.md`); desbloqueado: passivo em `http://juice-shop:3000/` → `httpStatus` 200, nenhum módulo em `ERROR` fora `CERT_TRANSPARENCY` e `CVE`, que dependem de serviço externo (BUG-16, ficha 3) |
| SCAN-02 | Motor | Alvo com atraso forçado no WireMock | Módulo marcado como timeout, relatório indica resultado parcial | RestAssured + WireMock | P1 | 🔲 A fazer |
| SCAN-03 | Motor | Varredura ativa no Juice Shop | XSS e SQL injection detectados | RestAssured | P0 | 🔲 A fazer, com alvo revisto — conexão (BUG-03) e SQLi (BUG-06) corrigidos: ativo no `/rest/products/search?q=apple` → `dbErrorLeakageSuspected:true`. XSS em alvo próprio (2026-11-15): stub `eco.json`, `eco-mock` (eco cru → `reflectedXssSuspected:true`) e `eco-escapado-mock` (controle → false com `xssProbePerformed:true`); no Juice Shop o `q` não ecoa no servidor e `false` ali está certo. Ficha 4 em `fichas-da-fila.md`. Teste `@Disabled` |
| SCAN-04 | Motor | Alvo limpo com headers corretos | Nenhum falso positivo nesses módulos | RestAssured + WireMock | P1 | 🔲 A fazer |
| SCAN-05 | Motor | Alvo que só anuncia `X-Powered-By: PHP/8.3.36` (stub `php-banner-mock`): CVE de extensão em que o PHP é só plataforma | `detectedVersions.PHP` = `8.3.36` (controle); `cveFindings` sem o CVE-2013-2220; nenhuma issue `CVE_*` nem nota "CVE potencial" no score | RestAssured + WireMock | P1 | 🔲 A fazer — BUG-08 corrigido no Backend (2026-11-07, `bugs/BUG-08-cve-de-extensao-como-cve-do-php.md`); contrato e armadilhas no HANDOFF §11 |
| SCAN-06 | Motor | DKIM por provedor: domínio com SPF da Hostinger e seletores `hostingermail-a` (chave) + `-b`/`-c` revogados (`p=` vazio); domínio só com revogados; domínio com TXT curinga `v=spf1` | 1º: `dnsSecurityResult.dkimHintFound` true e `dkimSelector` `hostingermail-a`; 2º e 3º: `NOT_DETECTED` | RestAssured + DNS falso | P2 | 🔲 A fazer — BUG-09 corrigido no Backend (2026-11-09, `bugs/BUG-09-dkim-hostinger-e-chave-revogada.md`); desbloqueado: DNS falso no ambiente (`env/coredns/`), alvos `dkim-hostinger`, `dkim-revogado` e `dkim-curinga` `.dns-mock.test`; contrato no HANDOFF §11, Etapa C |
| SCAN-07 | Motor | Risco de email spoofing pelo DNS falso: SPF `~all` + DMARC `p=quarantine`; quarantine com `pct=50`; `reject` com `pct=25`; SPF `+all` + `p=reject`; `p=none` | `dnsSecurityResult.emailSpoofingRisk`: LOW, MEDIUM, LOW, HIGH, MEDIUM; nota de spoofing em `score.notes` só nos MEDIUM/HIGH (-6, -12, -6) — os dois LOW sem nota nem issue `DNS_EMAIL_SPOOFING` | RestAssured + DNS falso | P1 | 🔲 A fazer — BUG-10 corrigido no Backend (2026-11-10, `bugs/BUG-10-spoofing-exigia-hardfail-e-ignorava-mais-all.md`); contrato no HANDOFF §11, Etapa C |
| SCAN-08 | Motor | Alvo sem `Strict-Transport-Security` (stub `sem-hsts-mock`) | `HSTS_MISSING` em `score.issues` com `severity` MEDIUM; nota `HSTS ausente: -10` em `score.notes` | RestAssured + WireMock | P2 | 🔲 A fazer — BUG-11 corrigido no Backend (2026-11-11, `bugs/BUG-11-hsts-ausente-rotulado-high.md`); contrato no HANDOFF §11, Etapa D |
| SCAN-09 | Motor | Alvo com cinco Set-Cookie (stub `cookies-mock`): sessão PHP sem HttpOnly, `XSRF-TOKEN` sem HttpOnly, `secure_pref` sem flag, `visitante` sem flag, `connect.sid` sem Secure | Em `cookieIssues`: `PHPSESSID` e `connect.sid` HIGH; `secure_pref` LOW com `secure:false`; `visitante` LOW; nenhum `XSRF-TOKEN`. Em `score.issues`, `INSECURE_COOKIES` com `severity` HIGH | RestAssured + WireMock | P1 | 🔲 A fazer — BUG-12 corrigido no Backend (2026-11-12, `bugs/BUG-12-cookies-atributo-por-substring.md`); contrato no HANDOFF §11, Etapa E |
| SCAN-10 | Pontuação | Alvo só com achados LOW de higiene (stub `pesos-mock`): versão no `Server`, sem Referrer-Policy, sem Permissions-Policy, `/admin/` no robots.txt respondendo 403, sem security.txt | Em `score.notes`: versão exposta e robots.txt `-2`; Referrer-Policy, Permissions-Policy e security.txt `-1`. Os cinco achados em `score.issues` com `severity` LOW | RestAssured + WireMock | P2 | 🔲 A fazer — BUG-14 corrigido no Backend (2026-11-13, `bugs/BUG-14-peso-low-igual-a-medium.md`); contrato no HANDOFF §11, Etapa F |
| SCAN-11 | Motor | Alvo com `Strict-Transport-Security: max-age=0` (stub `textos-mock`), scan em pt-BR e em en | `headers["Strict-Transport-Security"]` = `WEAK (max-age=0 apaga o HSTS)` em pt-BR e `WEAK (max-age=0 deletes HSTS)` em en | RestAssured + WireMock | P2 | 🔲 A fazer — BUG-15 corrigido no Backend (2026-11-14, `bugs/BUG-15-textos-do-laudo.md`); contrato no HANDOFF §11, Etapa G |
| SCAN-12 | Motor | Cache de scan: ativo na home e logo depois na busca do Juice Shop (qa-enterprise); passivo num endereço único por execução com o qa-enterprise e logo depois com o qa-pro; controle: o qa-pro repete o endereço | Busca com a `url` pedida e `dbErrorLeakageSuspected` true; `GET /history/juice-shop` do qa-pro com **1** item da `url` única, e ainda 1 depois do controle (acerto do cache) | RestAssured | P1 | 🔲 A fazer — BUG-17 corrigido no Backend (2026-11-18, `bugs/BUG-17-cache-de-scan-ignora-caminho.md`); contrato no HANDOFF §12, Etapa J; ficha 17 |
| SCAN-13 | Motor | Alvo inexistente ou sem resposta: `http://nao-existe-qa.invalid` (qa-free, cota lida antes e depois); `http://wiremock:9` (resolve, nada escuta na porta); controle: alvo que responde 404 | 1º: **422**, `error` `TARGET_NOT_FOUND`, `host` = o domínio, `remainingScans` igual antes e depois; 2º: **422**, `error` `TARGET_UNREACHABLE`, `host` `wiremock`, `message` sem "null"; controle: 200 com `httpStatus` 404 | RestAssured | P1 | 🔲 A fazer — BUG-16 corrigido no Backend (2026-11-22, `bugs/BUG-16-host-sem-resposta-ganha-nota.md`); contrato no HANDOFF §12, Etapa K; ficha 18 |
| SCAN-14 | Motor | Compliance (LGPD Art. 49) diz o mesmo que o card do cookie: `cookies-mock:8080/` (três cookies sem SameSite) e `cookies-mock:8080/so-csrf` (só um `XSRF-TOKEN` sem flags), qa-pro, pt-BR | Raiz: Art. 49 com o item "Cookies sem atributo SameSite" (e o de HttpOnly, controle). `/so-csrf`: Art. 49 com Secure e SameSite, **sem** HttpOnly; o `XSRF-TOKEN` com `problems` `["COOKIE_SEM_SECURE","COOKIE_SEM_SAMESITE"]` | RestAssured + WireMock | P2 | 🔲 A fazer — BUG-13 corrigido no Backend e no Frontend (2026-11-23, `bugs/BUG-13-compliance-samesite-nunca-dispara.md`); contrato no HANDOFF §12, Etapa M; ficha 20 |
| SCAN-15 | Motor | O painel "O que está em jogo" e o card do cookie dão a mesma resposta para "é sessão?": `cookies-mock:8080/nomes` (cinco cookies sem flag: `csrftoken`, `inside_banner`, `user_pref`, `_hjSession_1873402` e `laravel_session`), qa-pro, pt-BR | `impactIndicators` de `COOKIES`: um só, `laravel_session`. Card: `laravel_session` HIGH, `_hjSession_1873402` e os outros três LOW. `INSECURE_COOKIES` com título "1 cookie(s)…" | RestAssured + WireMock | P2 | 🔲 A fazer — BUG-21 corrigido no Backend (2026-11-23, `bugs/BUG-21-impacto-chama-token-csrf-de-sessao.md`); contrato no HANDOFF §12, Etapa N; ficha 21 |
| SCAN-16 | Motor | Cookie de analytics com "token", "sess" ou "auth" no nome não é sessão: `cookies-mock:8080/analytics` (`AMP_TOKEN`, `s_sess` e `_pin_unauth`, sem flag), qa-pro, pt-BR | Controle: `cookieIssues` com os três, todos LOW. Sem `INSECURE_COOKIES` em `score.issues`; sem `COOKIES` em `impactIndicators` | RestAssured + WireMock | P2 | 🔲 A fazer — BUG-22 corrigido no Backend (2026-11-23, `bugs/BUG-22-analytics-com-nome-de-sessao.md`); contrato no HANDOFF §12, Etapa O; ficha 22 |
| SCORE-01 | Pontuação | Scan com achado crítico e o resto limpo | Nota não fica alta (override de severidade) | RestAssured | P0 | 🔲 A fazer |

## Relatório e interface

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| REP-01 | Relatório | Download do PDF por usuário Pro | `application/pdf` válido, contém os achados do scan (checar com PDFBox) | RestAssured | P1 | ✅ Passou — RestAssured (2026-10-26; qa-enterprise → `GET /scan/report/pdf` → 200 `application/pdf`, magic `%PDF`, PDFBox confirma alvo + achados. Ator é enterprise, não qa-pro: PRO pessoal é verified-only e não tem domínio verificado. PDFBox 2.0.31 no pom) |
| REP-02 | Relatório | Achado conhecido no resultado do scan → mapeado para a norma | Mapeamento LGPD/ISO 27001 presente no `compliance` do `GET /scan` (NÃO no PDF — verificado 2026-10-26, o laudo exportável não traz o mapeamento) | RestAssured | P2 | ✅ Passou — RestAssured (2026-10-26; `GET /scan` → `compliance.lgpdItems`/`isoItems` presentes, achado conhecido mapeado: Art. 46 FAIL + A.8.24 FAIL). Escopo corrigido (estava "no PDF"; o mapeamento é na API) |
| UI-01 | Interface | Login completo com 2FA | Chega ao dashboard | Cypress, Selenium | P1 | ⚠️ Parcial — Cypress ✅ (2026-10-21; login + TOTP via cy.task → dashboard); Selenium pendente |
| UI-02 | Interface | Iniciar scan, acompanhar e ver no histórico | Resultado e nota exibidos | Cypress, Selenium | P1 | ⚠️ Parcial — Cypress **verde falso** (revisão 2026-11-15): o campo abre com "github.com", o `.type()` digita no fim e o scan vai para `github.comwiremock:8080/health`; passa porque host inexistente ganha nota (BUG-16) e `contains('wiremock')` casa com o host torto. Correção do spec é do dono (`fichas-da-fila.md`, fim). Com o fix do BUG-16 (2026-11-22), o host torto leva 422 e a tela não mostra nota: o spec como está deve ficar vermelho no `contains('/100')`. Selenium pendente |
| UI-03 | Interface | Criar, editar e remover agendamento recorrente | Alterações persistem após recarregar | Cypress | P2 | ✅ Passou — Cypress (2026-10-23; criar → pausar → persistir no reload → remover; host único idempotente) |
| UI-04 | Interface | Scan passivo de `quarantine-pct50.dns-mock.test` (DMARC `p=quarantine; pct=50` no DNS falso) e abrir o módulo Reconnaissance | Card DMARC mostra `p=quarantine; pct=50` — a política do registro, não o nível | Cypress | P2 | 🔲 A fazer — BUG-15 corrigido no Frontend (2026-11-14, `bugs/BUG-15-textos-do-laudo.md`); contrato no HANDOFF §11, Etapa G |

## Pagamento (checkout transparente — Pix + cartão)

Confirmado ao vivo em produção em 2026-09-30, com dinheiro real — ver
`Backend/HANDOFF.md` seção 2 para o histórico completo (webhook mal configurado no
painel do MP, CSP em 3 rodadas, `getSubscription` com bug de seleção, CPF virou
opcional no cartão). Os casos abaixo continuam válidos para automatizar contra o
ambiente de teste local (nunca contra produção — Regra 1).

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| PAY-01 | Pagamento | Checkout cartão com token válido (sandbox, cartão de teste aprovado) | 200, status AUTHORIZED, plano liberado na hora | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-16; MP mockado no WireMock, conta dedicada qa-pay-card) |
| PAY-02 | Pagamento | Checkout cartão com token de cartão de teste recusado | 502, plano continua FREE | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-16; stub rejeita card-recusado, conta qa-free) |
| PAY-03 | Pagamento | Checkout cartão sem cardTokenId | 400 | Postman, RestAssured | P1 | ⚠️ Parcial — Postman ✅ (2026-10-06); RestAssured pendente |
| PAY-04 | Pagamento | Checkout Pix com CPF válido | 200, QR code presente, plano continua FREE até confirmação | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-16; mock Pix com id único por chamada, conta qa-pay-pix) |
| PAY-05 | Pagamento | Checkout Pix com CPF de tamanho inválido | 400 | Postman, RestAssured | P1 | ⚠️ Parcial — Postman ✅ (2026-10-06); RestAssured pendente |
| PAY-06 | Pagamento | Webhook `payment` approved libera o plano e seta currentPeriodEnd | Plano liberado, período ~30 dias | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-18; checkout Pix → webhook HMAC válido → PRO/AUTHORIZED) |
| PAY-07 | Pagamento | Webhook `payment` rejected não derruba assinatura Pix já ativa | Status/plano inalterados | RestAssured | P1 | ✅ Passou — RestAssured (2026-10-21; webhook rejected no-op, continua AUTHORIZED) |
| PAY-08 | Pagamento | Webhook sem x-signature válido | 401, plano não muda | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-16; 401 "invalid signature", corpo texto puro) |
| PAY-09 | Pagamento | > 5 tentativas de checkout/minuto pelo mesmo usuário | 429 | RestAssured | P1 | ✅ Passou — RestAssured (2026-10-21; laço até 429, conta dedicada qa-pay-09) |
| PAY-10 | Pagamento | Job de expiração: assinatura Pix com currentPeriodEnd vencido | Conta rebaixada para FREE | RestAssured (gatilho de teste `/admin/test/expire-pix`, só no profile qa-docker) | P1 | ✅ Passou — RestAssured (2026-10-21; job expira a Pix e rebaixa a conta para FREE) |
| PAY-11 | Interface | Fluxo completo Pix na tela: QR aparece, copia-e-cola funciona, polling detecta confirmação | Tela mostra "plano ativo" sem reload manual | Cypress | P1 | ✅ Passou — Cypress (2026-10-26; conta dedicada qa-pay-11 + resetFree, webhook simulado via signMpWebhook, sucesso = card Pro vira "Cancelar assinatura"; idempotente no re-run) |
| PAY-12 | Interface | Fluxo completo cartão na tela: Secure Fields do MP carregam, submit gera token, checkout confirma | Tela mostra "plano ativo" | Cypress / Manual | P1 | ⚠️ Coberto por API (PAY-01/02/14) + verificação manual — E2E no Cypress inviável (Secure Fields em iframe cross-origin + tokenização no MP real); decisão 2026-10-26 (ver nota) |
| PAY-13 | Pagamento | Checkout Pix com CPF de 11 dígitos mas dígito verificador inválido | 400 (`CpfUtil` rejeita desde 2026-09-29) | RestAssured | P1 | ✅ Passou — RestAssured (2026-10-16; "dígito verificador não confere") |
| PAY-14 | Pagamento | Checkout cartão sem CPF nenhum (cliente estrangeiro) | 200/token gerado — CPF é opcional desde 2026-09-30, backend nunca recebeu CPF nesse fluxo | RestAssured, Cypress | P1 | ⚠️ Parcial — RestAssured ✅ (2026-10-16, conta qa-pay-nocpf); Cypress pendente |
| PAY-15 | Pagamento | `GET /billing/subscription` com um Pix AUTHORIZED mais antigo e um Pix PENDING mais novo na mesma conta | Devolve o AUTHORIZED, não o mais recente por data — regressão do bug corrigido em 2026-09-30 (`currentSubscription()`) | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-20; cenário semeado, GET devolve AUTHORIZED) |

## Notas de status (2026-09-30)

- Cartão: autorização de verdade (não só rejeição) ainda não testada — falta
  credencial `TEST-` de sandbox ou um cartão real. PAY-01 permanece não confirmado
  de ponta a ponta; PAY-02 confirmado (cartão de teste público do MP rejeitado em
  produção, como esperado).
- Pix: PAY-04 e PAY-06 confirmados de ponta a ponta em produção, com pagamento
  real. A configuração do webhook no painel do MP (URL, evento "Pagamentos"
  subscrito, secret correto) precisa ser replicada no ambiente de teste/sandbox
  quando a Fase 1 (Docker) for montada — não é algo que o código resolve sozinho.
- PAY-13, PAY-14 e PAY-15 são casos novos, criados a partir de bugs reais
  encontrados e corrigidos nesta sessão — bons candidatos a "bug real encontrado
  no CyberAudit" para o marco do CV (ver `plano-de-teste.md`).
- **SCAN-01, possível bug real encontrado montando a Fase 1 (2026-09-30).**
  Rodei `GET /scan?url=http://juice-shop:3000&active=true` de verdade contra o
  ambiente Docker (login + 2FA reais, `PLATFORM_STAFF_EMAILS` pra pular a posse
  de domínio). O scan completou (200), mas com `httpStatus:0`,
  `sslInfo.message:"Erro ao verificar certificado: Unsupported or unrecognized
  SSL message"` e `headers.error:"HTTP/1.1 header parser received no bytes"` —
  parece que o scanner tenta HTTPS no host:porta independente do esquema da URL
  de entrada, e quando isso falha contra um alvo HTTP puro numa porta
  não-padrão (como o Juice Shop, 3000), os módulos de headers/TLS erram em
  cascata em vez de cair pro HTTP puro. Também demorou ~2min por causa de
  `CrtShService` tentando consultar `crt.sh`/`certspotter` de verdade pra um
  hostname que não existe na internet (timeout real, não bug — mas vale
  considerar mockar isso via WireMock nesse ambiente). Não investiguei a causa
  raiz nem mexi no código do motor de scan — é exatamente o tipo de achado que
  SCAN-01 deveria registrar como bug, não ajustar o teste pra esperar isso.

## 2026-10-01 — Fase 2 (Postman) iniciada

AUTH-01 escrito e rodando verde via Newman (`newman run collection.json -e
environment.local.json`): 1 request, 3 assertions, 0 falhas. Primeiro caso real
da Fase 2, autoria do usuário.

## 2026-10-03 — Fase 3 (RestAssured) iniciada

Projeto Maven em `api-restassured/` montado (`pom.xml`, `TestConfig` como classe-base
com `@BeforeAll` que fixa o `baseURI`, `config.properties`). `AuthTest` com 3 testes
verdes contra o ambiente Docker: AUTH-01 (login válido → 200 + `requires2fa: true`) e
AUTH-02 (senha errada e usuário inexistente → 401, ambos com `{"error":"Autenticação
necessária."}`). O login válido funciona também como controle do AUTH-02: um path
inexistente devolve o mesmo 401 com o mesmo corpo, então só o 200 no path certo prova
que o 401 é de credencial rejeitada. Autoria do usuário.

## 2026-10-05 — AUTH-03 (RestAssured) verde

`AuthTokenTest` (classe nova, `extends TestConfig`) cobre o AUTH-03 com 6 testes
verdes contra o ambiente Docker, batendo em `GET /history/recent` (rota
`authenticated()`): token válido forjado → 200 (controle positivo), e expirado,
assinatura adulterada, `alg:none`, assinado com outro segredo e sem token → 401 com
`{"error":"Autenticação necessária."}`. Os tokens são forjados em Java com o jjwt
0.12.6 (mesma lib do Backend) usando o `jwt.secret` do ambiente de teste — fluxo que
dispensa o 2FA. O controle positivo é obrigatório: um 401 de path/rota errada é
idêntico, então só o 200 no mesmo path prova que os 401 são recusa real do token.
Autoria do usuário. (Pendente de registro à parte: no SSRF-03, a forma hex
`0x7f000001` não é normalizada pelo guard e passa — candidato a bug, ver conversa.)

## 2026-10-05 — RATE-01 adiado (decisão B registrada)

Contrato verificado: o rate limit "por usuário/IP" é o `RateLimitService` em
`ScanController.checkRateLimit`. **OWNER é isento** (`allow()` retorna true direto) e
os 3 usuários do seed são OWNER, então nenhum deles dispara o limite. Como guest são
5 req/min por IP (429 `{"error":"429 TOO_MANY_REQUESTS","message":"Muitas requisições.
Limite de 5 requests/min para visitantes."}`), mas esse limite **colide com o diário
de guest** (`GuestRateLimitService` = 5 scans/dia por IP, persistido no Postgres):
os 5 requests que esvaziam o balde de 1 min também esgotam os 5/dia, então o 7º
request pós-janela vira `DAILY_LIMIT_REACHED`, nunca 200 — a parte "volta a 200" fica
inobservável como guest.

**Decisão (dono, 2026-10-05): opção B.** Semear um usuário NÃO-OWNER (ex.
`FREE_EMPLOYEE` na conta enterprise, que tem scan diário ilimitado → sem colisão),
RPM 60; o 61º request → 429 e, com o refill ~1 token/seg, o próximo ~1-2s depois →
200 (testa os dois lados). Exige alteração no `seed.sql` (infra) + ~61 requests no
teste. **Retomar depois** — foco atual é Postman, RestAssured e iniciar o Cypress.

**Superado em 2026-11-23 (BUG-20).** A isenção do OWNER era bug: todo cadastro é OWNER.
Com o fix, o limite segue o plano (FREE 5/min), e o qa-free mostra o 429 no
`verify-check`, que não tem cota diária. A decisão B (semear membro de equipe) não é mais
necessária.

## 2026-10-05 — AUTH-05 vira bug (API key quebrada)

Ao verificar o contrato do AUTH-05 (chave revogada → 401), descobri que a API key
NÃO autentica de jeito nenhum: toda chamada com `X-Api-Key`, inclusive chave válida
recém-criada no próprio `/api-keys/ci`, dá 401 por `LazyInitializationException` em
`ApiKeyAuthFilter:54` (`user.getAuthorities()` sobre o proxy lazy de `createdBy`, fora
da sessão). Registrado em `bugs/BUG-01-apikey-auth-lazyinit.md` (Backend `8d32e2a`).
Impacto no caso: AUTH-05 não pode virar verde enquanto o bug existe — a chave válida
também dá 401, então não há controle positivo e "revogada → 401" passaria pelo motivo
errado. Caso marcado 🐛; automação (RestAssured/Postman) só depois do fix no Backend.

## 2026-10-06 — DOM-01 no Postman verde

DOM-01 fechado nas duas ferramentas. Postman: 3 requests / 8 asserts verdes via
Newman — AUTH-01 (200) + DOM-01 scan ativo em domínio não verificado (403,
`error=ACCOUNT_DOMAIN_NOT_VERIFIED`, `host=juice-shop:3000`) + controle sem token
(401, `error="401 UNAUTHORIZED"`). O token de sessão do qa-pro vem de um pre-request
nível collection que forja JWT HS256 com CryptoJS e o `jwtSecret` do ambiente
(dispensa 2FA) — infra do Claude; os `pm.test` são autoria do dono. RestAssured já
estava verde (ActiveScanDomainTest, 2026-10-05).

## 2026-10-08 — AUTHZ-01 (RestAssured) verde

`ScanHistoryAuthzTest` (extends TestConfig), 4 testes verdes contra o ambiente Docker.
`@BeforeAll` forja o token do enterprise (B), cria um scan passivo (`GET
/scan?url=http://wiremock:8080/health&active=false` → 200, ~persiste um ScanRecord) e
lê o `id` em `/history/recent`. Testes: controle (B lê o próprio id → 200 com corpo),
AUTHZ-01 cross-account (A=qa-pro lê o id do B → 404 corpo vazio), id inexistente (404
vazio) e sem token (401). Cross-account e id-inexistente são indistinguíveis (404
vazio, "não confirma existência") — por isso o controle 200 do dono é obrigatório pra
provar que o 404 do A é dado escondido, não id inválido. Forja via jjwt com o
`jwt.secret` do ambiente (dispensa 2FA). Autoria do dono.

## 2026-10-11 — SSRF-03: bug na forma hex (BUG-02)

Verificação do SSRF-03: decimal (`2130706433`) e curta (`127.1`) → 403 DOMAIN_BLOCKED;
hex (`0x7f000001`) → **200** (não bloqueado, `httpStatus:0`). O guard usa
`InetAddress.getAllByName`, que converte decimal/curta pra 127.0.0.1 mas não parseia o
hex → vira hostname que não resolve → o scan segue. Não é exploração (não alcança o
loopback), mas é gap de normalização. Registrado em `bugs/BUG-02-ssrf-hex-nao-normalizado.md`
(Backend `8d32e2a`). O teste do SSRF-03 cobre decimal/curto (403) e documenta a forma
hex como known-issue (ver abaixo) até o fix.

## 2026-10-12 — Bloco anti-SSRF (01–06) fechado

SSRF-01/02/04 (loopback, privados, hostname→IP privado) e SSRF-06 (allowlist inerte
na 8099) → 403 DOMAIN_BLOCKED. SSRF-03: decimal/curto → 403; forma hex `0x7f000001`
não normalizada (BUG-02), coberta por teste `@Disabled`. SSRF-05: redirect 302 para
`169.254.169.254` NÃO é seguido — scan volta 200, `finalUrl` fica na URL de entrada e
o módulo `headers` marca `error` com "Scan bloqueado... endereço interno/privado". Pra
isolar o cache de scan (por host, 2 min) do AUTHZ-01, o SSRF-05 usa o alias de rede
`redirect-mock` (mesmo WireMock, host distinto de "wiremock"); rodados juntos, zero
colisão. Classes: `SsrfGuardTest` (01-04, 06) e `SsrfRedirectTest` (05). Autoria do dono.

## 2026-10-15 — SCAN-01 reproduzido (BUG-03), SCAN-03 bloqueado

Verificação do SCAN-03: scan ATIVO de `http://juice-shop:3000/rest/products/search?q=apple`
(qa-enterprise, staff) → 200 em ~121s, mas `httpStatus:0`, `error:"...HTTP/1.1 header
parser received no bytes"`, `openPorts:[]`. Gating correto (`inputSurfaceDetected:true`,
`xssProbePerformed:true`), porém `reflectedXssSuspected:false` e `dbErrorLeakageSuspected:false`
— o scanner não conecta no alvo HTTP puro em porta não-padrão, então os probes não têm
resposta pra analisar. Isso reproduz o SCAN-01 (antes "suspeito") e o registra como
BUG-03 (`bugs/BUG-03-scan-http-porta-nao-padrao.md`, Backend 8d32e2a). SCAN-03 fica 🐛
bloqueado: teste `@Disabled` guardando a expectativa correta (XSS+SQLi detectados) até o
fix do motor. Mesmo bug afeta alvos HTTP locais (juice-shop:3000, wiremock:8080) — atenção
em SCAN-02/04 e SCORE-01.

## 2026-10-16 — PAY-01 verde (mock do Mercado Pago)

Primeiro caso de pagamento com o MP mockado. Patch no Backend tornou a URL base do MP
configurável (`mp.base-url`, default produção); no ambiente de teste `MP_BASE_URL` aponta
pro WireMock, `MP_ACCESS_TOKEN` é dummy (só pra `isConfigured()`), e stubs em
`env/wiremock/mappings/mp-*.json` simulam preapproval (aprovado/recusado) e payment Pix.
PAY-01: `POST /billing/checkout/card {cardTokenId:"card-ok"}` → 200, plano PRO, status
AUTHORIZED. Checkout MUTA estado (upgrade persiste), então cada teste de upgrade usa uma
conta FREE dedicada (`qa-pay-*` no seed); re-rodar no mesmo ambiente exige reset
(`down -v` ou UPDATE do plano) — em CI o ambiente é novo a cada run. `card-recusado` é o
token que o stub rejeita (PAY-02). Autoria do dono.

## 2026-10-21 — Módulo de pagamento (PAY-*) fechado no RestAssured

PAY-01/02/04/06/07/08/09/10/13/14/15 verdes no RestAssured + PAY-03/05 no Postman.
Os únicos PAY restantes são PAY-11/PAY-12, que são de UI (Cypress). Infra-chave do
módulo: Mercado Pago mockado no WireMock (patch `mp.base-url` configurável no Backend),
contas FREE dedicadas por caso que muta estado + `TestConfig.resetarContaParaFree`
(idempotência via `@BeforeEach`), cenários complexos semeados (PAY-15 AUTHORIZED+PENDING,
PAY-07 AUTHORIZED, PAY-10 Pix vencida via `prepararPixVencida`), e um gatilho de teste
`/admin/test/expire-pix` gated por `@Profile("qa-docker")` no Backend para o job de
expiração (PAY-10). Nenhum teste bate no MP real nem em produção (Regra 1).

## 2026-10-26 — PAY-11 (Pix na tela) verde no Cypress

`e2e-cypress/cypress/e2e/ui-11-pix.cy.js` fecha o fluxo Pix na UI: badge FREE → Planos →
Assinar Pro → aba Pix → CPF → Gerar Pix → QR + copia-e-cola (stub do `navigator.clipboard`)
→ webhook simulado → polling detecta e a tela mostra o plano ativo sem reload. Autoria do
dono; o Claude só revisou/rodou. Infra nova (Claude): conta FREE dedicada `qa-pay-11`
(seed + chaves no `cypress.config.js`) e dois `cy.task` — `resetFree` (devolve a conta a
FREE via psql do container, no `beforeEach` → idempotente no re-run) e `signMpWebhook`
(assina o webhook HMAC-SHA256 com o `mpWebhookSecret` e devolve headers+body prontos). O
`cy.request` do webhook vai pro `Cypress.env('apiUrl')` (8081), não pro `baseUrl` (8090).
Armadilha registrada: no sucesso o `onSuccess` fecha só o CheckoutModal, o PlansModal
continua aberto cobrindo o header — por isso a prova de sucesso é o card Pro virar
"Cancelar assinatura" (visível no modal aberto), não o badge PRO do header (coberto).
Restam só PAY-12 (cartão na tela, Secure Fields em iframe cross-origin — provável inviável
no Cypress) e o Selenium das telas (fase opcional).

## 2026-10-26 — PAY-12 (cartão na tela): E2E inviável no Cypress, coberto por API + manual

Decisão (dono, 2026-10-26): **opção A** — não automatizar o fluxo de cartão no Cypress.
Verificado no código e ao vivo: os três campos de cartão (número, validade, CVV) são
**iframes servidos de `https://secure-fields.mercadopago.com/`** (origem ≠ `localhost:8090`),
e o Cypress não digita em iframe cross-origin. Além disso o `createCardToken` do SDK vai pro
**MP real** (o build do 8090 tem chave de **produção** `APP_USR-…` em `Frontend/.env`), e o
WireMock só intercepta as chamadas do **backend** ao MP, não as do navegador — ou seja, a
tokenização sairia do ambiente e bateria em produção (fere a Regra 1). Logo, o E2E "de
verdade" é inviável. (Alternativa avaliada e descartada: mockar `window.MercadoPago` — dá
verde no Cypress porque `loadMercadoPagoSdk` faz `if (window.MercadoPago) return`, mas dubla
justamente os Secure Fields e a tokenização, então provaria só a cola de UI, que o PAY-01 de
API já cobre; não compensa o acoplamento ao SDK + o risco de falsa cobertura.)

**Cobertura efetiva:** a lógica de checkout de cartão está nos testes de API — PAY-01
(token válido → AUTHORIZED/PRO), PAY-02 (token recusado → 502/FREE), PAY-14 (sem CPF →
AUTHORIZED) — todos com o MP mockado no WireMock. A casca de UI do cartão fica como
**verificação manual roteirizada**.

**Roteiro manual (repetível):** pré-requisito — trocar a `VITE_MP_PUBLIC_KEY` do build do
frontend por uma chave **de sandbox** (`TEST-…`) antes do teste; com a chave de produção
atual os cartões de teste do MP são rejeitados (gap de config a resolver quando for rodar o
manual). Com a chave sandbox: 1) subir o ambiente, logar numa conta FREE; 2) badge do plano
→ Planos → Assinar "Pessoal Pro" → aba **Cartão**; 3) conferir que os três Secure Fields
montam (iframes de `secure-fields.mercadopago.com`) e o "Pagar" habilita; 4) preencher Nome,
e um **cartão de teste aprovado do MP** nos iframes; 5) **Pagar** → esperar o estado de
sucesso (plano ativo — igual ao PAY-11, o card Pro vira "Cancelar assinatura"). Registrar
print. Nunca com cartão/produção reais (Regra 1).

Com isso o módulo de pagamento está **fechado**: PAY-01..15 cobertos (API + Pix na UI),
PAY-12 documentado como manual. Próximos de UI: **PLAN-01 na tela** (P0, Free não exporta
PDF) e o **Selenium** das telas (fase opcional).

## 2026-10-26 — PLAN-01 na tela verde; AUTH-04 achou bug (TOTP replay), automação adiada

- **PLAN-01 na tela ✅** (`e2e-cypress/cypress/e2e/plan-01-pdf.cy.js`): Free vê o botão "PDF"
  desabilitado + title "Exportar PDF requer plano PRO ou superior"; controle positivo no Pro
  (habilitado, outro title). Fecha o P0 nas duas ferramentas (RestAssured já estava).
- **AUTH-04 → BUG-04 (replay de TOTP), P0.** Verificando o contrato do fluxo 2FA real
  (`/auth/login` → pré-auth token → `/auth/2fa/verify`), confirmei ao vivo que **o mesmo
  código TOTP válido pode ser reusado** (2º `verify` com o mesmo código → 200 + nova sessão):
  o `verifyLoginCode` não consome o código (só o Email OTP marca `used`). Os outros dois
  sub-casos negam certo (sem código / inválido → 422 `message:"Código TOTP inválido."`).
  Registrado em `docs/bugs/BUG-04-totp-replay.md`; automação **adiada** (a pedido do dono).
  Quando for escrever: sub-casos sem-código/inválido + controle válido verdes, reuso
  `@Disabled` até o fix; precisa de lib de TOTP no `pom.xml` (ex. `dev.samstevens.totp`) e
  uma conta 2FA dedicada no seed (throttle de 5 falhas/15 min por usuário).

## 2026-10-26 — REP-01 verde; REP-02 reescopado (mapeamento está na API, não no PDF)

- **REP-01 ✅** (`RelatorioPdfTest`, RestAssured): qa-enterprise → `GET /scan/report/pdf` →
  200 `application/pdf`, magic `%PDF`, PDFBox (2.0.31, novo no pom) confirma alvo + achados.
  Ator é enterprise de propósito: qa-pro é PRO pessoal `reportOnVerifiedOnly` sem domínio
  verificado → `checkReportDelivery` bloqueia (402/403).
- **REP-02 reescopado.** A matriz dizia "mapeamento LGPD/ISO no PDF", mas a extração completa
  do PDF do REP-01 não tem LGPD/ISO nenhum. O mapeamento (`ComplianceMappingService` →
  `ComplianceReport`) vem no **JSON do `GET /scan`**, campo `compliance`
  (`lgpdItems` "Art. 46".."50", `isoItems` "A.8.x"/"A.5.x", cada um com PASS/FAIL +
  `overallScore`/`riskLevel`). REP-02 vira teste de **API** (RestAssured sobre o JSON), sem
  PDFBox. Confirmado ao vivo (qa-enterprise, wiremock:8080/health): Art. 46 FAIL, A.8.24
  (criptografia) FAIL — coerente com o alvo HTTP sem HTTPS/cert inválido.
- **Observação de produto (não é bug):** o PDF exportável — o arquivo que vai pra auditoria —
  **não inclui** o mapeamento de compliance que a UI/API mostram. Candidato a melhoria futura.

## 2026-10-29 — BUG-01 corrigido; AUTH-05 desbloqueado

Fix no Backend: a query de candidatas da API key passou a trazer o dono e a conta dele
(`JOIN FETCH`), então o `ApiKeyAuthFilter` não toca mais em proxy lazy fora da sessão.
Regressão no próprio Backend (`ApiKeyAuthIntegrationTest`, vermelho antes / verde depois)
e confirmação ao vivo no ambiente Docker: chave válida → 200, revogada → 401. AUTH-05 sai
de 🐛 e vai pra 🔲 — o caso agora tem controle positivo possível. Automação é do dono.

## 2026-10-29 — BUG-02 corrigido; SSRF-07 novo (BUG-05)

- **BUG-02 corrigido no Backend.** O guard passou a recusar IPv4 fora da forma canônica
  `a.b.c.d` (hex, octal, inteiro, curta) antes de resolver. Na revisão apareceu uma
  divergência pior que o hex: o JDK lê `0177.0.0.1` como 177.0.0.1 (público, passava),
  navegador/curl leem 127.0.0.1. Ao vivo: as cinco formas → 403. SSRF-03 fica 🔲 só até o
  dono tirar o `@Disabled` do hex; o resto do bloco SSRF continua verde (rodado sem
  alteração: 15 verdes, 1 pulado).
- **SSRF-07 novo (P1), BUG-05.** `GET /scan/verify-check` (público) com host **com porta**
  (`127.0.0.1:8081`, `localhost:8081`, `169.254.169.254:80`) → 200 e o backend faz a
  requisição de saída; sem a porta → 403. SSRF cego (só `verified:true/false` volta),
  `bugs/BUG-05-ssrf-host-com-porta.md`. Próxima etapa de fix.

## 2026-10-29 — BUG-05 corrigido; SSRF-07 desbloqueado

Fix no Backend em duas camadas: o `validateHost` separa a porta e recusa delimitadores de
URL (`@ / ? #`, espaço), e o `verify-check` passa a validar a URL que realmente requisita.
Durante o fix apareceram mais variantes da mesma causa além da porta (espaço no fim, `/`,
`#`, `?`, `[::1]:443`, `x@<hostname interno>`) — todas cobertas; detalhe na tabela do
`bugs/BUG-05-ssrf-host-com-porta.md`. Ao vivo: 9 variantes → 403; `example.com:443` e
`juice-shop:3000` → 200. SSRF-07 sai de 🐛 e vai pra 🔲 (automação do dono).

## 2026-10-29 — BUG-03 corrigido; SCAN-01 desbloqueado; BUG-06 novo

A hipótese de TLS estava errada: o `HttpClient` do JDK sai em HTTP/2, e num alvo `http://` isso
vira um pedido de upgrade h2c que o Juice Shop (Node/socket.io) derruba sem responder. Fix no
Backend: os 22 clientes que falam com o alvo passam a usar HTTP/1.1 (`ScannerHttp.newClientBuilder()`).
Ao vivo: passivo e ativo no Juice Shop → `httpStatus` 200, nenhum módulo em `ERROR`; WireMock idem.
SCAN-01 sai de 🐛 e vai pra 🔲. SCAN-03 continua bloqueado por outros dois motivos:

- **BUG-06 novo (P1).** O probe de erro de SQL não reconhece `SQLITE_ERROR` (SQLite do Node):
  o Juice Shop vaza o erro e o scan diz que não. `bugs/BUG-06-probe-sql-nao-reconhece-sqlite.md`.
- **Expectativa de XSS do SCAN-03 a rever.** `/rest/products/search` não ecoa o `q` na resposta;
  o XSS de busca do Juice Shop é DOM (no navegador). Um probe do lado do servidor dar `false`
  ali está certo — o caso precisa de outro alvo para a parte de XSS.

## 2026-11-01 — BUG-06 corrigido; SQLi do SCAN-03 detectado

O probe de erro de SQL passou a reconhecer o SQLite como cada driver o imprime (`sqlite_error`,
`sqliteerror`, `sqliteexception`, `sqlite3::`; antes só o do Python). Ao vivo: ativo no Juice Shop
→ `dbErrorLeakageSuspected:true`. SCAN-03 sai de 🐛 e vai pra 🔲: o que falta é do caso, não do
produto — um alvo que ecoe o parâmetro para a parte de XSS.

## 2026-11-01 — BUG-04 corrigido; AUTH-04 desbloqueado; rodada de bugs fechada

O código TOTP passou a valer uma vez: o Backend grava a janela de 30 s do último código aceito
e só aceita uma mais nova, numa UPDATE condicional (dois pedidos simultâneos com o mesmo código
não passam os dois — antes, 6 de 6 passavam). Vale para o login e para a ativação do TOTP.
Ao vivo: mesmo código com o mesmo pré-auth → 200 e depois 422 "já utilizado"; o código da
janela seguinte passa. AUTH-04 sai de 🐛 e vai pra 🔲. Com isso os seis bugs registrados
(BUG-01 a BUG-06) estão corrigidos; o que sobra é a automação dos casos destravados (dono).

## 2026-11-02 — BUG-07 corrigido (probe de SQL com `Accept` inválido)

A observação lateral do BUG-06 (`Accept: ...,/;q=0.8`, era `*/*`) virou bug ao ser testada
contra um alvo em Spring: o Spring recusa a requisição inteira antes do controller, então o
probe de SQL nunca via erro num alvo Spring. Fix de um caractere no Backend, com regressão lá
(`bugs/BUG-07-probe-sql-accept-invalido.md`). Nenhum caso da matriz muda de status: o alvo de
SQLi do SCAN-03 é Node, que tolera o cabeçalho. Rodada de bugs fechada com BUG-01 a BUG-07.

## 2026-11-07 — BUG-08 corrigido (CVE de extensão como CVE do PHP); SCAN-05 novo

Rodada nova, a partir do feedback de um cliente sobre o laudo (etapas A–G no HANDOFF §11).
Etapa A: a correlação de CVE consultava o NVD por `cpeName` sem `isVulnerable`, e o NVD
devolvia CVEs em que o PHP é só plataforma — um PHP 8.3.36 levou o CVE-2013-2220 (extensão
PECL radius, CVSS 7.5) e -6 no score. Fix no Backend em duas camadas (parâmetro na consulta
e conferência do lado vulnerável no parse), com regressão lá
(`bugs/BUG-08-cve-de-extensao-como-cve-do-php.md`). Caso novo SCAN-05 (P1, 🔲) com stub
próprio no WireMock (`php-banner-mock`, alias novo no compose).

## 2026-11-09 — BUG-09 corrigido (DKIM da Hostinger e chave revogada); SCAN-06 novo

Etapa B do feedback de cliente (HANDOFF §11). A sonda de DKIM não conhecia a Hostinger
(laudo dizia "não detectado" num domínio com DKIM em `hostingermail-a`) e contava qualquer TXT
no seletor como DKIM — inclusive `v=DKIM1;p=`, chave revogada, que é como a Hostinger deixa
o `-b` e o `-c`. Fix no Backend com regressão lá (`bugs/BUG-09-dkim-hostinger-e-chave-revogada.md`).
Sem efeito no score (DKIM não entra no risco). Caso novo SCAN-06 (P2, 🔲), que depende de um
DNS falso no ambiente — montado na etapa C, que precisa dele para as variações de DMARC/SPF.

## 2026-11-10 — BUG-10 corrigido (régua do risco de spoofing); SCAN-07 novo; DNS falso

Etapa C do feedback de cliente (HANDOFF §11). O risco de spoofing só saía LOW com SPF `-all`
e DMARC `p=reject`: `~all` + `p=quarantine` perdia -6, o `pct=` era ignorado e o `+all` (que
anula o DMARC) saía MEDIUM. Fix no Backend com regressão lá
(`bugs/BUG-10-spoofing-exigia-hardfail-e-ignorava-mais-all.md`). Infra nova: CoreDNS no
compose com a zona `dns-mock.test` (`env/coredns/`), que desbloqueia o SCAN-06. Caso novo
SCAN-07 (P1, 🔲).

## 2026-11-11 — BUG-11 corrigido (HSTS ausente rotulado HIGH); SCAN-08 novo

Etapa D do feedback de cliente (HANDOFF §11). `HSTS_MISSING` saía HIGH e o rótulo acionava o
piso de risco do score: site sem outro achado, nota 90, saía "risco médio". Agora MEDIUM,
com os mesmos -10; as recomendações de HSTS ausente e fraco passam a dar a mesma meta (1 ano)
e o mesmo piso (30 dias). Fix no Backend com regressão lá
(`bugs/BUG-11-hsts-ausente-rotulado-high.md`). Caso novo SCAN-08 (P2, 🔲) com stub próprio no
WireMock (`sem-hsts-mock`, alias novo no compose).

## 2026-11-12 — BUG-12 corrigido (cookies); SCAN-09 novo; BUG-13 aberto

Etapa E do feedback de cliente (HANDOFF §11). A análise de cookies lia flag por substring
(`secure_pref` saía com Secure), tratava `XSRF-TOKEN`/`csrftoken` como sessão (todo Laravel
padrão perdia -10 por falta de HttpOnly), não via `connect.sid`, cobrava -5 de cookie que não é
de sessão e rotulava o agregado sempre MEDIUM. Fix no Backend com regressão lá
(`bugs/BUG-12-cookies-atributo-por-substring.md`). Caso novo SCAN-09 (P1, 🔲) com stub próprio
no WireMock (`cookies-mock`). Achado lateral registrado e não corrigido: BUG-13 (item de
SameSite do compliance nunca dispara).

## 2026-11-13 — BUG-14 corrigido (peso do LOW); SCAN-10 novo

Etapa F do feedback de cliente (HANDOFF §11). Achado LOW custava de 3 a 5 pontos, o mesmo que
um MEDIUM. Agora nenhum LOW passa de 2: -2 para o que entrega informação (versão exposta,
robots.txt, TRACE, UI de docs de API), -1 para boa prática ausente (security.txt,
Permissions-Policy, Referrer-Policy, WAF). Fix no Backend com regressão lá
(`bugs/BUG-14-peso-low-igual-a-medium.md`). Caso novo SCAN-10 (P2, 🔲) com stub próprio no
WireMock (`pesos-mock`).

## 2026-11-14 — BUG-15 corrigido (textos do laudo); SCAN-11 e UI-04 novos

Etapa G do feedback de cliente (HANDOFF §11), a última. Cinco textos errados sem mudar a
nota: "null" no bônus de WAF, porta 80 LOW quando só redireciona, módulos no idioma do
servidor (pool sem `IdiomaThreads`), HSTS `max-age=0` em inglês fixo e o card DMARC com o
nível no lugar da política ("p=medium"). Fix no Backend e no Frontend com regressão lá
(`bugs/BUG-15-textos-do-laudo.md`). Casos novos SCAN-11 (P2, 🔲, stub `textos-mock`) e
UI-04 (P2, 🔲, alvo do DNS falso).

## 2026-11-15 — Fichas da fila; BUG-16, BUG-17 e BUG-18 abertos; UI-02 verde falso

Fichas dos 14 casos da fila do dono em `fichas-da-fila.md`, com cada contrato reconferido ao
vivo depois das etapas A–G. Infra nova para o XSS do SCAN-03: stub `eco.json` (`eco-mock` cru
e `eco-escapado-mock` como controle negativo). Três achados laterais registrados e não
corrigidos:
- BUG-16: host que não responde ganha nota, e módulo que não verificou sai "OK".
- BUG-17: o cache de scan ignora caminho, query e conta.
- BUG-18: conta FREE cria API key.

Na revisão, o UI-02 (Cypress) saiu de ✅ para ⚠️: o scan dele vai para um host torto, e o
teste passa mesmo assim.

## 2026-11-17 — BUG-18 corrigido (FREE criava API key); PLAN-03 novo; BUG-19 aberto

Primeira etapa da rodada dos achados laterais (HANDOFF §12). Criar API key passa a ser de
plano pago (PRO, ENTERPRISE e equipe da plataforma). A checagem antiga saía cedo para o
papel OWNER, e todo cadastro é OWNER; a tela tinha a mesma regra. Chave que já existe
continua valendo e aparece para ser revogada (decisão do dono). Fix no Backend e no
Frontend, com regressão lá (`bugs/BUG-18-free-cria-api-key.md`). Caso novo PLAN-03 (P1, 🔲).

Achado lateral registrado e não corrigido: BUG-19. O gate de CI (`/api-keys/ci`) roda o
scan sem cota diária, sem rate limit e sem a trava de plano do scan ativo.

Infra: o seed do PAY-15 passa a renovar as assinaturas a cada carga. A AUTHORIZED tinha
vencido no banco local, 28 dias depois da primeira carga.

## 2026-11-17 — BUG-19 corrigido (gate de CI sem cota); PLAN-04 novo; BUG-20 aberto

Segunda etapa da rodada dos achados laterais (HANDOFF §12, Etapa I). O gate de CI passa
pelas mesmas travas do `/scan` antes de escanear: rate limit, cota diária e, no ativo, a
trava de plano e de domínio verificado na conta. As travas saíram do `ScanController` para
o `ScanLimitsService`, que os dois usam. Regressão no Backend
(`bugs/BUG-19-gate-de-ci-sem-cota.md`). Caso novo PLAN-04 (P1, 🔲).

Achado lateral registrado e não corrigido: BUG-20. O rate limit por minuto isenta o papel
OWNER, e todo cadastro é OWNER, então nenhuma conta registrada tem limite por minuto. A
isenção já constava da nota do RATE-01 (2026-10-05) como contrato. A correção pede decisão
do dono (quanto por minuto em cada plano).

## 2026-11-19 — BUG-17 corrigido (cache de scan por host); SCAN-12 novo

Terceira etapa da rodada dos achados laterais (HANDOFF §12, Etapa J). A chave do cache de
scan passa a ser conta + endereço completo + modo + idioma. Decisões do dono: caminho e
query em todo modo, e a conta na chave; membros da mesma conta dividem a entrada, e
visitantes dividem uma entre si. Regressão no Backend
(`bugs/BUG-17-cache-de-scan-ignora-caminho.md`). Caso novo SCAN-12 (P1, 🔲).

A armadilha de cache do SCAN-03 (ficha 4) deixou de valer.

## 2026-11-22 — BUG-16 corrigido (alvo sem resposta ganhava nota); SCAN-13 novo

Quarta etapa da rodada dos achados laterais (HANDOFF §12, Etapa K). Decisões do dono:
- o domínio que não resolve leva 422 `TARGET_NOT_FOUND` antes de gastar cota;
- o host que resolve, mas onde nada aceita conexão nem em http nem em https, leva 422
  `TARGET_UNREACHABLE` (a cota já foi gasta);
- o NVD e os logs de CT que não respondem marcam o módulo `ERROR`, que entra no aviso de
  resultado parcial, em vez de "OK".

Regressão no Backend e no Frontend (`bugs/BUG-16-host-sem-resposta-ganha-nota.md`). Caso
novo SCAN-13 (P1, 🔲).

Muda o SCAN-01: `CERT_TRANSPARENCY` e `CVE` podem sair `ERROR` quando o crt.sh ou o NVD não
respondem, e a ficha passa a excluir os dois. O SCAN-05 ganha `moduleStatus.CVE` como
pré-condição. O UI-02 deixa de passar com o host torto: verde falso → vermelho verdadeiro,
correção do spec com o dono.

Infra: o seed do PAY-07 passa a renovar a assinatura a cada carga, como o do PAY-15. A
AUTHORIZED tinha vencido no banco local, e o PAY-07 caiu com `EXPIRED`.

## 2026-11-23 — BUG-20 corrigido (rate limit isentava todo cadastro); RATE-01 retomado

Quinta etapa da rodada dos achados laterais (HANDOFF §12, Etapa L). Decisões do dono:
- requisições por minuto pelo plano efetivo da conta: FREE 5, PRO 60, ENTERPRISE 120
  (visitante segue com 5 por IP);
- membro de equipe segue o plano da conta, não o papel;
- a equipe da plataforma fica isenta.

Regressão no Backend (`bugs/BUG-20-rate-limit-isenta-todo-cadastro.md`). O RATE-01 sai do
⏸️ e vira 🔲, com o qa-free e sem seed novo. A contagem não muda (54): o RATE-01 já estava
na matriz.

Muda o PLAN-02: o `PlanoCotaScanTest` do dono fica vermelho no 429 da 6ª chamada seguida.
É o contrato novo; o ritmo do teste é com o dono. Mudam também as fichas do PLAN-04 (ritmo
do preparar), do SSRF-07 (motivo do qa-pro) e do SCAN-13 (balde do qa-free).

## 2026-11-23 — BUG-13 corrigido (compliance e card de cookie); SCAN-14 novo; BUG-21 aberto

Sexta etapa da rodada dos achados laterais (HANDOFF §12, Etapa M). Decisões do
dono:
- o compliance segue o card do cookie: conta todo cookie em que o card lista o problema;
- a cobrança de HttpOnly do token CSRF no compliance, achada na mesma leitura, entra junto
  (mesma raiz);
- os avisos do card na tela, que tinham o mesmo defeito, entram junto: o Backend manda os
  códigos em `problems`, e a tela desenha a partir deles.

Regressão no Backend e no Frontend (`bugs/BUG-13-compliance-samesite-nunca-dispara.md`).
Caso novo SCAN-14 (P2, 🔲), com o stub novo `env/wiremock/mappings/cookies-csrf.json`
(`cookies-mock:8080/so-csrf`). O SCAN-09 não muda: `problems` é campo novo e só acrescenta.

Achado lateral registrado e não corrigido: BUG-21 (P3). O painel "O que está em jogo" chama
o `XSRF-TOKEN` de cookie de sessão, porque o rótulo de impacto tem a sua própria lista de
nomes.

## 2026-11-23 — BUG-21 corrigido ("é cookie de sessão?" com duas respostas); SCAN-15 novo; BUG-22 aberto

Sétima etapa da rodada dos achados laterais (HANDOFF §12, Etapa N). Decisões do dono:
- uma regra só para "é cookie de sessão?", a do card, no card e no painel "O que está em
  jogo". O token CSRF, o `inside_banner` e o `user_pref` saem do painel, e o Hotjar deixa
  de ser HIGH no card;
- o painel continua vendo só cookie com problema (fica como está).

Regressão no Backend (`bugs/BUG-21-impacto-chama-token-csrf-de-sessao.md`). Caso novo
SCAN-15 (P2, 🔲), com o stub novo `env/wiremock/mappings/cookies-nomes.json`
(`cookies-mock:8080/nomes`). O SCAN-09 e o SCAN-14 não mudam: o painel não é afirmado
neles, e o card dos cookies deles é o mesmo.

Achado lateral registrado e não corrigido: BUG-22 (P2). `AMP_TOKEN` (Google Analytics) e
`s_sess` (Adobe Analytics) saem HIGH no card e como sessão no painel, pelos fragmentos
"token" e "sess".

## 2026-11-23 — BUG-22 corrigido (analytics com nome de sessão); SCAN-16 novo

Oitava etapa da rodada dos achados laterais (HANDOFF §12, Etapa O). Decisão do dono: uma
lista de nomes conhecidos de analytics, junto com o Hotjar. `AMP_TOKEN` (Google
Analytics), `s_sess` (Adobe Analytics) e `_pin_unauth` (tag do Pinterest, achado na
leitura) deixam de ser sessão, no card e no painel. O `intercom-session-*` (chat)
continua sessão.

Regressão no Backend (`bugs/BUG-22-analytics-com-nome-de-sessao.md`). Caso novo SCAN-16
(P2, 🔲), com o stub novo `env/wiremock/mappings/cookies-analytics.json`
(`cookies-mock:8080/analytics`). O SCAN-09, o SCAN-14 e o SCAN-15 não mudam. Nenhum achado
lateral novo.
