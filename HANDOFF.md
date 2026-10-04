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
