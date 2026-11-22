# BUG-17 — Cache de scan ignora caminho e query (e a conta de quem pediu)

Achado lateral em 2026-11-15, ao preparar a ficha do SCAN-03. **✅ Corrigido em 2026-11-18**
(HANDOFF §12, Etapa J). Formato conforme `../template-bug.md`.

```markdown
## Resumo

A chave do cache de scan é host + modo + idioma; caminho e query ficam de fora. Durante 2
minutos, um scan de outro endereço do mesmo host devolve o resultado do primeiro. No scan
ativo isso esconde achado: as sondas de XSS e SQL rodam no parâmetro da URL, e o resultado
que volta é de uma URL sem parâmetro.

A conta também fica de fora. Outra conta recebe o resultado em milissegundos, mas o scan não
entra no histórico dela. E o resultado guardado traz as `changes` calculadas contra o
histórico de quem pediu primeiro.

## Severidade

P1 — falso negativo no modo ativo, no fluxo natural "escaneia a home, depois a página de
busca". A janela é de 2 minutos (`expireAfterWrite`).

## Ambiente

- Commit/versão testada: Backend `d0f9594`
- Ambiente: docker-compose de teste, backend 8081; alvo `juice-shop` (local). Nunca produção.
- Ferramenta que encontrou: revisão do contrato do SCAN-03

## Passos para reproduzir

1. qa-enterprise, ativo: `GET /scan?url=http://juice-shop:3000/&active=true`.
2. Em menos de 2 min, ativo:
   `GET /scan?url=http://juice-shop:3000/rest/products/search?q=apple&active=true`.
3. qa-enterprise, passivo, `http://juice-shop:3000/`; em seguida o qa-pro, passivo, o mesmo
   endereço. Contar as linhas de `scan_records` de cada conta antes e depois.

## Resultado esperado

2. O scan roda na URL pedida: `url` termina em `search?q=apple` e
   `dbErrorLeakageSuspected` é true (BUG-06).
3. O scan do qa-pro aparece no histórico do qa-pro.

## Resultado obtido (antes do fix)

2. Volta em **9 ms** com `url` `http://juice-shop:3000/`, `inputSurfaceDetected` false,
   `dbErrorLeakageSuspected` false. Sozinho, depois de o cache expirar: `url` certa e
   `dbErrorLeakageSuspected` true.
3. O qa-pro recebe em 9 ms o resultado do qa-enterprise. Histórico: o do qa-enterprise vai
   de 2 para 3 linhas, o do qa-pro fica em 3.

## Causa raiz

`ScanOrchestrator.buildCacheKey` usa `extractHostSafe(url)`, que dá só o host, desde a criação
do cache (2026-05-07). Depois a detecção de mudanças passou a comparar pelo caminho
(`findLastResult(host, caminhoDe(inputUrl), …)`, "comparar o /login com a home gerava
mudanças"), mas a chave do cache não acompanhou.

O cache hit sai antes de `runScan`, que é quem grava o histórico e calcula as `changes`
com a conta de quem pediu.

## Correção

Decisões do dono (2026-11-18):

- **Caminho e query entram na chave em todo modo.** O passivo também busca a página
  pedida: headers, cookies e tecnologia vêm dela.
- **A conta entra na chave.** Membros da mesma conta dividem a entrada, porque o histórico
  é da conta. Visitantes dividem uma entre si; eles não têm histórico.

`buildCacheKey` passa a ser `v4:scan:<conta ou guest>:<endereço>:active=…:lang=…`. O endereço
é esquema + host + porta + caminho + query. O caminho é normalizado como no histórico
(`normalizarCaminho`: com e sem barra no fim são o mesmo), e o fragmento `#` fica de fora.
A porta também não entrava antes: `juice-shop:3000` e `juice-shop:8080` dividiam a entrada.

Efeito lido no código e não reproduzido ao vivo: no ativo, o acerto de outra conta pulava a
prova de posse por arquivo, que mora no `runScan`. Continuava valendo a trava do controller
(domínio verificado na conta de quem pede). O seed não tem duas contas com o mesmo domínio
verificado, então não deu para mostrar. Com a conta na chave, o acerto só serve a quem já
passou pela prova.

## Teste de regressão

- Backend: `ScanCacheChaveIntegrationTest`, com 7 testes, 5 vermelhos contra o código antigo.
  Nenhum scan de verdade roda. O `SSLService`, primeiro passo do scan, é um mock que lança
  uma exceção de sinal. Então "lançou" quer dizer "o cache não acertou e o scan começou". Um
  espião no `ScanCacheService` captura a chave consultada. Os testes cobrem:
  - a home guardada: a mesma conta na home acerta; a busca e outra conta rodam o scan;
  - caminho, query, porta e conta mudam a chave; visitante não divide com conta;
  - controles: sem barra, com barra e com `#` dão a mesma chave; membros da mesma conta e
    visitantes dividem.
- Suíte do Backend: 799 → 806, verde.
- Ao vivo (8081):

  | Passo | Antes | Depois |
  |---|---|---|
  | 2. Ativo na busca, logo depois da home | 0,23 s; `url` da home; `dbErrorLeakageSuspected` false | 3 s; `url` termina em `search?q=apple`; `inputSurfaceDetected` e `dbErrorLeakageSuspected` true |
  | 4. qa-pro na home, logo depois do qa-enterprise | 0,24 s; histórico do qa-pro 4 → 4 | 2,4 s; histórico do qa-pro 4 → 5 |
  | 5. Controle: o qa-pro de novo na home | 0,23 s (acerto) | 0,23 s (acerto) |

  RestAssured do dono: 44 / 0 falhas / 2 pulados.
- Caso de QA: **SCAN-12** (P1, 🔲): contrato no HANDOFF §12, Etapa J; ficha 17.
```

## O que mudou nas fichas com o fix

- **SCAN-03:** a armadilha "outro teste no Juice Shop em modo ativo noutro caminho até 2 min
  antes entrega o resultado dele" deixou de valer. Afirmar a `url` pedida continua sendo uma
  boa primeira asserção.
- **Todos os SCAN-*:** o cache segue com **2 minutos** e com o idioma na chave. Agora ele
  também separa por endereço e por conta. Repetir o **mesmo** endereço com a **mesma** conta
  em menos de 2 min ainda devolve o resultado guardado.
