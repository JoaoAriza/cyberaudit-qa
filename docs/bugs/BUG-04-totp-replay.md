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

## Causa raiz (confirmada no fix, 2026-11-01)

`TwoFactorService.verifyLoginCode`, ramo TOTP: valida via `TotpService.verify` mas não
registra o código/time-step como usado. O `DefaultCodeVerifier` é stateless e a
discrepância de ±1 período (em `TotpService`) ainda alarga a janela. Correção padrão
(RFC 6238): persistir o último time-step aceito por usuário e rejeitar código cujo step
seja ≤ o último aceito.

Confirmado e pior que o relatado: **em pedidos simultâneos**, um único código abria uma
sessão por pedido (6 de 6 no teste de regressão, antes do fix). E o mesmo vale para o
código que confirma a ativação do TOTP (`confirmTotpSetup`), que também só validava.

## Correção

Corrigido no Backend em 2026-11-01:

- `AppUser.totpLastUsedStep` (coluna nova, nula) guarda a janela de 30 s do último código
  aceito. O `ddl-auto=update` cria a coluna; contas existentes sobem com nulo (nenhum
  código usado ainda), sem migração nem logout.
- `TotpService.matchingTimeStep` diz **qual** janela casou (a atual ±1) — o verificador da
  biblioteca só dizia sim/não. Mesmo gerador, comparação em tempo constante.
- `AppUserRepository.consumeTotpStep`: uma UPDATE que só grava se a janela for **mais
  nova** que a gravada, e devolve quantas linhas mudou. Comparar e gravar na mesma
  instrução é o que barra os pedidos simultâneos: o segundo altera 0 linhas.
- Login (`verifyLoginCode`) e ativação (`confirmTotpSetup`) consomem o código. Reuso →
  **422** `"Código TOTP já utilizado. Aguarde o próximo código no aplicativo."`; código
  errado continua 422 `"Código TOTP inválido."`. O reuso conta como falha no throttle de
  2FA (5 falhas → 15 min de bloqueio; um sucesso zera).
- A coluna é `updatable = false`: só a UPDATE condicional escreve nela. Sem isso, o
  `save()` da ativação regravava o valor antigo e o código voltava a servir — conferido
  tirando o atributo e vendo o teste da ativação falhar.

Verificado ao vivo (container `backend` rebuildado, coluna `totp_last_used_step bigint`
criada no Postgres), como qa-free, com o mesmo pré-auth:

| código | antes | depois |
|---|---|---|
| N (janela atual) | 200 | **200**, sessão emitida |
| N de novo | 200, outra sessão | **422** "já utilizado" |
| N+1 (relógio do app adiantado) | 200 | **200** — mais novo que o último usado |
| N de novo (agora mais velho) | 200 | **422** "já utilizado" |
| `000000` | 422 | 422 "inválido" |

No Frontend nada muda para o usuário: o botão de confirmar fica desabilitado durante o
pedido e não há envio automático, então ninguém esbarra no "já utilizado" sem querer.

## Teste de regressão

- Backend: `TotpReplayIntegrationTest` (novo, rota real com a cadeia de filtros, não
  `@Transactional`): mesmo código 2× → 200 e 422; janela seguinte passa e a anterior não;
  6 pedidos simultâneos com o mesmo código → exatamente 1 sessão; código da ativação não
  serve para logar. Os 4 falhavam antes do fix. `TotpServiceTest` ganhou 3 casos de
  `matchingTimeStep` com relógio fixo. Suíte inteira verde (681).
- QA: destrava o sub-caso de reuso do AUTH-04. **Pendente (dono, na fila):** AUTH-04 —
  precisa de lib de TOTP no pom do RestAssured.
```
