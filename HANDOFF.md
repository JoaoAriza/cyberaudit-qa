# HANDOFF — cyberaudit-qa (para colar no próximo chat)

> **Retomar pela §13 (2026-11-24):** estado atual, próximo caso (AUTH-04), como o chat
> trabalha e a primeira mensagem sugerida. As seções 1 a 12 são histórico.

Atualizado em 2026-10-03, porque a janela de contexto do chat anterior encheu.
Autocontido: dá pra retomar sem ler o histórico. O escopo original é o PDF na raiz
(`CyberAudit QA — Escopo do projeto de testes.pdf`); a versão em Markdown é
`docs/plano-de-teste.md`, e a matriz de casos com status é `docs/casos-de-teste.md`.

---

## 0. Contexto e regras que valem pra tudo

**O que é:** repositório público de portfólio de QA (Postman/Newman, RestAssured,
Cypress, Selenium) testando o CyberAudit, um scanner de postura de segurança de
sites. `https://github.com/JoaoAriza/cyberaudit-qa`. Fica ao lado de
`C:\Projetos\Cyberaudit\Backend` e `...\Frontend` (repos próprios).

**Papel do Claude aqui (confirmado pelo dono em 2026-09-30): só planejamento e
revisão. Nunca escrever o código de teste** — nem assertion de Postman, nem
`@Test` de RestAssured, nem spec de Cypress, nem page object de Selenium, nem um
"esqueleto pra adaptar". Motivo: Regra 3 do escopo ("autoria real"; o repo existe
pra provar competência a recrutador). O Claude pode: montar infraestrutura
(docker-compose, seed, `pom.xml`, config), confirmar contratos da API lendo o
Backend e chamando o ambiente, revisar o que o dono escreve (rodando numa cópia no
scratchpad, sem tocar nos arquivos dele), atualizar a matriz/docs e explicar conceitos.
O Claude **não** roda `git commit`/`git push`: manda o comando pronto (a mensagem de
commit é só `tipo(escopo): descricao`, sem acento, sem corpo, sem atribuição).

**As 5 regras do escopo:** (1) nunca rodar testes contra produção
(`cyberauditapp.com`); (2) varredura ativa só contra alvos locais do ambiente de
teste; (3) autoria real; (4) sem segredos no repo (credenciais e TOTP só de teste;
a ideia é vir de variável de ambiente/GitHub Secrets); (5) CV acompanha o código.

---

## 1. Estado das fases

| Fase | Estado |
|---|---|
| 0. Planejamento | ✅ `plano-de-teste.md`, `casos-de-teste.md` (39 casos), `template-bug.md` |
| 1. Ambiente Docker | ✅ confirmado de ponta a ponta |
| 2. Postman/Newman | 🟡 `collection.json` reescrita pelo dono; só **AUTH-01** pronto. Faltam AUTH-05, DOM-01, PAY-03, PAY-05. Newman fora do CI |
| 3. RestAssured | 🟡 iniciada; **AUTH-01 e AUTH-02 verdes** (3 testes). Falta estabilizar o AUTH-02 (ver §4, item 1) |
| 4. Cypress / 5. Selenium / 6. CI+Allure | não iniciadas |

Conferir `git status` no início: o `AuthTest`, o `TestConfig`, o `config.properties`,
o `pom.xml` (com o reporter de árvore, §3) e este arquivo podem estar sem commit.

---

## 2. Como rodar

**Ambiente (cinco serviços + seeder), a partir de `cyberaudit-qa/`:**

```
docker compose -f env/docker-compose.test.yml up --build
```

Portas locais: backend `8081`, frontend `8090`, Juice Shop `3001`, WireMock `8082`,
Postgres `5433`. Reset total do banco: `... down -v`. Health: `GET
http://localhost:8081/actuator/health`.

**Usuários semeados** (`env/seed.sql`; senha e segredo TOTP fixos, só de teste, todos
com 2FA ligado): `qa-free@`, `qa-pro@`, `qa-enterprise@cyberaudit.test`.
- `qa-enterprise` é a **única** em `PLATFORM_STAFF_EMAILS` (pula a posse de domínio no
  scan ativo; tem `juice-shop:3000` e `wiremock:8080` verificados).
- `qa-pro` fica **sem bypass e sem domínio verificado de propósito**: é a conta do
  **DOM-01** (PRO de verdade, scan ativo em domínio não verificado → recusado).
- `qa-free` é bloqueada no scan ativo pelo plano FREE (outro erro, serve ao PLAN-01).

**Postman:** `cd api-postman` → `newman run collection.json -e environment.local.json`.

**RestAssured:** o `mvn` do PowerShell usa **JDK 17** por padrão e o pom pede 21:

```
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"; mvn test
```

Saída do `mvn test`: lista cada teste com `[OK]`/`[XX]` e o `@DisplayName` (reporter
de árvore de terceiros, `me.fabriciorby`, configurado no `pom.xml`). Pra ver o
status HTTP de cada request no console, uma linha no `@BeforeAll` do `TestConfig`:
`RestAssured.filters(new RequestLoggingFilter(LogDetail.URI), new
ResponseLoggingFilter(LogDetail.STATUS));` (imports em `io.restassured.filter.log`).
Pelo IntelliJ, o botão de play ao lado de cada `@Test` também mostra pass/fail por teste.

**IntelliJ:** o `pom.xml` precisa estar registrado (`Add as Maven Project`); sem isso
não há sugestão nem import resolvido. Project SDK = 21.

---

## 3. Estrutura

```
cyberaudit-qa/
├── HANDOFF.md                         (este arquivo)
├── docs/                              plano-de-teste, casos-de-teste, template-bug, payment-module-requests
├── env/                               docker-compose.test.yml, seed.sql, wiremock/
├── api-postman/                       collection.json, environment.local.json
├── api-restassured/
│   ├── pom.xml                        rest-assured 5.5.0, json-schema-validator, JUnit 5, AssertJ, surefire
│   └── src/test/
│       ├── java/com/qa/TestConfig.java   classe-base: carrega .properties; @BeforeAll fixa RestAssured.baseURI
│       ├── java/com/qa/AuthTest.java     extends TestConfig
│       └── resources/config.properties   base.uri=http://localhost:8081, password, emailFree/Pro/Enterprise, cpf
└── (e2e-cypress/, e2e-selenium/, .github/workflows/ ainda não existem)
```

Toda classe de teste precisa fazer `extends TestConfig`. Se esquecer, o `baseURI` não é
fixado e a request vai pro padrão da biblioteca (`localhost:8080`, onde há outro serviço
na máquina do dono respondendo 404 — falha confusa, sem "connection refused").

---

## 4. Pendências, em ordem

1. **Estabilizar o AUTH-02 (teste do usuário inexistente).** Ele usa um e-mail **fixo**
   (`usuario.nao.existe@cyberaudit.test`). Cada execução soma uma falha de login pra
   esse e-mail; na 5ª execução em 15 min o backend responde **429**
   ("Muitas tentativas de login para esta conta"), e o teste quebra com
   `Expected status code <401> but was <429>`. Foi o que aconteceu no fim desta sessão.
   Correção: e-mail **único por execução** (ex.: um UUID no local-part). Se já estiver
   travado: esperar ~15 min ou reiniciar o backend (o contador é em memória):
   `docker compose -f env/docker-compose.test.yml restart backend`.
2. Commitar o que estiver pendente (ver `git status`).
3. Próximos casos: **AUTH-03** (JWT expirado/adulterado/`alg: none` → 401; não precisa
   de login, comece por ele), depois **AUTHZ-01**, **DOM-01** (com `qa-pro`), os
   **SSRF-01..06**, **PLAN-01/02**, **RATE-01**. Pelo lado Postman, AUTH-05, DOM-01,
   PAY-03 e PAY-05.
4. Qualquer caso que precise de **sessão completa** (AUTHZ-01, PLAN-*, SCAN-*) exige o
   código TOTP em Java: falta uma dependência no `pom.xml` (ex.:
   `dev.samstevens.totp:totp`, a mesma que o Backend usa, ou `com.eatthepath:java-otp`)
   e o segredo TOTP via config/variável de ambiente (Regra 4). Fluxo: `POST /auth/login`
   → token provisório → `POST /auth/2fa/verify` com `Authorization: Bearer <provisório>`
   e `{"code":"<6 dígitos>","method":"TOTP"}` → token de sessão.
5. Regra 4 ainda não aplicada no `TestConfig`: `get()` deveria olhar `System.getenv`
   antes do `.properties` (o CI da Fase 6 vai precisar). E lançar erro claro quando a
   chave não existe (hoje devolve `null` em silêncio).
6. Fase 6 (CI): Newman e Maven no GitHub Actions; o `docker-compose` assume
   `Backend/` e `Frontend/` como pastas irmãs, então o workflow precisa dar checkout
   dos três repos lado a lado.

---

## 5. Armadilhas já pagas (não repetir)

- **Path inexistente devolve o MESMO 401 + mesmo corpo** que credencial errada
  (`{"error":"Autenticação necessária."}` — qualquer rota não liberada). Um teste de 401
  com path errado passa pelo motivo errado. Sempre ter um **controle**: o caso feliz
  (200) no mesmo path na mesma classe. O endpoint certo é `/auth/login` (não `/login`) e
  `/auth/2fa/verify` (não `/2fa/verify`).
- **Contrato do login:** 401 `{"error":"Autenticação necessária."}` (campo `error`, não
  `message`; senha errada e usuário inexistente são idênticos). 200 com usuário 2FA:
  `{"token":"<provisório>","tokenType":"Bearer","user":null,"requires2fa":true,
  "twoFactorMethods":["TOTP"]}` — o token **não** é sessão; qualquer outro endpoint
  responde 403 `2FA_PENDING` até o `/auth/2fa/verify`.
- **Nunca chutar nome de campo ou texto de asserção.** Pôr `.then().log().body()`, rodar
  uma vez, e escrever a asserção em cima do JSON real. (`message` e "Login Válido" não
  existem na API; `Actual: null` quer dizer "campo não existe".)
- **Rate-limit de login:** 5 falhas por e-mail e 20 por IP → 429 por 15 min. Um login
  com sucesso zera o contador **só do e-mail que logou e do IP**, não de outros e-mails.
  Rodar um teste de falha sozinho várias vezes pode travar `qa-pro` e quebrar o login dele.
- **`.properties`:** aspas viram parte do valor (`password="x"` lê com aspas); chaves são
  case-sensitive.
- **`@BeforeAll`** só roda na classe de teste ou numa superclasse dela; `given()` lê o
  `baseURI` na hora em que é chamado, antes dos argumentos do `.body(...)`.
- **Domínios no seed têm a porta** (`juice-shop:3000`): `PlanLimitService.normalizeHost`
  só remove esquema e path. Existe uma segunda guarda, ao vivo (arquivo
  `/.well-known/cyberaudit.txt` no alvo), que o Juice Shop não consegue servir — por isso o
  bypass via `PLATFORM_STAFF_EMAILS`.
- **A allowlist de SSRF** do ambiente só existe sob o profile Spring `qa-docker`
  (`SsrfTestAllowlistConfig` no Backend). Fora dele o bean nem é criado. O caso SSRF-06
  deve provar isso contra um backend **sem** o profile.
- **Achado de scanner (candidato a bug real, SCAN-01):** scan contra alvo HTTP puro em
  porta não-padrão (Juice Shop, 3000) volta com `httpStatus:0` e erros de TLS/headers em
  cascata; o scanner parece tentar HTTPS mesmo com `http://`. Além disso, o módulo de
  Certificate Transparency consulta `crt.sh`/`certspotter` de verdade pra hostname
  inexistente e leva ~2 min. Não investigado; é pra o SCAN-01 registrar como bug.
- **Billing/pagamento:** o contrato completo (checkout cartão/Pix, webhook, erros) está em
  `docs/payment-module-requests.md`. Cartão ainda não foi autorizado de verdade (só a
  rejeição foi testada em produção); Pix foi confirmado de ponta a ponta.

---

## 6. Atualização de estado e convenções (2026-10-20)

Esta seção põe o HANDOFF em dia (as seções 1–5 acima são de 2026-10-03 e ficaram
desatualizadas). A matriz `docs/casos-de-teste.md` é a fonte da verdade do status caso
a caso; aqui vai o panorama e os padrões que surgiram.

### Convenção ao passar um caso pro dono escrever
Ao descrever um caso de teste pro dono implementar, o Claude **explica o QUE o caso
prova e POR QUE** (o risco/contrato que ele cobre), não só os passos e asserts. Ex.:
"PAY-09 prova que o teto de 5 checkouts/min por usuário barra abuso de teste de
cartão". Isso deixa claro o valor do caso (e ajuda no CV). Mantido pelos próximos chats.

### O que já está verde (resumo; detalhe na matriz)
- **RestAssured (Fase 3):** AUTH-01/02/03, DOM-01, AUTHZ-01, PLAN-01/02, SSRF-01..06
  (hex do SSRF-03 é bug, ver abaixo), PAY-01/02/04/06/08/13/14/15.
- **Postman (Fase 2):** AUTH-01, DOM-01, PAY-03, PAY-05 (via Newman; `npm test` com
  reporter htmlextra; schema do VS Code em `.vscode/settings.json`).
- **Cypress (Fase 4):** só o scaffold (`e2e-cypress/`, tasks `totp`/`forgeToken`,
  smoke verde). Specs de UI ainda a escrever pelo dono.

### Bugs reais encontrados (em `docs/bugs/`)
- **BUG-01** — API key não autentica (LazyInitializationException). Bloqueia AUTH-05.
- **BUG-02** — SSRF guard não normaliza IPv4 hex (`0x7f000001` passa). SSRF-03 cobre
  decimal/curto; o hex fica num teste `@Disabled` até o fix.
- **BUG-03** — scanner não conecta em HTTP puro em porta não-padrão (`httpStatus:0`).
  Bloqueia SCAN-01/03 (e provavelmente SCAN-02/04, SCORE-01). SCAN-03 fica `@Disabled`.

### Padrões/infra que valem pra continuar
- **Forja de JWT:** os testes forjam o token HS256 com `jwt.secret` do ambiente (mesmo
  do `docker-compose.test.yml`), dispensando o 2FA. O `subject` TEM que ser o **e-mail**
  (o backend carrega o usuário por ele) — erro recorrente foi pôr o userId no subject.
- **Pagamento (PAY-*):** o MP é **mockado no WireMock**. Precisou de um patch no Backend
  (`mp.base-url` configurável, default produção) pra apontar `MP_BASE_URL` pro WireMock;
  `MP_ACCESS_TOKEN` é dummy e `MP_WEBHOOK_SECRET` está setado. Stubs em
  `env/wiremock/mappings/mp-*.json` (preapproval aprovado/recusado por `card_token_id`,
  payment Pix com id único, GET payment/preapproval). `card-recusado` é o token que o
  stub rejeita (→ 502). Webhook: assinatura HMAC-SHA256 do manifest
  `id:<dataId>;request-id:<reqId>;ts:<ts>;` com o `mpWebhookSecret`.
- **Isolamento de estado:** checkout que faz upgrade MUTA a conta (vira PRO, persiste).
  Cada teste de upgrade usa uma **conta FREE dedicada** (`qa-pay-*` no seed) e um
  `@BeforeEach` chama `TestConfig.resetarContaParaFree(email)` (JDBC, driver postgres no
  pom) pra devolver a conta a FREE antes de cada teste → idempotente no re-run. Casos
  que não mutam (recusa, 400, read-only) não precisam. Cenários complexos (ex. PAY-15:
  AUTHORIZED+PENDING na mesma conta) são **semeados** no `seed.sql`, não criados via API.
- **Reset manual rápido** (quando precisar, fora do @BeforeEach):
  `docker exec env-postgres-1 psql -U cyberaudit_qa -d cyberaudit_qa -c "UPDATE accounts SET plan='FREE' WHERE id='<accId>'; DELETE FROM subscriptions WHERE account_id='<accId>';"`
- **Ambiente caiu / PC reiniciou:** `docker compose -f env/docker-compose.test.yml up -d`
  (daemon do Docker Desktop precisa estar aberto). Health: `curl http://localhost:8081/actuator/health`.

### Pendências
- **PAY-07** (webhook rejected não derruba Pix ativa), **PAY-09** (em andamento),
  **PAY-10** (job de expiração — precisa semear `currentPeriodEnd` vencido e disparar o
  job; confirmar se dá sem endpoint de teste novo).
- **RATE-01** — adiado, decisão B (semear usuário não-OWNER; ver nota na matriz).
  Retomado em 2026-11-23 com o qa-free, sem seed novo: §12, Etapa L.
- **SCAN-*** bloqueados pelo BUG-03.
- **Cypress:** specs de UI (UI-01 login 2FA, UI-02 scan+histórico, UI-03, PAY-11/12).
- **Fase 6 (CI):** Newman + Maven no GitHub Actions, checkout dos 3 repos lado a lado,
  Allure/relatórios. O `nip.io` do SSRF-04 depende de DNS externo — em CI isolada, trocar
  pelos hosts `extra_hosts` locais (já no compose).

---

## 7. Atualização de estado (2026-10-23) — fim de módulo de pagamento + Cypress iniciado

Continua valendo a convenção da seção 6 (descrever o QUE/POR QUE de cada caso). A
matriz `docs/casos-de-teste.md` é a fonte da verdade do status.

### Progresso desde a seção 6
- **Módulo de pagamento FECHADO no RestAssured:** além dos já verdes, entraram
  **PAY-07** (webhook rejected não derruba Pix ativa), **PAY-09** (teto 5 checkout/min
  → 429), **PAY-10** (job de expiração rebaixa pra FREE) e **PAY-15** (subscription
  prioriza AUTHORIZED). Restam só PAY-11/PAY-12, que são de UI (Cypress).
- **Cypress (Fase 4) ANDANDO:** scaffold + **UI-01** (login 2FA real), **UI-02**
  (scan → nota → histórico) e **UI-03** (CRUD de agendamento) verdes. Faltam
  **PLAN-01 na tela**, **PAY-11** (Pix na UI) e **PAY-12** (cartão na UI).

### Infra nova de pagamento (além do mock MP da seção 6)
- **Contas/cenários semeados** (`env/seed.sql`, chaves em `config.properties`):
  `qa-pay-07` (Pix AUTHORIZED ativa, mp_payment_id `pay07-sub`), `qa-pay-09` (rate
  limit), `qa-pay-10` (job de expiração), `qa-pay-15` (AUTHORIZED antigo + PENDING
  novo). Cenários complexos são **semeados**, não criados por API.
- **Stub webhook rejected:** `env/wiremock/mappings/mp-get-payment-rejected.json`
  (GET /v1/payments/pay07-sub → rejected, prioridade 1).
- **Gatilho de teste no Backend:** `TestBillingController` (`@Profile("qa-docker")`,
  `POST /admin/test/expire-pix`) dispara o job `expirarAssinaturasPixVencidas` pro
  PAY-10 testar por API. **Só existe no profile de teste.** Helper
  `TestConfig.prepararPixVencida(email)` (JDBC) monta a Pix vencida no `@BeforeEach`.
- **Idempotência:** testes de upgrade usam `@BeforeEach { resetarContaParaFree(...) }`
  (JDBC, driver postgres no pom). Checkout **muta** a conta (vira PRO, persiste).
- **Plano da CONTA** (pós-expiração/downgrade) está em `GET /auth/me` → `account.plan`
  (NÃO no `GET /billing/subscription`, que mostra o plano da assinatura).
- **Webhook válido:** assinatura HMAC-SHA256 de `id:<dataId>;request-id:<reqId>;ts:<ts>;`
  com `mpWebhookSecret`; header `x-signature: ts=<ts>,v1=<hex-minusculo>` (a vírgula
  entre ts e v1 foi erro recorrente). type `"payment"` exato; `data.id` = paymentId.

### Cypress — como está montado e rodar
- Projeto em `e2e-cypress/`. Rodar: `cd e2e-cypress && npm run cy:run` (headless) ou
  `npm run cy:open` (interativo); `-- --spec cypress/e2e/<arquivo>` roda um só.
  Precisa do **frontend (8090) + backend (8081)** no ar.
- **Specs ficam em `cypress/e2e/*.cy.js`** (o `specPattern`); na raiz não roda.
- Tasks (em `cypress.config.js`): **`cy.task('totp')`** (código 2FA do segredo do seed,
  pro login real do UI-01) e **`cy.task('forgeToken', {email})`** (JWT de sessão pro
  bypass). Config/env (emails, tokenKey `cyberaudit.token`, segredos) no `cypress.config.js`.
- **Bypass de login** (specs que não são de login): `cy.task('forgeToken',{email}).then(t =>
  cy.visit('/', { onBeforeLoad: w => w.localStorage.setItem(Cypress.env('tokenKey'), t) }))`.
- **`jsconfig.json`** liga o IntelliSense do Cypress no VS Code (precisa Reload Window).

### Cypress — armadilhas já pagas (não repetir)
- **Sem `data-testid`** no Frontend → selecionar por placeholder/tipo/texto. Login:
  `input[type=email]`/`input[type=password]`, submit `cy.get('form').contains('button','Entrar')`
  (há 2 "Entrar": aba e submit). 2FA: `input[placeholder="000000"]` + botão "Confirmar".
  Dashboard: `cy.contains('Histórico')` (nav só-logado). Scan: `input[placeholder="example.com"]`
  + `cy.contains('button','◈ Scan')` (o `◈` distingue da nav "Scanner").
- **Scan demora ~10-25s** → `{ timeout: 60000 }` na asserção da nota (`/100`).
- **Agendamentos:** após criar, o grupo vem EXPANDIDO (Pausar/Remover visíveis) — NÃO
  clicar no domínio (recolhe). Após **reload** vem RECOLHIDO (▸) — aí clicar no domínio
  pra expandir antes do Remover. "Editar" = toggle **Pausar/Retomar** (backend só tem
  create/toggle/delete). **Remover dispara `window.confirm`** → Cypress aceita sozinho.
  `cy.contains('Agendado')` casa com o título "Scans Agendados" (h2) — escopar em `tr`.
  Use host único por run (`example-${Date.now()}.com`, com CRASES) + remova no fim.
- **Binário do Cypress** pode "sumir" do cache (`AppData\Local\Cypress\Cache`) por
  antivírus travando o `Cypress.exe` → `npx cypress install --force` + exclusão no AV.
- Pra inspecionar a UI manualmente: forjar JWT (openssl, mesmo segredo), setar em
  `localStorage["cyberaudit.token"]` e recarregar. O token expira em ~2h.

### PENDENTE / próximos
- **PLAN-01 na tela** (P0): Free não exporta PDF na UI (complementa o PLAN-01 de API).
- **PAY-11** (P1): Pix na tela (QR, copia-e-cola, polling detecta confirmação — pode
  precisar simular o webhook pro polling "ver" a confirmação).
- **PAY-12** (P1): cartão na tela usa **Secure Fields do MP em iframe cross-origin** —
  Cypress não acessa fácil; provável inviável no Cypress (avaliar mock do SDK ou marcar
  como coberto manualmente).
- **UI-02/UI-03/PLAN-01/UI-01** são ⚠️ Parcial (Cypress ✅, **Selenium** pendente — última
  fase, opcional conforme vagas-alvo).
- **Fase 6 (CI):** Newman + Maven no GitHub Actions, checkout dos 3 repos, Allure.

### COMMITS PENDENTES (conferir `git status` nos DOIS repos)
- **Backend** tem mudanças não commitadas: `MercadoPagoService.java` (patch mp.base-url),
  `application.properties` (mp.base-url), `TestBillingController.java` (gatilho PAY-10).
  Commitar no repo do Backend.
- **cyberaudit-qa**: specs de Cypress, testes de pagamento, `seed.sql`, `config.properties`,
  `TestConfig.java`, stubs do wiremock, `docker-compose.test.yml`, `jsconfig.json`, docs.
  Ver `git status`; os comandos de commit sugeridos ao longo do chat seguem o padrão
  `tipo(escopo): descricao` (sem acento, sem atribuição).

---

## 8. Atualização de estado (2026-10-26) — PAY-11 verde, PAY-12 decidido, módulo de pagamento fechado

Atualiza o "PENDENTE / próximos" da §7 (PAY-11 saiu de "a fazer"; PAY-12 decidido). A matriz
`docs/casos-de-teste.md` continua sendo a fonte da verdade.

### PAY-11 (Pix na tela) ✅ verde e idempotente
`e2e-cypress/cypress/e2e/ui-11-pix.cy.js` (autoria do dono; Claude revisou/rodou). Fluxo:
badge FREE → Planos → Assinar "Pessoal Pro" → aba Pix → CPF → Gerar Pix → QR + copia-e-cola
→ webhook simulado → polling detecta e a tela mostra o plano ativo sem reload.

### Infra nova de Cypress (Claude) — além de totp/forgeToken
Dois `cy.task` novos no `cypress.config.js` + uma conta dedicada:
- **`cy.task('resetFree', { accountId })`** — devolve a conta a FREE e apaga as subscriptions,
  via `docker exec env-postgres-1 psql`. Chamar no `beforeEach` (checkout MUTA pra PRO e
  persiste → re-run idempotente). accountId validado como UUID (nunca roda sem WHERE).
- **`cy.task('signMpWebhook', { paymentId })`** — assina o webhook `payment` do MP
  (manifesto `id:<id>;request-id:<req>;ts:<ts>;` → HMAC-SHA256 com `mpWebhookSecret`) e
  devolve `{ headers, body }` prontos pro `cy.request`. Mesmo contrato do PAY-06.
- **Conta `qa-pay-11`** (`qa-pay-11@cyberaudit.test`, FREE) no `env/seed.sql` + chaves no
  `cypress.config.js` (`emailPay11`/`userIdPay11`/`accountIdPay11`/`mpWebhookSecret`). Isola
  das contas de pagamento do RestAssured (que resetam no `@BeforeEach` — rodar junto dá corrida).

### Armadilhas já pagas no PAY-11 (valem pra todo caso de pagamento na UI)
- **`cy.request` do webhook tem que ir pro `Cypress.env('apiUrl')` (8081).** URL relativa cai
  no `baseUrl` (8090 = frontend nginx → 405), não no backend.
- **No sucesso, o `onSuccess` fecha só o CheckoutModal; o PlansModal continua aberto cobrindo
  o header.** O badge "PRO" do header fica **coberto** → `should('be.visible')` nele falha
  (Cypress não considera visível elemento tapado por overlay). Prova de sucesso: o card Pro
  vira **"Cancelar assinatura"** (`cy.contains('[class*=planCard]','Pessoal Pro').within(...)`),
  visível DENTRO do modal aberto. (Ou fechar o modal pelo `[class*=modalClose]` antes.)
- **O input do CPF some assim que o QR aparece** (a tela troca do form pro QR), ANTES da
  confirmação — então `input[...].should('not.exist')` NÃO é sinal de sucesso. Sinal real: o
  "Aguardando confirmação do pagamento..." sumir, ou o "Cancelar assinatura" aparecer.
- **`cy.get(seletor)` vs `cy.contains(texto)` vs `cy.contains(seletor, texto)`**: `@alias` só
  resolve em `cy.get('@...')`, nunca como seletor dentro de `cy.contains`.

### PAY-12 (cartão na tela) — E2E inviável no Cypress; coberto por API + manual
Decisão (dono, 2026-10-26): **opção A**. Os campos de cartão são **iframes cross-origin de
`secure-fields.mercadopago.com`** (Cypress não digita), e o `createCardToken` vai pro **MP
real** (build do 8090 com chave de **produção** `APP_USR-…`; WireMock só mocka o backend).
Lógica coberta por PAY-01/02/14 (API); a casca de UI fica como verificação manual roteirizada
(ver nota na matriz). Gap pra o manual: o frontend precisa de chave **sandbox** (`TEST-…`)
pra usar cartões de teste do MP.

### PENDENTE / próximos (substitui o da §7)
- **PLAN-01 na tela** (P0): Free não exporta PDF na UI — botão "PDF" desabilitado + title
  "Exportar PDF requer plano PRO ou superior"; controle positivo no PRO (habilitado). Já
  especificado; é o próximo a escrever.
- **Selenium** das telas (UI-01/02/03/PLAN-01) — fase opcional conforme vagas-alvo.
- **Fase 6 (CI):** Newman + Maven + Cypress no GitHub Actions, checkout dos 3 repos, Allure.

---

## 9. Atualização (2026-10-28) — Fase 6 FECHADA; próximo chat é correção de bugs

Esta seção põe o handoff em dia (as §7/§8 ficaram desatualizadas). Matriz = fonte da verdade.

### O que fechou desde a §8
- **PLAN-01 na tela ✅** (`e2e-cypress/.../plan-01-pdf.cy.js`) e **REP-01/REP-02 ✅**
  (`api-restassured/.../RelatorioPdfTest.java`; PDFBox 2.0.31 no pom; REP-02 é na API —
  `GET /scan` → `compliance.lgpdItems`/`isoItems`, NÃO no PDF).
- **Fase 6 (CI + Allure) CONCLUÍDA.** `.github/workflows/ci.yml`: 1 job sobe o
  docker-compose (os 3 repos lado a lado), roda RestAssured + Newman + Cypress, gera o
  relatório Allure unificado e publica no **GitHub Pages** — ao vivo em
  `https://joaoariza.github.io/cyberaudit-qa/`, **59 testes, 100% verde**. Allure: adapter
  no pom (`allure-junit5`), `newman-reporter-allure`, `@shelex/cypress-allure-plugin`.
  Armadilha já paga da CI: o app segue `navigator.language`; o runner é en-US → renderizava
  em inglês e os seletores PT quebravam. Fix: `cypress/support/e2e.js` fixa
  `localStorage['cyberaudit.lang']='pt-BR'` num `window:before:load`.
- **LinkedIn:** série de 6 posts pronta em `docs/linkedin-plan.md` (texto + notas de imagem).

### PRÓXIMO CHAT — corrigir os bugs no Backend (e automatizar a regressão)
Os 4 bugs estão documentados em `docs/bugs/`. O trabalho de fix é no **repo do Backend**
(`cyberaudit-backend`). **Regra combinada com o dono: para CADA bug corrigido, escrever (ou
reativar) o caso de teste de regressão em cima** — os testes `@Disabled` viram verdes e os
casos saem de 🐛 na matriz. Mapa bug → caso a destravar:
- **BUG-01** (API key não autentica, `LazyInitializationException` em `ApiKeyAuthFilter`) →
  desbloqueia **AUTH-05** (chave revogada → 401; precisa do controle: chave válida → 200).
- **BUG-02** (SSRF: IPv4 hex `0x7f000001` não normalizado) → reativa o teste **SSRF-03 hex**
  que está `@Disabled` em `SsrfGuardTest`.
- **BUG-03** (scan não conecta em HTTP puro em porta não-padrão, `httpStatus:0`) →
  desbloqueia **SCAN-01**, reativa **SCAN-03** (`@Disabled`, espera XSS+SQLi no Juice Shop) e
  libera **SCAN-02/04** e **SCORE-01**.
- **BUG-04** (replay de TOTP — mesmo código 2FA reusável; `verifyLoginCode` ramo TOTP não
  consome o código, diferente do Email OTP que marca `used`) → escrever **AUTH-04** (sub-casos
  sem-código/inválido → 422 e controle válido → 200 já desenhados; o reuso fica `@Disabled`
  guardando "2º uso → negado" até o fix). **Precisa adicionar lib de TOTP no pom** do
  RestAssured (ex. `dev.samstevens.totp:totp`) + conta 2FA dedicada no seed (throttle de
  2FA: 5 falhas/15 min por usuário).

### Caso novo pedido pelo dono — verificação de posse de domínio (fluxo feliz)
O dono comprou **PRO na conta pessoal dele** no CyberAudit e quer testar o **módulo de
vincular o próprio domínio**: subir o arquivo que o sistema pede (`/.well-known/cyberaudit.txt`
com o token) pra provar a posse, e verificar se o módulo **libera** o domínio (→ scan ativo /
PDF nesse domínio). Hoje a matriz só tem o **negativo** (DOM-01: domínio não verificado →
recusado); isto é o **positivo** — candidato a **caso novo (ex. DOM-02)**. Observações:
- É verificação da **própria** conta/domínio do dono (uso legítimo do produto, não é scan
  ativo de terceiro) — pode ser feito **manualmente** na conta PRO real pra validar o módulo.
- Pro portfólio automatizado, reproduzir o **fluxo local** (o mecanismo de posse já existe:
  `PlanLimitService` + a checagem ao vivo de `/.well-known/cyberaudit.txt`; o
  `PLATFORM_STAFF_EMAILS` hoje pula essa checagem no ambiente de teste — ver §5). Planejar
  como manual (conta real) + automatizado (ambiente local).

### Commits pendentes neste repo (conferir `git status`)
Em 2026-10-28 o resto já está no `origin/main` (foi preciso pra a CI rodar verde): CI,
adapters de Allure, specs, pom, `.gitignore`, bugs, matriz, etc. Pendentes só: **`HANDOFF.md`**
e **`docs/linkedin-plan.md`**. Commit no padrão `tipo(escopo): descricao` (sem acento, sem
atribuição); o Claude não commita sozinho — manda o comando.

---

## 10. Correção de bugs, uma etapa por vez (2026-10-29)

Ritmo combinado com o dono: **um bug por etapa** → Claude corrige no Backend + escreve a
regressão na suíte do **Backend** (produto, não portfólio) + verifica ao vivo no ambiente
Docker + atualiza bug/matriz/HANDOFF → o dono commita → o dono escreve o caso de QA
destravado (Regra 3) → próxima etapa. Ordem: BUG-01 → BUG-02 → BUG-05 → BUG-03 → BUG-04
(o BUG-05 apareceu na revisão do BUG-02 e entrou na frente por ser o mesmo arquivo).

**Ajuste do dono (2026-10-29, depois da Etapa 3):** os casos de QA destravados ficam para
**depois da rodada de bugs** — o dono escreve todos de uma vez. Cada etapa agora termina
nos comandos de commit; a fila do dono vai acumulando no fim desta seção.

### Etapa 1 — BUG-01 ✅ corrigido (API key não autenticava)
- **Fix:** `ApiKeyRepository.findActiveByKeyPrefixWithOwner` (`JOIN FETCH` do `createdBy` +
  `account`) no lugar de `findByKeyPrefixAndRevokedAtIsNull`; `ApiKeyService.validate` usa a
  nova query. Regressão: `ApiKeyAuthIntegrationTest` (3 testes). Suíte do Backend: 622 verdes.
- **Ambiente:** o container `backend` foi rebuildado com o fix
  (`docker compose -f env/docker-compose.test.yml up -d --build backend`). O
  `backend-noallowlist` (8099, SSRF-06) continua com a imagem antiga — não importa pro AUTH-05.
- **Destrava AUTH-05 (dono escreve).** O que prova: uma chave revogada perde o acesso na
  hora — se o 401 da revogada viesse sem um 200 da MESMA chave antes, ele não distingue
  "revogação funciona" de "API key quebrada" (que era exatamente o BUG-01). Contrato
  confirmado ao vivo (qa-pro, token forjado):
  - `POST /api-keys` `{"name":"..."}` com Bearer → **201**, corpo traz `id`, `keyPrefix` e
    `plainKey` (`ca_` + 32 hex; só aparece nesta resposta).
  - Qualquer rota autenticada com header **`X-Api-Key: <plainKey>`** (sem Bearer) → ex.
    `GET /api-keys` → **200** (array). Chave revogada ou adulterada → **401**
    `{"error":"Autenticação necessária."}`.
  - `DELETE /api-keys/{id}` com Bearer → **204**; revogar de novo → 409 (lido no código).
  - Armadilhas (lidas no `ApiKeyService`): nome da chave é **único por conta** (repetir → 409, use nome único por run);
    máximo de **10 chaves ativas** por conta (passou disso → 402, então revogue o que criar);
    ~~qa-free não cria chave (FREE → 402)~~ — **errado** (conferido ao vivo em 2026-11-15:
    o qa-free cria, 201). Virou o BUG-18, aberto; ver §12. O nome de chave revogada continua
    ocupado (repetir → 409).

### Etapa 2 — BUG-02 ✅ corrigido (SSRF: IPv4 em formato alternativo)
- **Fix:** `SsrfGuard.rejectNonCanonicalIpv4` — host cujo último rótulo é número (regra
  WHATWG) só passa se for `a.b.c.d` decimal canônico; o resto → 403 com "equivale a X".
  Recusa em vez de normalizar porque o JDK e o navegador discordam (`0177.0.0.1`: JDK =
  177.0.0.1 público, navegador = 127.0.0.1). Regressão: 18 casos novos no `SsrfGuardTest`
  do Backend. Suíte do Backend: 640 verdes. Container `backend` rebuildado.
- **Destrava o SSRF-03 hex (dono tira o `@Disabled`).** O que prova: o guard não pode ser
  contornado reescrevendo o endereço interno — se só a forma "bonita" `127.0.0.1` fosse
  barrada, qualquer reescrita (hex, octal, inteiro) virava porta de entrada pra rede
  interna. A expectativa do teste já está certa (403 `DOMAIN_BLOCKED`); a mensagem mudou
  (agora "formato alternativo (equivale a 127.0.0.1)"), mas o teste só confere `error`.
  Ideia pro dono (opcional): acrescentar `0177.0.0.1` como caso — é o mais forte dos dois.
- **Achado na revisão → BUG-05 / SSRF-07** (`docs/bugs/BUG-05-ssrf-host-com-porta.md`):
  `/scan/verify-check` (público) aceita host **com porta** e faz requisição de saída pra
  destino interno. Ainda não corrigido.

### Etapa 3 — BUG-05 ✅ corrigido (host disfarçado escapa do guard no verify-check)
- **Fix (2 camadas):** `SsrfGuard.stripPort` (chamado no início do `validateHost`) apara
  as pontas, recusa `@ / ? # \`/espaço/controle e separa a porta antes das checagens;
  `DomainProtectionService.isOwnershipVerified` valida a **URL montada** com
  `SsrfGuard.validate(url)`. Regressão no Backend: 24 casos (incl. um `ServerSocket`
  provando que nenhuma conexão chega ao "serviço interno"). Suíte do Backend: 664 verdes.
  Container `backend` rebuildado. Suíte RestAssured do dono: 44, 0 falhas, 2 pulados.
- **Destrava SSRF-07 (dono escreve).** O que prova: o guard valida o MESMO host que o
  servidor vai conectar. Se o guard lê `127.0.0.1:8081` como "não resolve, deixa passar"
  e o cliente HTTP lê como "127.0.0.1 na porta 8081", qualquer visitante (o endpoint é
  público) faz o servidor bater na rede interna. Contrato confirmado ao vivo:
  - `GET /scan/verify-check?host=<h>` — parâmetro `host`, não `url`. Sem token funciona,
    mas visitante tem 5 req/min por IP (a 6ª variante já dá 429); qa-pro é `OWNER` no
    seed e `OWNER` não tem limite por minuto → use o token dele. (Desde a Etapa L da §12,
    o limite segue o plano: o qa-pro tem 60/min e as variantes cabem. A escolha continua,
    o motivo mudou.)
  - `127.0.0.1:8081`, `localhost:8081`, `169.254.169.254:80`, `[::1]:443`, `127.0.0.1/`,
    `127.0.0.1#`, `127.0.0.1 ` → **403** `{"error":"DOMAIN_BLOCKED"}`.
  - Controle: `example.com:443` → **200** `{"verified":false,...}` (porta em host público
    vale). Atenção: `example.com` depende de DNS externo (mesma ressalva do `nip.io` do
    SSRF-04 na CI); `juice-shop:3000` → 200 é a alternativa local.
  - Armadilha: o host vai em query string → caracteres como `#`, espaço e `[ ]` precisam
    de URL-encoding (`queryParam` do RestAssured já codifica; na mão, `%23`, `%20`).
    Montando a URL na mão, `?host=127.0.0.1#` manda só `127.0.0.1` (o `#` vira
    fragmento) — que já era 403, e o teste fica verde sem testar o bug.
  - Ver o vermelho: o `backend-noallowlist` (8099) ainda roda a imagem de antes do fix
    (conferido em 2026-10-29: com porta e com `#` → 200). Apontar o SSRF-07 uma vez para
    `base.uri.noAllowlist` mostra os 403 falhando; depois rebuildar a 8099
    (`docker compose -f env/docker-compose.test.yml up -d --build backend-noallowlist`).

### Etapa 4 — BUG-03 ✅ corrigido (scan não conectava em alvo `http://`)
- **Causa (não era TLS):** o `HttpClient` do JDK sai em HTTP/2; num alvo `http://` isso
  vira pedido de upgrade h2c, e o Juice Shop (Node/socket.io) fecha a conexão sem
  responder → "header parser received no bytes", `httpStatus 0`.
- **Fix:** `ScannerHttp.newClientBuilder()` (já em HTTP/1.1) nos 22 clientes de 21 módulos
  que falam com o alvo; as 4 APIs externas fixas ficaram como estavam. Regressão:
  `ScannerHttpVersionTest` (servidor local que derruba upgrade + varredura dos beans).
  Suíte do Backend: 666 verdes. Container `backend` rebuildado.
- **Ao vivo (qa-enterprise):** passivo e ativo no Juice Shop e passivo no WireMock →
  `httpStatus` real (200 / 403 do Jetty na raiz), nenhum módulo em `ERROR`.
- **Destrava SCAN-01** (e a conexão que SCAN-02/04 e SCORE-01 precisavam no WireMock).
  O que prova: o motor completa a troca HTTP com um alvo `http://` em porta não-padrão —
  sem isso, todo módulo "roda" sem resposta e o relatório parece normal. Contrato:
  - `GET /scan?url=http://juice-shop:3000/&active=false` com token (qa-pro ou
    qa-enterprise) → 200; no corpo, `httpStatus` 200 e todo valor de `moduleStatus`
    (hoje 15 módulos) = `OK`. Leva ~6 s. Conferido ao vivo com os dois tokens.
  - `openPorts: []` é esperado: o port scan procura portas de risco conhecidas (21, 22,
    3306, 6379...) e a 3000 não está na lista. Não asserte porta aberta.
  - O resultado fica em cache (Caffeine, memória) por **host + modo + idioma, por 2 min**
    (corrigido em 2026-11-15; antes dizia "por URL + modo"). Caminho, query e conta ficavam
    fora da chave (BUG-17). Desde 2026-11-18 a chave é conta + endereço completo + modo +
    idioma (§12, Etapa J). Restart do container também zera.
- **SCAN-03 continua bloqueado**, por dois motivos que não são o BUG-03:
  - SQLi → **BUG-06** (novo, P1): o probe anexa `'` (`apple'`), o Juice Shop vaza
    `SQLITE_ERROR` e o probe não reconhece (não tem padrão de SQLite do Node).
  - XSS → **expectativa do caso**, não bug: `/rest/products/search` não ecoa o `q` na
    resposta (o XSS de busca do Juice Shop é DOM, no navegador), então `false` ali está
    certo. A parte de XSS precisa de outro alvo — ex. um stub do WireMock que ecoe o
    parâmetro sem escapar (infra, posso montar quando chegar a hora).

### Etapa 5 — BUG-06 ✅ corrigido (probe de SQL não reconhecia SQLite) — 2026-11-01
- **Fix:** `ErrorDisclosureService.containsDbErrorPatterns` ganhou o SQLite de cada
  driver (`sqlite_error` Node/Java, `sqliteerror` better-sqlite3, `sqliteexception`
  Java/.NET/Android, `sqlite3::` Ruby/PHP); corpo em minúsculas com `Locale.ROOT`.
  Regressão: `ErrorDisclosureServiceTest` (8 casos; os 6 de driver falhavam antes).
  Suíte do Backend: 674 verdes. Container `backend` rebuildado. Suíte RestAssured do
  dono: 44, 0 falhas, 2 pulados.
- **Ao vivo (qa-enterprise):** ativo em `http://juice-shop:3000/rest/products/search?q=apple`
  → `dbErrorLeakageSuspected:true` (antes `false`); nota 37 → 22.
- **SCAN-03 sem bug de produto pendente.** O que falta é do caso: a parte de XSS precisa
  de um alvo que ecoe o parâmetro do lado do servidor (esse endpoint não ecoa; `false`
  ali está certo). O SQLi já pode ser asserido nesse mesmo URL.
- Observação lateral: o probe manda `Accept: ...,/;q=0.8` (era `*/*`); irrelevante no
  Juice Shop. Virou o **BUG-07** (Etapa 7) quando testado contra um alvo em Spring.

### Etapa 6 — BUG-04 ✅ corrigido (código TOTP reutilizável) — 2026-11-01
- **Fix:** coluna nova `AppUser.totpLastUsedStep` (janela de 30 s do último código aceito,
  `updatable = false`); `TotpService.matchingTimeStep` diz qual janela casou;
  `AppUserRepository.consumeTotpStep` é uma UPDATE que só grava se a janela for mais nova
  (barra pedidos simultâneos). Login e ativação do TOTP consomem o código. Regressão:
  `TotpReplayIntegrationTest` (4, rota real) + 3 casos no `TotpServiceTest`. Suíte do
  Backend: 681 verdes. Container `backend` rebuildado (coluna criada pelo `ddl-auto`).
- **Ao vivo (qa-free, mesmo pré-auth):** código N → 200; N de novo → 422 "já utilizado";
  N+1 → 200; N de novo → 422; `000000` → 422 "inválido".
- **Destrava AUTH-04 (dono escreve).** O que prova: um código TOTP vale uma vez — quem
  observar um código não consegue cunhar uma segunda sessão com ele. Contrato:
  - `POST /auth/login {email, password}` → 200, `requires2fa:true`, `token` pré-auth.
  - `POST /auth/2fa/verify` com `Authorization: Bearer <pré-auth>` e
    `{"code":"...","method":"TOTP"}` → válido **200** (`token` de sessão); sem código ou
    errado → **422** `message:"Código TOTP inválido."`; o mesmo código de novo → **422**
    `message:"Código TOTP já utilizado. Aguarde o próximo código no aplicativo."`.
  - Precisa gerar o código em Java: lib de TOTP no pom do RestAssured (ex.
    `dev.samstevens.totp:totp`, a mesma do Backend). Segredo de teste no seed.
- **Armadilhas novas (valem pra TODO login real com TOTP nas suítes):**
  - Cada código passa **uma vez por usuário**. Dois logins reais do mesmo usuário na mesma
    janela de 30 s → o segundo leva 422. Rodar o AUTH-04 duas vezes seguidas em menos de
    30 s quebra o controle positivo da 2ª execução — espere a janela virar.
  - Use **conta dedicada**: AUTH-04 com qa-free; o UI-01 (Cypress) já loga como qa-pro.
    Na CI as suítes rodam em sequência no mesmo backend (RestAssured → Newman → Cypress),
    então usuários diferentes evitam colisão. O mesmo vale pro UI-01 em Selenium quando
    sair: não usar o qa-pro na mesma janela que o Cypress.
  - Throttle de 2FA: 5 falhas → 429 por 15 min para aquele usuário (em memória; rebuild do
    container zera). Um sucesso zera a contagem — intercale o válido entre os negativos.
  - Os casos que usam `forgeToken` (bypass) não são afetados: não passam pelo 2FA.

### Etapa 7 — BUG-07 ✅ corrigido (probe de SQL mandava `Accept` inválido) — 2026-11-02
- **Achado:** a observação lateral do BUG-06, testada contra um alvo em Spring (o próprio
  backend na 8081): com `...,/;q=0.8` o Spring loga `Could not parse 'Accept' header` e
  recusa a requisição antes do controller (401 ali; 406 num Spring sem segurança). A
  consulta com a aspa nem roda → nenhum alvo Spring era testado pelo probe de SQL. O Node
  do Juice Shop tolera, por isso o BUG-06 passou ao vivo.
- **Fix:** `*/*` no lugar de `/` em `ErrorDisclosureService` (um caractere). Regressão:
  `alvoQueValidaOAcceptRecebeOProbe` no `ErrorDisclosureServiceTest` — o servidor local lê
  o `Accept` com o parser do próprio Spring (`MediaType.parseMediaTypes`). Suíte do
  Backend: 682 verdes. Container `backend` rebuildado; ativo no Juice Shop continua
  `dbErrorLeakageSuspected:true`. Suíte RestAssured do dono: 44, 0 falhas, 2 pulados.
- **Não destrava caso da fila** (nenhum caso cobre alvo Spring); coberto só no Backend.

### Rodada de bugs fechada (2026-11-02)
BUG-01 a BUG-07 corrigidos no Backend, cada um com regressão na suíte dele (622 → 682).
Nenhum caso da matriz está mais em 🐛. Próximo passo: a fila do dono abaixo.

### Fila do dono — depois da rodada de bugs
**Fichas prontas em `docs/fichas-da-fila.md` (2026-11-15).** Uma por caso, na ordem
sugerida, com cada contrato reconferido ao vivo depois das etapas A–G. Onde a ficha e esta
lista divergirem, vale a ficha (§12).

- **AUTH-05** (BUG-01) — contrato na Etapa 1.
- **SSRF-03 hex** (BUG-02) — ✅ feito em 2026-11-24 (`1b1e469`).
- **SSRF-07** (BUG-05) — ✅ feito em 2026-11-24 (`eb7a861`).
- **SCAN-01** (BUG-03) — contrato na Etapa 4.
- **AUTH-04** (BUG-04) — contrato e armadilhas na Etapa 6 (lib TOTP no pom, conta qa-free).
- **SCAN-03** — produto pronto (BUG-03 + BUG-06). SQLi: `dbErrorLeakageSuspected:true` no
  `/rest/products/search?q=apple` (Etapa 5). XSS: stub `eco.json` montado em 2026-11-15
  (`eco-mock` cru, `eco-escapado-mock` como controle); ver a ficha 4.
- **SCAN-05** (BUG-08) — contrato e armadilhas na §11, Etapa A.
- **SCAN-06** (BUG-09) — desbloqueado pelo DNS falso; contrato na §11, Etapa C.
- **SCAN-07** (BUG-10) — contrato e armadilhas na §11, Etapa C.
- **SCAN-08** (BUG-11) — contrato e armadilhas na §11, Etapa D.
- **SCAN-09** (BUG-12) — contrato e armadilhas na §11, Etapa E.
- **SCAN-10** (BUG-14) — contrato e armadilhas na §11, Etapa F.
- **SCAN-11** e **UI-04** (BUG-15) — contrato e armadilhas na §11, Etapa G.
- **PLAN-03** (BUG-18) — contrato na §12, Etapa H; ficha 15.
- **PLAN-04** (BUG-19) — contrato na §12, Etapa I; ficha 16.
- **SCAN-12** (BUG-17) — contrato na §12, Etapa J; ficha 17.
- **SCAN-13** (BUG-16) — contrato na §12, Etapa K; ficha 18.
- **RATE-01** (BUG-20) — contrato na §12, Etapa L; ficha 19. O PLAN-02, já escrito, fica
  vermelho com o fix (revisão no fim das fichas).
- **SCAN-14** (BUG-13) — contrato na §12, Etapa M; ficha 20.
- **SCAN-15** (BUG-21) — contrato na §12, Etapa N; ficha 21.
- **SCAN-16** (BUG-22) — contrato na §12, Etapa O; ficha 22.
- **Depois da fila: Fase 7 — Playwright** (pedido do dono em 2026-11-24). O plano está em
  `docs/handoff-playwright.md`. Pasta `playwright/`, ao lado do `e2e-cypress/`. É trilha de
  aprendizado real, como foi o Cypress: o dono escreve os casos e o Claude faz a infra e revisa.
  Começa pela Etapa P0, as decisões da §3 de lá.

## 11. Feedback de cliente sobre o laudo — etapas A–G (2026-11-07)

Um cliente contestou itens do laudo do site dele (PHP 8.3.36 na Hostinger). O Claude
conferiu cada item no código e ao vivo (API pública do NVD, DNS público), sem aceitar o
feedback de cara. Saíram 7 etapas, aprovadas pelo dono, no mesmo ritmo da §10: uma por
etapa, regressão no Backend, dono commita, caso de QA destravado entra na fila acima.
O nome do cliente fica fora deste repositório (é público e o laudo é confidencial).

- **A** — CVE de extensão vira CVE do PHP (`isVulnerable`). Bug. ✅ BUG-08.
- **B** — DKIM: Hostinger fora da lista de provedores; chave revogada (`p=` vazio) conta
  como encontrada. Bug. ✅ BUG-09.
- **C** — Spoofing: DMARC `quarantine`/`reject` + SPF válido passa a LOW; `pct<100` não
  conta como aplicado; SPF `+all` conta como quebrado; texto deixa de exigir `-all`.
  ✅ BUG-10 (+ DNS falso no ambiente).
- **D** — HSTS ausente: rótulo HIGH → MEDIUM mantendo -10 (o HIGH acionava o override de
  risco); textos de recomendação alinhados (31536000 x 2592000). ✅ BUG-11.
- **E** — Cookies: atributos lidos de verdade (hoje `contains("secure")` no Set-Cookie
  inteiro); cookie de CSRF sem exigir HttpOnly; `connect.sid` como sessão; severidade do
  agregado = a do pior cookie; cookie que não é de sessão → LOW sem desconto. ✅ BUG-12
  (+ BUG-13 lateral; ✅ corrigido na §12, Etapa M).
- **F** — Pesos por severidade: LOW custa no máximo 2 (versão exposta, robots.txt,
  security.txt, Referrer/Permissions ausentes, sem WAF). Muda a nota de todo scan. ✅ BUG-14
  (o "robots.txt sem desconto quando tudo responde 401/403" foi retirado — ver a Etapa F).
- **G** — Cosméticos: "null" no bônus de WAF; porta 80 INFO quando redireciona; hosts
  relacionados no idioma do laudo (pool próprio sem `IdiomaThreads`). ✅ BUG-15 (+ HSTS
  `max-age=0` fora do catálogo e card DMARC com `p=medium`, achados na mesma revisão).

**As 7 etapas fecharam em 2026-11-14.** A fila volta para os casos do dono (§10); os casos
novos desta rodada estão nela.

### Etapa A — BUG-08 ✅ corrigido (CVE de extensão como CVE do PHP) — 2026-11-07
- **Achado:** o NVD registra "extensão X do PHP" como um AND — a extensão com
  `vulnerable:true` e o `php:php` com `vulnerable:false` ("roda em PHP"). A consulta por
  `cpeName` sem `isVulnerable` casa com os dois lados. Medido: `php:php:8.3.36` → 2 CVEs
  sem o parâmetro, 1 com. O CVE-2013-2220 (PECL radius, 7.5) tirava -6.
- **Fix:** `&isVulnerable` na URL + `produtoMarcadoVulneravel` no parse (descarta o CVE se
  nenhum critério `vulnerable:true` é do nosso vendor:produto); javadocs corrigidos; URL do
  NVD injetável só para o teste. Regressão: `CVEProdutoVulneravelTest` (6; 3 vermelhos
  antes do fix). Suíte do Backend: 688 verdes. Container `backend` rebuildado (o 8099
  continua na imagem antiga). Suíte RestAssured do dono: 44, 0 falhas, 2 pulados.
- **Infra nova (Claude):** stub `env/wiremock/mappings/php-banner.json` e alias
  `php-banner-mock` no WireMock + na `SSRF_TEST_ALLOWLIST` do `backend`. Exige recriar os
  dois containers: `docker compose -f env/docker-compose.test.yml up -d wiremock backend`.
- **Contrato do SCAN-05 (ao vivo, depois do fix):** qa-pro, `GET /scan?url=http://php-banner-mock:8080`
  (passivo) → 200; `techFingerprint.detectedVersions` = `{"PHP":"8.3.36"}`;
  `cveFindings` = só `CVE-2007-3205` (CVSS 5.0); nenhuma issue com id começando em
  `CVE_` em `score.issues`; nenhuma nota com "CVE" em `score.notes`. Antes do fix:
  `CVE-2013-2220` na lista, issue `CVE_CVE_2013_2220` (MEDIUM) e nota
  `CVE potencial tier HIGH detectado: -6`.
- **Armadilhas:**
  - Alvo é o host `php-banner-mock`, não `wiremock`: a chave do cache de scan era o host, e
    `wiremock` colidia com o AUTHZ-01 (até o fix do BUG-17, em 2026-11-18). O stub só
    responde para esse Host, só na raiz.
  - O fingerprint faz **HEAD**, não GET — por isso o stub é `ANY`. Com GET só, o
    `detectedVersions` volta vazio e o CVE nem é consultado (a asserção negativa passaria
    sem testar nada).
  - Depende do NVD real (internet do container, sem API key: 5 req/30 s). Se o NVD falhar
    ou der 429, `cveFindings` volta `[]` e a asserção "sem CVE-2013-2220" passa à toa. Use
    a presença do `CVE-2007-3205` como prova de que a consulta aconteceu; se um dia ele
    sumir, é o NVD que mudou, não o produto. Não fixe a lista inteira nem o score exato.
  - `score` é um objeto (`score.score`, `score.riskLevel`, `score.notes`, `score.issues`);
    as issues de CVE têm id `CVE_` + o CVE com `_` (`CVE_CVE_2013_2220`).
  - O cache de scan guarda o resultado por 2 min (por conta e endereço, desde o BUG-17): para
    ver o "antes" de novo, espere 2 min ou reinicie o `backend` (cache em memória).

### Etapa B — BUG-09 ✅ corrigido (DKIM da Hostinger e chave revogada) — 2026-11-09
- **Achado:** conferido no DNS público do domínio do cliente — SPF
  `include:_spf.mail.hostinger.com`, MX `mx1/mx2.hostinger.com`, e **três** seletores como
  CNAME para `*.dkim.mail.hostinger.com`: `hostingermail-a` com chave, `-b` e `-c` com
  `v=DKIM1;p=` (revogada). A Hostinger não estava na tabela, e a sonda contava qualquer TXT
  como DKIM (`records.length > 0`) — os revogados contariam, e um TXT curinga também.
- **Fix:** provedor `Hostinger` na tabela + seletores na reserva; `temChaveDkim` só conta
  `p=` não vazio e descarta `v=` diferente de `DKIM1`. Regressão: `DkimChavePublicadaTest`
  (12) + `DkimProviderHintTest` (+2); 9 vermelhos antes. Suíte do Backend: 702 verdes.
  Container `backend` rebuildado; suíte RestAssured do dono: 44, 0 falhas, 2 pulados.
- **Ao vivo:** o `DnsSecurityService` real rodado da máquina contra o DNS público (só DNS,
  nenhum HTTP no site do cliente): antes `NOT_DETECTED`, depois `hostingermail-a`; a regra
  aplicada aos TXT reais aceita o `-a` e recusa `-b`/`-c`. Não dá para mostrar pelo
  `GET /scan` no ambiente: o scan do domínio real faria HTTP no site do cliente, e o
  ambiente não tem DNS que a gente controle.
- **SCAN-06 (P2) fica esperando infra:** o caso precisa de um DNS falso no ambiente
  (zona de teste com SPF/MX da Hostinger e os seletores). Ele entra na etapa C, que
  precisa da mesma infra para as variações de DMARC/SPF — o Claude monta e documenta o
  contrato lá.

### Etapa C — BUG-10 ✅ corrigido (régua do risco de spoofing) — 2026-11-10
- **Achado:** LOW exigia SPF `-all` E DMARC `p=reject`. O cliente tem `~all` +
  `p=quarantine` (conferido no DNS público) e perdia -6 como "proteção parcial"; sair de
  `p=none` para `quarantine` não mexia no score. Com DMARC em bloqueio o qualificador do SPF
  não muda o destino do falso (fail/softfail/neutral reprovam igual no DMARC). Do outro
  lado, `+all` — que faz o falso PASSAR alinhado e anula o DMARC — saía MEDIUM, e `pct=` era
  ignorado.
- **Fix:** LOW = SPF válido + DMARC em bloqueio (`reject` com qualquer `pct`; `quarantine`
  só com `pct` 100). `+all`/`all` = SPF quebrado (HIGH com DMARC, CRITICAL sem). Política do
  SPF pelo qualificador do termo `all`, não por substring. Textos MEDIUM/LOW/recomendação
  revistos (pt-BR e en), texto novo para o `+all`, HIGH de SPF duplicado aponta o SPF.
  Regressão: `SpoofingEmBloqueioTest` (30; 16 vermelhos antes). Suíte do Backend: 732
  verdes. Suíte RestAssured do dono: 44, 0 falhas, 2 pulados. Decisões (reject parcial,
  `?all`, `+all` não CRITICAL) e o que ficou de fora: no BUG-10.
- **Infra nova (Claude): DNS falso.** Serviço `coredns` no compose servindo a zona
  `dns-mock.test` (`env/coredns/`, README com a tabela de domínios e como conferir). O
  backend aponta o dnsjava para ele (`JAVA_TOOL_OPTIONS=-Ddns.server=coredns`,
  `DNS_RESOLVER=system`); o HTTP do scan continua no DNS do Docker, por isso cada domínio é
  também alias do WireMock (stub `dns-mock.json`) e está na `SSRF_TEST_ALLOWLIST`. Subir:
  `docker compose -f env/docker-compose.test.yml up -d --build coredns wiremock backend`.
  Só o `backend` (8081) usa o DNS falso; o 8099 não.
- **Contrato (ao vivo, depois do fix):** qa-pro, `GET /scan?url=http://<dominio>:8080`
  (passivo) → 200. Campos em `dnsSecurityResult`.
  - SCAN-06: `dkim-hostinger.dns-mock.test` → `dkimHintFound` true, `dkimSelector`
    `hostingermail-a`; `dkim-revogado` e `dkim-curinga` → false, `NOT_DETECTED`.
  - SCAN-07: `quarantine` → `emailSpoofingRisk` LOW; `quarantine-pct50` → MEDIUM;
    `reject-pct25` → LOW; `spf-mais-all` → HIGH; `dmarc-none` → MEDIUM. Em `score.notes`,
    `DNS Security (email spoofing risk MEDIUM): -6` (ou `HIGH): -12`) só nos MEDIUM/HIGH;
    em `score.issues`, `DNS_EMAIL_SPOOFING` com `severity` = o risco, só nos MEDIUM/HIGH.
    Antes do fix: `quarantine` MEDIUM -6 e `spf-mais-all` MEDIUM -6.
- **Armadilhas:**
  - Confira `dnsSecurityResult.lookupFailed == false` antes de tudo. Se o CoreDNS não
    respondeu, o risco vira `UNKNOWN`, sem nota — e "LOW sem nota" passaria à toa.
  - `dkim-revogado` e `dkim-curinga` não têm DMARC, então saem HIGH. É esperado e não é
    do SCAN-06: não afirme risco nesses dois.
  - Não fixe `score.score` nem o texto do `summary` (depende do idioma da requisição); no
    `spf-mais-all`, no máximo `summary` contém `+all`.
  - Cache de scan por host: o primeiro scan de cada domínio vale até reiniciar o
    `backend`. Editou a zona, incremente o serial do SOA e reinicie o `backend`.
  - Vermelho de verdade (failing-first) só com o backend buildado antes do fix:
    `9f9e84e` para o SCAN-07, `7b3236d` para o SCAN-06.

### Etapa D — BUG-11 ✅ corrigido (HSTS ausente rotulado HIGH) — 2026-11-11
- **Achado:** `HSTS_MISSING` saía HIGH. O -10 está certo, mas qualquer issue HIGH aciona o
  piso de risco do `ScoreService` (`riskLevel` no mínimo MEDIUM): site sem outro achado,
  nota 90, saía "risco médio". E as recomendações davam duas metas (`31536000` no ausente,
  "`>= 2592000`" no fraco). O argumento do cliente ("sem HSTS sobra a janela do primeiro
  acesso") estava invertido — ver o BUG-11.
- **Fix:** `HSTS_MISSING` MEDIUM, -10 mantido. Textos pt-BR/en: mesma meta (1 ano), mesmo piso
  (30 dias, o limite do `HeaderService`), impacto explicando a janela de verdade. Regressão:
  `HstsAusenteRotuloTest` (6; 3 vermelhos antes). Suíte do Backend: 738 verdes. Suíte
  RestAssured do dono: 44, 0 falhas, 2 pulados.
- **Infra nova (Claude):** stub `env/wiremock/mappings/sem-hsts.json` e alias `sem-hsts-mock`
  no WireMock + na `SSRF_TEST_ALLOWLIST` do `backend`. Recriar os dois:
  `docker compose -f env/docker-compose.test.yml up -d wiremock backend`.
- **Contrato do SCAN-08 (ao vivo, depois do fix):** qa-pro, `GET /scan?url=http://sem-hsts-mock:8080`
  (passivo) → 200; `headers["Strict-Transport-Security"]` = `MISSING`; em `score.issues`,
  `HSTS_MISSING` com `severity` MEDIUM; em `score.notes`, `HSTS ausente: -10`. Antes do fix
  (imagem `dc1c999`, mesmo alvo): `severity` HIGH, nota igual.
- **Armadilhas:**
  - O piso de risco (nota 90 que não pode virar MEDIUM) não dá para testar aqui: o alvo não
    tem HTTPS válido, então `SSL_INVALID` e `CSP_MISSING` (HIGH) já levam o `riskLevel` a
    HIGH. Não afirme `riskLevel` nem `score.score` no SCAN-08 — o piso está coberto no
    Backend (`HstsAusenteRotuloTest`).
  - A nota vem no idioma da requisição (`HSTS ausente: -10` / `HSTS missing: -10`). Afirme
    pelo `id` da issue e, na nota, no máximo que contém `-10`.
  - Cache de scan por host: o primeiro scan vale até reiniciar o `backend`.

### Etapa E — BUG-12 ✅ corrigido (cookies) — 2026-11-12
- **Achado:** flag lida por substring do Set-Cookie inteiro (`secure_pref` saía Secure;
  `samesite = Lax` e valor inválido saíam `UNKNOWN`, sem problema); `XSRF-TOKEN`/`csrftoken`
  como sessão por conter "token"/"csrf" (todo Laravel padrão perdia -10 por falta de HttpOnly,
  que nesse cookie é proposital); `connect.sid`/`sid` não eram sessão; cookie que não é de
  sessão custava -5; `INSECURE_COOKIES` sempre MEDIUM, até com o -10 de HIGH.
- **Fix:** parser de atributos (RFC 6265 §5.2); tipo do cookie (CSRF antes de sessão; família
  `sid`); só sessão custa ponto — o resto é LOW, listado sem desconto; CSRF não exige HttpOnly;
  agregado com a severidade do pior e o título contando só os que custam. Regressão:
  `CookieAtributosTest` (19; 14 vermelhos rodando contra o código antigo). Suíte do Backend:
  757 verdes.
- **Decisão a rever pelo dono:** o agregado HIGH passa a acionar o piso de risco (MEDIUM). O
  cliente (sessão PHP sem HttpOnly) sai risco MEDIUM mesmo com nota alta. Alternativa no BUG-12.
- **Lateral:** BUG-13 (compliance "Cookies sem SameSite" nunca dispara) — aberto, fora deste
  commit. ✅ Corrigido na §12, Etapa M.
- **Infra nova (Claude):** stub `env/wiremock/mappings/cookies.json` e alias `cookies-mock` no
  WireMock + na `SSRF_TEST_ALLOWLIST` do `backend`. Recriar `wiremock` e `backend`.
- **Contrato do SCAN-09:** qa-pro, `GET /scan?url=http://cookies-mock:8080` (passivo) → 200.
  Em `cookieIssues` (por `name`): `PHPSESSID` `risk` HIGH; `connect.sid` HIGH; `secure_pref` LOW
  com `secure` false; `visitante` LOW; nenhum item `XSRF-TOKEN`. Em `score.issues`,
  `INSECURE_COOKIES` com `severity` HIGH; nota de cookies com `-10`. Antes do fix: `XSRF-TOKEN`
  HIGH, `secure_pref` MEDIUM com `secure` true, `visitante` e `connect.sid` MEDIUM,
  `INSECURE_COOKIES` MEDIUM.
  **Ao vivo (8081, 2026-11-12): confirmado nos dois lados** — na imagem da etapa D saiu
  exatamente o "antes"; depois do rebuild, exatamente o contrato (`INSECURE_COOKIES` HIGH,
  "2 cookie(s)", nota -10). Suíte RestAssured: 44 / 0 falhas / 2 skipped.
  Depois de reiniciar o Docker, `juice-shop` e `backend-noallowlist` não voltam sozinhos
  (`up -d --no-build juice-shop backend-noallowlist`): sem o `juice-shop` o nome não resolve,
  o guard não tem IP para bloquear e o SSRF-06 `[1]` cai com 200 em vez de 403.
- **Armadilhas:**
  - Procure cada cookie pelo `name`, não pela posição: a ordem da lista segue a dos headers,
    que não é contrato.
  - O título traz a contagem no idioma da requisição ("2 cookie(s) com problemas de
    segurança"); afirme a `severity` e o `id`, não o texto.
  - Não afirme `riskLevel` nem `score.score`: o alvo não tem HTTPS válido.
  - Cache de scan por host: o primeiro scan vale até reiniciar o `backend`.

### Etapa F — BUG-14 ✅ corrigido (peso do LOW) — 2026-11-13
- **Achado:** achado LOW custava de 3 a 5 — versão exposta, robots.txt e Referrer-Policy
  ausente tiravam 5, o mesmo que X-Frame-Options ausente (MEDIUM). No primeiro laudo do
  cliente, três itens de higiene somavam -13, mais que o cookie de sessão sem HttpOnly.
- **Fix:** nenhum LOW passa de 2. -2 para o LOW que entrega informação (versão exposta,
  robots.txt, TRACE/método atrás de auth, UI de docs de API); -1 para boa prática ausente
  (security.txt, Permissions-Policy, Referrer-Policy, sem WAF). Referrer-Policy ausente desceu
  mais porque o navegador atual já aplica `strict-origin-when-cross-origin` sem o header — o
  texto de impacto, que dizia "URLs internas podem vazar", foi reescrito. As notas do
  catálogo (pt e en) trazem o número novo. Regressão: `PesoLowTest` (21; 19 vermelhos contra
  o código antigo). Suíte do Backend: 778 verdes.
- **Retirado da proposta:** o robots.txt com tudo em 401/403 não zera. A sonda não segue
  redirect: tela de login responde 302 (ou 200), Basic auth responde 401 — mesma proteção,
  mesmo "/admin existe" revelado. Fica -2 para todos.
- **Laudos do cliente refeitos com A–F:** primeiro laudo 60 → 74 (risco MEDIUM, pelo cookie
  HIGH da etapa E); laudo depois da correção 86 → 97 (SECURE).
- **Alavanca que ficou com o dono:** o bônus de WAF segue até +8 — com "sem WAF" a -1, ter
  ou não WAF vale até 9 pontos, mais que qualquer LOW. Se 97 parecer generoso, é aqui. E o
  resto da escada (MEDIUM acima de 6, HIGH abaixo de 10) ficou como está; lista no BUG-14.
- **Infra nova (Claude):** stub `env/wiremock/mappings/pesos.json` (raiz, `/robots.txt` e
  `/admin/`) e alias `pesos-mock` no WireMock + na `SSRF_TEST_ALLOWLIST` do `backend`.
  Recriar `wiremock` e `backend`.
- **Contrato do SCAN-10:** qa-pro, `GET /scan?url=http://pesos-mock:8080` (passivo,
  `Accept-Language: pt-BR`) → 200. Em `score.notes`: "Versão de software exposta
  (Server/X-Powered-By): -2", "Paths sensíveis em robots.txt: -2", "Referrer-Policy ausente:
  -1", "Permissions-Policy ausente: -1", "security.txt ausente: -1". Em `score.issues`,
  `SERVER_VERSION_EXPOSED`, `SENSITIVE_ROBOTS_PATHS`, `REFERRER_POLICY_MISSING`,
  `PERMISSIONS_POLICY_MISSING` e `SECURITY_TXT_MISSING` com `severity` LOW. Antes do fix: -5,
  -5, -5, -3, -3.
  **Ao vivo (8081, 2026-11-13): confirmado nos dois lados**, em pt e en (nota 34 → 48).
  Suíte RestAssured: 44 / 0 falhas / 2 skipped.
- **Armadilhas:**
  - As notas são texto no idioma da requisição, sem `id`: fixe o `Accept-Language` e
    procure cada nota pelo começo, afirmando o final (`: -2`). Os achados, ao contrário,
    afirme pelo `id`.
  - "Sem WAF" não aparece: a detecção de WAF não roda no scan passivo (está na nota de
    escopo passivo). O -1 dele fica coberto pelo teste do Backend.
  - Não afirme `riskLevel` nem `score.score`: o alvo não tem HTTPS válido (-35) nem HSTS
    (-10), e esses descontos não são do caso.
  - Cache de scan por host: o primeiro scan vale até reiniciar o `backend`.

### Etapa G — BUG-15 ✅ corrigido (textos do laudo) — 2026-11-14
- **Achado:** cinco textos errados, sem mudar a nota: "WAF detectado (null, probe
  bloqueado)" quando o WAF é achado só pelo bloqueio; porta 80 LOW com "HTTP exposto" quando
  ela só redireciona (a sonda não lia o `Location`); hosts relacionados no idioma do servidor
  ("max-age far too short: 86,400s" num laudo em português — pool sem `IdiomaThreads`, e o
  takeover tinha o mesmo); HSTS `max-age=0` em inglês fixo no código; card DMARC com
  "p=medium" (o nível, não a política).
- **Fix:** Backend — fallback "provedor não identificado"; porta HTTP com 30x para
  `https://` vira INFO com texto próprio; três pools com a fábrica de idioma; `max-age=0` no
  catálogo. Frontend — `politicaDmarc()` lê o `p=` (e o `pct=` < 100) do registro.
  Regressão: `TextosDoLaudoTest` (9, com guarda que varre `src/main/java` atrás de pool sem
  idioma) e `dmarc.test.ts` (6). Suítes: Backend 787, Vitest 31, build ok.
- **Infra nova (Claude):** stub `env/wiremock/mappings/textos.json` e alias `textos-mock` no
  WireMock + na `SSRF_TEST_ALLOWLIST` do `backend`. Recriar `wiremock` e `backend`; para o
  UI-04, rebuild do `frontend` (`up -d --build frontend`).
- **Contrato do SCAN-11:** qa-pro, `GET /scan?url=http://textos-mock:8080` (passivo) → 200.
  Com `Accept-Language: pt-BR`, `headers["Strict-Transport-Security"]` =
  `WEAK (max-age=0 apaga o HSTS)`; com `en`, `WEAK (max-age=0 deletes HSTS)`. Antes do fix:
  o texto em inglês nos dois.
- **Contrato do UI-04:** login qa-pro (2FA), scan passivo de
  `http://quarantine-pct50.dns-mock.test:8080`, módulo Reconnaissance → card DMARC com
  `p=quarantine; pct=50`. Antes do fix: `p=medium`.
  **Ao vivo (2026-11-14): os dois confirmados nos dois lados** — SCAN-11 por API em pt e en,
  UI-04 na tela (frontend 8090). Suíte RestAssured: 44 / 0 falhas / 2 skipped.
- **Armadilhas:**
  - SCAN-11: o idioma vem do `Accept-Language`. ~~Reinicie o `backend` entre os dois
    idiomas~~ — **errado** (conferido em 2026-11-15): o idioma faz parte da chave do cache,
    e en → pt-BR → en sem restart sai cada um no seu idioma.
  - SCAN-11: afirme só o valor do header; o alvo não tem HTTPS, então nota e risco não são
    do caso.
  - UI-04: o campo de URL pode vir preenchido (com o último alvo, por exemplo) — limpe antes
    de digitar, ou o texto é colado no fim e o scan vai para um alvo torto.
  - UI-04: depois de rebuildar o `frontend`, o navegador pode manter o bundle antigo em
    cache e mostrar o "antes". No Cypress cada execução abre limpa; na mão, recarregue
    furando o cache.
  - UI-04: o card fica dentro do módulo Reconnaissance (abre pelo menu lateral); procure pelo
    título "DMARC" e leia o valor logo abaixo dele.

## 12. Fichas da fila e achados laterais (2026-11-15)

O dono pediu as fichas da fila (§10) antes de escrever os casos. Saiu
`docs/fichas-da-fila.md`: uma ficha por caso (o que prova e por quê, preparar, pedido,
afirmar, armadilhas, como ver o vermelho), em ordem sugerida. Todo contrato foi reconferido
ao vivo (Backend `d0f9594`, Frontend `c7006bc`). Quase tudo bateu com §10/§11; o que mudou
está abaixo e nas fichas.

- **Infra nova (Claude):** stub `env/wiremock/mappings/eco.json` e aliases `eco-mock` e
  `eco-escapado-mock` no WireMock, mais na `SSRF_TEST_ALLOWLIST` do `backend`. É a parte
  de XSS do SCAN-03: o mesmo eco do `q`, cru num host e escapado no outro (controle
  negativo). Recriar `wiremock` e `backend`. O qa-enterprise faz o ativo sem linha nova no
  seed. O WireMock 3.9.1 roda o Handlebars **sem escape** e não tem `escapeHtml`; o escape
  é feito com `replace`.
- **Correções do que este HANDOFF dizia:**
  - O cache de scan é host + modo + **idioma**, por **2 minutos** (`expireAfterWrite`), e não
    "por URL" nem "até reiniciar". Mesmo host em dois idiomas não pede restart (Etapa G
    corrigida no lugar).
  - O qa-free **cria** API key (Etapa 1 corrigida no lugar) → BUG-18.
  - `moduleStatus` "OK" não prova contato com o alvo → BUG-16. O SCAN-01 precisa afirmar
    `httpStatus` 200 junto. (Corrigido na Etapa K: alvo sem resposta leva 422, e CT e CVE
    saem `ERROR` quando o serviço de fora não responde.)
- **Vermelho sem buildar nada:** o 8099 (imagem de antes da rodada) falha nos quatro casos de
  segurança: SSRF-03 hex → 200, SSRF-07 → 200, AUTH-05 com chave válida → 401, AUTH-04 com
  código repetido → 200. Por isso o rebuild do 8099 fica para **depois** de o dono ver esses
  vermelhos (ficha, seção 0).
- **Achados laterais, abertos (não corrigidos):**
  - **BUG-16** (P1) — host que não responde ganha nota (`.invalid` → 29/100, HIGH; 14
    módulos "OK"; "Erro ao conectar: null"). O mesmo "OK" aparece quando o NVD estoura o
    timeout: `cveFindings: []` com `moduleStatus.CVE` OK. ✅ corrigido, Etapa K abaixo.
  - **BUG-17** (P1) — a chave do cache ignora caminho e query (ativo em `/` e depois na
    busca do Juice Shop devolve o resultado de `/` em 9 ms, sem SQLi) e a conta (outra
    conta recebe o resultado, que não entra no histórico dela). ✅ corrigido, Etapa J abaixo.
  - **BUG-18** (P1) — conta FREE cria API key: `checkApiKeyAccess` libera `OWNER`, e todo
    cadastro nasce `OWNER`. ~~A tela mostra "disponível no PRO"~~ — errado: a tela tinha a
    mesma regra e mostrava o painel liberado ao FREE (conferido em 2026-11-17).
    ✅ corrigido, Etapa H abaixo.
- **Revisão do código do dono:** o UI-02 (Cypress) está verde sem testar o que diz. O campo
  abre com "github.com", o `.type()` digita no fim, e o banco tem 21 scans de
  `https://github.comwiremock:8080/health`. Passa porque o host inexistente ganha nota
  (BUG-16) e `cy.contains('wiremock')` casa com "github.comwiremock". Matriz: ✅ → ⚠️. A
  correção é do dono (ficha, fim do documento).
- **Próximo passo:** o dono escreve os casos pela ficha. BUG-13, 16, 17 e 18 seguem abertos;
  a ordem de correção é decisão do dono.

### Rodada dos achados laterais — uma etapa por bug (2026-11-17)
O dono pediu para corrigir os laterais agora, no ritmo de sempre: um por etapa, regressão
no Backend, conferência ao vivo, caso de QA novo na matriz, dono commita. Ordem: **H =
BUG-18**, que não tinha decisão pendente. Depois vêm o BUG-19 (mesma área, achado na H), o
BUG-17 e o BUG-16. O 17 e o 16 pedem decisões do dono, listadas em cada bug; o Claude
traz uma recomendação na vez de cada um. O BUG-13 segue aberto (✅ corrigido depois, na
Etapa M). O BUG-20 (achado na
Etapa I) entra na fila. Ele pede decisão do dono (quanto por minuto em cada plano), e a
posição dele na fila também é decisão do dono.

### Etapa H — BUG-18 ✅ corrigido (FREE criava API key) — 2026-11-17
- **Achado corrigido na conferência:** a tela **não** mostrava "disponível no PRO" ao FREE.
  O `canApiKeys` do Frontend tinha a mesma regra do Backend (`isAdmin()` = OWNER/ADMIN). O
  cardápio `/billing/plans` não cita API key em plano nenhum. A trava "PRO" só existia em
  ramos que ninguém alcançava, então a regra virou decisão do dono.
- **Decisões do dono:** (1) criar chave é de **plano pago** — PRO, ENTERPRISE e equipe da
  plataforma; FREE → 402, inclusive conta Empresa no FREE (o tipo se escolhe no cadastro).
  (2) **Chave antiga continua valendo**, listável e revogável.
- **Fix:** Backend — `checkApiKeyAccess` decide só por `effectivePlan(user)`. Frontend —
  `src/apiKeys.ts` (`podeCriarApiKey`, `painelApiKey`); conta sem direito e com chave ativa
  vê a lista com "Revogar", sem o formulário. Regressão:
  - `ApiKeyPlanoIntegrationTest` (6; 3 vermelhos contra o código antigo);
  - `apiKeys.test.ts` (9).

  Suítes: Backend 787 → 793, Vitest 31 → 40, build ok. Containers `backend` e `frontend`
  rebuildados. RestAssured do dono: 44 / 0 falhas / 2 pulados.
- **Ao vivo:** antes, qa-free → 201 e painel liberado. Depois, qa-free → 402; qa-pro e
  qa-enterprise → 201. No cenário da chave antiga (qa-free posto em PRO, cria, volta para
  FREE): a chave autentica com 200, a nova leva 402 e a tela mostra só a lista com
  "Revogar". Tudo foi restaurado: planos do seed e nenhuma chave de teste ativa.
- **Contrato do PLAN-03 (dono escreve):** qa-free, token forjado, `POST /api-keys`
  `{"name":"<único>"}` → **402** com `message` "API Keys requerem plano PRO ou Empresa.".
  Controle: qa-pro, mesmo pedido → **201** com `plainKey`; depois `DELETE /api-keys/{id}` →
  204.
- **O que o PLAN-03 prova:** que o recurso pago não sai de graça. A trava por papel deixava
  qualquer cadastro passar, porque todo cadastro é dono da própria conta. O controle PRO
  prova que o 402 é do plano, e não de "API key quebrada".
- **Armadilhas do PLAN-03:**
  - Nome único por execução; revogar o que o controle criar (10 ativas por conta → 402 por
    outro motivo).
  - Não afirme sobre conta Empresa no FREE nem sobre a chave antiga: o seed não tem essa
    conta, e a chave antiga pede mudar o plano no banco. As duas estão cobertas no Backend.
  - Vermelho: `base.uri.noAllowlist` (8099, imagem antiga) → qa-free 201.
- **Lateral → BUG-19** (aberto): `/api-keys/ci` roda scan sem cota diária, sem rate limit e
  sem `checkActiveScan`. Conferido: com a cota do qa-free estourada, `/scan` dá 402 e o gate
  dá 200 três vezes. Num ativo de FREE, o gate pula o "requer PRO" e só para na prova de
  posse por arquivo.
- **Infra:** o seed do PAY-15 passa a renovar as duas assinaturas a cada carga (`DO UPDATE`).
  A AUTHORIZED vence 28 dias depois da carga; no banco local, carregado em 20/10, ela venceu hoje, o
  PAY-10 a expirou, e o PAY-15 caiu (esperava AUTHORIZED, veio PENDING). Na CI o banco nasce
  novo. Local: `docker compose -f env/docker-compose.test.yml up seeder`.

### Etapa I — BUG-19 ✅ corrigido (gate de CI sem cota) — 2026-11-17
- **Fix (Backend):** as travas `checkRateLimit` e `enforceScanLimits` saíram do
  `ScanController` (eram `private`) para o serviço novo `ScanLimitsService`. O
  `checkBeforeScan` chama as duas, rate limit primeiro (um 429 não gasta cota). O
  `ScanController` usa o serviço nos mesmos pontos, sem mudar comportamento, e o
  `ApiKeyController.ciGate` passa a chamar `checkBeforeScan` antes do orchestrator.
- **Regressão:** `ApiKeyCiGateLimitesIntegrationTest`, com 6 testes, 5 vermelhos contra o
  código antigo. O alvo é `http://127.0.0.1/`: o SsrfGuard recusa com 403 `DOMAIN_BLOCKED`
  antes de qualquer requisição, então esse 403 quer dizer "passou pelas travas e chegou ao
  scan", sem tocar a rede. Os testes cobrem:
  - a chave antiga de conta rebaixada, com a cota gasta → 402;
  - o gate gasta da mesma cota do `/scan`;
  - FREE pedindo ativo → 402;
  - PRO pedindo ativo sem domínio verificado na conta → 403 `ACCOUNT_DOMAIN_NOT_VERIFIED`;
  - membro de equipe: a 61ª chamada no minuto → 429;
  - controle: PRO passivo.

  Suíte do Backend: 793 → 799. Container `backend` rebuildado. RestAssured do dono: 44 / 0
  falhas / 2 pulados.
- **Ao vivo (8081):** antes, com a cota do qa-free gasta e uma chave antiga (qa-free posto
  em PRO para criar e devolvido ao FREE), o gate deu **200** três vezes, e o ativo deu 403 na
  prova de posse em vez do 402 de plano. Depois: **402** nas três ("Limite diário de 10 scans
  atingido."), e o ativo dá 402 "Scan ativo requer plano PRO ou superior." no `/scan` e no
  gate. Chave revogada, qa-free no FREE, `backend` e `backend-noallowlist` reiniciados para
  zerar a cota.
- **Contrato do PLAN-04 (dono escreve), detalhado na ficha 16:**
  - preparar: qa-free, token forjado, `GET /scan?url=http://wiremock:8080/health` até vir
    402;
  - `GET /api-keys/ci?url=http://wiremock:8080/health&threshold=0` → **402**, `message`
    começando por "Limite diário de 10 scans atingido.";
  - `GET /api-keys/ci?url=http://juice-shop:3000&active=true` → **402** "Scan ativo requer
    plano PRO ou superior.";
  - controle: qa-pro, `threshold=0` → **200** `pass: true`; `threshold=100` → **422**
    `pass: false`.
- **O que o PLAN-04 prova:** que o gate de CI não é atalho para scan sem cota nem para ativo
  sem plano. Ele roda um scan inteiro, então tem de custar o mesmo que o `/scan`. O controle
  PRO prova que o 402 vem da cota e do plano, não de "gate quebrado".
- **Armadilhas do PLAN-04:**
  - Token, não `X-Api-Key`: o qa-free não cria chave (BUG-18). A chave antiga fica no
    Backend.
  - Limiares 0 e 100 não dependem da nota; não afirme `score`.
  - A cota é em memória: depois do caso, o qa-free fica sem scan até a meia-noite ou o
    `restart backend`. Vale o mesmo para o PLAN-02, que gasta a mesma cota.
  - O rate limit do gate não dá para afirmar com o seed: todas as contas são OWNER
    (BUG-20). ✅ Corrigido na Etapa L: o qa-free leva 429 na 6ª chamada do minuto, e quem
    cobre é o RATE-01.
  - Vermelho: no 8099, os dois 402 viram 403 `DOMAIN_BLOCKED`. O 8099 entra na lista dos
    vermelhos a ver antes do rebuild dele (SSRF-03 hex, SSRF-07, AUTH-05, AUTH-04, PLAN-03,
    PLAN-04).
- **Lateral → BUG-20** (aberto, P2): o `RateLimitService.allow` isenta o papel OWNER, e
  todo cadastro é OWNER. Conferido no `verify-check`: visitante leva 429 na 6ª chamada do
  minuto, e o qa-free fica em 200 nas doze. A isenção estava na nota do RATE-01 (2026-10-05)
  como contrato. A correção pede decisão do dono: quanto por minuto em cada plano.
  ✅ corrigido, Etapa L abaixo.
- **Observação:** o nome de chave revogada continua ocupado (`POST /api-keys` com o mesmo
  nome → 409). É mais um motivo para o nome único por execução do PLAN-03.

### Etapa J — BUG-17 ✅ corrigido (cache de scan por host) — 2026-11-19
- **Decisões do dono (2026-11-18):** (1) caminho e query entram na chave **em todo modo**,
  porque o passivo também busca a página pedida (headers, cookies e tecnologia vêm dela);
  (2) a **conta entra na chave**. Membros da mesma conta dividem a entrada, porque o
  histórico é da conta. Visitantes dividem uma entre si.
- **Fix (Backend):** `ScanOrchestrator.buildCacheKey` passa a ser
  `v4:scan:<conta ou guest>:<endereço>:active=…:lang=…`. O endereço é esquema + host + porta +
  caminho + query, com o caminho normalizado como no histórico e sem o `#`. A porta também
  não entrava antes. Lido no código: no ativo, o acerto de outra conta pulava a prova de posse
  por arquivo; com a conta na chave, isso acabou.
- **Regressão:** `ScanCacheChaveIntegrationTest` (7 testes; 5 vermelhos contra o código
  antigo). Nenhum scan roda. O `SSLService`, primeiro passo do scan, é um mock que lança uma
  exceção de sinal ("lançou" = o cache não acertou), e um espião no `ScanCacheService`
  captura a chave consultada. Suíte do Backend: 799 → 806. Container `backend` rebuildado.
  RestAssured do dono: 44 / 0 falhas / 2 pulados.
- **Ao vivo (8081):**

  | Passo | Antes | Depois |
  |---|---|---|
  | Ativo na busca, logo depois da home | 0,23 s; `url` da home; SQLi false | 3 s; `url` da busca; SQLi true |
  | qa-pro na home, logo depois do qa-enterprise | 0,24 s; histórico do qa-pro 4 → 4 | 2,4 s; 4 → 5 |
  | Controle: mesma conta, mesmo endereço | 0,23 s (acerto) | 0,23 s (acerto) |

- **Contrato do SCAN-12 (dono escreve), detalhado na ficha 17:**
  - parte A, qa-enterprise, ativo: `/` e logo depois `/rest/products/search?q=apple` → o
    segundo com `url` = a pedida e `dbErrorLeakageSuspected` true;
  - parte B, passivo, endereço único `http://juice-shop:3000/?scan12=<nanoTime>`: o
    qa-enterprise escaneia, o qa-pro escaneia logo depois, e `GET /history/juice-shop` do
    qa-pro tem **1** item com essa `url`;
  - controle: o qa-pro repete o endereço, e o histórico continua com **1** item (acerto).
- **O que o SCAN-12 prova:** que o cache só devolve resultado guardado para o mesmo endereço
  e a mesma conta. Sem isso, o ativo dá falso negativo no fluxo "home, depois a busca", e o
  scan de uma conta some do histórico dela. O controle prova que o 1 vem da separação por
  conta, e não de "cache desligado".
- **Armadilhas do SCAN-12:**
  - Conte os itens com a `url` única, não o total: o `/history/{host}` devolve no máximo
    50, e cada execução deixa uma linha nova em cada conta.
  - Passe a URL do alvo com `queryParam`: concatenada, a query do alvo vira parâmetro do
    `/scan`.
  - Ativo só com o qa-enterprise (dispensa a prova de posse).
  - Vermelho: o 8099 barra o Juice Shop. O vermelho de verdade pede o `backend` no commit
    `256670f`; o Claude prepara se o dono pedir.
- **Fichas:** a armadilha de cache do SCAN-03 (ficha 4) deixou de valer, e a seção 0 das
  fichas passou a descrever a chave nova.

### Etapa K — BUG-16 ✅ corrigido (alvo sem resposta ganhava nota) — 2026-11-22
- **Decisões do dono (2026-11-22):**
  1. domínio que não resolve → 422 `TARGET_NOT_FOUND`, **antes** de gastar cota;
  2. resolve, mas nada aceita conexão nem em http nem em https → 422 `TARGET_UNREACHABLE`
     (a cota já foi gasta: só se descobre tentando conectar);
  3. NVD ou logs de CT que não respondem → módulo `ERROR`, no aviso de resultado parcial,
     em vez de "OK".
- **Fix (Backend):**
  - `ScanTargetService` (novo): resolve o domínio no `ScanLimitsService.checkBeforeScan`
    (depois do rate limit, antes da cota; vale para `/scan`, `/scan/async`, `/scan/report`,
    `/scan/report/pdf` e `/api-keys/ci`) e de novo no `ScanOrchestrator.execute`, para o
    agendador.
  - Nome que o `SsrfGuard` recusa sem DNS (sufixo interno, IPv4 ofuscado como
    `http://2130706433`) passa por essa checagem e continua levando 403. Sem essa ressalva,
    o SSRF-03 viraria 422.
  - `HttpFetchResult.semConexao` + o https conferido antes de desistir →
    `TargetUnreachableException`. O texto do erro diz o motivo, sem "null".
  - `CVECorrelationService.correlacionar` diz se ficou `completa`. O
    `CrtShService.fetchCertsOuNull` devolve `null` quando nenhuma fonte respondeu. CVE e CT
    marcam `ERROR` nesses casos.
  - O polling do `/scan/async` recebe `errorCode` e `errorHost`.
- **Fix (Frontend):** os dois códigos mostram a mensagem do Backend como aviso, no 422 do
  POST e no ERROR do polling (`src/alvoInacessivel.ts`).
- **Regressão:**
  - `AlvoInacessivelIntegrationTest`: 8 testes, 5 vermelhos contra o código antigo, 3
    controles verdes nos dois. Nada sai da máquina: o alvo que responde é um servidor em
    127.0.0.1, liberado só na classe. O `CookieSecurityService`, logo depois da checagem de
    conexão, é um mock que lança uma exceção de sinal.
  - Testes de unidade: `HttpFetchSemConexaoTest`, `CVENvdSemRespostaTest`,
    `ModuloExternoNaoVerificadoTest` e `AsyncScanAlvoInacessivelTest`. Usam API nova e não
    compilam contra o código antigo.
  - O `ScanCacheChaveIntegrationTest` trocou `.invalid` por `127.0.0.1` liberado.
  - Suíte do Backend: 806 → 838. Vitest: 40 → 45. Containers `backend` e `frontend`
    rebuildados. RestAssured do dono: 44 / 0 falhas / 2 pulados, depois do conserto do seed
    do PAY-07 (abaixo).
- **Ao vivo (8081)** — tabela completa no bug:
  - `.invalid` com o qa-free: antes 200, 17/100 CRITICAL, cota 10 → 9; depois **422** em
    0,28 s, cota 10 → 10. Com a cota zerada, continua 422.
  - `wiremock:9`: antes 200 em 21 s, 29/100 HIGH; depois **422** `TARGET_UNREACHABLE`. No
    async: antes DONE com nota; depois ERROR com `errorCode`.
  - Controles: Juice Shop 200 com `httpStatus` 200; `http://wiremock:8080/nao-existe-qa` 200
    com `httpStatus` 404; `http://2130706433` 403; `quarantine` e `dkim-hostinger`
    (`.dns-mock.test`) 200, com o resultado das fichas.
  - Tela (8090): avisos "⚠ O domínio … não foi encontrado no DNS…" e "⚠ O site … não
    respondeu…".
- **Contrato do SCAN-13 (dono escreve), detalhado na ficha 18:**
  - qa-free: `remainingScans` antes; `GET /scan?url=http://nao-existe-qa.invalid` → **422**,
    `error` `TARGET_NOT_FOUND`, `host` o domínio; `remainingScans` igual depois;
  - qa-pro: `GET /scan?url=http://wiremock:9` → **422**, `error` `TARGET_UNREACHABLE`, `host`
    `wiremock`, `message` sem "null";
  - controle: qa-pro, `http://wiremock:8080/nao-existe-qa` → **200** com `httpStatus` 404.
- **O que o SCAN-13 prova:** que alvo que não existe ou não responde fica sem laudo e sem
  nota, e que o domínio inexistente não custa cota. O controle prova que o 422 não pega
  quem respondeu, mesmo com erro.
- **Armadilhas do SCAN-13:**
  - O PLAN-02 zera a cota do qa-free. O 422 continua (vem antes da cota), mas o "igual
    antes e depois" vira 0 = 0.
  - A parte B gasta cota, então usa o qa-pro (sem limite).
  - Afirme o `error`, não só o 422.
  - `localhost` e nomes internos continuam 403 do SsrfGuard.
  - Vermelho: a parte A no 8099 (200, 15/100, `httpStatus` 0); a B pede o `backend` no
    commit `92303a4`.
- **Fichas que mudaram:**
  - SCAN-01 (ficha 3): `CERT_TRANSPARENCY` e `CVE` ficam fora do "todo OK", porque o crt.sh
    falha em parte das rodadas no Docker (502, e o certspotter sempre dá 403). A porta
    fechada do "ver o vermelho" agora dá 422.
  - SCAN-05 (ficha 9): `moduleStatus.CVE` = `OK` entra na pré-condição.
  - UI-02 (revisão no fim das fichas): o host torto leva 422 e a tela não mostra nota, então
    o spec como está deve ficar vermelho no `contains('/100')`. A correção do spec continua
    sendo do dono.
- **Infra:** o seed do PAY-07 passa a renovar a assinatura e o plano a cada carga
  (`DO UPDATE`), como o do PAY-15. A AUTHORIZED tinha vencido no banco local, e o PAY-07
  caiu com `EXPIRED`. Local: `docker compose -f env/docker-compose.test.yml up seeder`.
- **Laterais:** nenhum bug novo de produto. O seed do PAY-07 é infra e entra num commit
  próprio.

### Etapa L — BUG-20 ✅ corrigido (rate limit isentava todo cadastro) — 2026-11-23
- **Decisões do dono (2026-11-23):**
  1. requisições por minuto pelo plano efetivo: FREE 5, PRO 60, ENTERPRISE 120 (visitante
     segue com 5 por IP);
  2. membro de equipe segue o plano da conta, não o papel;
  3. a equipe da plataforma (`PLATFORM_STAFF_EMAILS`) fica isenta.
- **Fix (Backend, `RateLimitService`):**
  - O limite sai do `PlanLimitService.effectivePlan(user)`, a mesma fonte da cota diária. A
    isenção é `isPlatformStaff(user)`. Saíram a isenção do `OWNER` e os limites por papel.
  - `rpmDoPlano` é um `switch` sem `default`: plano novo no enum não compila sem limite.
  - O limite entra na chave do balde (`user:<id>:<limite>`): quem troca de plano usa o
    balde novo na próxima requisição.
  - O limite genérico do billing não mudou.
- **Regressão:**
  - `RateLimitPorPlanoIntegrationTest`: 8 testes pelo `/scan/verify-check` e pelo `/scan`,
    com alvo `127.0.0.1` (403 = passou, 429 = barrou; nada sai da máquina). Contra o código
    antigo, 6 vermelhos e 2 controles verdes nos dois (equipe isenta, visitante 5/IP).
  - `RateLimitServiceTest`: 13 testes de unidade, com contagem exata. Usa o construtor novo
    e não compila contra o código antigo.
  - `ApiKeyCiGateLimitesIntegrationTest.rateLimitValeNoGate` passou a usar um membro de
    conta FREE (a 6ª no gate dá 429), no lugar do membro de conta ENTERPRISE a 60/min.
  - Suíte do Backend: 838 → 859. Frontend sem mudança. Container `backend` rebuildado.
- **Ao vivo (8081)** — tabela completa no bug:
  - qa-free no `verify-check`: antes 200 nas 12; depois 200×5 e 429 na 6ª. O `/scan` logo
    depois também leva 429, com a cota 10/10 intacta.
  - qa-pro: antes 200 nas 65; depois 429 na 61ª (75 chamadas em 1,2 s, num curl só).
  - qa-enterprise (equipe): 200 nas 150. Visitante: igual, 429 na 6ª.
  - As rajadas precisam de um curl só, com várias URLs: um curl por chamada leva ~150 ms no
    Windows, e o PRO repõe 1 ficha por segundo.
- **RestAssured do dono: 44 / 1 falha / 2 pulados.** A falha é o PLAN-02
  (`PlanoCotaScanTest`): "Tentativa 6 era esperado 403 … but was: <429>". É o contrato
  novo. A correção é do dono: tratar o 429 como "espere 12 s e repita", sem contar como
  tentativa. Revisão no fim das fichas; na matriz, o PLAN-02 foi de ✅ para ⚠️.
- **Contrato do RATE-01 (dono escreve), detalhado na ficha 19:**
  - pré-condição: balde do qa-free cheio (restart do backend, ou 60 s sem chamada dele);
  - qa-free, `GET /scan/verify-check?host=juice-shop:3000` → 200 nas 5 primeiras, **429**
    na 6ª (`error` `429 TOO_MANY_REQUESTS`, `message` começando por "Muitas requisições.");
  - controle: qa-pro, logo depois → 200 (balde por usuário, mesmo IP);
  - 13 s depois → 200; a seguinte → 429.
- **O que o RATE-01 prova:** que conta registrada tem limite por minuto, que ele volta
  sozinho, e que o 429 de um usuário não trava outro. Sai do ⏸️ sem a decisão B de
  2026-10-05: o `verify-check` não tem cota diária, então o qa-free não colide com nada.
- **Armadilhas do RATE-01:**
  - Um balde só para `/scan`, `/scan/async`, `/scan/report`, `/api-keys/ci` e
    `verify-check`: qualquer chamada do qa-free logo antes rouba ficha.
  - Espera entre 13 e 20 s (12 s cravados pode dar 429; 24 s já são 2 fichas).
  - A `message` sai em português mesmo com `Accept-Language: en`; sem `Retry-After`.
  - O qa-enterprise é isento (equipe), não serve de controle de limite.
  - Vermelho: no 8099, `juice-shop:3000` dá 200 nas 8 (o 8099 não tem o fix do BUG-05). O
    `wiremock` não serve lá (403 já na 1ª). O 8099 entra na lista dos vermelhos a ver antes
    do rebuild dele.
- **Fichas que mudaram:**
  - seção 0: bloco novo "Rate limit por minuto", e o RATE-01 na tabela do 8099;
  - SSRF-07 (ficha 2): continua com o qa-pro, por outro motivo (60/min cabem as 8);
  - PLAN-04 (ficha 16): o preparar trata o 429 como "espere e repita"; 13 s antes dos
    passos 1 e 2. Conferido: o preparar levou 90 s; sem a espera, o passo 1 deu 429; com
    ela, 402;
  - SCAN-13 (ficha 18): 13 s antes do passo 2 se o qa-free acabou de passar por outro caso;
  - ficha 19 nova (RATE-01) e a revisão do PLAN-02 no fim.
- **Laterais:** nenhum bug novo. Nit no Backend, sem mudança: o javadoc do
  `PlanLimitService.checkActiveScan` ainda diz "OWNER / ADMIN → sempre permitido", e o
  código já não faz isso.

### Etapa M — BUG-13 ✅ corrigido (compliance e card de cookie) — 2026-11-23
- **Decisões do dono (2026-11-23):**
  1. o compliance segue o card do cookie: conta todo cookie em que o card lista o
     problema, inclusive SameSite inválido e cookie de preferência;
  2. a cobrança de HttpOnly do token CSRF no compliance (achada na leitura, mesma raiz)
     entra junto, como exceção à regra "lateral vira bug próprio";
  3. os avisos do card na tela, com o mesmo defeito, entram junto: o Backend manda os
     códigos e a tela desenha a partir deles.
- **Fix (Backend):**
  - `CookieFinding.problems` (campo novo na API): os códigos do veredito do card, que são as
    chaves do catálogo (`COOKIE_SEM_SECURE`, `COOKIE_SEM_HTTPONLY`, `COOKIE_SEM_SAMESITE`,
    `COOKIE_SAMESITE_NONE_SEM_SECURE`), na ordem do `issues`.
  - `ComplianceMappingService`: os três itens de cookie do Art. 49 leem `problems`. Saíram
    os métodos que refaziam a conta pelas flags.
- **Fix (Frontend):** `src/cookieAvisos.ts` decide os avisos do card por `problems`. Scan
  antigo do histórico, sem o campo, usa as flags, agora com `"MISSING"` e com
  `SameSite=None` só sem Secure.
- **Regressão:**
  - `ComplianceCookiesTest` (44): casos nomeados, a regra geral "item do compliance ⇔ card
    lista o problema" em 17 Set-Cookie, e "código ⇔ texto" nos mesmos 17. O `ScanResult`
    sai do `CookieSecurityService` real. Contra o código antigo, 16 de 27 vermelhos (os de
    código não compilam lá).
  - `cookieAvisos.test.ts` (8). `PesoLowTest` passou a montar o `CookieFinding` com
    `problems`.
  - Backend 859 → 903; Vitest 45 → 53; `tsc -b` limpo. Containers `backend` e `frontend`
    rebuildados.
- **Infra:** stub novo `env/wiremock/mappings/cookies-csrf.json`: o mesmo host
  `cookies-mock`, caminho `/so-csrf`, um `XSRF-TOKEN` sem flags. Não pede alias nem
  allowlist novos. Recarregar o WireMock:
  `curl -X POST http://localhost:8082/__admin/mappings/reset`.
- **Ao vivo (8081, qa-pro, pt-BR)** — tabela no bug:
  - raiz do `cookies-mock`: Art. 49 antes Secure e HttpOnly; depois **+ SameSite**;
  - `/so-csrf`: antes Secure e **HttpOnly**; depois Secure e **SameSite**;
  - o mesmo em `en`; nota e placar do compliance iguais;
  - tela (8090, qa-pro): o card do `XSRF-TOKEN` mostra só "Secure ausente" e "SameSite
    ausente/None"; o Art. 49 lista Secure e SameSite;
  - RestAssured do dono: 44 / 1 / 2. A falha é o PLAN-02 (Etapa L), já conhecida.
- **Contrato do SCAN-14 (dono escreve), detalhado na ficha 20:**
  - qa-pro, pt-BR: `GET /scan?url=http://cookies-mock:8080/`. Art. 49 (achar por
    `reference`) contém o item de SameSite e, como controle, o de HttpOnly;
  - `GET /scan?url=http://cookies-mock:8080/so-csrf`: Art. 49 contém Secure e SameSite e
    **não** contém HttpOnly; `cookieIssues` = só o `XSRF-TOKEN`, com `problems`
    `["COOKIE_SEM_SECURE","COOKIE_SEM_SAMESITE"]`.
- **O que o SCAN-14 prova:** que o relatório de conformidade diz o mesmo que o card do
  cookie, nos dois sentidos: o item que faltava aparece, e o que sobrava some.
- **Armadilhas do SCAN-14:**
  - qa-free e visitante recebem `compliance` null (trava de plano): use o qa-pro;
  - o texto sai no idioma pedido; o `problems`, não;
  - não afirme `overallScore` nem o `status` do Art. 49 (já era FAIL);
  - vermelho: o 8099 barra o `cookies-mock`; o vermelho de verdade pede o `backend` no
    commit `260074a`.
- **Laterais:**
  - **BUG-21** (P3, ✅ corrigido na Etapa N): o painel "O que está em jogo" chama o
    `XSRF-TOKEN` de cookie de sessão (`impactIndicators` `COOKIES`/`XSRF-TOKEN`). O
    `ImpactLabelService` tem a sua própria lista de nomes de sessão, por "contém"
    (`token`, `sid`, `user`...). O nível não muda: ele vem só dos campos de formulário.
  - Observação, sem bug: a linha de flags do card continua mostrando "✗ HttpOnly" em
    vermelho no token CSRF. É a flag crua, não o veredito.

### Etapa N — BUG-21 ✅ corrigido ("é cookie de sessão?" com duas respostas) — 2026-11-23
- **Decisões do dono (2026-11-23):**
  1. uma regra só, a do card (`CookieSecurityService.tipo`), no painel "O que está em
     jogo" e no card. O que a lista do painel descartava (CDN e analytics) também deixa
     de ser sessão no card;
  2. o painel continua vendo só cookie com problema (`cookieIssues`). Um cookie de sessão
     bem configurado não vira indício (fica como está).
- **Fix (Backend):**
  - `ImpactLabelService`: saíram a lista de fragmentos (`sess`, `sid`, `token`, `user`...)
    e a de CDN/analytics. O painel pergunta ao `CookieSecurityService.tipo`.
  - `CookieSecurityService.tipo`: cookie que começa com `_hj` (Hotjar) não é de sessão. Do
    resto da lista antiga do painel, nenhum nome tinha fragmento de sessão pela regra do
    card. Já saíam LOW, e o teste confere isso.
- **Regressão:**
  - `CookieDeSessaoTest` (73): uma tabela de 26 nomes com a resposta certa, conferida no
    card e no painel; os 19 nomes da lista antiga do painel; o alvo `/nomes` inteiro.
  - `CookieAtributosTest.hotjarNaoCusta`: só Hotjar sem flag, nota 100.
  - Contra o código antigo: 17 de 93 vermelhos. Backend 903 → 977. Container `backend`
    rebuildado.
- **Infra:** stub novo `env/wiremock/mappings/cookies-nomes.json`: o mesmo host
  `cookies-mock`, caminho `/nomes`, cinco cookies sem flag (`csrftoken`,
  `inside_banner`, `user_pref`, `_hjSession_1873402`, `laravel_session`). Recarregar o
  WireMock: `curl -X POST http://localhost:8082/__admin/mappings/reset`.
- **Ao vivo (8081, qa-pro)** (a tabela está no bug):
  - `/so-csrf`: o painel, antes `XSRF-TOKEN`, agora vazio;
  - `/nomes`: o painel, antes `csrftoken`, `inside_banner` e `user_pref` (sem o
    `laravel_session`), agora só `laravel_session`. `_hjSession_1873402` HIGH → LOW.
    "2 cookie(s)" → "1 cookie(s)". Nota igual (22);
  - raiz (SCAN-09) igual; o mesmo em `en`; visitante só com a origem;
  - na tela (8090, qa-pro), "Cookies de sessão: laravel_session";
  - RestAssured do dono: 44 / 1 / 2. A falha é o PLAN-02 (Etapa L), já conhecida.
- **Contrato do SCAN-15 (o dono escreve), detalhado na ficha 21:**
  - qa-pro, pt-BR: `GET /scan?url=http://cookies-mock:8080/nomes`;
  - `impactIndicators` com `source` `COOKIES`: um só, `laravel_session`;
  - `cookieIssues`: `laravel_session` HIGH, `_hjSession_1873402` LOW, os outros três LOW;
  - `INSECURE_COOKIES` com título começando por "1 cookie(s)".
- **O que o SCAN-15 prova:** que o painel e o card dão a mesma resposta para "é cookie de
  sessão?". O token CSRF, a preferência e o analytics saem do painel, e o cookie de
  sessão de verdade volta para ele.
- **Armadilhas do SCAN-15:**
  - qa-free e visitante recebem o indício sem `detail` (trava de plano): use o qa-pro;
  - filtre `impactIndicators` por `source`;
  - não afirme `overallScore` (22 antes e depois) nem o nível (`SHOWCASE`);
  - vermelho: o 8099 barra o `cookies-mock`. O vermelho de verdade pede o `backend` no
    commit `0ee80e9`.
- **Lateral:** **BUG-22** (P2, ✅ corrigido na Etapa O): `AMP_TOKEN` (Google Analytics)
  e `s_sess` (Adobe Analytics) saem HIGH no card e como sessão no painel, pelos
  fragmentos "token" e "sess". Os dois lados agora concordam, mas erram juntos.
  Reproduzido com um mapeamento temporário do WireMock, apagado pelo reset.

### Etapa O — BUG-22 ✅ corrigido (analytics com nome de sessão) — 2026-11-23
- **Decisão do dono (2026-11-23):** uma lista de nomes conhecidos de analytics, junto
  com o Hotjar. `AMP_TOKEN`, `s_sess` e o `_pin_unauth` (achado na leitura) deixam de ser
  sessão. O `intercom-session-*` (chat) continua sessão: segundo a Intercom, ele dá acesso
  às conversas do visitante. Cada nome novo entra com teste.
- **Fix (Backend):** `CookieSecurityService.tipo` ganhou `ANALYTICS_COM_NOME_DE_SESSAO`
  (nome inteiro, sem caixa). Vale para o card e o painel, que leem a mesma regra desde o
  BUG-21.
- **Regressão:**
  - `CookieDeSessaoTest`: a tabela passou de 26 para 30 nomes (os três, mais o
    `intercom-session-qa123` como sessão), mais o alvo `/analytics` inteiro, com controle;
  - `CookieAtributosTest.analyticsComNomeDeSessaoNaoCusta`: os três sem flag, nota 100;
  - contra o código antigo: 8 de 103 vermelhos (o Intercom é verde nos dois). Backend
    977 → 987. Container `backend` rebuildado.
- **Infra:** stub novo `env/wiremock/mappings/cookies-analytics.json`: o mesmo host
  `cookies-mock`, caminho `/analytics`, os três cookies sem flag. Recarregar o WireMock:
  `curl -X POST http://localhost:8082/__admin/mappings/reset`.
- **Ao vivo (8081, qa-pro):**
  - `/analytics`: antes, os três HIGH, "3 cookie(s)", nota 22 e painel com os três;
    depois, os três LOW, sem `INSECURE_COOKIES`, nota 32 e painel sem cookie;
  - o mesmo em `en`;
  - `/nomes` (SCAN-15) e raiz (SCAN-09) iguais;
  - na tela (8090, qa-pro), o painel sem "Cookies de sessão" e os três cards LOW;
  - RestAssured do dono: 44 / 1 / 2. A falha é o PLAN-02 (Etapa L), já conhecida.
- **Contrato do SCAN-16 (o dono escreve), detalhado na ficha 22:**
  - qa-pro, pt-BR: `GET /scan?url=http://cookies-mock:8080/analytics`;
  - controle: `cookieIssues` com os três, todos LOW;
  - sem `INSECURE_COOKIES` em `score.issues`; sem `COOKIES` em `impactIndicators`.
- **O que o SCAN-16 prova:** que cookie de analytics com "token", "sess" ou "auth" no nome
  não vira achado HIGH nem custa ponto.
- **Armadilhas do SCAN-16:**
  - afirmar ausência pede o controle antes: sem o stub carregado, tudo passa vazio;
  - use o qa-pro (trava de plano);
  - não afirme `overallScore` (22 → 32, mas depende dos outros módulos);
  - vermelho: o 8099 barra o `cookies-mock`. O vermelho de verdade pede o `backend` no
    commit `c4711db`.

---

## 13. Retomar aqui (2026-11-24)

Esta seção é o ponto de partida do próximo chat. Ela substitui as listas de pendência das
seções anteriores, que ficam como histórico. As regras da §0 continuam valendo.

### Estado

- **Bugs:** BUG-01 a BUG-22 corrigidos, cada um com regressão no Backend. **Aberto: BUG-23**
  (P2, 2026-11-26): `security.txt` e `robots.txt` lentos saem como "não encontrado", com o
  módulo `OK`. Achado na conferência do SCAN-02. **Decisão do dono (2026-11-26): corrigir
  depois da fila**, isto é, depois do caso 33 e antes do Playwright.
- **Repositórios:**
  - Backend `11fd610` (suíte 987, verde);
  - Frontend `a88d471`;
  - cyberaudit-qa: SCAN-03 em `3f2e806` + `e290dac`; bloco 2 em `2440e4a` + `2074fbb`;
    AUTH-05 (2026-11-26) nos commits seguintes.
- **Ambiente local recriado em 2026-11-26** (`up -d --no-build wiremock backend`, mais
  `up --no-build seeder`) para os aliases e a conta do bloco 2. O restart zerou cota e
  rate limit em memória.
- **Suíte RestAssured do dono (2026-11-24):** 52 testes, 1 falha, 1 pulado.
  - A falha é o PLAN-02, que espera o contrato antigo (ver "Correções pendentes" abaixo).
  - O pulado era o SCAN-03. Em 2026-11-25 ele virou três testes, sem `@Disabled`, e a classe
    está verde no 8081. Em 2026-11-26 entrou o AUTH-05 (um teste, verde). A suíte inteira
    não foi rodada de novo: a conta esperada é 55 / 1 / 0.

### A fila: 33 casos, na ordem da tabela "Ordem sugerida" de `docs/fichas-da-fila.md`

Os 22 primeiros, mais o bloco 2 (23 a 33), acrescentado em 2026-11-26 por decisão do dono:
o que a matriz pedia e as fichas não cobriam, no fim da fila e antes do Playwright.

| # | Caso | Estado |
|---|---|---|
| 1 | SSRF-03 hex | ✅ `1b1e469`. Vermelho visto no 8099 |
| 2 | SSRF-07 | ✅ `eb7a861` + `51cecef`. Vermelho visto no 8099 |
| 3 | SCAN-01 | ✅ `ScanMotorTest` (2026-11-24). Vermelho visto com `juice-shop:3999` (422) |
| 4 | SCAN-03 | ✅ `ScanAtivoJuiceShopTest`, três testes (2026-11-25). Vermelho visto: XSS apontado para o `eco-escapado-mock` e controle apontado para o `eco-mock` |
| 5 | AUTH-05 | ✅ `a6c67d0` + `80a0388` (2026-11-26). Vermelho visto no 8099 pelo dono: o controle dá 401 em vez de 200 |
| **6** | **AUTH-04** | **Passado ao dono em 2026-11-26**, com o contrato reconferido ao vivo (ficha 6). Pede a lib TOTP no pom e usa a conta qa-free. O vermelho é no 8099 (passo 4 → 200) |
| 7 a 22 | SCAN-09, SCAN-07, SCAN-05... | 🔲, na ordem da tabela |
| 23 a 28 | AUTH-05 (Postman), PAY-03, PAY-05, SCAN-04, SCORE-01, SCAN-02 | 🔲 Fichas prontas, contratos conferidos ao vivo em 2026-11-26 |
| 29 | AUTH-04 (Cypress) | 🔲 Ficha pronta: API conferida, tela lida no código (conferir na tela no dia) |
| 30 a 33 | PAY-14 (Cypress), UI-01 e UI-02 (Selenium), DOM-02 | 🔲 Começam por decisão do dono (fichas 30 a 33) |

**O que a revisão do SCAN-01 ensinou (vale para os próximos SCAN):**
- **Mande o esquema da ficha.** Com `https://juice-shop:3000/` o teste ficava verde mesmo assim:
  o backend tenta HTTPS, falha e cai para `http://` (`finalUrl` mostra). O verde passava pelo
  fallback, não pelo pedido `http://` que o caso prova.
- **Afirme que a coleção existe e não está vazia antes do laço.** Um `if (x != null)` em volta
  da asserção, ou um mapa `{}`, deixa o teste verde sem conferir nada. Sem o campo, uma
  `NullPointerException` vira *error* no relatório, não *failure*.

**O que a revisão do SCAN-03 ensinou (2026-11-25):**
- **`Actual: null` quase sempre é caminho ou campo errado.** O GPath devolve `null` para
  caminho que não existe, sem erro. Aconteceu com `issues.name` (o certo é
  `score.issues.id`) e com `reflectXssSuspected`.
- **Closure no GPath precisa de método na frente:** `score.issues.find { it.id == '…' }`.
  Sem o `find`, o Groovy procura um método `issues()`, e o teste dá *error* com "No
  signature of method".
- **O RestAssured para no primeiro `.body()` que falha.** Os erros de trás só aparecem
  depois de corrigir o da frente.
- **Asserção de ausência passa com caminho errado.** `not(hasItem(...))` sobre `null` dá
  verde: medido trocando `score.issues.id` por `score.issue.id` no controle. Afirmar antes
  que a lista não está vazia fecha isso (sugestão dada ao dono, não bloqueou).

**O que a revisão do AUTH-05 ensinou (2026-11-26):**
- **`equalTo` compara a string inteira.** A mensagem do 401 é `Autenticação necessária.`,
  com ponto. Copie o texto da resposta real, não da memória.
- **Path param precisa do marcador no caminho:** `.delete("/api-keys/{id}", id)`. Sem o
  `{id}`, o RestAssured recusa o valor que sobrou ("Invalid number of path parameters") e
  o teste dá *error*: a requisição nem sai.
- **`"Bearer " + token`, com o espaço.** Sem ele, o backend não lê o token, e a chamada
  vira anônima. O sintoma é um 401 onde o teste esperava outro código (aqui, 409).
- **Teste que cria estado no servidor limpa no `@AfterEach`.** O vermelho do 8099 para no
  passo 2, antes da revogação. Sem a limpeza, cada vermelho deixaria uma chave ativa no
  qa-pro. Zerar o id depois do `DELETE` do próprio teste evita revogar duas vezes.

**Depois da fila:** Fase 7, Playwright. O plano está em `docs/handoff-playwright.md`, na pasta
`playwright/`, e começa pela Etapa P0 (decisões).

### Correções pendentes nos testes já escritos (do dono)

Os detalhes estão no fim de `docs/fichas-da-fila.md`.
- **UI-02** (⚠️, verde falso): o campo abre com "github.com" e o `.type()` acrescenta ao
  texto. Falta `.clear()` e afirmar o host exato.
- **PLAN-02** (⚠️): toma 429 na 6ª chamada, pelo rate limit do BUG-20. Falta tratar o 429
  como "espere e repita".
- **Sugestão, sem pressa:** o gerador de token está copiado no `SsrfGuardTest` (com
  `userId`) e no `SsrfVerifyCheckTest` (sem). As duas cópias já divergiram. Levar o
  `tokenPara(email, userId)` para o `TestConfig`.

### Como o chat trabalha nesta fase (o ritmo que funcionou)

1. **O Claude passa o caso:**
   - o que ele prova e por quê;
   - o que fazer;
   - as armadilhas;
   - como ver o vermelho.

   Antes, ele reconfere o contrato ao vivo no mesmo dia.
2. **O dono escreve** e diz "verifica".
3. **O Claude revisa sem tocar nos arquivos do dono.** Copia o `api-restassured` para o
   scratchpad e roda a classe:
   - contra o 8081, para o verde;
   - numa variação da cópia, contra o 8099 ou um alvo quebrado, para o vermelho.

   Depois responde em duas partes: "corrija antes do commit" e "sugestões". Ele explica o
   porquê de cada uma, porque o dono é iniciante em Java.
4. **O Claude marca o caso como ✅ na matriz** e manda os comandos de commit. O dono commita.
5. **O Claude passa o próximo caso.**

### Subir o ambiente depois de reiniciar o PC

Armadilha paga em 2026-11-24: todos os containers saem com "Exited (255)". Com o Docker
Desktop aberto, rode a partir de `cyberaudit-qa/`:

```
docker compose -f env/docker-compose.test.yml up -d --no-build postgres coredns wiremock backend backend-noallowlist frontend juice-shop
curl http://localhost:8081/actuator/health
curl http://localhost:8099/actuator/health
```

- O `seeder` fica de fora: ele só roda para renovar os seeds.
- Os stubs do WireMock que estão em arquivo carregam sozinhos quando o container sobe.
- Depois de rodar a suíte inteira, reinicie o backend (`docker restart env-backend-1`).
  Isso devolve a cota do qa-free, que fica em memória.
- **Máquina nova (sem a imagem antiga do 8099):** o 8099 é o Backend `4ed5f34`, o último commit
  antes do fix do BUG-01. Recrie sem mexer no checkout, a partir de `cyberaudit-qa/`:
  `git -C ../Backend archive 4ed5f34 | docker build -t env-backend-noallowlist -`. Depois suba
  sempre com `--no-build`: um `up --build` sem nomear o serviço refaz o 8099 do HEAD.

### Armadilhas novas (2026-11-24)

- **8099 e endereço de fora:** a imagem antiga lê `0177.0.0.1` como 177.0.0.1, que é
  público. Nunca rode o octal no 8099 (está na ficha 1).
- **8099 e metadata de nuvem:** o vermelho do SSRF-07 no 8099 usa só `127.0.0.1:8081` e
  `127.0.0.1#`. O `169.254.169.254:80` faria o servidor antigo tentar conectar de verdade.
- **Quando refazer a imagem do 8099:** ela continua antiga até o dono ver os vermelhos do
  AUTH-05 e do AUTH-04 (fichas, seção 0). Só depois disso ela é refeita. O do AUTH-05 foi
  visto em 2026-11-26; falta o do AUTH-04.
- **Mensagem de commit:** sempre `tipo(escopo): descricao`. O `51cecef` ("ajustes de escrita
  SSRF-07") saiu fora do padrão.

### Primeira mensagem sugerida para o próximo chat

> Leia o `HANDOFF.md` do `cyberaudit-qa`: as regras da §0 e a §13 (retomar aqui). Vamos
> seguir a fila das fichas no ritmo da §13. O próximo é o AUTH-04 (ficha 6): reconfira o
> contrato ao vivo e me passe o resumo. Lembre: o código de teste é meu; você faz infra,
> revisão numa cópia e docs.
