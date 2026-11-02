# BUG-06 — Probe de erro de SQL não reconhece SQLite do Node (`SQLITE_ERROR`) (SCAN-03)

Encontrado em 2026-10-29 pelo Claude verificando ao vivo o fix do BUG-03: com a conexão
de pé, o scan ativo no Juice Shop continuou sem detectar o vazamento de erro de SQL.
Confirmado ao vivo contra o ambiente de teste. Formato conforme `../template-bug.md`.
Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

O probe ativo de vazamento de erro de banco (`ErrorDisclosureService.detectsDbErrorLeakage`)
não reconhece a mensagem de erro do SQLite usada por apps Node (`SQLITE_ERROR: ...`).
Contra o Juice Shop, que devolve essa mensagem na página de erro, o scan dá
`dbErrorLeakageSuspected:false` — falso negativo.

## Severidade

P1 — falso negativo num check de segurança ativo: o alvo vaza erro de SQL (sinal forte
de injeção) e o relatório diz que não. Não é P0 porque não expõe nada nem quebra o scan;
o módulo roda, só não reconhece essa família de banco. Bloqueia a parte de SQLi do
SCAN-03.

## Ambiente

- Commit/versão testada: Backend `aa1b715` + fix do BUG-03 (cliente em HTTP/1.1)
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: manual (curl) + leitura do código do probe

## Passos para reproduzir

Autenticado como qa-enterprise (pode scan ativo; juice-shop verificado na conta):

1. `GET /scan?url=http://juice-shop:3000/rest/products/search?q=apple&active=true`
   → `httpStatus:200`, `dbErrorLeakageSuspected:false`.
2. Fazer na mão o que o probe faz (anexa `'` ao 1º parâmetro):
   `GET http://juice-shop:3000/rest/products/search?q=apple'` (porta 3001 no host)
   → **500**, página HTML com `Error: SQLITE_ERROR: near "'%'": syntax error`.

## Resultado esperado

O probe vê a mensagem de erro do banco na resposta da requisição mutada →
`dbErrorLeakageSuspected:true`.

## Resultado obtido

`dbErrorLeakageSuspected:false`. A requisição mutada chega e a resposta tem o erro, mas
nenhum padrão casa.

## Caso de teste relacionado

SCAN-03 (varredura ativa no Juice Shop → SQLi detectado). Junto com a revisão do alvo de
XSS (ver BUG-03, seção "O que o fix NÃO resolve"), é o que falta para reativar o SCAN-03.

## Causa raiz

`containsDbErrorPatterns` compara o corpo (em minúsculas) com uma lista fixa de
mensagens: MySQL, SQL Server, Postgres, Oracle, ODBC, JDBC e `sqlite3.operationalerror`
(a exceção do **Python**). Não há o código de erro do SQLite em si (`sqlite_error`, que é
o que o driver `sqlite3` do Node, o Sequelize e outros clientes C/JS mostram). O payload
está certo (anexar `'` ao valor quebra a consulta do Juice Shop); só a detecção falha.

Detalhe que importa para o fix: `'` sozinho NÃO quebra essa consulta — o SQLite lê
`'%'%' ... '%'%'` como uma conta válida e devolve 200. É anexando ao valor original
(`apple'`), que é o que o probe já faz, que vem o erro.

## Correção

Corrigido no Backend em 2026-11-01. A lista de padrões ganhou o SQLite como cada driver
o imprime — o banco é o mesmo, o nome do erro muda:

| padrão | de onde vem |
|---|---|
| `sqlite_error` | código de erro do próprio SQLite: Node (`sqlite3`, o do Juice Shop), Java (sqlite-jdbc) |
| `sqliteerror` | Node, `better-sqlite3` |
| `sqliteexception` | Java, .NET (`Microsoft.Data.Sqlite`, `System.Data.SQLite`), Android |
| `sqlite3::` | Ruby (`SQLite3::SQLException`), PHP (`SQLite3::query()`) |

`sqlite3.operationalerror` (Python) continua. O corpo passou a ir para minúsculas com
`Locale.ROOT`: no locale turco o "I" de `SQLITE_ERROR` viraria "ı" e não casaria.

Verificado ao vivo (container `backend` rebuildado), como qa-enterprise: ativo em
`http://juice-shop:3000/rest/products/search?q=apple` → `dbErrorLeakageSuspected:true`
(antes `false`); a nota caiu de 37 para 22, agora contando o achado. Passivo inalterado.

Observação lateral, fora deste fix: o probe manda `Accept: text/html,...,/;q=0.8` — o
`/` era para ser `*/*`. Não muda nada no Juice Shop (ele responde HTML), mas uma API que
só serve JSON e respeita o `Accept` à risca pode responder 406 em vez do erro.

## Teste de regressão

- Backend: `ErrorDisclosureServiceTest` (novo). Servidor local que responde o erro só
  quando o valor termina em `'`, como o Juice Shop; um caso por driver (Node `sqlite3`
  com o HTML escapado do Express, `better-sqlite3`, sqlite-jdbc, `Microsoft.Data.Sqlite`,
  Ruby, PHP) — os 6 falhavam antes do fix —, mais o controle do Python (já passava) e
  "sem erro de banco → sem achado". Também confere que a aspa é anexada ao valor
  (`apple'`). Suíte inteira verde (674).
- QA: libera a parte de SQLi do SCAN-03. O SCAN-03 só sai do `@Disabled` com a parte de
  XSS num alvo que ecoe o parâmetro (ver BUG-03, "O que o fix NÃO resolve").
```
