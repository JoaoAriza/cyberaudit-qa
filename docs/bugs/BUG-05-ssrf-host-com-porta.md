# BUG-05 — Host com porta escapa do guard anti-SSRF no `/scan/verify-check`

Encontrado em 2026-10-29 pelo Claude ao revisar o `SsrfGuard` durante o fix do BUG-02.
Confirmado ao vivo contra o ambiente de teste. Formato conforme `../template-bug.md`.
Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

`SsrfGuard.validateHost` assume host SEM porta. Um host no formato `ip:porta` ou
`nome:porta` (`127.0.0.1:8081`, `localhost:8081`, `169.254.169.254:80`) não casa com
nenhuma checagem e passa. O endpoint **público** `GET /scan/verify-check?host=...` então
faz uma requisição HTTPS de saída para esse destino interno.

## Severidade

P1 — SSRF cego, sem autenticação (`/scan/verify-check` é `permitAll`). O atacante
escolhe IP interno e porta; o servidor faz `GET https://<host>:<porta>/.well-known/cyberaudit.txt`.
O corpo da resposta não volta (só `verified:true/false`), redirects não são seguidos e
o timeout é 5s — então é um oráculo de varredura de rede interna (porta aberta x
fechada x sem rota, por tempo/erro), não leitura de dados. Mitigado pelo rate-limit do
endpoint (429 depois de poucas chamadas). Não é P0 porque não lê resposta nem alcança
HTTP puro (sempre `https://`).

## Ambiente

- Commit/versão testada: Backend `05ef07f`
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: revisão de código + manual (curl)

## Passos para reproduzir

Sem token (o endpoint é público):

1. `GET /scan/verify-check?host=127.0.0.1`          → 403 DOMAIN_BLOCKED (controle)
2. `GET /scan/verify-check?host=127.0.0.1:8081`     → **200**
3. `GET /scan/verify-check?host=localhost`          → 403 DOMAIN_BLOCKED (controle)
4. `GET /scan/verify-check?host=localhost:8081`     → **200**
5. `GET /scan/verify-check?host=169.254.169.254:80` → **200**

## Resultado esperado

Com ou sem porta, destino interno é recusado: 403 `DOMAIN_BLOCKED` nos cinco.

## Resultado obtido

Os três com porta → 200 `{"host":"<host>","verified":false,"message":"Arquivo não
encontrado ou token inválido."}`, ou seja, o guard deixou passar e o backend tentou
buscar o arquivo no destino interno. Os mesmos hosts sem porta → 403.

## Caso de teste relacionado

SSRF-07 (novo na matriz): host com porta no `/scan/verify-check` → 403.

## Causa raiz

`validateHost(host)` recebe o valor cru do parâmetro, com a porta junto:
- a checagem de sufixo (`localhost`, `.internal`...) usa `endsWith` — `localhost:8081`
  termina em `:8081`, não em `localhost`;
- `InetAddress.getAllByName("127.0.0.1:8081")` vê `:` e tenta ler como IPv6 →
  `UnknownHostException` → o guard cai no `catch` que **retorna sem lançar** (por
  design, "host que não resolve não é alvo acessível");
- `DomainProtectionService.isOwnershipVerified` monta `"https://" + host + "/.well-known/..."`
  e aí o `URI` separa host e porta normalmente → conecta em `127.0.0.1:8081`.

Mesma família do BUG-02: o guard e o cliente HTTP leem o mesmo texto de jeitos
diferentes, e o "não resolveu → deixa passar" transforma a divergência em bypass.

Correção possível (a decidir no Backend): separar a porta no `validateHost` antes das
checagens (cuidando de `[::1]:443`), e/ou validar com `SsrfGuard.validate(url)` a URL
que o `DomainProtectionService` realmente vai requisitar.

## Correção

(pendente — fix no Backend)

## Teste de regressão

(pendente — SSRF-07 em RestAssured: os três hosts com porta → 403, com os controles
sem porta → 403 e um host público com porta → não-403)
```
