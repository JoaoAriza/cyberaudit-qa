const { defineConfig } = require('cypress');
const { authenticator } = require('otplib');
const crypto = require('crypto');
const { execSync } = require('child_process');

// base64url de um Buffer/string (sem padding) — mesmo formato de JWT do backend.
function b64url(input) {
  return Buffer.from(input)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

module.exports = defineConfig({
  e2e: {
    // O Frontend servido pelo docker-compose.test.yml (Cypress roda no host).
    baseUrl: 'http://localhost:8090',
    supportFile: 'cypress/support/e2e.js',
    specPattern: 'cypress/e2e/**/*.cy.js',
    video: false,
    // Valores de teste (Regra 4) — espelham api-restassured/.../config.properties
    // e o env/seed.sql. So de teste, nunca de producao.
    env: {
      apiUrl: 'http://localhost:8081',
      tokenKey: 'cyberaudit.token',
      jwtSecret: 'qa-docker-jwt-secret-32-bytes-minimo-fixo-e-conhecido',
      totpSecret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
      password: 'Teste@123',
      emailFree: 'qa-free@cyberaudit.test',
      emailPro: 'qa-pro@cyberaudit.test',
      emailEnterprise: 'qa-enterprise@cyberaudit.test',
      userIdPro: 'b0000000-0000-0000-0000-000000000090',
      userIdEnterprise: 'b0000000-0000-0000-0000-00000000e9e9',
      userIdFree: 'b0000000-0000-0000-0000-00000000f1ee',
      // PAY-11 (Pix na tela): conta FREE dedicada que vira PRO no fluxo de upgrade.
      // accountIdPay11 é usado pelo cy.task('resetFree') pra devolver a conta a FREE
      // antes de cada run (o checkout muta o plano e persiste). Semeada em env/seed.sql.
      emailPay11: 'qa-pay-11@cyberaudit.test',
      userIdPay11: 'b0000000-0000-0000-0000-000000000011',
      accountIdPay11: 'a0000000-0000-0000-0000-000000000011',
      // Secret do webhook do MP (= MP_WEBHOOK_SECRET do docker-compose.test.yml). So de
      // teste (Regra 4). Usado pelo cy.task('signMpWebhook') pra assinar o webhook simulado.
      mpWebhookSecret: 'qa-mp-webhook-secret-de-teste'
    },
    setupNodeEvents(on, config) {
      on('task', {
        // TOTP de 6 digitos a partir do segredo (base32) do seed. Roda no Node
        // porque otplib nao existe no browser. Infra pro login real com 2FA (UI-01).
        totp(secret) {
          return authenticator.generate(secret || config.env.totpSecret);
        },

        // Forja um JWT HS256 de SESSAO completa (twoFactorPending:false), igual ao
        // JwtUtil.generateToken do backend, usando o jwtSecret do ambiente. Infra pro
        // bypass de login (injetar o token no localStorage) nos casos que NAO sao de
        // login. Parametros: { email, userId } — o resto tem default do qa-pro.
        forgeToken({ email, userId } = {}) {
          const now = Math.floor(Date.now() / 1000);
          const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
          const payload = b64url(JSON.stringify({
            sub: email || config.env.emailPro,
            role: 'OWNER',
            userId: userId || config.env.userIdPro,
            name: 'QA',
            twoFactorPending: false,
            iat: now,
            exp: now + 3600
          }));
          const data = `${header}.${payload}`;
          const sig = b64url(crypto.createHmac('sha256', config.env.jwtSecret).update(data).digest());
          return `${data}.${sig}`;
        },

        // Devolve uma conta a FREE e apaga as subscriptions dela, pelo postgres do
        // docker-compose.test.yml (container env-postgres-1). Infra do PAY-11: o checkout
        // de upgrade MUTA a conta pra PRO e persiste, entao o beforeEach chama isto pra
        // o re-run ser idempotente. Sem dependencia nova (usa o psql do container, igual
        // ao reset manual do HANDOFF). accountId e' obrigatorio e validado como UUID pra
        // nunca rodar o UPDATE/DELETE sem WHERE. Roda no Node, nao no browser.
        resetFree({ accountId } = {}) {
          if (!accountId || !/^[0-9a-fA-F-]{36}$/.test(accountId)) {
            throw new Error('resetFree: accountId (UUID) obrigatorio');
          }
          const sql = `UPDATE accounts SET plan='FREE' WHERE id='${accountId}'; `
                    + `DELETE FROM subscriptions WHERE account_id='${accountId}';`;
          execSync(
            `docker exec env-postgres-1 psql -U cyberaudit_qa -d cyberaudit_qa -c "${sql}"`,
            { stdio: 'pipe' }
          );
          return null;
        },

        // Assina um webhook 'payment' do Mercado Pago como o backend valida: manifesto
        // id:<paymentId>;request-id:<reqId>;ts:<ts>; -> HMAC-SHA256 com o mpWebhookSecret,
        // hex minusculo, no header x-signature "ts=<ts>,v1=<hex>" (a virgula importa). O
        // corpo NAO entra na assinatura. Infra do PAY-11: devolve headers+body prontos; o
        // cy.request e os asserts ficam no teste (autoria do dono). Mesmo contrato do PAY-06.
        signMpWebhook({ paymentId, requestId = 'req-pay11', ts = '1' } = {}) {
          if (!paymentId) throw new Error('signMpWebhook: paymentId obrigatorio');
          const manifest = `id:${paymentId};request-id:${requestId};ts:${ts};`;
          const v1 = crypto.createHmac('sha256', config.env.mpWebhookSecret)
                           .update(manifest).digest('hex');
          return {
            headers: {
              'Content-Type': 'application/json',
              'x-request-id': requestId,
              'x-signature': `ts=${ts},v1=${v1}`,
            },
            body: { type: 'payment', data: { id: paymentId } },
          };
        }
      });

      // Na CI (Linux) o browser resolve `localhost` como ::1 (IPv6), mas o backend
      // so e publicado em 127.0.0.1 (IPv4) pelo docker-compose.test.yml. Sem isto, o
      // /auth/me do app (XHR cross-origin, no browser) nao conecta na CI -> o bypass
      // de login "nao loga" e todos os specs logados quebram (so o smoke passa, porque
      // usa cy.request, que roda no Node e resolve localhost como IPv4, igual ao Java).
      // Forca o browser (Chromium/Electron) a resolver localhost como IPv4. Inofensivo
      // no Windows local (onde localhost ja e 127.0.0.1).
      on('before:browser:launch', (_browser, launchOptions) => {
        launchOptions.args.push('--host-resolver-rules=MAP localhost 127.0.0.1');
        return launchOptions;
      });

      return config;
    }
  }
});
