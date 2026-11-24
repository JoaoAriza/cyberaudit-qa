# HANDOFF — Playwright no cyberaudit-qa (Fase 7, para colar no próximo chat)

Criado em 2026-11-23. Complementa o `HANDOFF.md` da raiz do `cyberaudit-qa` (estado até a
§12, Etapa N) e **não o substitui**: regras, ambiente, contratos e armadilhas de lá continuam
valendo. Este arquivo cobre só a entrada do Playwright no projeto. O estado abaixo foi
conferido no repositório em 2026-11-23.

> **Na fila:** a Fase 7 começa depois dos casos das fichas (`docs/fichas-da-fila.md`, HANDOFF
> §10). Decisões já tomadas pelo dono (2026-11-24): a pasta é `playwright/` e o Playwright é
> trilha de aprendizado real, como o Cypress, não só vitrine.

---

## 0. Por que esta fase existe

1. **Portfólio:** Playwright ao lado do Cypress nos mesmos casos. O mercado pede os dois, e o
   comparativo medido (§5, Etapa P8) vale mais do que "sei os dois".
2. **Trilha de aprendizado, não vitrine** (dono, 2026-11-24): o Playwright entra como entrou o
   Cypress, com pasta própria (`playwright/`) e o mesmo ritmo: o dono escreve cada caso, o
   Claude revisa numa cópia. A jornada DEMO-01 (§5, Etapa P4) é o cenário rico para mostrar o
   que ele faz.
3. **Cobrir o que o Cypress não cobre bem:** download com leitura do arquivo, diálogos,
   interceptação e espera de resposta no mesmo teste, iframe cross-origin (PAY-12, exploratório)
   e trace passo a passo.

---

## 1. Regras (herdadas do HANDOFF.md §0) e a divisão de trabalho no Playwright

**As 5 regras do escopo continuam valendo:**
1. Nunca testar contra produção (`cyberauditapp.com`).
2. Varredura ativa só contra alvos locais.
3. Autoria real.
4. Sem segredos no repositório: só valores de teste, vindos de variável de ambiente com padrão local.
5. O CV acompanha o código.

**O Claude NUNCA escreve código de teste.** Vale no Playwright exatamente como no Cypress.

| O Claude pode fazer | Só o dono escreve |
|---|---|
| `package.json`, `playwright.config.ts`, `tsconfig.json`, `.gitignore`, README | Todo arquivo com `test(...)`, `expect(...)` ou interação com página |
| Helpers Node **puros**, sem `test`/`expect`: TOTP, forja de JWT, reset de conta, assinatura de webhook, leitura de env | Specs, page objects e fixtures que interagem com a página (`test.extend` com `page`) |
| Global setup que gera os arquivos de sessão (`storageState`) a partir de token forjado | O setup de login **pela tela** (UI-01) |
| Seed de conta nova, stubs do WireMock, workflow da CI, matriz e docs | As asserções, inclusive textos esperados |
| Revisar e rodar o código do dono **numa cópia**, sem tocar nos arquivos dele | — |

**Na dúvida, é do dono.**

**Convenções que continuam:**
- Ao passar um caso, explicar **o QUE ele prova e POR QUE** (o risco ou contrato coberto), não só os passos.
- **Nunca chutar texto nem campo:** conferir ao vivo (tela ou JSON) antes de o dono escrever a asserção.
- O Claude **não** roda `git commit`/`git push`. Manda o comando pronto, só `tipo(escopo): descricao`, sem acento, sem corpo e sem atribuição.

---

## 2. Estado atual que importa para o Playwright (conferido em 2026-11-23)

**Frontend:** React 19 + Vite + TypeScript.
- **Não há `data-testid`** (conferido). Os seletores precisam ser por papel, rótulo, placeholder e texto, como no Cypress.
- O idioma segue o `navigator.language`, mas o app lê **`localStorage['cyberaudit.lang']` antes**. A CI roda em en-US: sem fixar pt-BR, os textos em português não casam (armadilha paga na Fase 6).
- A sessão fica em `localStorage['cyberaudit.token']`.

**Ambiente** (`docker compose -f env/docker-compose.test.yml up -d`, a partir de `cyberaudit-qa/`):
- Portas: backend `8081`, frontend `8090`, backend sem allowlist `8099`, Juice Shop `3001`, WireMock `8082`, Postgres `5433`.
- Health: `GET http://localhost:8081/actuator/health`.
- O daemon do Docker Desktop precisa estar aberto.

**Contas semeadas** (`env/seed.sql`, senha e segredo só de teste):
- Base, com 2FA TOTP ligado: `qa-free`, `qa-pro` e `qa-enterprise` (`@cyberaudit.test`).
- Pagamento: `qa-pay-07`, `09`, `10`, `11`, `15`, `-card`, `-nocpf`, `-pix` e `-webhook`.
- O `qa-pay-11` é a conta FREE dedicada do PAY-11 do Cypress.
- **IDs, e-mails e segredos:** espelhar do `e2e-cypress/cypress.config.js`, que espelha o `config.properties` do RestAssured. Não duplicar valores à mão em outro lugar sem necessidade.

**Cypress 13.15** (`e2e-cypress/`):
- Specs: `smoke`, `ui-01-login`, `ui-02-scan-historico`, `ui-03-agendamento`, `ui-11-pix` e `plan-01-pdf`.
- Tasks: `totp`, `forgeToken`, `resetFree` e `signMpWebhook`.
- Scripts `demo` e `demo:falha` gravam vídeo em `cypress/evidencias-demo/`.
- **O UI-02 está ⚠️ verde falso.** O campo abre com "github.com" e o `.type()` acrescenta no fim. Com o BUG-16 corrigido, o host torto leva 422. A correção do spec é do dono (`docs/fichas-da-fila.md`, fim).

**Contratos já conferidos que o Playwright vai reusar:**

- **JWT de sessão forjado (HS256 com o `jwtSecret` do ambiente):**
  - `sub` = **e-mail** (não o userId);
  - `twoFactorPending:false`, `role:'OWNER'`, `userId`, `exp` de 1 h.
  - É a mesma lógica do `forgeToken` do Cypress.
- **Login real:**
  - `POST /auth/login` devolve um token provisório (`requires2fa:true`).
  - `POST /auth/2fa/verify` com o código TOTP devolve a sessão.
  - **Rate limit:** 5 falhas por e-mail e 20 por IP levam a 429 por 15 min.
  - **TOTP com proteção de replay:** o mesmo código não vale duas vezes (AUTH-04).
- **Webhook do MP:**
  - Manifesto `id:<paymentId>;request-id:<reqId>;ts:<ts>;` assinado com HMAC-SHA256 e o `mpWebhookSecret`.
  - Header `x-signature: ts=<ts>,v1=<hex-minúsculo>` (a vírgula importa).
  - Corpo `{type:'payment', data:{id}}`.
  - Vai para a **8081**, nunca para a 8090.
- **Plano da conta:** está em `GET /auth/me` → `account.plan`, **não** em `/billing/subscription`.
- **Reset de conta:** `docker exec env-postgres-1 psql ...` volta a conta para FREE e apaga as subscriptions. O accountId é validado como UUID.

**CI** (`.github/workflows/ci.yml`):
- Um job `ubuntu-latest` com Node 20 e JDK 21.
- Checkout dos 3 repositórios lado a lado e `COMPOSE_PROJECT_NAME=env`.
- Roda RestAssured, Newman e Cypress, todos com `continue-on-error`.
- O Allure unificado é publicado no GitHub Pages.

---

## 3. Decisões do dono antes de começar (Etapa P0)

| # | Decisão | Opções | Recomendação | Por quê |
|---|---|---|---|---|
| 1 | Linguagem | TypeScript ou JavaScript | **TypeScript** (alta) | Padrão do Playwright, o Frontend é TS, e é o que as vagas pedem |
| 2 | Onde fica | ✅ **Decidido (2026-11-24): `playwright/`**, pasta irmã do `e2e-cypress/`; o Cypress fica | — | O comparativo só existe com os dois |
| 3 | Navegadores | Só Chromium ou Chromium + Firefox | **Chromium na CI; Firefox opcional, local** (média-alta) | O UI-01 em dois navegadores dobra os logins (rate limit) e reusa o TOTP (replay) |
| 4 | Conta do fluxo de pagamento | Reusar `qa-pay-11` ou criar `qa-pw-pay` | **Conta nova `qa-pw-pay`** (alta) | Cypress e Playwright na mesma conta brigam pelo estado, pelo mesmo motivo que separou o `qa-pay-11` do RestAssured |
| 5 | Reset de conta | `docker exec psql` (igual ao Cypress) ou driver `pg` na 5433 | **`docker exec`** (média) | Mesma infra e sem dependência nova. O `pg` só compensa se o nome do container virar problema |
| 6 | Sessão nos testes que não são de login | `storageState` de token forjado ou login real num projeto de setup | **Token forjado** (alta) | Rápido, não gasta rate limit nem TOTP. Login pela tela só no UI-01 |
| 7 | CI | Passo no mesmo job, depois do Cypress, ou job separado | **Mesmo job** (alta) | O ambiente já está de pé, e rodar em sequência evita disputa de contas |
| 8 | Relatório | `allure-playwright` no Allure unificado, mais o HTML nativo | **Os dois** (alta) | O Allure mantém a página da CI; o HTML nativo traz o trace |
| 9 | PAY-12 (cartão, iframe do MP) | Manter manual (decisão de 2026-10-26) ou exploratório no Playwright | **Exploratório só com a guarda da Etapa P6** (média) | O Playwright entra em iframe cross-origin, mas o build usa chave de **produção** do MP |
| 10 | Dependências extras | `pdf-parse` (ler o PDF), `@axe-core/playwright` (acessibilidade) | Decidir na etapa que usar | Cada dependência nova é decisão do dono |

---

## 4. Mapa Cypress → Playwright (conceitos; o código é do dono)

| No Cypress | No Playwright | Atenção |
|---|---|---|
| `cy.visit` + `baseUrl` | `page.goto` + `baseURL` | — |
| `cy.get` / `cy.contains` | `getByRole`, `getByLabel`, `getByPlaceholder`, `getByText`, `locator` | **Modo estrito:** agir num localizador que casa com mais de um elemento dá erro. Os dois "Entrar" (aba e submit) exigem escopo, por exemplo dentro do `form` |
| `.should(...)` | `expect(locator).toBeVisible()`, `toHaveText()`... | Tenta de novo por 5 s por padrão; `timeout` por asserção para o scan (10 a 25 s) |
| `cy.task` (ponte para o Node) | Importar o helper direto | O teste já roda em Node: não existe ponte |
| `cy.request` | Fixture `request` ou contexto de API com `baseURL` da 8081 | O `request` herda o `baseURL` **8090**. Chamada ao backend precisa da 8081 (a mesma armadilha do PAY-11) |
| `cy.intercept` para espiar | `page.waitForResponse` / `page.on('response')` | Começar a esperar **antes** do clique que dispara a requisição |
| `cy.intercept` para simular | `page.route` / `context.route` | — |
| `onBeforeLoad` + localStorage | `storageState` por origem, ou `page.addInitScript` | A origem precisa ser exatamente `http://localhost:8090` |
| `window:before:load` com o idioma | `locale: 'pt-BR'` no config + `cyberaudit.lang` no `storageState` | Os dois juntos: o `locale` muda o `navigator.language`; a chave é a que o app lê primeiro |
| `window.confirm` aceito sozinho | **Diálogo recusado por padrão** | Sem um handler que aceite o diálogo, o "Remover" do UI-03 cancela em silêncio |
| `.type()` acrescenta ao valor | `fill()` **substitui** o valor | O bug do UI-02 (campo com "github.com") some. Afirmar o host no histórico mesmo assim |
| `Cypress.env` | `process.env` com padrão de teste | Regra 4 |
| `video` / `screenshot` | `video`, `screenshot` e **`trace`** no config, por projeto | O trace (DOM, rede e console de cada passo) é o diferencial na demo |
| `cy.wait(ms)` | Proibido: asserção com timeout, `expect.poll` ou `toPass` | — |
| Ler arquivo baixado (exige plugin no Cypress) | `page.waitForEvent('download')` + caminho do arquivo | Começar a esperar o download antes do clique |
| `@shelex/cypress-allure-plugin` | `allure-playwright` | — |
| `cypress open` | `npx playwright test --ui` e `npx playwright codegen` | O código do `codegen` serve para **descobrir seletores**, não entra no repositório como está (Regra 3) |

---

## 5. Etapas (uma por vez, no ritmo de sempre: etapa, conferência, dono escreve, Claude revisa)

### P0 — Decisões (§3). Nada começa sem elas.

### P1 — Scaffold (Claude)

| Arquivo | O que contém |
|---|---|
| `playwright/package.json` | `@playwright/test` (versão estável atual: conferir com `npm view @playwright/test version`), `otplib`, `allure-playwright`. Scripts: `test`, `test:smoke` (`--grep @smoke`), `test:ui`, `report`, `demo` (`--project=demo`), `codegen` |
| `playwright.config.ts` | `baseURL` de env com padrão `http://localhost:8090`; `apiURL` de env com padrão `8081`. Em `use`: `locale: 'pt-BR'`, `timezoneId: 'America/Sao_Paulo'`, `trace: 'on-first-retry'`, `video: 'retain-on-failure'`, `screenshot: 'only-on-failure'`. `retries` 1 na CI e 0 local; `fullyParallel: false`. Reporters `list`, `html` e `allure-playwright`. Projetos `chromium`, `firefox` (opcional) e `demo` (Chromium com vídeo e trace sempre ligados) |
| `support/env.ts` | Lê e-mails, IDs, segredos e URLs de `process.env`, com os padrões de teste do `cypress.config.js` |
| `support/auth.ts` | `totp()` e `forgeToken({email, userId})`: porte das tasks do Cypress |
| `support/db.ts` | `resetFree(accountId)`, com a mesma validação de UUID |
| `support/mp.ts` | `signMpWebhook({paymentId})`, que devolve `{headers, body}` |
| `global-setup.ts` | Espera o health da 8081. Gera `.auth/<conta>.json` (`storageState` com `cyberaudit.token` forjado e `cyberaudit.lang = pt-BR`) para `qa-free`, `qa-pro`, `qa-enterprise` e `qa-pw-pay`. Roda a cada execução, porque o token vale 1 h |
| `tsconfig.json`, `.gitignore`, `README.md` | O `.gitignore` cobre `test-results/`, `playwright-report/`, `.auth/` e `allure-results/` |
| `env/seed.sql` | Conta `qa-pw-pay@cyberaudit.test` (FREE, com 2FA, IDs próprios), no padrão do `qa-pay-11` |

**Pronto quando:**
- `npx playwright test --list` roda sem erro;
- o global setup gera os 4 arquivos `.auth`;
- `npx playwright show-report` abre.

### P2 — SMOKE-PW (dono)

**Caso:** com o `storageState` do `qa-pro`, o app abre logado e mostra a navegação que só aparece logado ("Histórico"). Tag `@smoke`.

**Prova:** que a sessão forjada e o idioma fixo funcionam. É a base de todos os outros casos. Um vermelho aqui é infraestrutura, não produto.

### P3 — Portar os casos do Cypress (dono), nesta ordem

| Ordem | Caso | O que prova | Cuidados no Playwright |
|---|---|---|---|
| 1 | **PLAN-01** (P0) | Recurso pago não sai de graça na tela: no FREE, botão "PDF" desabilitado com o title "Exportar PDF requer plano PRO ou superior"; no PRO, habilitado | Afirmar o atributo `title` e o estado desabilitado; controle positivo no PRO |
| 2 | **UI-01** | O login real com 2FA chega ao dashboard | Só no projeto Chromium. Nunca senha errada no `qa-pro` (bloqueio). Não reexecutar em sequência no mesmo intervalo de 30 s, por causa do replay do TOTP |
| 3 | **UI-03** | Agendamento criado, pausado e removido persiste depois do reload | **Aceitar o diálogo** do Remover. Host único por execução. Depois do reload o grupo vem recolhido: expandir antes de remover |
| 4 | **UI-02** | Scan iniciado pela tela aparece com nota e no histórico, **com o host digitado** | Afirmar o host exato no histórico (o motivo do verde falso no Cypress). Timeout de 60 s na nota. Com o BUG-16 corrigido, host inexistente leva 422 e não mostra nota |
| 5 | **PAY-11** | Pix pela tela: QR, copia-e-cola e polling detectando a confirmação sem reload | Conta `qa-pw-pay` com `resetFree` antes de cada execução. Webhook na 8081. Sinal de sucesso: o card Pro vira "Cancelar assinatura" (ver §7, item 6) |

Na matriz, cada caso portado ganha "Playwright" na coluna de ferramenta.

### P4 — DEMO-01: jornada completa (dono)

Um arquivo com o caminho feliz em `test.step()`: cada passo aparece com nome no relatório e no
trace, que é o que se mostra na demo. Os casos negativos ficam num arquivo à parte, para a
jornada não misturar sucesso com falha. **Todos os textos abaixo precisam ser conferidos ao vivo
antes de virar asserção.**

| Passo | Recurso do Playwright | O que prova |
|---|---|---|
| 1. `resetFree(qa-pw-pay)` antes do teste | Hook + helper Node | Rodar de novo dá o mesmo resultado |
| 2. Sessão pelo `storageState` | Sessão sem tela de login | Sessão reaproveitada sem custo |
| 3. `GET /auth/me` mostra `account.plan = FREE` | `request` na 8081 | A pré-condição é afirmada, não suposta |
| 4. Planos → "Pessoal Pro" → aba Pix → CPF → Gerar Pix; **capturar a resposta do checkout** e tirar o `paymentId` | `waitForResponse` | Teste híbrido: tela e API no mesmo fluxo (conferir o contrato do checkout ao vivo) |
| 5. QR visível e copia-e-cola não vazio; botão copiar mais leitura da área de transferência | Permissão de clipboard no contexto (só Chromium) | O que o usuário copia é o que a API gerou (conferir o formato real do stub) |
| 6. Webhook assinado enviado à 8081 responde 200 | `request` + `signMpWebhook` | Simula o retorno assíncrono do gateway |
| 7. Sem reload: "Aguardando confirmação..." some e o card vira "Cancelar assinatura" | Asserção que tenta de novo sozinha, sem pausa fixa | O polling da tela funciona |
| 8. `GET /auth/me` mostra `account.plan = PRO` | `request` | Tela e banco concordam |
| 9. Scan passivo de alvo permitido (wiremock) → nota → no histórico, PDF habilitado → **download** → arquivo com mais de 0 bytes e com o host no texto | `waitForEvent('download')` (+ `pdf-parse`, se o dono aprovar) | O recurso liberado pelo pagamento entrega o arquivo certo |
| 10. Agendamento com host único → Remover (diálogo aceito) → some depois do reload | Handler de diálogo | Persistência e confirmação nativa |
| 11. Guarda hermética: durante a jornada, nenhuma requisição sai para host fora de `localhost` | `page.on('request')` coletando hosts | Regras 1 e 2 provadas pelo próprio teste |

**Arquivo de negativos:**
- Checkout respondendo **500**, **502** e **400** via `page.route`, um teste por status gerado por um laço (parametrização).
- Em cada um, a tela mostra o erro sem quebrar e `/auth/me` continua FREE.
- Prova que a falha do gateway não deixa a conta num estado intermediário.

**Evidência:** projeto `demo` com vídeo e trace sempre ligados; tags `@demo` e `@regressao`.

**Pronto quando:**
- fica verde 3 vezes seguidas no local e na CI;
- o projeto `demo` gera vídeo e trace;
- o trace abre com os 11 passos nomeados.

### P5 — Extras opcionais (escolha do dono)

- **Acessibilidade:** `@axe-core/playwright` na tela de login, afirmando nenhuma violação crítica. É barato e diferencia o portfólio.
- **UI-04 (🔲 na matriz):** card DMARC com `p=quarantine; pct=50`. Pode nascer direto no Playwright. O contrato está no HANDOFF §11, Etapa G.
- **Comparação visual (`toHaveScreenshot`):** só local. As fontes do Linux da CI diferem das do Windows, então seria preciso uma referência por sistema. **Não recomendado na CI** (alta).
- **Teste de API puro no Playwright** (por exemplo, um AUTH-03 equivalente): só para mostrar que ele também faz API. Já está coberto no RestAssured, então é opcional.

### P6 — PAY-12 exploratório (só se decidido no P0, item 9)

O objetivo é avaliar se o Playwright cobre o cartão pela tela, que o Cypress não alcança porque
os campos são iframes cross-origin de `secure-fields.mercadopago.com`.

- **Como:** localizar os campos dentro do iframe (`frameLocator`) e simular a tokenização do MP com `context.route`, devolvendo um token falso aceito pelo stub do WireMock (`card-recusado` é o token recusado).
- **Guarda obrigatória:** o teste **falha** se qualquer requisição para `*.mercadopago.com` que não seja o carregamento do próprio iframe sair sem simulação. Sem essa guarda, a etapa não avança.
- **Ressalvas:**
  - o build do 8090 usa chave de **produção** (`APP_USR-…`);
  - o iframe carrega do MP real, então precisa de internet;
  - a interceptação de requisições feitas de dentro de um iframe cross-origin precisa ser **comprovada** na primeira execução (confiança média).
- Se a guarda não fechar, o PAY-12 continua manual (decisão de 2026-10-26) e a etapa vira um registro do motivo na matriz.

### P7 — CI (Claude)

Passo novo no `ci.yml`, depois do Cypress, no mesmo job:
1. `npm ci`;
2. `npx playwright install --with-deps chromium`;
3. `npx playwright test`, com `continue-on-error`;
4. upload de `playwright-report/` e `test-results/` como artifact, com `if: always()`;
5. cópia de `allure-results/` para o Allure unificado.

Conferir que o passo de status final da CI inclui o resultado do Playwright.

### P8 — Docs (Claude)

- **Matriz:** Playwright na coluna de ferramenta dos casos portados, mais DEMO-01 e SMOKE-PW.
- **HANDOFF.md:** seção nova.
- **README:** como rodar.
- **`docs/cypress-vs-playwright.md`:** comparativo **medido** nos mesmos casos:
  - tempo de execução local e na CI;
  - linhas por caso;
  - intermitência em 10 execuções;
  - o que um cobre e o outro não.

  É o material da demo e do post no LinkedIn.

---

## 6. Contexto da demo

Fica fora deste repositório, que é público.

---

## 7. Armadilhas antecipadas do Playwright (ainda não pagas; confirmar na primeira vez)

1. **Diálogos são recusados por padrão** (alta): sem handler, o Remover do UI-03 não remove nada e o teste pode passar pelo motivo errado. Afirmar que o item sumiu.
2. **Modo estrito** (alta): agir num localizador que casa com 2 elementos é erro. Os dois "Entrar" e "Scanner" × "◈ Scan" pedem escopo ou nome exato.
3. **O `request` herda o `baseURL` 8090** (alta): webhook e `/auth/me` vão para a 8081.
4. **`storageState` é por origem** (alta): `http://localhost:8090` exato. Com `127.0.0.1` a sessão não carrega.
5. **Token forjado expira em 1 h** (alta): o global setup gera de novo a cada execução. Não commitar `.auth/`.
6. **`toBeVisible` não detecta elemento coberto por overlay** (média-alta): o badge "PRO" atrás do PlansModal pode passar como visível no Playwright, ao contrário do Cypress. O sinal de sucesso continua sendo o card "Cancelar assinatura" (HANDOFF §8).
7. **Paralelismo** (alta): arquivos rodam em paralelo por padrão. Testes que mudam a mesma conta não podem rodar juntos: `fullyParallel: false` mais conta dedicada por fluxo que muda estado.
8. **Replay do TOTP** (média-alta): o mesmo código não vale duas vezes. O UI-01 em dois navegadores ou com retry imediato pode levar 401 no segundo. Gerar o código na hora e, no retry, esperar o próximo intervalo.
9. **Rate limit de login** (alta): 20 falhas por IP em 15 min. Retries mais Firefox mais Cypress na mesma máquina somam.
10. **Download** (alta): começar a esperar o evento antes do clique. O arquivo fica num caminho temporário até ser salvo.
11. **Cache de scan** (média): é por host, modo, idioma, caminho, query e conta, por 2 min. Repetir o mesmo scan logo em seguida pode vir do cache (BUG-17 corrigido).
12. **Idioma** (alta): `locale: 'pt-BR'` sozinho resolve o `navigator.language`, mas manter `cyberaudit.lang` no `storageState`, porque o app lê essa chave primeiro.
13. **Node** (média): a CI usa Node 20. Conferir o mínimo exigido pela versão do Playwright instalada.

---

## 8. Como rodar (quando o scaffold existir)

```
# ambiente (a partir de cyberaudit-qa/)
docker compose -f env/docker-compose.test.yml up -d
curl http://localhost:8081/actuator/health

# Playwright
cd playwright
npm ci
npx playwright install chromium
npx playwright test                    # tudo
npx playwright test --grep @smoke      # só o smoke
npx playwright test --project=demo     # jornada com vídeo e trace
npx playwright test --ui               # modo interativo
npx playwright show-report             # relatório HTML
npx playwright show-trace <caminho>/trace.zip
npx playwright codegen http://localhost:8090   # só para descobrir seletores
```

---

## 9. Primeira mensagem sugerida para o próximo chat

> Leia o `HANDOFF.md` (regras da §0 e estado até a §12) e este `handoff-playwright.md`. Vamos
> começar a Fase 7 pela Etapa P0: me traga as decisões da §3 com a sua recomendação, confira
> ao vivo o que estiver marcado para conferir e, depois que eu decidir, monte o scaffold da P1.
> Lembre: código de teste é meu; você faz infra, revisão e docs.
