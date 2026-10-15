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

## Correção

(pendente — fix no Backend)

## Teste de regressão

(pendente — SCAN-03 em RestAssured: hoje `@Disabled` esperando
`reflectedXssSuspected:true` + `dbErrorLeakageSuspected:true`; reativa quando o motor
conectar no alvo HTTP:3000. Fecha a issue quando o SCAN-03 passar.)
```
