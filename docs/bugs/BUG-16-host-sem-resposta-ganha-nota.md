# BUG-16 — Host que não responde ganha nota, e módulo que não verificou nada sai "OK"

Achado lateral em 2026-11-15, ao preparar as fichas da fila do dono (conferindo o SCAN-01 e
o UI-02). **✅ Corrigido em 2026-11-22** (HANDOFF §12, Etapa K).
Formato conforme `../template-bug.md`.

```markdown
## Resumo

Quando o alvo não responde, o scan segue como se tivesse respondido e devolve um laudo com
nota. Um domínio que não existe (`nao-existe-qa.invalid`) sai com 29/100 e risco HIGH. Dos
15 módulos, só o `HTTP_FETCH` marca `ERROR`; os outros 14 marcam `OK` sem terem falado com
ninguém. O erro mostrado é "Erro ao conectar: null".

O mesmo "OK sem ter verificado" aparece num alvo que responde: quando o NVD demora mais que
o timeout de 12 s, `cveFindings` volta `[]` e `moduleStatus.CVE` marca `OK`. Não dá para
distinguir "o NVD não tem CVE" de "o NVD não respondeu".

## Severidade

P1 — resultado errado apresentado como real. Quem erra uma letra do domínio recebe "risco
alto" de um site que não existe, e o scan gasta a cota. O `moduleStatus`, que existe para
distinguir verificado de não concluído, diz "OK" nos dois casos.

## Ambiente

- Commit/versão testada: Backend `d0f9594`, Frontend `c7006bc`
- Ambiente: docker-compose de teste, backend 8081. Nunca produção do CyberAudit (Regra 1).
- Ferramenta que encontrou: revisão do contrato do SCAN-01 e histórico do banco de teste

## Passos para reproduzir

1. qa-pro (token forjado): `GET /scan?url=http://nao-existe-qa.invalid&active=false`.
   `.invalid` é reservado (RFC 2606) e nunca resolve.
2. No Postgres de teste:
   `select url, score, count(*) from scan_records where host = 'github.comwiremock' group by 1, 2`.
3. qa-pro: `GET /scan?url=http://php-banner-mock:8080` algumas vezes, com mais de 2 min entre
   elas (o cache dura 2 min); ler `cveFindings` e `moduleStatus.CVE`.

## Resultado esperado

1. O scan diz que o alvo não respondeu, sem nota: recusa (por exemplo 422) ou resultado
   marcado como não concluído. Mensagem sem "null".
2. —
3. Quando o NVD não responde, `moduleStatus.CVE` diz `ERROR` (ou "não verificado").

## Resultado obtido

1. **200** em 33 s; `httpStatus` 0, `score.score` 29, `riskLevel` HIGH;
   `moduleStatus.HTTP_FETCH` `ERROR` e os outros 14 `OK`; `headers.error` "Erro ao conectar: null".
2. `https://github.comwiremock:8080/health`, nota 15, **21 vezes**. É o UI-02 (Cypress): o
   campo de URL já abre com "github.com" e o teste digita no fim. O scan de um host
   inexistente mostrou "/100", e o teste passou (ver a ficha do UI-02 em `../fichas-da-fila.md`).
3. Uma rodada `cveFindings: []` e outra `["CVE-2007-3205"]`, as duas com
   `moduleStatus.CVE: "OK"`. Na mesma hora, o NVD direto levou 12,08 s.

## Causa raiz

Lida no código e confirmada no fix:

- `ScanOrchestrator`: depois de `fetchHeaders` com erro, o fluxo grava `HTTP_FETCH=ERROR` e
  continua; o score é calculado do mesmo jeito.
- Os módulos engolem a falha de rede como "nada encontrado" (`catch → List.of()` /
  `false`), e `moduleState(future)` só diz `ERROR` quando o future termina com exceção. No
  `CVECorrelationService`, `.timeout(12 s)` e `catch (Exception e) { return List.of(); }`,
  sem log.
- `HttpFetchService.fetchHeaders` monta "Erro ao conectar: " + `e.getMessage()`, que é nulo
  para a falha de resolução e de conexão do `HttpClient`.
- O `CrtShService` fazia o mesmo que o NVD: crt.sh e certspotter fora do ar viravam lista
  vazia, e o módulo de Certificate Transparency marcava `OK`.
- Visto no fix: o `HttpClient` embrulha a falha de DNS numa `ConnectException` (com a
  `UnresolvedAddressException` por dentro). Quem lê só a primeira exceção da cadeia chama
  "conexão recusada" um domínio que nem existe.

## Correção

Decisões do dono (2026-11-22):

- **Domínio que não resolve: recusar antes da cota.** 422 com `error` `TARGET_NOT_FOUND`,
  `host` e uma mensagem no idioma pedido. Não gasta cota.
- **Resolve, mas nada aceita conexão nem em http nem em https: recusar com 422.** `error`
  `TARGET_UNREACHABLE`. Aqui a cota já foi gasta: só se descobre tentando conectar.
- **Serviço externo que não respondeu (NVD, crt.sh/certspotter): marcar `ERROR`.** O módulo
  entra no aviso de resultado parcial em vez de dizer "OK, nada encontrado".

O que mudou:

- Backend:
  - `ScanTargetService` (novo) confere se o domínio resolve. Roda no
    `ScanLimitsService.checkBeforeScan`, depois do rate limit e antes da cota, para todo
    endpoint que dispara scan (`/scan`, `/scan/async`, `/scan/report`, `/scan/report/pdf`,
    `/api-keys/ci`). Roda também no `ScanOrchestrator.execute`, para quem chama o motor
    direto: o agendador.
  - Nome que o `SsrfGuard` recusa sem consultar DNS passa por essa checagem sem erro e
    continua levando 403 do guard. São os sufixos de rede interna (`metadata.google.internal`)
    e o IPv4 fora do formato canônico (`http://2130706433`, o SSRF-03). Esses nomes não
    resolvem, e sem essa ressalva sairiam como "domínio não encontrado".
  - `HttpFetchResult.semConexao`: verdadeiro quando nada aceitou a conexão (DNS, conexão
    recusada, sem rota, timeout de conexão). Falha depois de conectar (TLS quebrado, servidor
    que não responde a tempo) não conta: o site existe e segue escaneado. No
    `ScanOrchestrator`, sem conexão no fetch principal, o https é conferido antes de desistir.
    Se também não conectar, sai `TargetUnreachableException` → 422.
  - O motivo do erro em texto ("conexão recusada", "o domínio não resolve no DNS"...), no
    lugar do `null`.
  - `CVECorrelationService.correlacionar` devolve os achados e se ficou `completa`. A
    consulta que falha marca a correlação como incompleta, e os achados das consultas que
    responderam ficam. 404 do NVD continua sendo resposta (cpeName desconhecido).
    `CrtShService.fetchCertsOuNull` devolve `null` quando nenhuma das duas fontes
    respondeu, e o `CertTransparencyService` repassa o `null`. No orchestrator, CVE e CT
    marcam `ERROR` nesses casos.
  - No `/scan/async`, o erro chega ao polling com `errorCode` e `errorHost`, como o
    `OWNERSHIP_REQUIRED`.
- Frontend: `TARGET_NOT_FOUND` e `TARGET_UNREACHABLE` mostram a mensagem do Backend como
  aviso, tanto no 422 do POST quanto no ERROR do polling.

## Teste de regressão

- Backend:
  - `AlvoInacessivelIntegrationTest`: 8 testes. Contra o código antigo, 5 ficam vermelhos
    (o `.invalid` e a porta fechada seguem para a análise, e o `/scan/async` devolve 202) e
    os 3 controles ficam verdes nos dois lados. Nada sai da máquina: o alvo que responde é
    um servidor em 127.0.0.1, liberado no `SsrfGuard` só durante a classe. O
    `CookieSecurityService`, que roda logo depois da checagem de conexão, é um mock que lança
    uma exceção de sinal ("lançou" = o alvo respondeu e o scan seguiu). Os testes cobrem:
    - `.invalid` no `/scan` → 422 `TARGET_NOT_FOUND`, com a última da cota de um FREE
      ainda sobrando depois;
    - o mesmo no `/scan/async` (sem `scanId`) e em inglês;
    - o motor chamado direto, como o agendador faz;
    - porta fechada → 422 `TARGET_UNREACHABLE`, sem "null" na mensagem;
    - controles: alvo que responde 200 e alvo que responde 503 seguem para a análise;
      `metadata.google.internal` e `http://2130706433` continuam 403.
  - `HttpFetchSemConexaoTest` (8), `CVENvdSemRespostaTest` (7),
    `ModuloExternoNaoVerificadoTest` (5) e `AsyncScanAlvoInacessivelTest` (4). Cobrem o
    `semConexao` e o texto do motivo, a correlação incompleta com NVD local em 503 e porta
    fechada, o `null` do CT, o estado `ERROR` dos dois módulos, e o código no status
    assíncrono. Eles usam as APIs novas, então não compilam contra o código antigo. O vermelho
    deles é "a API não existia".
  - Um deles pegou um erro meu antes do commit: o motivo dizia "conexão recusada" para o
    `.invalid` (a `ConnectException` de fora, ver Causa raiz).
  - `ScanCacheChaveIntegrationTest` (BUG-17) trocou os hosts `.invalid` por `127.0.0.1`
    liberado: o motor agora recusa o `.invalid` antes do cache.
  - Suíte do Backend: 806 → 838, verde.
- Frontend: `alvoInacessivel.test.ts` (5). Vitest: 40 → 45.
- Ao vivo (8081). Antes: imagem do Backend `92303a4`.

  | Passo | Antes | Depois |
  |---|---|---|
  | qa-free, `http://nao-existe-qa.invalid` | 200 em 5,4 s; 17/100, CRITICAL; cota 10/10 → 9/10 | 422 `TARGET_NOT_FOUND` em 0,28 s; cota 10/10 → 10/10 |
  | O mesmo, `Accept-Language: en` | 200; 17/100 | 422, "The domain nao-existe-qa.invalid was not found in DNS. …" |
  | qa-pro, `http://wiremock:9` (resolve, porta sem nada) | 200 em 21 s; 29/100, HIGH | 422 `TARGET_UNREACHABLE` em 0,37 s, `host` wiremock |
  | `/scan/async` no mesmo alvo (caminho da tela) | DONE, 29/100 | ERROR, `errorCode` `TARGET_UNREACHABLE`, `errorHost` wiremock |
  | Controle: `http://juice-shop:3000/` | 200; 44/100 | 200; 44/100; `httpStatus` 200 |
  | Controle: `http://2130706433` (SSRF-03) | 403 | 403 |

  Também conferido depois do fix:
  - O qa-free no `wiremock:9` leva 422 e gasta a cota (10 → 9), como decidido.
  - Um alvo que responde 404 (`http://wiremock:8080/nao-existe-qa`) continua escaneado:
    200, `httpStatus` 404, 32/100.
  - Numa rodada, o crt.sh deu 502 e o certspotter 403. O CT saiu `ERROR`, com
    "Resultado parcial — não concluíram: Certificate Transparency" nas notas.
  - Na tela (8090, visitante), o `.invalid` mostra "⚠ O domínio nao-existe-qa.invalid não
    foi encontrado no DNS…" sem gastar a cota. O `wiremock:9` mostra "⚠ O site wiremock não
    respondeu…".
  - O 8099 (imagem antiga, sem allowlist) dá o vermelho do `.invalid` sem buildar nada:
    200, 15/100, `httpStatus` 0.
  - RestAssured do dono: 44 / 0 falhas / 2 pulados.
- Caso de QA: **SCAN-13** (P1, 🔲): contrato no HANDOFF §12, Etapa K; ficha 18.
```

## O que mudou nas fichas com o fix

- **SCAN-01:** `moduleStatus` voltou a significar "verificado", mas o CT e o CVE dependem
  de serviço externo e podem sair `ERROR` sem o caso ter falhado. No Docker o crt.sh falha
  em parte das rodadas. A asserção passa a ser "nenhum módulo diferente de OK, fora
  `CERT_TRANSPARENCY` e `CVE`", junto com `httpStatus` 200. A porta fechada do "ver o
  vermelho" agora dá 422 em vez de `httpStatus` 0.
- **SCAN-05:** `moduleStatus.CVE` = `OK` passa a dizer que todas as consultas ao NVD
  responderam e serve de pré-condição, junto da presença do `CVE-2007-3205`.
- **UI-02:** o host torto (`github.comwiremock`) não resolve. O scan leva 422 e a tela
  mostra o aviso, sem "/100". O verde falso vira vermelho, e a correção do spec continua
  sendo do dono.
