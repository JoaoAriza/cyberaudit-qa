# HANDOFF — cyberaudit-qa (para colar no próximo chat)

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
    qa-free não cria chave (FREE → 402).

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
    seed e `OWNER` não tem limite por minuto → use o token dele.
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
  - O resultado fica em cache (Caffeine, memória) por URL + modo: rodar de novo devolve o
    mesmo resultado; rebuild do container zera.
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
- **AUTH-05** (BUG-01) — contrato na Etapa 1.
- **SSRF-03 hex** (BUG-02) — tirar o `@Disabled`; opcional `0177.0.0.1`. Etapa 2.
- **SSRF-07** (BUG-05) — contrato e armadilhas na Etapa 3.
- **SCAN-01** (BUG-03) — contrato na Etapa 4.
- **AUTH-04** (BUG-04) — contrato e armadilhas na Etapa 6 (lib TOTP no pom, conta qa-free).
- **SCAN-03** — produto pronto (BUG-03 + BUG-06). SQLi: `dbErrorLeakageSuspected:true` no
  `/rest/products/search?q=apple` (Etapa 5). XSS: precisa de outro alvo; o stub do
  WireMock que ecoa o parâmetro é infra, o Claude monta quando o dono chegar nesse caso.
- **SCAN-05** (BUG-08) — contrato e armadilhas na §11, Etapa A.
- **SCAN-06** (BUG-09) — desbloqueado pelo DNS falso; contrato na §11, Etapa C.
- **SCAN-07** (BUG-10) — contrato e armadilhas na §11, Etapa C.
- **SCAN-08** (BUG-11) — contrato e armadilhas na §11, Etapa D.
- **SCAN-09** (BUG-12) — contrato e armadilhas na §11, Etapa E.

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
  (+ BUG-13 lateral, aberto).
- **F** — Pesos por severidade: LOW custa no máximo 2 (versão exposta, robots.txt,
  security.txt, Referrer/Permissions ausentes, sem WAF); robots.txt sem desconto quando
  tudo responde 401/403. Muda a nota de todo scan.
- **G** — Cosméticos: "null" no bônus de WAF; porta 80 INFO quando redireciona; hosts
  relacionados no idioma do laudo (pool próprio sem `IdiomaThreads`).

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
  - Alvo é o host `php-banner-mock`, não `wiremock`: a chave do cache de scan é o host, e
    `wiremock` colide com o AUTHZ-01. O stub só responde para esse Host, só na raiz.
  - O fingerprint faz **HEAD**, não GET — por isso o stub é `ANY`. Com GET só, o
    `detectedVersions` volta vazio e o CVE nem é consultado (a asserção negativa passaria
    sem testar nada).
  - Depende do NVD real (internet do container, sem API key: 5 req/30 s). Se o NVD falhar
    ou der 429, `cveFindings` volta `[]` e a asserção "sem CVE-2013-2220" passa à toa. Use
    a presença do `CVE-2007-3205` como prova de que a consulta aconteceu; se um dia ele
    sumir, é o NVD que mudou, não o produto. Não fixe a lista inteira nem o score exato.
  - `score` é um objeto (`score.score`, `score.riskLevel`, `score.notes`, `score.issues`);
    as issues de CVE têm id `CVE_` + o CVE com `_` (`CVE_CVE_2013_2220`).
  - O cache de scan guarda o resultado do host: para ver o "antes" de novo, reinicie o
    `backend` (cache em memória).

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
  commit.
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
