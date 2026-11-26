# BUG-23 — Sonda que não responde a tempo é lida como "não encontrado", e o módulo sai OK

Achado lateral em 2026-11-26, na conferência ao vivo do SCAN-02 (caso novo da fila, ficha
28), com mapeamentos temporários do WireMock. **🔲 Aberto.** Formato conforme
`../template-bug.md`.

```markdown
## Resumo

Quando o `security.txt` ou o `robots.txt` do alvo demora mais que o timeout da sonda, o
scan conclui que o arquivo não existe:
- `security.txt` que existe, com `Contact`, sai "security.txt ausente: -1" e
  `SECURITY_TXT_MISSING` (LOW);
- `robots.txt` com caminho sensível no `Disallow` sai sem `SENSITIVE_ROBOTS_PATHS`.

Nos dois, o `moduleStatus` do módulo continua `OK`, e o aviso "Resultado parcial" não cita
o módulo. O laudo afirma um fato ("ausente", "nada sensível") sobre um arquivo que o scan
não conseguiu ler.

É o mesmo padrão do BUG-16, que corrigiu isso para o CT e o CVE (serviço de fora sem
resposta → `ERROR`). O `security.txt` e o `robots.txt` ficaram com a regra antiga.

## Severidade

P2. O erro no `security.txt` custa 1 ponto e mostra um achado LOW falso. O do `robots.txt`
é falso negativo: o achado some, e o módulo diz que verificou. Nos dois, o cliente lê como
verificado o que não foi.

## Ambiente

- Commit/versão testada: Backend `11fd610`
- Ambiente: docker-compose de teste, backend 8081, alvos `limpo-mock` e `pesos-mock`
  (WireMock local). Nunca produção.
- Ferramenta que encontrou: conferência ao vivo, com mapeamento temporário do WireMock
  (`POST /__admin/mappings`, desfeito com `POST /__admin/mappings/reset`).

## Passos para reproduzir

security.txt:
1. Mapeamento temporário: `GET /.well-known/security.txt` no Host `limpo-mock`, `200`,
   corpo `Contact: mailto:security@limpo-mock.test`, `fixedDelayMilliseconds` 7000
   (o timeout da sonda é 5 s).
2. `GET /scan?url=http://limpo-mock:8080/&active=false`, qa-enterprise, pt-BR.

robots.txt:
1. Mapeamento temporário: `GET /robots.txt` no Host `pesos-mock`, `200`, corpo
   `User-agent: *` / `Disallow: /admin/`, `fixedDelayMilliseconds` 10000 (o timeout é 8 s).
2. `GET /scan?url=http://pesos-mock:8080/&active=false`, qa-enterprise, pt-BR.

## Resultado esperado

O módulo que não conseguiu ler o arquivo sai `ERROR` (ou `TIMEOUT`), entra no aviso de
resultado parcial e não gera achado nem desconto sobre o arquivo. É o que o BUG-16
decidiu para o CT e o CVE.

## Resultado obtido

- security.txt: `securityTxtPresent` false, `SECURITY_TXT_MISSING` LOW, nota
  "security.txt ausente: -1" (64 em vez de 65), `moduleStatus.SECURITY_TXT` = `OK`.
  Sem o atraso, o mesmo alvo dá `securityTxtPresent` true e 65.
- robots.txt: sem `SENSITIVE_ROBOTS_PATHS` (50 em vez de 48), `moduleStatus.ROBOTS` =
  `OK`. Sem o atraso: `SENSITIVE_ROBOTS_PATHS` LOW, "Paths sensíveis em robots.txt: -2".

## Caso de teste relacionado

SCAN-02 (alvo com atraso forçado). A ficha 28 cobre o atraso na raiz, que já sai `ERROR`.
Depois do fix, o atraso no `security.txt` e no `robots.txt` vira parte do SCAN-02.

## Causa raiz (preencher após investigar)

Leitura do código, ainda sem fix:
- `SecurityTxtService.probe` pega qualquer exceção e devolve
  `SecurityTxtResult(false, null, null)`, a mesma resposta de um 404.
- `RobotsTxtService.fetchRobots` pega qualquer exceção e devolve `null`, que o `check`
  transforma em `RobotsTxtResult.ausente()`.
- No `ScanOrchestrator`, os dois usam `moduleState(future)`, que diz `OK` para todo
  future terminado. O `moduleStateVerificado` do BUG-16 só foi aplicado ao CT e ao CVE.

Vale procurar a terceira cópia antes de corrigir: outras sondas passivas também
transformam exceção em lista vazia (`exceptionally(e -> List.of())` e `catch` internos:
métodos HTTP, directory listing, source maps, API docs, GraphQL). Não reproduzi essas.

## Correção

Pendente. Pede decisão do dono: o que o laudo diz quando a sonda não respondeu.

## Teste de regressão

Pendente.
```
