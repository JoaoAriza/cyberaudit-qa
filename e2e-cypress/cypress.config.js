const { defineConfig } = require('cypress');
const { authenticator } = require('otplib');
const crypto = require('crypto');

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
      userIdFree: 'b0000000-0000-0000-0000-00000000f1ee'
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
        }
      });
      return config;
    }
  }
});
