# e2e-cypress — Fase 4 (Cypress)

Testes E2E de interface do CyberAudit, rodando contra o ambiente local
(`../env/docker-compose.test.yml`). O Cypress roda **no host** e aponta pro
Frontend publicado em `http://localhost:8090` (API em `http://localhost:8081`).

## Pré-requisitos

- Ambiente no ar: `docker compose -f ../env/docker-compose.test.yml up -d` (precisa de
  `backend` **e** `frontend` saudáveis).
- `npm install` aqui (baixa o Cypress e o otplib).

## Como rodar

```
npm run cy:open     # modo interativo (escolhe o browser, vê rodando)
npm run cy:run      # headless (CI)
npm run smoke       # só o smoke test de scaffold
```

## Infra pronta (de autoria do Claude)

Config em `cypress.config.js`: `baseUrl`, `env` (emails, segredo, userIds — valores de
teste, Regra 4) e dois `cy.task` Node-side:

- **`totp(secret?)`** → código 2FA de 6 dígitos a partir do segredo do seed (base32).
  Node-side porque `otplib` não roda no browser. Necessário pro login real com 2FA.
- **`forgeToken({ email?, userId? })`** → JWT HS256 de **sessão completa**
  (`twoFactorPending:false`), igual ao `JwtUtil.generateToken` do backend, assinado com
  o `jwtSecret` do ambiente. Default = qa-pro. Habilita o bypass de login.

## Duas estratégias de login (os comandos são de autoria do dono — Regra 3)

**1. Bypass (pros casos que NÃO são de login — UI-02, UI-03, PAY-11/12...).**
Injeta um token de sessão forjado no `localStorage` (chave `cyberaudit.token`, que é
onde o Frontend lê o JWT — ver `Frontend/src/api/client.ts`) e visita a página já
autenticado, pulando o 2FA. Esqueleto da ideia:

```
// cypress/support/commands.js (você escreve)
Cypress.Commands.add('bypassLogin', (email) => {
  cy.task('forgeToken', { email }).then((token) => {
    window.localStorage.setItem(Cypress.env('tokenKey'), token);
  });
});
```

**2. Login real com 2FA (pro UI-01, que testa o próprio fluxo de login).**
Preenche e-mail/senha na tela, e no passo do 2FA usa o `cy.task('totp', ...)` pra gerar
o código do momento e submeter. É o único jeito de testar o login de verdade — o bypass
não exercita a tela de login.

> Por que o bypass existe: logar pela UI exige 2FA em toda conta do seed. Pra um teste
> que só quer chegar autenticado numa tela (ex.: iniciar um scan), repetir o fluxo de
> 2FA em cada spec é lento e frágil. O bypass injeta a sessão direto; o login real fica
> reservado pro caso que de fato testa o login (UI-01).

## Casos (matriz `../docs/casos-de-teste.md`)

UI-01 (login 2FA → dashboard), UI-02 (scan + histórico), UI-03 (agendamento),
PLAN-01 (Free sem PDF na tela), PAY-11/PAY-12 (checkout Pix/cartão na tela). Todos de
autoria do dono.

O `smoke.cy.js` é só validação do scaffold (não é caso da matriz).
