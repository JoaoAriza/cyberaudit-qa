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
  - `GET /scan/verify-check?host=<h>` (sem token funciona; com token de qa-pro foge do
    rate-limit de visitante, que dá 429 depois de poucas chamadas).
  - `127.0.0.1:8081`, `localhost:8081`, `169.254.169.254:80`, `[::1]:443`, `127.0.0.1/`,
    `127.0.0.1#`, `127.0.0.1 ` → **403** `{"error":"DOMAIN_BLOCKED"}`.
  - Controle: `example.com:443` → **200** `{"verified":false,...}` (porta em host público
    vale). Atenção: `example.com` depende de DNS externo (mesma ressalva do `nip.io` do
    SSRF-04 na CI); `juice-shop:3000` → 200 é a alternativa local.
  - Armadilha: o host vai em query string → caracteres como `#`, espaço e `[ ]` precisam
    de URL-encoding (`queryParam` do RestAssured já codifica; na mão, `%23`, `%20`).

### Próxima etapa — BUG-03 (scan não conecta em HTTP puro em porta não-padrão)
Destrava SCAN-01, reativa SCAN-03 (`@Disabled`) e libera SCAN-02/04 e SCORE-01. Depois, BUG-04.
