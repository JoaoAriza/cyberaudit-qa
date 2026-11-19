# BUG-19 — Gate de CI roda scan sem cota, sem rate limit e sem a trava de plano do ativo

Achado lateral em 2026-11-17, durante o fix do BUG-18 (lendo o `ApiKeyController`).
**✅ Corrigido em 2026-11-17** (HANDOFF §12, Etapa I). Formato conforme `../template-bug.md`.

```markdown
## Resumo

`GET /api-keys/ci?url=…&threshold=…` é o gate de qualidade para pipeline: roda um scan e
devolve 200 (passou) ou 422 (reprovou). Ele chamava `scanOrchestrator.execute(...)` direto e
pulava o que o `/scan` faz antes de escanear:

- a cota diária do plano (`checkAndIncrementDailyScan`);
- o rate limit por minuto (`checkRateLimit`);
- a trava de plano do scan ativo (`checkActiveScan`: FREE não faz ativo, PRO só em domínio
  verificado na conta).

A prova de posse por arquivo (`/.well-known/cyberaudit.txt`) continuava, porque mora dentro
do orchestrator. Por esse caminho não dava para fazer scan ativo em site de terceiro.

## Severidade

P1 — cota e plano contornados por qualquer chave. Depois do BUG-18 só plano pago cria
chave, mas a chave antiga de conta FREE continua valendo (decisão do dono), e quem rebaixa
de PRO para FREE fica com ela.

## Ambiente

- Commit/versão testada: Backend `d0f9594` (achado) e `5f240d4` (antes do fix, ao vivo)
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

## Resultado obtido (antes do fix)

1. 200 dez vezes e depois 402.
2. 402.
3. **200 nas três**, com `score` e `pass`.
4. `/scan` → 402 "requer plano PRO". O gate → **403** "não autorizado para este domínio": a
   trava de plano foi pulada e o pedido só parou na prova de posse por arquivo. Num domínio
   que a conta FREE controle (e onde publique o token), o ativo rodaria.

A chave de teste do qa-free foi revogada em seguida.

## Causa raiz

`ApiKeyController.ciGate` chamava `scanOrchestrator.execute(url, active, user, false)` sem
passar pelo `enforceScanLimits`/`checkRateLimit` do `ScanController`, que eram `private` lá.
O endpoint nasceu (2026-06-21) junto com as API keys e nunca ganhou as travas que o
`/scan` foi ganhando.

## Correção

As duas travas saíram do `ScanController` para um serviço novo, `ScanLimitsService`:
`checkRateLimit`, `enforceScanLimits` e `checkBeforeScan`, que chama as duas nessa ordem
(um 429 não gasta cota). O `ScanController` usa o serviço nos mesmos pontos de antes, sem
mudar comportamento. O `ciGate` chama `checkBeforeScan` antes do orchestrator.

O rate limit agora é chamado no gate, mas OWNER passa direto por ele, e todo cadastro é
OWNER. Isso é outro bug (BUG-20), aberto. Por isso a regressão do rate limit usa um membro
de equipe (`FREE_EMPLOYEE`, 60/min).

## Teste de regressão

- Backend: `ApiKeyCiGateLimitesIntegrationTest` (6 testes; 5 vermelhos contra o código
  antigo). O alvo é `http://127.0.0.1/`, que o SsrfGuard recusa com 403 `DOMAIN_BLOCKED`
  antes de qualquer requisição: "403 DOMAIN_BLOCKED" quer dizer "passou pelas travas e
  chegou ao scan", e nenhum teste toca a rede.
  - chave antiga de conta FREE (criada no PRO, conta rebaixada) com a cota gasta → 402
    "Limite diário de 10 scans atingido.";
  - o gate gasta da mesma cota do `/scan`: sobra 1, o gate passa, e o `/scan` seguinte dá 402;
  - FREE pedindo ativo → 402 "Scan ativo requer plano PRO ou superior.";
  - PRO pedindo ativo fora de domínio verificado na conta → 403 `ACCOUNT_DOMAIN_NOT_VERIFIED`;
  - membro de equipe: a 61ª chamada no minuto → 429;
  - controle: PRO passivo passa pelas travas.
- Suíte do Backend: 793 → 799, verde.
- Ao vivo (8081, depois do rebuild): com a cota do qa-free gasta, o gate com a chave antiga
  dá **402 nas três** chamadas; o ativo dá 402 de plano no `/scan` e no gate. Controle:
  qa-pro → 200 com `threshold=0` e 422 com `threshold=100`. Chave de teste revogada; plano
  do qa-free de volta ao FREE.
- Caso de QA: **PLAN-04** (P1, 🔲) — contrato no HANDOFF §12, Etapa I; ficha 16.
```
