# BUG-04 — Código TOTP pode ser reusado no login (replay do 2º fator) (AUTH-04)

Encontrado em 2026-10-26 verificando o contrato do AUTH-04 (antes de automatizar).
Confirmado ao vivo contra o ambiente de teste. Formato conforme `../template-bug.md`.
Colar como issue no GitHub quando for abrir. Automação adiada (a pedido do dono):
registrar agora, escrever o teste de regressão depois.

```markdown
## Resumo

No login com 2FA, o mesmo código TOTP válido pode ser **verificado mais de uma vez**
dentro da sua janela de validade e emite uma **nova sessão a cada vez**. O
`POST /auth/2fa/verify` valida o código mas **não o marca como consumido**: o
`TwoFactorService.verifyLoginCode` (ramo TOTP) só chama `TotpService.verify()`, que é
stateless (`dev.samstevens.totp`, `DefaultCodeVerifier`). Contraste: o ramo de Email
OTP marca `used=true` (`verifyEmailOtp`), então só o TOTP tem a falha.

## Severidade

P0 — replay do segundo fator. Um código OTP deve valer **uma vez**. Quem observar um
código válido (ombro, phishing, log, proxy) pode reusá-lo dentro da janela (~período
atual ±1, até ~90s) para cunhar sessões adicionais, derrotando a intenção de uso único
do OTP. O token pré-auth (5 min) também é reusável, ampliando a janela de abuso.

## Ambiente

- Commit/versão testada: Backend `4ed5f34`
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: verificação manual (Node/fetch + otplib) do contrato do AUTH-04

## Passos para reproduzir

Com um usuário do seed que tem TOTP ligado (ex. qa-free, segredo
`JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP`):

1. `POST /auth/login {email, password}` → 200 com `token` pré-auth (`requires2fa:true`).
2. Gerar um código TOTP válido do segredo (ex. otplib / `dev.samstevens.totp`).
3. `POST /auth/2fa/verify` com `Authorization: Bearer <pré-auth>` e
   `{"code":"<válido>","method":"TOTP"}` → 200 + token de sessão.
4. Repetir o passo 3 **com o mesmo código e o mesmo pré-auth token**, na hora.

## Resultado esperado

O segundo `verify` com o mesmo código é **recusado** (o código já foi consumido) —
ex. 422/401, sem emitir nova sessão.

## Resultado obtido

O segundo (e seguintes) `verify` com o mesmo código retorna **200 + nova sessão**,
igual ao primeiro. O código nunca é invalidado dentro da janela.

## Caso de teste relacionado

AUTH-04 (2FA: sem código / código inválido / código reutilizado → negado nos três).
Os sub-casos "sem código" e "código inválido" negam certo (422 `message:"Código TOTP
inválido."`); só o **reuso** é o bug. O controle positivo (código válido → 200) e o
teste de reuso exigem gerar um código válido em Java → falta uma lib de TOTP no
`pom.xml` do RestAssured (ex. `dev.samstevens.totp:totp`).

## Causa raiz (a investigar no Backend)

`TwoFactorService.verifyLoginCode`, ramo TOTP: valida via `TotpService.verify` mas não
registra o código/time-step como usado. O `DefaultCodeVerifier` é stateless e a
discrepância de ±1 período (em `TotpService`) ainda alarga a janela. Correção padrão
(RFC 6238): persistir o último time-step aceito por usuário e rejeitar código cujo step
seja ≤ o último aceito.

## Correção

(pendente — fix no Backend)

## Teste de regressão

(pendente — AUTH-04 em RestAssured: o sub-caso de reuso fica `@Disabled` guardando a
expectativa correta "2º uso do mesmo código → negado"; reativa quando o backend passar
a consumir o código. Precisa da lib de TOTP no pom. Fecha a issue quando esse teste passar.)
```
