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

Corrigido no Backend em 2026-10-29: `SsrfGuard.validateHost` passou a **recusar IPv4
fora da forma canônica** `a.b.c.d` decimal, antes de resolver. Um host cujo último
rótulo é numérico (regra "ends in a number" da especificação de URL do WHATWG, a que
navegadores e curl seguem) só passa se for exatamente quatro octetos decimais, sem
zero à esquerda. Qualquer outra forma (hex, octal, inteiro, curta, cinco partes,
estouro de 32 bits) → 403 `DOMAIN_BLOCKED`, com a mensagem dizendo a que endereço ela
equivale (`'0x7f000001' ... (equivale a 127.0.0.1)`).

Por que recusar em vez de normalizar: na revisão apareceu uma segunda divergência, mais
séria que o hex. O JDK lê `0177.0.0.1` como **177.0.0.1** (público, passava no guard),
enquanto navegador e curl leem **127.0.0.1** (octal). Validar com um parser e conectar
com outro é a receita clássica de SSRF. Recusar a forma ambígua elimina a divergência
inteira, inclusive para destino público (`134744072` = 8.8.8.8 também dá 403 agora;
nenhum alvo legítimo é escrito assim).

Verificado ao vivo (container `backend` rebuildado): `2130706433`, `127.1`,
`0x7f000001`, `0177.0.0.1`, `0x7f.0.0.1` → todos **403**.

## Teste de regressão

- Backend: `SsrfGuardTest` ganhou 18 casos (hex inteiro/por octeto/maiúsculo/com zeros
  à esquerda, octal inteiro/por octeto, inteiro decimal, curta, cinco partes, estouro;
  mensagem com o equivalente; e os controles: `8.8.8.8` e hostnames com rótulo numérico
  no meio continuam aceitos). 11 falhavam antes do fix; suíte inteira verde (640).
- QA: `SsrfGuardTest` do RestAssured rodado sem alteração → 15 verdes, 1 pulado (o hex
  `@Disabled`). **Pendente (dono):** tirar o `@Disabled` do hex; a issue fecha quando
  ele passar.
```
