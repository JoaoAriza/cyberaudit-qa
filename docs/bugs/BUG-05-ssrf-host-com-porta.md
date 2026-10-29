# BUG-05 — Host com porta (ou delimitador de URL) escapa do guard anti-SSRF no `/scan/verify-check`

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

## Variantes encontradas no fix (mesma causa)

A porta era só uma das formas. Qualquer coisa que o `InetAddress` não resolve, mas que o
`URI` (depois do `trim()` do `DomainProtectionService`) reduz a um host interno, passava:

| host enviado | guard (antes) | `URI` conectava em |
|---|---|---|
| `127.0.0.1:8081` | passa | 127.0.0.1:8081 |
| `127.0.0.1 ` (espaço) | passa | 127.0.0.1 |
| `127.0.0.1/`, `127.0.0.1#`, `127.0.0.1?` | passa | 127.0.0.1 |
| `[::1]:443` | passa | ::1 |
| `x@<hostname que resolve para IP privado>` | passa | o IP privado |

(`x@127.0.0.1` já dava 403 por acaso: o fix do BUG-02 recusa esse texto como IP inválido.)

## Correção

Corrigido no Backend em 2026-10-29, em duas camadas:

1. **`SsrfGuard.validateHost`** apara espaço nas pontas, recusa delimitadores de URL
   (`@ / ? # \`, espaço e caracteres de controle no meio) e **separa a porta**
   (`host:porta`, `[ipv6]:porta`; porta não numérica → 403) antes das checagens. A
   porta em host público continua aceita: domínio verificado pode ter porta
   (`juice-shop:3000`, que agora passa pela allowlist de teste em vez de pelo buraco).
2. **`DomainProtectionService.isOwnershipVerified`** valida a **URL montada** com
   `SsrfGuard.validate(url)` — a que o cliente realmente requisita — em vez do
   parâmetro cru. Validar o que se conecta fecha a classe, não só as variantes conhecidas.

Verificado ao vivo (container `backend` rebuildado): as 9 variantes da tabela/passos →
**403**; controles `example.com:443` e `juice-shop:3000` → 200 (não bloqueados).

## Teste de regressão

- Backend: `DomainProtectionServiceTest` ganhou um teste que sobe um `ServerSocket` em
  127.0.0.1 fazendo papel de serviço interno e prova que **nenhuma conexão chega** a ele
  (5 variantes; antes do fix as 5 conectavam). `SsrfGuardTest` ganhou 19 casos (15
  recusas, 3 aceites de host público com porta, allowlist casando sem a porta). Suíte
  inteira verde (664).
- QA: suíte RestAssured inteira rodada sem alteração → 44 rodados, 0 falhas, 2 pulados
  (os `@Disabled` do SSRF-03 hex e do SCAN-03). **Pendente (dono):** SSRF-07.
```
