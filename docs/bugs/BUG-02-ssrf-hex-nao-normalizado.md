# BUG-02 — SSRF guard não normaliza IPv4 em hexadecimal

Encontrado em 2026-10-11 pela QA (cyberaudit-qa) ao verificar o contrato do SSRF-03.
Formato conforme `../template-bug.md`. Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

O guard anti-SSRF rejeita formas alternativas de `127.0.0.1` em decimal
(`2130706433`) e curta (`127.1`), mas NÃO rejeita a forma hexadecimal
(`0x7f000001`): o scan é aceito (200) em vez de recusado (403).

## Severidade

P1 — gap de normalização no guard anti-SSRF (caso SSRF-03, P0, é parcialmente
coberto). Não é exploração confirmada: o `0x7f000001` não é resolvido pelo
`InetAddress` do Java, então a conexão também falha e NÃO alcança o 127.0.0.1
(scan volta com `httpStatus:0`). Mas é defesa-em-profundidade furada — o guard
devia recusar formas ambíguas de endereço em vez de deixá-las passar como host.

## Ambiente

- Commit/versão testada: Backend `8d32e2a`
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: RestAssured/manual (curl) na verificação do SSRF-03

## Passos para reproduzir

Autenticado (token de sessão qualquer), scan passivo:

1. `GET /scan?url=http://2130706433&active=false`  → 403 DOMAIN_BLOCKED
2. `GET /scan?url=http://127.1&active=false`        → 403 DOMAIN_BLOCKED
3. `GET /scan?url=http://0x7f000001&active=false`   → **200** (não bloqueado)

## Resultado esperado

As três formas são `127.0.0.1` reescrito; todas deveriam ser recusadas com
403 `DOMAIN_BLOCKED`, como as outras variações do SSRF-03.

## Resultado obtido

- decimal `2130706433` → 403 `{"error":"DOMAIN_BLOCKED"}`
- curta `127.1`        → 403 `{"error":"DOMAIN_BLOCKED"}`
- hex `0x7f000001`     → **200**, com `httpStatus:0`, `analyzedHost:"0x7f000001"`,
  `headers.error`/`"error":"Erro ao conectar: null"` — o scanner tratou `0x7f000001`
  como hostname, não resolveu, e seguiu em vez de recusar.

## Caso de teste relacionado

SSRF-03 (matriz `casos-de-teste.md`). O caso cobre as formas decimal e curta (403);
a forma hex fica como known issue até o fix. O teste de regressão do hex só vira
verde (esperando 403) depois da correção.

## Causa raiz

`SsrfGuard.validateHost` resolve o host via `InetAddress.getAllByName(normalized)`.
O `InetAddress` do Java converte as formas decimal (`2130706433`) e curta (`127.1`)
para `127.0.0.1` → `isForbidden` → 403. Mas NÃO interpreta o literal hex
`0x7f000001` como IPv4: trata como nome, `getAllByName` lança `UnknownHostException`,
o guard cai no `catch` que retorna sem lançar (deixa o fluxo seguir, por design, para
hosts que não resolvem) → o scan roda normalmente. Ou seja, o hex escapa da checagem
de IP interno porque nunca chega a ser reconhecido como IP.

Correção possível (a decidir no Backend): normalizar/parsear o host para IPv4
canônico ANTES do `getAllByName` (cobrir `0x...`, octal, e misturas), ou recusar
hosts com formato numérico não-decimal-pontuado.

## Correção

(pendente — fix no Backend)

## Teste de regressão

(pendente — SSRF-03 em RestAssured: hoje documenta decimal/curto → 403; quando o fix
entrar, `0x7f000001` passa a esperar 403 e fecha a issue)
```
