# BUG-17 — Cache de scan ignora caminho e query (e a conta de quem pediu)

Achado lateral em 2026-11-15, ao preparar a ficha do SCAN-03. **Aberto, não corrigido.**
Formato conforme `../template-bug.md`.

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

## Resultado obtido

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

A fazer. Decisões para o dono:

- Caminho e query na chave: em todo modo, ou só no ativo (no passivo os módulos olham o
  host, mas `inputSurfaceDetected` e os headers são da página pedida)?
- Conta: o cache compartilhado entre contas é de propósito (o gating de plano faz cópia
  não destrutiva por causa dele). Se continuar compartilhado, o hit precisa gravar o
  histórico de quem pediu e recalcular as `changes` com a conta dele.

## Teste de regressão

A escrever junto com a correção. Caso de QA candidato: ativo em `/` e depois na busca, em
menos de 2 min → o segundo com a `url` pedida e o SQLi.
```

## O que muda nas fichas enquanto não corrige

- **SCAN-03:** afirme que `url` é a pedida antes das flags de sonda. Se outro teste da mesma
  execução escanear o Juice Shop em modo ativo noutro caminho até 2 min antes, o SCAN-03
  pega o resultado dele.
- **Todos os SCAN-*:** o cache dura **2 minutos** (não "até reiniciar o backend", como o
  HANDOFF dizia) e a chave inclui o idioma. Rodar o mesmo host em pt-BR e em en não pede
  reinício.
