-- Seed do ambiente de teste (cyberaudit-qa) — roda pelo serviço `seeder` do
-- docker-compose.test.yml, depois que o backend já criou o schema
-- (ddl-auto=update). Idempotente: pode rodar de novo sem duplicar nada.
--
-- Um usuário por plano, senha e segredo TOTP fixos e conhecidos (Regra do
-- escopo — ver docs/plano-de-teste.md). NENHUM destes valores é real ou usado
-- em produção.
--
-- Senha de todo mundo: Teste@123
-- (hash BCrypt custo 12, gerado uma vez com o mesmo PasswordEncoder do
-- PasswordConfig.java — não precisa recalcular)
--
-- Segredo TOTP de todo mundo (base32): JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP
-- (o exemplo clássico de secret TOTP — mesmo algoritmo do TotpService: SHA1, 6
-- dígitos, período de 30s. otplib no Cypress gera o código com esse mesmo
-- secret sem configuração extra.)

-- ── Conta Free (INDIVIDUAL) ──────────────────────────────────────────────────

INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa)
VALUES ('a0000000-0000-0000-0000-00000000f1ee', 'INDIVIDUAL', 'FREE',
        'QA Free', 'QA Free', now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                        account_id, terms_accepted, terms_accepted_at,
                        totp_secret, totp_enabled, email_otp_enabled)
VALUES ('b0000000-0000-0000-0000-00000000f1ee', 'QA Free', 'qa-free@cyberaudit.test',
        '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
        'a0000000-0000-0000-0000-00000000f1ee', true, now(),
        'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;

-- ── Conta Pro (INDIVIDUAL) ───────────────────────────────────────────────────

INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa)
VALUES ('a0000000-0000-0000-0000-000000000090', 'INDIVIDUAL', 'PRO',
        'QA Pro', 'QA Pro', now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                        account_id, terms_accepted, terms_accepted_at,
                        totp_secret, totp_enabled, email_otp_enabled)
VALUES ('b0000000-0000-0000-0000-000000000090', 'QA Pro', 'qa-pro@cyberaudit.test',
        '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
        'a0000000-0000-0000-0000-000000000090', true, now(),
        'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;

-- ── Conta Enterprise (COMPANY) ───────────────────────────────────────────────

INSERT INTO accounts (id, type, plan, display_name, company_name, cnpj, created_at, require2fa)
VALUES ('a0000000-0000-0000-0000-00000000e9e9', 'COMPANY', 'ENTERPRISE',
        'QA Enterprise', 'QA Enterprise Ltda', '11444777000161', now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                        account_id, terms_accepted, terms_accepted_at,
                        totp_secret, totp_enabled, email_otp_enabled)
VALUES ('b0000000-0000-0000-0000-00000000e9e9', 'QA Enterprise', 'qa-enterprise@cyberaudit.test',
        '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
        'a0000000-0000-0000-0000-00000000e9e9', true, now(),
        'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;

-- ── Domínios locais já verificados (Active Scan exige posse — PRO/Enterprise) ─
--
-- Host COM porta — PlanLimitService.normalizeHost só remove esquema e path, a
-- porta fica. Sem a porta aqui, o valor nunca bate com o host real do scan
-- ("juice-shop:3000") e o scan ativo recusa com ACCOUNT_DOMAIN_NOT_VERIFIED
-- mesmo com a linha existindo — confirmado ao vivo em 2026-09-30.
--
-- Isso resolve só a checagem no NÍVEL DA CONTA. Existe uma segunda guarda,
-- independente, que busca /.well-known/cyberaudit.txt no alvo AO VIVO antes de
-- todo scan ativo (DomainProtectionService) — terceiros como o Juice Shop não
-- têm como servir esse arquivo. Quem resolve essa segunda guarda é
-- PLATFORM_STAFF_EMAILS no docker-compose.test.yml.
--
-- SÓ a conta Enterprise recebe domínio verificado aqui, e só ela está em
-- PLATFORM_STAFF_EMAILS (revisado em 2026-10-01, antes as duas tinham bypass +
-- domínio verificado). qa-pro fica SEM nenhum domínio verificado e SEM bypass
-- de propósito — é a conta do caso DOM-01 (PRO de verdade, domínio não
-- verificado, scan ativo recusado). Com bypass ou domínio verificado, esse
-- cenário nunca seria alcançável com ela.

INSERT INTO domains (id, account_id, host, verified, verified_at, created_at)
VALUES ('d0000000-0000-0000-0000-0000000000e9', 'a0000000-0000-0000-0000-00000000e9e9',
        'juice-shop:3000', true, now(), now())
ON CONFLICT (account_id, host) DO NOTHING;

INSERT INTO domains (id, account_id, host, verified, verified_at, created_at)
VALUES ('d0000000-0000-0000-0000-0000000000ea', 'a0000000-0000-0000-0000-00000000e9e9',
        'wiremock:8080', true, now(), now())
ON CONFLICT (account_id, host) DO NOTHING;

-- ── Contas FREE dedicadas aos testes de pagamento que MUTAM estado ───────────
-- Checkout muda o plano/subscription da conta (upgrade, PENDING), e isso persiste.
-- Cada teste de PAY que faz upgrade usa a SUA conta, pra não colidir com o qa-free
-- nem entre si. Mesmo password/TOTP de teste das outras. (PAY-02/03/05/13, que só
-- validam e NÃO mutam, podem seguir usando o qa-free.)
INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa) VALUES
  ('a0000000-0000-0000-0000-00000000ca01', 'INDIVIDUAL', 'FREE', 'QA Pay Card',    'QA Pay Card',    now(), false),
  ('a0000000-0000-0000-0000-00000000ca14', 'INDIVIDUAL', 'FREE', 'QA Pay Card s/CPF','QA Pay Card s/CPF', now(), false),
  ('a0000000-0000-0000-0000-00000000b104', 'INDIVIDUAL', 'FREE', 'QA Pay Pix',     'QA Pay Pix',     now(), false),
  ('a0000000-0000-0000-0000-00000000eb06', 'INDIVIDUAL', 'FREE', 'QA Pay Webhook', 'QA Pay Webhook', now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                       account_id, terms_accepted, terms_accepted_at,
                       totp_secret, totp_enabled, email_otp_enabled) VALUES
  ('b0000000-0000-0000-0000-00000000ca01', 'QA Pay Card', 'qa-pay-card@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-00000000ca01', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false),
  ('b0000000-0000-0000-0000-00000000ca14', 'QA Pay Card s/CPF', 'qa-pay-nocpf@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-00000000ca14', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false),
  ('b0000000-0000-0000-0000-00000000b104', 'QA Pay Pix', 'qa-pay-pix@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-00000000b104', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false),
  ('b0000000-0000-0000-0000-00000000eb06', 'QA Pay Webhook', 'qa-pay-webhook@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-00000000eb06', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;

-- ── PAY-15: conta com Pix AUTHORIZED (antigo) + Pix PENDING (novo) ────────────
-- Regressão de bug real (corrigido 2026-09-30): GET /billing/subscription deve
-- devolver a assinatura AUTHORIZED, NÃO a mais recente por data (que é a PENDING).
-- Caso read-only: o teste só faz GET e asserta status=AUTHORIZED. Cenário fixo.
INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa) VALUES
  ('a0000000-0000-0000-0000-000000000015', 'INDIVIDUAL', 'PRO', 'QA Pay 15', 'QA Pay 15', now(), false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                       account_id, terms_accepted, terms_accepted_at,
                       totp_secret, totp_enabled, email_otp_enabled) VALUES
  ('b0000000-0000-0000-0000-000000000015', 'QA Pay 15', 'qa-pay-15@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-000000000015', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;

-- AUTHORIZED, criada 2 dias ATRÁS (mais antiga), ainda vigente
INSERT INTO subscriptions (id, account_id, plan, status, payment_method, amount, currency,
                           mp_payment_id, created_at, current_period_end, updated_at) VALUES
  ('50000000-0000-0000-0000-0000000015a0', 'a0000000-0000-0000-0000-000000000015',
   'PRO', 'AUTHORIZED', 'PIX', 19.99, 'BRL', 'pay15-authorized',
   now() - interval '2 days', now() + interval '28 days', now() - interval '2 days')
ON CONFLICT (id) DO NOTHING;

-- PENDING, criada AGORA (mais recente) — a "pegadinha" do bug
INSERT INTO subscriptions (id, account_id, plan, status, payment_method, amount, currency,
                           mp_payment_id, created_at, updated_at) VALUES
  ('50000000-0000-0000-0000-0000000015b0', 'a0000000-0000-0000-0000-000000000015',
   'PRO', 'PENDING', 'PIX', 19.99, 'BRL', 'pay15-pending', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── PAY-09: conta dedicada ao teste de rate limit de checkout ─────────────────
-- O teto é 5 checkouts/min POR USUÁRIO (bucket em memória). Conta própria pra o
-- laço do PAY-09 não esgotar a bucket da qa-free (que PAY-02/03/05/13 usam).
INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa) VALUES
  ('a0000000-0000-0000-0000-000000000009', 'INDIVIDUAL', 'FREE', 'QA Pay 09', 'QA Pay 09', now(), false)
ON CONFLICT (id) DO NOTHING;
INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                       account_id, terms_accepted, terms_accepted_at,
                       totp_secret, totp_enabled, email_otp_enabled) VALUES
  ('b0000000-0000-0000-0000-000000000009', 'QA Pay 09', 'qa-pay-09@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-000000000009', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;

-- ── PAY-07: conta com Pix AUTHORIZED ativa (webhook rejected não pode derrubar) ─
-- A sub tem mp_payment_id conhecido ('pay07-sub') pra o teste mandar um webhook
-- rejected pra ESSE payment e provar que continua AUTHORIZED (no-op).
INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa) VALUES
  ('a0000000-0000-0000-0000-000000000007', 'INDIVIDUAL', 'PRO', 'QA Pay 07', 'QA Pay 07', now(), false)
ON CONFLICT (id) DO NOTHING;
INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                       account_id, terms_accepted, terms_accepted_at,
                       totp_secret, totp_enabled, email_otp_enabled) VALUES
  ('b0000000-0000-0000-0000-000000000007', 'QA Pay 07', 'qa-pay-07@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-000000000007', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;
INSERT INTO subscriptions (id, account_id, plan, status, payment_method, amount, currency,
                           mp_payment_id, created_at, current_period_end, updated_at) VALUES
  ('50000000-0000-0000-0000-0000000007a0', 'a0000000-0000-0000-0000-000000000007',
   'PRO', 'AUTHORIZED', 'PIX', 19.99, 'BRL', 'pay07-sub',
   now() - interval '1 day', now() + interval '29 days', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

-- ── PAY-10: conta para o job de expiração de Pix ─────────────────────────────
-- O helper TestConfig.prepararPixVencida recria, antes de cada teste, uma Pix
-- AUTHORIZED com currentPeriodEnd no passado nesta conta; o gatilho de teste
-- /admin/test/expire-pix dispara o job e ela deve cair para FREE/EXPIRED.
INSERT INTO accounts (id, type, plan, display_name, full_name, created_at, require2fa) VALUES
  ('a0000000-0000-0000-0000-000000000010', 'INDIVIDUAL', 'FREE', 'QA Pay 10', 'QA Pay 10', now(), false)
ON CONFLICT (id) DO NOTHING;
INSERT INTO app_users (id, name, email, password_hash, role, active, created_at,
                       account_id, terms_accepted, terms_accepted_at,
                       totp_secret, totp_enabled, email_otp_enabled) VALUES
  ('b0000000-0000-0000-0000-000000000010', 'QA Pay 10', 'qa-pay-10@cyberaudit.test',
   '$2a$12$MOSJU8sFnD8zZKGbvBgA3OdKOVfQ08BUFB/ybHUnXivlrckV36Ybe', 'OWNER', true, now(),
   'a0000000-0000-0000-0000-000000000010', true, now(), 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP', true, false)
ON CONFLICT (email) DO NOTHING;
