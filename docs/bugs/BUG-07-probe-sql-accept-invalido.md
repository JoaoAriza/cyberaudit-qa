# BUG-07 — Probe de erro de SQL manda `Accept` inválido e alvo em Spring recusa antes de rodar a consulta

Encontrado em 2026-10-31 pelo Claude no fix do BUG-06 (anotado lá como "observação
lateral, irrelevante no Juice Shop") e reclassificado como bug em 2026-11-02, ao testar
o mesmo cabeçalho contra um alvo em Spring. Confirmado ao vivo contra o ambiente de
teste. Formato conforme `../template-bug.md`. Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

O probe ativo de vazamento de erro de banco (`ErrorDisclosureService.detectsDbErrorLeakage`)
manda `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,/;q=0.8` — o
último item era para ser `*/*`. `/` sozinho não é um tipo de mídia válido. O Spring MVC lê
o `Accept` inteiro e, com um item inválido, recusa a requisição **antes do controller**:
a consulta com a aspa nem roda, e o probe nunca vê erro de SQL num alvo feito em Spring.

## Severidade

P1 — falso negativo sistemático num check de segurança ativo para uma família inteira de
alvos (Spring/Spring Boot, um dos stacks mais comuns em sistemas corporativos). Não é P0:
não expõe nada nem quebra o scan, e alvos que toleram o cabeçalho (Node/Express, como o
Juice Shop) continuam sendo testados.

## Ambiente

- Commit/versão testada: Backend `ac958ca`
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: revisão de código (no fix do BUG-06) + manual (curl)

## Passos para reproduzir

O próprio backend do CyberAudit (porta 8081) serve de alvo em Spring:

1. `GET /scan/verify-check?host=example.com` com
   `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8` → **200**
   (controle: mesmo cabeçalho, com o curinga certo).
2. A mesma requisição com o `Accept` do probe (`...,/;q=0.8`) → **401**, e no log do
   backend: `HttpMediaTypeNotAcceptableException: Could not parse 'Accept' header [...]:
   Invalid mime type "/;q=0.8": does not contain subtype after '/'`.
   (401 porque o Spring despacha o erro para `/error`, que exige login; num Spring sem
   segurança é 406. Nos dois casos, o controller não roda.)
3. Contraste: o Juice Shop (Node) com o mesmo cabeçalho em `?q=apple'` → 500 com o
   `SQLITE_ERROR` — por isso o BUG-06 passou ao vivo e este ficou escondido.

## Resultado esperado

O probe manda um `Accept` válido; o alvo processa a requisição mutada e, se a consulta
quebrar, o erro de SQL aparece na resposta para o probe analisar.

## Resultado obtido

Num alvo em Spring, toda requisição do probe é recusada no parse do cabeçalho
(406/401), a consulta não roda, e o resultado é sempre `dbErrorLeakageSuspected:false`.

## Caso de teste relacionado

Nenhum caso da matriz pega isso hoje: o alvo de SQLi do SCAN-03 é o Juice Shop (Node),
que tolera o cabeçalho. Fica coberto pela regressão no Backend.

## Causa raiz

Erro de digitação no literal do cabeçalho em `ErrorDisclosureService` (`/` no lugar de
`*/*`). Os outros módulos que mandam `Accept` usam valores válidos (o fetch principal já
manda `*/*;q=0.8`).

## Correção

Corrigido no Backend em 2026-11-02: o literal passou a `...,application/xml;q=0.9,*/*;q=0.8`
(um caractere), com um comentário dizendo por que o `*/*` importa.

Verificado ao vivo (container `backend` rebuildado): o mesmo cabeçalho, agora válido,
passa no parse do Spring (passo 1 dos "Passos para reproduzir" → 200); e o ativo no Juice
Shop continua `dbErrorLeakageSuspected:true` (sem regressão do BUG-06). Um scan de um alvo
Spring de verdade não dá para mostrar no ambiente: o único Spring ali é o próprio backend,
que o `SsrfGuard` não deixa escanear — a prova fica na regressão abaixo.

## Teste de regressão

- Backend: `ErrorDisclosureServiceTest` ganhou `alvoQueValidaOAcceptRecebeOProbe`. O
  servidor local passa a ler o `Accept` com `MediaType.parseMediaTypes`, o mesmo parser
  que o Spring MVC usa, e responde 406 sem rodar a "consulta" quando um item é inválido.
  Antes do fix: `false` (406); depois: `true`, e a requisição mutada chega ao
  "controller". Suíte inteira verde (682).
- QA: nenhum caso da matriz cobre (o alvo de SQLi do SCAN-03 é Node). Um caso de SQLi
  contra um alvo Spring exigiria subir um app Spring vulnerável no ambiente — fica como
  ideia, não está na fila.
```
