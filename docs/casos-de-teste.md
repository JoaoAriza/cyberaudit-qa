# Matriz de casos de teste — CyberAudit QA

Consolida os 26 casos originais do escopo (`CyberAudit QA — Escopo do projeto de
testes.pdf`, 2026-09-29) com os 12 casos do módulo de pagamento (já catalogados em
`payment-module-requests.md` durante a validação do checkout transparente) e 1 caso
novo (PAY-13), depois que o cartão virou opcional sem CPF. 39 casos: 22 P0, 14 P1, 3 P2.

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
| AUTH-04 | 2FA | Sem código TOTP, código inválido, código reutilizado | Acesso negado nos três | RestAssured, Cypress | P0 | 🔲 A fazer |
| AUTH-05 | API key | Chamada com API key revogada | 401 | Postman, RestAssured | P1 | 🐛 Bug — API key não autentica (ver `bugs/BUG-01-apikey-auth-lazyinit.md`); automação bloqueada até o fix |
| AUTHZ-01 | Controle de acesso | Usuário A consulta scan do usuário B pelo ID | 403 ou 404, nunca os dados | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-08) |

## Planos e limites

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| PLAN-01 | Planos | Usuário Free solicita relatório PDF | Bloqueado (recurso Pro) | RestAssured, Cypress | P0 | 🔲 A fazer |
| PLAN-02 | Planos | Exceder a cota de scans do plano | Bloqueado com mensagem clara | RestAssured | P1 | 🔲 A fazer |
| RATE-01 | Rate limit | Exceder o limite configurado por usuário/IP | 429; volta a 200 após a janela | RestAssured | P1 | ⏸️ Adiado — decisão B tomada (ver nota 2026-10-05) |

## Anti-SSRF

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| SSRF-01 | Anti-SSRF | Alvo `127.0.0.1`, `localhost`, `::1` | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-02 | Anti-SSRF | Alvo `169.254.169.254`, `10.x`, `192.168.x` | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-03 | Anti-SSRF | Formas alternativas: `2130706433`, `0x7f000001`, `127.1` | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-04 | Anti-SSRF | Hostname público que resolve para IP privado | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-05 | Anti-SSRF | Alvo externo que redireciona para IP interno | Redirecionamento não seguido | RestAssured + WireMock | P1 | 🔲 A fazer |
| SSRF-06 | Anti-SSRF | Allowlist de teste fora do profile `test` | Allowlist inexistente | RestAssured | P0 | 🔲 A fazer |

## Domínio e motor de scan

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| DOM-01 | Domínio | Varredura ativa em domínio não verificado | Recusada | Postman, RestAssured | P0 | ✅ Passou — RestAssured (2026-10-05) e Postman (2026-10-06) |
| SCAN-01 | Motor | Varredura passiva no alvo controlado | Módulos esperados com status OK | RestAssured | P0 | 🐛 Bug suspeito já observado manualmente (ver nota abaixo) — teste formal pendente |
| SCAN-02 | Motor | Alvo com atraso forçado no WireMock | Módulo marcado como timeout, relatório indica resultado parcial | RestAssured + WireMock | P1 | 🔲 A fazer |
| SCAN-03 | Motor | Varredura ativa no Juice Shop | XSS e SQL injection detectados | RestAssured | P0 | 🔲 A fazer |
| SCAN-04 | Motor | Alvo limpo com headers corretos | Nenhum falso positivo nesses módulos | RestAssured + WireMock | P1 | 🔲 A fazer |
| SCORE-01 | Pontuação | Scan com achado crítico e o resto limpo | Nota não fica alta (override de severidade) | RestAssured | P0 | 🔲 A fazer |

## Relatório e interface

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| REP-01 | Relatório | Download do PDF por usuário Pro | `application/pdf` válido, contém os achados do scan (checar com PDFBox) | RestAssured | P1 | 🔲 A fazer |
| REP-02 | Relatório | Achado conhecido no PDF | Mapeamento LGPD/ISO 27001 presente | RestAssured | P2 | 🔲 A fazer |
| UI-01 | Interface | Login completo com 2FA | Chega ao dashboard | Cypress, Selenium | P1 | 🔲 A fazer |
| UI-02 | Interface | Iniciar scan, acompanhar e ver no histórico | Resultado e nota exibidos | Cypress, Selenium | P1 | 🔲 A fazer |
| UI-03 | Interface | Criar, editar e remover agendamento recorrente | Alterações persistem após recarregar | Cypress | P2 | 🔲 A fazer |

## Pagamento (checkout transparente — Pix + cartão)

Confirmado ao vivo em produção em 2026-09-30, com dinheiro real — ver
`Backend/HANDOFF.md` seção 2 para o histórico completo (webhook mal configurado no
painel do MP, CSP em 3 rodadas, `getSubscription` com bug de seleção, CPF virou
opcional no cartão). Os casos abaixo continuam válidos para automatizar contra o
ambiente de teste local (nunca contra produção — Regra 1).

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| PAY-01 | Pagamento | Checkout cartão com token válido (sandbox, cartão de teste aprovado) | 200, status AUTHORIZED, plano liberado na hora | RestAssured | P0 | 🔲 A fazer |
| PAY-02 | Pagamento | Checkout cartão com token de cartão de teste recusado | 502, plano continua FREE | RestAssured | P0 | 🔲 A fazer |
| PAY-03 | Pagamento | Checkout cartão sem cardTokenId | 400 | Postman, RestAssured | P1 | ⚠️ Parcial — Postman ✅ (2026-10-06); RestAssured pendente |
| PAY-04 | Pagamento | Checkout Pix com CPF válido | 200, QR code presente, plano continua FREE até confirmação | RestAssured | P0 | 🔲 A fazer |
| PAY-05 | Pagamento | Checkout Pix com CPF de tamanho inválido | 400 | Postman, RestAssured | P1 | ⚠️ Parcial — Postman ✅ (2026-10-06); RestAssured pendente |
| PAY-06 | Pagamento | Webhook `payment` approved libera o plano e seta currentPeriodEnd | Plano liberado, período ~30 dias | RestAssured | P0 | 🔲 A fazer |
| PAY-07 | Pagamento | Webhook `payment` rejected não derruba assinatura Pix já ativa | Status/plano inalterados | RestAssured | P1 | 🔲 A fazer |
| PAY-08 | Pagamento | Webhook sem x-signature válido | 401, plano não muda | RestAssured | P0 | 🔲 A fazer |
| PAY-09 | Pagamento | > 5 tentativas de checkout/minuto pelo mesmo usuário | 429 | RestAssured | P1 | 🔲 A fazer |
| PAY-10 | Pagamento | Job de expiração: assinatura Pix com currentPeriodEnd vencido | Conta rebaixada para FREE | RestAssured (chamada direta ao método, ou expor endpoint de teste) | P1 | 🔲 A fazer |
| PAY-11 | Interface | Fluxo completo Pix na tela: QR aparece, copia-e-cola funciona, polling detecta confirmação | Tela mostra "plano ativo" sem reload manual | Cypress | P1 | 🔲 A fazer |
| PAY-12 | Interface | Fluxo completo cartão na tela: Secure Fields do MP carregam, submit gera token, checkout confirma | Tela mostra "plano ativo" | Cypress | P1 | 🔲 A fazer |
| PAY-13 | Pagamento | Checkout Pix com CPF de 11 dígitos mas dígito verificador inválido | 400 (`CpfUtil` rejeita desde 2026-09-29) | RestAssured | P1 | 🔲 A fazer |
| PAY-14 | Pagamento | Checkout cartão sem CPF nenhum (cliente estrangeiro) | 200/token gerado — CPF é opcional desde 2026-09-30, backend nunca recebeu CPF nesse fluxo | RestAssured, Cypress | P1 | 🔲 A fazer |
| PAY-15 | Pagamento | `GET /billing/subscription` com um Pix AUTHORIZED mais antigo e um Pix PENDING mais novo na mesma conta | Devolve o AUTHORIZED, não o mais recente por data — regressão do bug corrigido em 2026-09-30 (`currentSubscription()`) | RestAssured | P0 | 🔲 A fazer |

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
