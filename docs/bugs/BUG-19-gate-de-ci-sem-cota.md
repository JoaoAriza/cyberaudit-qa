# BUG-19 — Gate de CI roda scan sem cota, sem rate limit e sem a trava de plano do ativo

Achado lateral em 2026-11-17, durante o fix do BUG-18 (lendo o `ApiKeyController`).
**Aberto, não corrigido.** Formato conforme `../template-bug.md`.

```markdown
## Resumo

`GET /api-keys/ci?url=…&threshold=…` é o gate de qualidade para pipeline: roda um scan e
devolve 200 (passou) ou 422 (reprovou). Ele chama `scanOrchestrator.execute(...)` direto e
pula o que o `/scan` faz antes de escanear:

- a cota diária do plano (`checkAndIncrementDailyScan`);
- o rate limit por minuto (`checkRateLimit`);
- a trava de plano do scan ativo (`checkActiveScan`: FREE não faz ativo, PRO só em domínio
  verificado na conta).

A prova de posse por arquivo (`/.well-known/cyberaudit.txt`) continua, porque mora dentro
do orchestrator. Por esse caminho não dá para fazer scan ativo em site de terceiro.

## Severidade

P1 — cota e plano contornados por qualquer chave. Depois do BUG-18 só plano pago cria
chave, mas a chave antiga de conta FREE continua valendo (decisão do dono), e quem rebaixa
de PRO para FREE fica com ela.

## Ambiente

- Commit/versão testada: Backend `d0f9594`
- Ambiente: docker-compose de teste, backend 8081; alvos `wiremock` e `juice-shop`
  (locais). Nunca produção.
- Ferramenta que encontrou: revisão de código no fix do BUG-18, confirmada ao vivo

## Passos para reproduzir

1. qa-free: `GET /scan?url=http://wiremock:8080/health` até levar 402 (cota de 10/dia).
2. Com uma API key do qa-free: o mesmo `GET /scan` com `X-Api-Key` → 402 (controle: a
   chave autentica como qa-free e a cota vale).
3. Com a mesma chave: `GET /api-keys/ci?url=http://wiremock:8080/health&threshold=10`,
   três vezes.
4. qa-free, ativo: `GET /scan?url=http://juice-shop:3000&active=true` e
   `GET /api-keys/ci?url=http://juice-shop:3000&active=true` com a chave.

## Resultado esperado

3. 402, como no `/scan`.
4. 402 "Scan ativo requer plano PRO ou superior." nos dois.

## Resultado obtido

1. 200 dez vezes e depois 402.
2. 402.
3. **200 nas três**, com `score` e `pass`.
4. `/scan` → 402 "requer plano PRO". O gate → **403** "não autorizado para este domínio": a
   trava de plano foi pulada e o pedido só parou na prova de posse por arquivo. Num domínio
   que a conta FREE controle (e onde publique o token), o ativo rodaria.

A chave de teste do qa-free foi revogada em seguida.

## Causa raiz

`ApiKeyController.ciGate` chama `scanOrchestrator.execute(url, active, user, false)` sem
passar pelo `enforceScanLimits`/`checkRateLimit` do `ScanController`, que são `private` lá.
O endpoint nasceu (2026-06-21) junto com as API keys e nunca ganhou as travas que o
`/scan` foi ganhando.

## Correção

A fazer. Sugestão: tirar as travas do `ScanController` para um serviço que os dois chamem
(cota, rate limit e `checkActiveScan`), com teste que bata no `/api-keys/ci` com chave de
conta FREE (cota estourada → 402; ativo → 402).

## Teste de regressão

A escrever junto com a correção. Caso de QA candidato: chave de conta com cota estourada →
gate 402.
```
