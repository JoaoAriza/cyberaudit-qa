# BUG-16 — Host que não responde ganha nota, e módulo que não verificou nada sai "OK"

Achado lateral em 2026-11-15, ao preparar as fichas da fila do dono (conferindo o SCAN-01 e
o UI-02). **Aberto, não corrigido** (regra da rodada: achado lateral vira bug próprio).
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

Lida no código, a confirmar no fix:

- `ScanOrchestrator`: depois de `fetchHeaders` com erro, o fluxo grava `HTTP_FETCH=ERROR` e
  continua; o score é calculado do mesmo jeito.
- Os módulos engolem a falha de rede como "nada encontrado" (`catch → List.of()` /
  `false`), e `moduleState(future)` só diz `ERROR` quando o future termina com exceção. No
  `CVECorrelationService`, `.timeout(12 s)` e `catch (Exception e) { return List.of(); }`,
  sem log.
- `HttpFetchService.fetchHeaders` monta "Erro ao conectar: " + `e.getMessage()`, que é nulo
  para a falha de resolução e de conexão do `HttpClient`.

## Correção

A fazer. Decisões para o dono antes do fix:

- Host que não resolve: recusar logo na entrada (o `SsrfGuard` já resolve o host) ou
  devolver resultado sem nota?
- Host que resolve mas não responde: laudo parcial sem nota, ou nota só dos módulos que
  verificaram (DNS de email, por exemplo)?
- Módulos com dependência externa (NVD, crt.sh/certspotter): devolver "não verificado" em
  vez de lista vazia, para o `moduleStatus` sair `ERROR`.

## Teste de regressão

A escrever junto com a correção. Caso de QA candidato: um host `.invalid` → sem nota (e o
UI-02 corrigido passa a pegar isso).
```

## O que muda nas fichas enquanto não corrige

- **SCAN-01:** "todo módulo OK" não prova contato com o alvo; o host inexistente também dá
  14 OK. A prova é `httpStatus` 200, que precisa ser afirmado junto.
- **SCAN-05:** a presença do `CVE-2007-3205` é a única prova de que o NVD respondeu. Sem ela,
  a negativa ("sem CVE-2013-2220") passa à toa.
