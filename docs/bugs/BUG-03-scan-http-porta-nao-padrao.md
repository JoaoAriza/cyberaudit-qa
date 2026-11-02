# BUG-03 — Scan não conecta em alvo HTTP puro em porta não-padrão (SCAN-01)

Observado manualmente ao montar a Fase 1 (2026-09-30) e **reconfirmado** em 2026-10-15
na verificação do SCAN-03, que fica bloqueado por ele. Formato conforme
`../template-bug.md`. Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

Scan (passivo e ativo) contra um alvo HTTP puro em porta não-padrão (Juice Shop
`:3000`, WireMock `:8080`) não completa a troca HTTP: volta `httpStatus:0` com
"Erro ao conectar: HTTP/1.1 header parser received no bytes" (e, em outros módulos,
"Unsupported or unrecognized SSL message"). O scanner parece tentar TLS/HTTPS contra
a porta HTTP. Efeito em cascata: módulos de headers/TLS erram e os probes ativos
(XSS, SQLi) rodam mas não têm resposta para analisar.

## Severidade

P0 — derruba a capacidade de escanear alvos HTTP em porta não-padrão, que é o
cenário dos alvos de teste locais. Bloqueia o SCAN-03 (não detecta XSS/SQLi no Juice
Shop) e impacta SCAN-02/04 e SCORE-01.

## Ambiente

- Commit/versão testada: Backend `8d32e2a`
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: manual (curl) na Fase 1; reconfirmado via RestAssured/curl no SCAN-03

## Passos para reproduzir

Autenticado como qa-enterprise (staff, pode scan ativo; juice-shop verificado na conta):

1. `GET /scan?url=http://juice-shop:3000/rest/products/search?q=apple&active=true`
2. Observar o resultado (leva ~2 min).

## Resultado esperado

O scanner faz um GET HTTP no alvo, lê a resposta, e (no caso ativo com query) os
probes detectam o XSS refletido e o erro de SQL do Juice Shop (alvo deliberadamente
vulnerável) → `reflectedXssSuspected:true`, `dbErrorLeakageSuspected:true`.

## Resultado obtido

Scan completa (HTTP 200 no envelope), mas:
- `httpStatus: 0`, `error: "Erro ao conectar: HTTP/1.1 header parser received no bytes"`
- `openPorts: []`
- `xssProbePerformed: true` mas `reflectedXssSuspected: false`
- `dbErrorLeakageSuspected: false`
- `inputSurfaceDetected: true` (o gating por query está correto)

Ou seja: a infra do scan funciona (gating, envelope, probes disparam), mas a
CONEXÃO com o alvo HTTP:3000 nunca entrega bytes HTTP válidos, então não há o que
analisar. Mesmo sintoma contra `wiremock:8080`.

## Caso de teste relacionado

SCAN-01 (a matriz já listava como "bug suspeito"). Bloqueia o SCAN-03. Provável
impacto em SCAN-02 (timeout parcial) e SCAN-04 (falso positivo) e SCORE-01.

## Causa raiz (a investigar no Backend)

Hipótese (da investigação da Fase 1): o cliente HTTP do scanner tenta
TLS/HTTPS mesmo quando a URL de entrada é `http://`, e/ou não trata porta não-padrão
— contra um servidor HTTP puro o handshake falha e o parser não recebe bytes HTTP.
A linha exata (montagem do request/esquema em `ScannerHttp`/motor de fetch) precisa
ser confirmada no Backend. Também vale mockar o `CrtShService` neste ambiente (hoje
consulta crt.sh/certspotter de verdade e soma ~2 min por hostname inexistente).

## Causa raiz (confirmada no fix, 2026-10-29)

A hipótese de TLS estava errada. O motor manda `http://` certo para o fetch; o problema
é a **versão do HTTP**. O `HttpClient` do JDK sai em HTTP/2 por padrão, e num alvo
`http://` isso vira um pedido de upgrade h2c na primeira requisição
(`Connection: Upgrade, HTTP2-Settings` + `Upgrade: h2c`). O Juice Shop (Node com
socket.io, cujo engine.io derruba upgrade que não é dele) **fecha a conexão sem mandar
nenhum byte** — daí o "header parser received no bytes". Reproduzido fora do Spring com o
mesmo cliente: padrão → erro; `.version(HTTP_1_1)` → 200.

O "Unsupported or unrecognized SSL message" é outra coisa, e é esperado: o módulo de TLS
testa `https://juice-shop:3000` de propósito para saber se o alvo tem HTTPS. O WireMock
(Jetty) aceita o upgrade h2c — reproduzindo agora ele respondia 200 em HTTP/2, então o
"mesmo sintoma" relatado ali não se repetiu; depois do fix, o scan nele roda sem módulo
em `ERROR`.

## Correção

Corrigido no Backend em 2026-10-29. `ScannerHttp.newClientBuilder()` devolve um builder
já em HTTP/1.1, e os **22 clientes de 21 módulos** que falam com o alvo passaram a
partir dele (antes, `HttpClient.newBuilder()` em cada um). As 4 APIs externas fixas
(NVD, CT, Mercado Pago, Resend) ficaram como estavam: são HTTPS, o h2 vem por ALPN, sem
upgrade. Motivo de HTTP/1.1 também em `https://` do alvo: o RFC 9113 depreciou o upgrade
h2c, os probes modelam HTTP/1.1 (Host, CRLF, métodos) e um protocolo só deixa o
resultado igual nos dois esquemas.

Verificado ao vivo (container `backend` rebuildado), como qa-enterprise:

| scan | antes | depois |
|---|---|---|
| passivo `http://juice-shop:3000/` | `httpStatus 0` | `httpStatus 200`, nenhum módulo em `ERROR` |
| ativo `http://juice-shop:3000/rest/products/search?q=apple` | `httpStatus 0` | `httpStatus 200`, `inputSurfaceDetected`/`xssProbePerformed` true, nenhum módulo em `ERROR` |
| passivo `http://wiremock:8080/` | — | `httpStatus 403` (resposta real do Jetty na raiz), nenhum módulo em `ERROR` |

`openPorts: []` no Juice Shop é esperado: o port scan procura portas de risco conhecidas
(21, 22, 3306, 6379...) e a 3000 não está na lista.

## O que o fix NÃO resolve — SCAN-03 continua bloqueado

Com a conexão de pé, o ativo no `/rest/products/search?q=apple` ainda dá
`reflectedXssSuspected:false` e `dbErrorLeakageSuspected:false`. São duas coisas
diferentes, nenhuma delas é o BUG-03:

- **SQLi → bug novo, BUG-06.** O probe anexa `'` ao valor (`apple'`) e o Juice Shop
  responde 500 com `SQLITE_ERROR: near "'%'": syntax error` na página. O probe não
  reconhece: a lista de padrões não tem SQLite do Node (só `sqlite3.operationalerror`,
  do Python). Ver `BUG-06-probe-sql-nao-reconhece-sqlite.md`.
- **XSS → expectativa do caso, não bug.** Esse endpoint não ecoa o `q` na resposta (só
  devolve a lista de produtos, vazia para o probe); o XSS de busca do Juice Shop é DOM,
  acontece no navegador. Um probe do lado do servidor dar `false` ali está correto. O
  XSS refletido conhecido do Juice Shop (`/rest/track-order/<id>`) é por caminho, e o
  probe muta o 1º parâmetro de query. O SCAN-03 precisa de outro alvo para a parte de
  XSS (ex.: um stub do WireMock que ecoa o parâmetro sem escapar).

## Teste de regressão

- Backend: `ScannerHttpVersionTest` (novo). Um servidor local que faz o mesmo que o
  Juice Shop (fecha a conexão sem responder a pedido de upgrade) e o fetch principal
  contra ele → antes do fix, o mesmo "header parser received no bytes"; depois, 200. E
  uma varredura dos beans: todo `HttpClient` de módulo que fala com o alvo tem que ser
  HTTP/1.1 — antes do fix listava os 22. Suíte inteira verde (666).
- QA: destrava o SCAN-01 (passivo, módulos OK). SCAN-03 segue `@Disabled` até o BUG-06 e
  a revisão do alvo de XSS. **Pendente (dono, depois da rodada de bugs):** SCAN-01.
```
