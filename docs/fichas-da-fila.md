# Fichas da fila do dono

Uma ficha por caso da fila (HANDOFF §10, "Fila do dono"), na ordem sugerida para escrever.
**Todos os contratos foram conferidos ao vivo em 2026-11-15** (Backend `d0f9594`, Frontend
`c7006bc`, docker-compose de teste; só alvos locais, mais o NVD do SCAN-05). As etapas A–G
mudaram pesos e textos, e o que mudou está anotado em cada ficha.

É planejamento, não código (Regra 3). Cada ficha diz o que o caso prova e por quê, o que
preparar, o que pedir, o que afirmar, onde ele engana e como ver o teste falhar. O teste,
quem escreve é o dono. Os nomes de classe são sugestões.

## 0. Antes de começar (vale para todas)

**Ambiente.**
- O stub novo do SCAN-03 (`env/wiremock/mappings/eco.json`) e os aliases `eco-mock` e
  `eco-escapado-mock` só valem com os containers recriados:
  `docker compose -f env/docker-compose.test.yml up -d wiremock backend`.
- O stub do SCAN-14 (`env/wiremock/mappings/cookies-csrf.json`, `cookies-mock:8080/so-csrf`)
  só vale com o WireMock recarregado: `curl -X POST http://localhost:8082/__admin/mappings/reset`.
  O do SCAN-15 (`env/wiremock/mappings/cookies-nomes.json`, `cookies-mock:8080/nomes`) e o do
  SCAN-16 (`env/wiremock/mappings/cookies-analytics.json`, `cookies-mock:8080/analytics`) também.
- Depois de reiniciar o Docker, `juice-shop` e `backend-noallowlist` não sobem sozinhos:
  `docker compose -f env/docker-compose.test.yml up -d --no-build juice-shop backend-noallowlist`.

**Ver o vermelho dos cinco casos de segurança.** O `backend-noallowlist` (8099,
`base.uri.noAllowlist`) roda uma imagem de antes da rodada de bugs. Conferido hoje:

| Caso | No 8081 (corrigido) | No 8099 (imagem antiga) |
|---|---|---|
| SSRF-03 hex | 403 `DOMAIN_BLOCKED` | **200**, o scan roda em `0x7f000001` |
| SSRF-07 | 403 `DOMAIN_BLOCKED` | **200** com `127.0.0.1:8081` e com `127.0.0.1#` |
| AUTH-05 | chave válida → 200 | chave válida → **401** (BUG-01) |
| AUTH-04 | código repetido → 422 | código repetido → **200** (BUG-04) |
| RATE-01 | qa-free, `verify-check` de `juice-shop:3000`: 429 na 6ª | **200** nas 8 (BUG-20) |

Rode cada um desses uma vez contra o 8099, veja falhar e volte para o 8081. **Só depois**
rebuilde o 8099 (`up -d --build backend-noallowlist`): o rebuild apaga esse vermelho. Os
casos de scan não dão para ver assim: o 8099 não tem allowlist e barra todo alvo local.

Para os casos de scan, quando a ficha diz "imagem do commit X", o vermelho de verdade
pede o `backend` buildado no commit de antes do fix. Isso é trabalhoso: dar checkout do
commit no Backend, rebuildar, rodar e depois voltar ao HEAD e rebuildar de novo. É
opcional. Se quiser essa prova num caso, peça que eu preparo (é infra). Fora isso, inverter
uma expectativa uma vez mostra que a asserção sabe falhar.

**Cache de scan.** Corrige o que o HANDOFF dizia:
- Desde o fix do BUG-17 (2026-11-18), a chave é **conta + endereço completo + modo +
  idioma** e vale **2 minutos**, não "até reiniciar o backend". O endereço inclui caminho,
  query e porta; com e sem barra no fim é o mesmo endereço. Visitantes dividem uma entrada
  entre si. O mesmo host em pt-BR e em en não pede reinício.
- Repetir o **mesmo** endereço com a **mesma** conta em menos de 2 min devolve o resultado
  guardado. É rápido, mas não testa de novo. Para testar de novo: espere 2 min ou
  `docker compose -f env/docker-compose.test.yml restart backend`. O restart também zera o
  throttle do 2FA e os rate limits.
- A tela chama `POST /scan/async?…&refresh=true`, que fura o cache. O UI-04 não sofre com isso.

**Rate limit por minuto** (desde o fix do BUG-20, 2026-11-23).
- Vale o plano da conta: qa-free 5/min, qa-pro 60/min. O qa-enterprise está em
  `PLATFORM_STAFF_EMAILS` e fica isento. Visitante: 5/min por IP, e todo teste sai do mesmo
  IP.
- Um balde por usuário, o mesmo para `/scan`, `/scan/async`, `/scan/report`,
  `/scan/report/pdf`, `/api-keys/ci` e `/scan/verify-check`. Ele repõe 1 ficha a cada
  60/limite segundos: 12 s no FREE, 1 s no PRO.
- O 429 vem antes da cota e não gasta scan. Laço com o qa-free que faz mais de 5 chamadas
  seguidas tem de esperar e repetir (PLAN-02, PLAN-04). O scan repetido em menos de 2 min
  volta do cache e é rápido, mas passa pelo rate limit do mesmo jeito.
- O restart do backend zera os baldes.

**Lendo o resultado do scan.**
- `score` é um objeto: `score.score`, `score.riskLevel`, `score.notes` (lista de texto) e
  `score.issues` (lista com `id`, `severity` e `title`).
- Nota e título saem no idioma da requisição. Mande `Accept-Language` em todo scan e afirme
  issue pelo `id`; nota, pelo começo do texto e pelo número do fim.
- `moduleStatus`: desde o fix do BUG-16 (2026-11-22), alvo que não existe ou não responde
  nem chega a laudo (422, SCAN-13). `CERT_TRANSPARENCY` e `CVE` dependem de serviço de fora
  (crt.sh e NVD) e saem `ERROR` quando ele não responde, sem o caso ter falhado. A prova de
  contato com o alvo continua sendo `httpStatus`.
- Nenhum stub tem HTTPS válido. Por isso ficha nenhuma afirma `score.score` nem `riskLevel`:
  os -35 do SSL e o HIGH do CSP ausente não são do caso.

**Token.** O padrão das suas classes continua: forjar o JWT com `jwt.secret` e o **e-mail** no
`subject`. O método `tokenPara` já está repetido em quatro classes. Vale considerar subir
ele para o `TestConfig`, mas a decisão é sua.

## Ordem sugerida

| # | Caso | Prior. | Classe sugerida | Por que nessa posição |
|---|---|---|---|---|
| 1 | SSRF-03 hex | P0 | `SsrfGuardTest` (existe) | Já está escrito: só tirar o `@Disabled`. Aquecimento. |
| 2 | SSRF-07 | P1 | `SsrfVerifyCheckTest` (nova) | Mesmo molde do SSRF-01 (`@ParameterizedTest`), outro endpoint. |
| 3 | SCAN-01 | P0 | `ScanMotorTest` (nova) | Primeiro scan: apresenta o formato da resposta que todos os SCAN usam. |
| 4 | SCAN-03 | P0 | `ScanAtivoJuiceShopTest` (existe) | Usa o SCAN-01 e acrescenta o modo ativo e o controle negativo. |
| 5 | AUTH-05 | P1 | `ApiKeyRevogadaTest` (nova) | Primeiro fluxo de vários passos (criar, usar, revogar, usar). |
| 6 | AUTH-04 | P0 | `TotpReplayTest` (nova) | É P0, mas é o único que pede dependência nova no pom. O AUTH-05 ensina o "controle positivo antes do negativo" que ele também usa. |
| 7 | SCAN-09 | P1 | `LaudoStubsTest` (nova) | Primeiro dos stubs: procurar item numa lista por nome. |
| 8 | SCAN-07 | P1 | `DnsFalsoTest` (nova) | Parametrizado com duas colunas (domínio, risco esperado). |
| 9 | SCAN-05 | P1 | `LaudoStubsTest` | Depende do NVD de verdade: apresenta `Assumptions`. |
| 10 | SCAN-08 | P2 | `LaudoStubsTest` | Curto: uma issue e uma nota. |
| 11 | SCAN-11 | P2 | `LaudoStubsTest` | O mesmo alvo em dois idiomas. |
| 12 | SCAN-10 | P2 | `LaudoStubsTest` | Cinco notas pelo começo do texto. |
| 13 | SCAN-06 | P2 | `DnsFalsoTest` | Igual ao SCAN-07, com outro campo. |
| 14 | UI-04 | P2 | `ui-04-dmarc.cy.js` (nova) | O único de tela. Faça junto com a revisão do UI-02 (fim do documento). |
| 15 | PLAN-03 | P1 | `ApiKeyRevogadaTest` ou classe própria | Entrou em 2026-11-17 (BUG-18). Pode ir logo depois do AUTH-05: mesmo endpoint. |
| 16 | PLAN-04 | P1 | junto do PLAN-02 ou classe própria | Entrou em 2026-11-17 (BUG-19). Reaproveita a cota gasta do PLAN-02. |
| 17 | SCAN-12 | P1 | `ScanAtivoJuiceShopTest` ou classe própria | Entrou em 2026-11-19 (BUG-17). Usa o SQLi do SCAN-03: faça depois dele. |
| 18 | SCAN-13 | P1 | `ScanMotorTest` ou classe própria | Entrou em 2026-11-22 (BUG-16). Dois 422 e um controle: faça depois do SCAN-01. |
| 19 | RATE-01 | P1 | `RateLimitTest` (nova) | Retomado em 2026-11-23 (BUG-20). Pede o balde cheio: rode a classe sozinha depois do restart, ou espere 60 s no começo. |
| 20 | SCAN-14 | P2 | `LaudoStubsTest` | Entrou em 2026-11-23 (BUG-13). Mesmo stub do SCAN-09: faça depois dele. |
| 21 | SCAN-15 | P2 | `LaudoStubsTest` | Entrou em 2026-11-23 (BUG-21). Mesmo host do SCAN-09 e do SCAN-14: faça junto. |
| 22 | SCAN-16 | P2 | `LaudoStubsTest` | Entrou em 2026-11-23 (BUG-22). Mesmo host do SCAN-09, SCAN-14 e SCAN-15: faça junto. |

### Bloco 2: o que a matriz pedia e as fichas não cobriam (2026-11-26)

O dono decidiu pôr no fim da fila, antes do Playwright. São partes de casos em outra
ferramenta, três casos liberados pelo BUG-03 que nunca ganharam ficha, e o DOM-02.

| # | Caso | Prior. | Ferramenta · classe/arquivo sugerido | Por que nessa posição |
|---|---|---|---|---|
| 23 | AUTH-05 | P1 | Postman · `collection.json` | Mesmo contrato do caso 5. Fecha a coluna Postman da matriz. |
| 24 | PAY-03 | P1 | RestAssured · `PagamentoValidacaoTest` (nova) | 400 curto, sem estado. Postman já tem. |
| 25 | PAY-05 | P1 | RestAssured · `PagamentoValidacaoTest` | Igual ao 24, no Pix. |
| 26 | SCAN-04 | P1 | RestAssured · `LaudoStubsTest` | Stub novo `limpo-mock`. É o controle do SCORE-01: faça antes dele. |
| 27 | SCORE-01 | P0 | RestAssured · `LaudoStubsTest` | Stub novo `critico-mock`, com o `limpo-mock` de controle. |
| 28 | SCAN-02 | P1 | RestAssured · `LaudoStubsTest` | Stub novo `lento-mock`. Um scan de ~55 s. |
| 29 | AUTH-04 | P0 | Cypress · `auth-04-replay.cy.js` (nova) | Login real pela tela, conta nova `qa-auth04-ui`. Depois do AUTH-04 da API (caso 6). |
| 30 | PAY-14 | P1 | Cypress → **decisão** | Bate na mesma parede do PAY-12. Proposta: fechar como coberto por API. |
| 31 | UI-01 | P1 | Selenium → **começa pela Fase 5** | Selenium nunca foi montado: decisões e scaffold antes do caso. |
| 32 | UI-02 | P1 | Selenium | Depois do 31 e da correção do UI-02 no Cypress. |
| 33 | DOM-02 | P1 | **decisão** (manual × automatizado) | Caso novo, pedido em 2026-10-28. O automatizado pede HTTPS confiável no ambiente. |

---

### 1. SSRF-03 hex (P0) · `SsrfGuardTest`

**O que prova e por quê.** Que o guard não é contornado escrevendo o endereço interno de
outro jeito. Se só a forma "bonita" `127.0.0.1` fosse barrada, hex, octal e inteiro virariam
porta de entrada para a rede interna do servidor.

**Preparar.** Nada: o teste existe (`deveBloquarFormatoHexadecimalLoopback`, com "Bloquar"
sem o "e"; se for mexer, aproveite para corrigir o nome).

**Pedido.** `GET /scan?url=http://0x7f000001&active=false`, Bearer qa-pro.

**Afirmar.** 403 e `error` = `DOMAIN_BLOCKED`, como já está. Opcional e mais forte:
`http://0177.0.0.1` (octal). O JDK lê essa forma como 177.0.0.1, público; o navegador lê
como 127.0.0.1. Dá para virar `@ParameterizedTest` com as duas.

**Armadilhas.** A mensagem mudou para "…formato alternativo (equivale a 127.0.0.1)…". Não
afirme a mensagem, só o `error`.

**Ver o vermelho.** `base.uri.noAllowlist`: 200 (o scan roda). **Só com o hex.** No 8099, o
octal `0177.0.0.1` vira scan de 177.0.0.1, um endereço público de verdade. A chamada fica
pendurada (passou de 90 s) e é tráfego para fora do ambiente de teste. Não rode o octal
no 8099.

**Conferido em 2026-11-15 e de novo em 2026-11-24.** 8081: `0x7f000001` e `0177.0.0.1` →
403 `DOMAIN_BLOCKED`. 8099: `0x7f000001` → 200.

---

### 2. SSRF-07 (P1) · `SsrfVerifyCheckTest`

**O que prova e por quê.** Que o guard valida o mesmo host em que o servidor vai conectar.
O guard lia `127.0.0.1:8081` como "não resolve, deixa passar", e o cliente HTTP lia como
"127.0.0.1 na porta 8081". O endpoint é público, então qualquer visitante fazia o servidor
bater na rede interna.

**Preparar.** Nada. Use o token do qa-pro: visitante tem 5 req/min por IP, e a 6ª variante
já levaria 429. Desde o fix do BUG-20 (2026-11-23), o limite segue o plano. O qa-pro (PRO)
tem 60/min, e as 8 chamadas cabem. O qa-free (FREE) tem 5/min, como o visitante.

**Pedido.** `GET /scan/verify-check?host=<h>`. O parâmetro é `host`, não `url`.

**Afirmar.**
- 403 `DOMAIN_BLOCKED` para: `127.0.0.1:8081`, `localhost:8081`, `169.254.169.254:80`,
  `[::1]:443`, `127.0.0.1/`, `127.0.0.1#`, `127.0.0.1 ` (espaço no fim).
- Controle: `juice-shop:3000` → 200, `verified` false. É ele que prova que porta em host
  permitido passa, ou seja, que o 403 não é "recusa tudo".

**Armadilhas.**
- Passe o host com `.queryParam("host", h)`, que codifica `#`, espaço e colchetes. Montando a
  URL na mão, `?host=127.0.0.1#` manda só `127.0.0.1` (o `#` vira fragmento). Esse já era
  403, e o teste fica verde sem testar o bug.
- Evite `example.com:443` como controle: depende de DNS externo.

**Ver o vermelho.** `base.uri.noAllowlist`: 200 com porta e com `#`.

**Conferido em 2026-11-15.** As 7 variantes → 403; `juice-shop:3000` → 200
`{"verified":false,"host":"juice-shop:3000",…}`.

---

### 3. SCAN-01 (P0) · `ScanMotorTest`

**O que prova e por quê.** Que o motor completa a troca HTTP com um alvo `http://` em porta
não padrão. Sem isso (BUG-03), todo módulo "roda" sem resposta e o relatório parece normal.

**Preparar.** Nada.

**Pedido.** `GET /scan?url=http://juice-shop:3000/&active=false`, Bearer qa-pro,
`Accept-Language: pt-BR`. Leva ~20 s. Deixe o timeout do RestAssured folgado, se tiver
configurado algum.

**Afirmar.**
- 200 e **`httpStatus` = 200**. Esta é a asserção que prova o caso.
- Todo valor de `moduleStatus` = `OK`, **fora `CERT_TRANSPARENCY` e `CVE`**. Hoje são 15
  módulos; afirme "nenhum diferente de OK entre os outros", não a contagem.

**Armadilhas.**
- **CT e CVE podem sair `ERROR` sem o caso ter falhado** (desde o fix do BUG-16,
  2026-11-22). Os dois dependem de serviço de fora (crt.sh/certspotter e NVD) e marcam
  `ERROR` quando ele não responde. No Docker o crt.sh falha em parte das rodadas (502), e o
  certspotter sempre dá 403. O resultado traz então a nota "Resultado parcial — não
  concluíram: Certificate Transparency". Não é o que o SCAN-01 testa: deixe os dois fora.
- Alvo que não existe ou não responde não chega a laudo: dá 422 (é o SCAN-13). O
  `httpStatus` 200 continua sendo a prova de que o alvo respondeu.
- `openPorts: []` é esperado (a 3000 não está na lista de portas de risco). Não afirme
  porta aberta.

**Ver o vermelho.** Não há imagem antiga à mão. Para ver a asserção falhar, troque o alvo
uma vez por `http://juice-shop:3999/` (porta fechada). Desde o fix do BUG-16 a resposta é
**422** `TARGET_UNREACHABLE` (antes era 200 com `httpStatus` 0). Quebra já o "status 200".

**Conferido em 2026-11-15 e 2026-11-22.** `httpStatus` 200, 15 módulos OK, 22 s. Em
2026-11-22, `httpStatus` 200 nas duas rodadas: numa, os 15 OK; na outra,
`CERT_TRANSPARENCY` `ERROR` (crt.sh 502). `juice-shop:3999` → 422.

---

### 4. SCAN-03 (P0) · `ScanAtivoJuiceShopTest`

**O que prova e por quê.** Que o modo ativo acha as duas falhas clássicas de entrada: o
erro de banco que vaza com uma aspa (SQLi) e o parâmetro refletido sem escape (XSS). O
controle prova que a sonda de XSS não marca qualquer reflexão. É o caso que justifica o
modo ativo existir.

**Preparar.**
- Recriar `wiremock` e `backend` (seção 0).
- Token do **qa-enterprise**: é o único com bypass de posse (equipe da plataforma). O qa-pro
  leva 403 `ACCOUNT_DOMAIN_NOT_VERIFIED` no ativo, de propósito (DOM-01).

**Pedido e afirmar.** Separe o teste atual em três. Todos com `active=true`:

| Teste | `url` | Afirmar |
|---|---|---|
| SQLi | `http://juice-shop:3000/rest/products/search?q=apple` | `dbErrorLeakageSuspected` true; issue `DB_ERROR_LEAKAGE_SUSPECTED` (HIGH) |
| XSS | `http://eco-mock:8080/busca?q=apple` | `reflectedXssSuspected` true; issue `REFLECTED_XSS_SUSPECTED` (HIGH) |
| XSS, controle | `http://eco-escapado-mock:8080/busca?q=apple` | `xssProbePerformed` true **e** `reflectedXssSuspected` false; nenhuma issue `REFLECTED_XSS_SUSPECTED` |

- Em todos, antes das flags: `url` = a URL pedida. Era a proteção contra o BUG-17, hoje
  corrigido; continua sendo a melhor primeira asserção.
- No controle, o `xssProbePerformed` true é o que separa "a sonda rodou e viu o escape" de
  "a sonda nem rodou". Sem ele, o false passa à toa.

**Armadilhas.**
- O teste atual afirma XSS **e** SQLi no Juice Shop. A parte de XSS está errada ali, e o
  produto está certo: o `/rest/products/search` não ecoa o `q` no servidor (o XSS de busca
  do Juice Shop é DOM, no navegador). Lá, `reflectedXssSuspected` é false.
- **Cache:** até o fix do BUG-17 (2026-11-18), um ativo noutro caminho do Juice Shop até
  2 min antes entregava o resultado dele ao SQLi (`url` errada, SQLi false, 9 ms). Hoje só
  o mesmo endereço com a mesma conta acerta o cache. O SCAN-12 cobre esse cenário.
- O stub responde só em `/busca` e só para os dois hosts. Outro caminho dá 404 e
  `inputSurfaceDetected` false, e aí as sondas nem rodam.
- Notas em pt-BR (se for afirmar): "Possível exposição de erro de banco (modo ativo): -15" e
  "Suspeita de Reflected XSS (marcador refletido): -25".

**Ver o vermelho.** O controle é o próprio vermelho da sonda: o mesmo teste do XSS
apontado para `eco-escapado-mock` falha (false).

**Conferido em 2026-11-15.** SQLi true (XSS false ali); `eco-mock` → XSS true, HIGH, -25;
`eco-escapado-mock` → `xssProbePerformed` true, XSS false, sem issue. O diário do WireMock
mostra a sonda chegando nos dois (`/busca?q=applecyberaudit<id><">`).

**Conferido de novo em 2026-11-25** (Backend `11fd610`). A tabela inteira, com `url` = a
pedida nos três e as duas notas acima. Tempos: SQLi 21 s, XSS 22 s, controle 5 s. qa-pro no
ativo → 403 `ACCOUNT_DOMAIN_NOT_VERIFIED`.

---

### 5. AUTH-05 (P1) · `ApiKeyRevogadaTest`

**O que prova e por quê.** Que uma chave revogada perde o acesso na hora. O 401 da revogada
só vale se a **mesma** chave deu 200 antes. Sem esse controle, o teste não distingue
"revogação funciona" de "API key quebrada", que era exatamente o BUG-01.

**Preparar.** Nada. Conta qa-pro.

**Pedido e afirmar**, em sequência no mesmo teste:
1. `POST /api-keys` `{"name":"<único por execução>"}`, Bearer → **201**; guarde `id` e
   `plainKey` (`ca_` + 32 hex, só aparece nesta resposta).
2. `GET /api-keys` com header **`X-Api-Key: <plainKey>`**, sem Bearer → **200** (array).
3. `DELETE /api-keys/{id}`, Bearer → **204**.
4. O mesmo `GET` do passo 2 → **401** `{"error":"Autenticação necessária."}`.
5. Opcional: `DELETE` de novo → 409 "API key já revogada.".

**Armadilhas.**
- **O nome continua ocupado depois de revogar:** repetir um nome dá 409 "Já existe uma API
  key com este nome.". Use timestamp ou UUID no nome.
- Máximo de 10 chaves **ativas** por conta (passou disso → 402). Revogue tudo o que criar.
  O teste do jeito acima já faz isso.
- O qa-free não entra neste caso. Ele criava chave (BUG-18); depois do fix de 2026-11-17
  leva 402, e isso é o PLAN-03 (ficha 15).

**Ver o vermelho.** `base.uri.noAllowlist`: o passo 2 dá 401 (BUG-01). O controle positivo
pega o bug.

**Conferido em 2026-11-15.** 201 → 200 → adulterada 401 → 204 → 401 → 409.

**Conferido de novo em 2026-11-25** (Backend `11fd610`). 8081: 201 → 200 (array) →
adulterada 401 → 204 → 401 → 409 "API key já revogada." → mesmo nome 409. 8099: 201 → **401**
no passo 2 → 204. Achados da conferência:
- **O 401 só tem `error`** (`{"error":"Autenticação necessária."}`), sem `message`. Os 409
  são o contrário: o texto vem em `message`, e o `error` é `"409 CONFLICT"`.
- Na lista do passo 2, cada item tem o campo `plainKey`, mas `null`. A chave só vem na
  resposta do passo 1.
- O `GET /api-keys` com Bearer lista também as revogadas (`active` false). Não conte o total.
- **O 8081 e o 8099 usam o mesmo Postgres.** A chave criada num aparece no outro, e o limite
  de 10 ativas é um só.
- **Teste que falha no meio deixa a chave ativa.** No 8099 o passo 2 falha, e o `DELETE` do
  passo 3 nunca roda. Cada vermelho deixa uma chave ativa no qa-pro. Uma limpeza que roda
  mesmo com falha (`@AfterEach`) resolve.

---

### 6. AUTH-04 (P0) · `TotpReplayTest`

**O que prova e por quê.** Que um código TOTP vale uma vez. Quem observar um código (por
cima do ombro, num log, num proxy) não consegue abrir uma segunda sessão com ele.

**Preparar.**
- Dependência nova no `pom.xml` do RestAssured, escopo `test`:
  `dev.samstevens.totp:totp:1.7.1`. É a mesma família que o Backend usa (lá é o starter do
  Spring; aqui é a lib pura). Conceito: o pom é a lista de bibliotecas que o Maven baixa. Sem
  a linha, o `import` não compila. O jar já está no `~/.m2` (o Backend baixou), e a lib traz
  junto o `commons-codec`.
- **O pom tem dois blocos `<dependencies>`.** O de cima, logo depois das `<properties>`, é o
  do projeto: é nele que a linha entra. O de baixo fica dentro do `maven-surefire-plugin` e
  lista o que o plugin usa. Uma dependência `test` lá dentro nem deixa o Maven ler o pom:
  "dependency.scope ... must be one of [compile, runtime, system] but is 'test'" (conferido
  em 2026-11-26).
- Gerar o código: `DefaultCodeGenerator` com `HashingAlgorithm.SHA1`, método
  `generate(segredo, contador)`. O contador é `segundos desde 1970 / 30`, que é a janela
  de 30 s.
- **`generate` lança exceção checada** (`CodeGenerationException`). Conceito: em Java,
  quem chama um método que declara `throws` de uma exceção checada tem de tratá-la ou
  repassá-la. Sem isso, o código não compila ("unreported exception ... must be caught or
  declared to be thrown"). Num teste, o comum é repassar: `throws Exception` na assinatura
  do método. Se ela acontecer, o JUnit mostra como *error*.
- Segredo de teste: o do `env/seed.sql` (o mesmo do `totpSecret` do `cypress.config.js`). Ponha
  no `config.properties` (Regra 4: só valor de teste).
- Conta **qa-free** (login real, senha do seed). O UI-01 do Cypress já loga como qa-pro.

**Pedido e afirmar:**
1. `POST /auth/login` `{email, password}` → 200, `requires2fa` true, `token` (pré-auth).
2. `POST /auth/2fa/verify` com `Authorization: Bearer <pré-auth>` e
   `{"code":"000000","method":"TOTP"}` → **422**, `message` "Código TOTP inválido.".
3. Mesmo pré-auth, `{"code":"<N>","method":"TOTP"}` → **200**, com `token` de sessão.
4. Login de novo (pré-auth novo) e o **mesmo N** → **422**, `message` "Código TOTP já
   utilizado. Aguarde o próximo código no aplicativo.".
5. Sem `code` → 422 "Código TOTP inválido." (é o "sem código" da matriz).

**O que mais a resposta traz** (conferido em 2026-11-26, se quiser afirmar):
- passo 1: `user` null e `twoFactorMethods` `["TOTP"]`;
- passo 3: `requires2fa` false e `user.email` igual ao do qa-free;
- 422: o texto vem em `message`; o `error` é `422 UNPROCESSABLE_ENTITY`;
- o token pré-auth não abre sessão: `GET /auth/me` com ele dá **403**. Com o token do
  passo 3, dá 200.

**Armadilhas.**
- **A virada da janela não quebra o teste** (corrige o que esta ficha dizia em 2026-11-15).
  O backend aceita o código da janela atual, da anterior e da seguinte, para compensar
  relógio fora de hora. Medido: o código da janela anterior entra (200). O N gerado antes
  da virada continua valendo no passo 3, e o replay do passo 4 continua sendo do mesmo
  código. Não precisa esperar janela nenhuma.
- **Duas execuções na mesma janela de 30 s:** a segunda gera o mesmo N, que já foi usado, e
  o passo 3 dela leva 422 "já utilizado". Não é bug. Na janela seguinte o N é outro.
- **Throttle: 5 falhas seguidas → 429 por 15 min**, com "Muitas tentativas de verificação
  2FA". A contagem só zera com um sucesso, com os 15 min do bloqueio ou com
  `docker restart env-backend-1`: ela não expira sozinha.
  - Cada execução verde deixa 2 falhas na conta (passos 4 e 5). A próxima soma mais uma no
    passo 2 e zera no passo 3.
  - Enquanto o teste estiver quebrado antes do passo 3, cada execução soma 1 ou 2. Na
    terceira ou quarta, o passo 2 já leva 429.
  - Se aparecer o 429, é o throttle, não o seu código: reinicie o backend e rode de novo.
- Na CI, RestAssured → Newman → Cypress rodam no mesmo backend. Usuários diferentes evitam
  colisão de janela. Não troque para o qa-pro.

**Ver o vermelho.** `base.uri.noAllowlist`: o passo 4 dá 200 (BUG-04). Mesmo banco, mesmo
usuário.

**Conferido em 2026-11-15. Reconferido ao vivo em 2026-11-26** (Backend `11fd610`, qa-free):
os cinco passos com os status e mensagens acima. No 8099, o passo 4 dá 200 e os outros
quatro iguais. Depois da conferência, o backend do 8081 foi reiniciado e o throttle do
qa-free começa do zero.

---

### 7. SCAN-09 (P1) · `LaudoStubsTest`

**O que prova e por quê.** Que a análise de cookies lê os atributos de verdade e só cobra
de cookie de sessão. Antes (BUG-12), `secure_pref` saía como Secure, todo Laravel padrão
perdia -10 pelo `XSRF-TOKEN` sem HttpOnly (que nesse cookie é proposital) e `connect.sid`
não era reconhecido como sessão.

**Preparar.** Nada (stub `cookies-mock`).

**Pedido.** `GET /scan?url=http://cookies-mock:8080&active=false`, qa-pro, pt-BR.

**Afirmar.** Em `cookieIssues`, procurando **por `name`**:
- `PHPSESSID` com `risk` HIGH;
- `connect.sid` com HIGH;
- `secure_pref` com LOW **e** `secure` false;
- `visitante` com LOW;
- **nenhum** item `XSRF-TOKEN`.

Em `score.issues`, `INSECURE_COOKIES` com `severity` HIGH. Em `score.notes`, a nota de
cookies termina em `-10` ("Cookies com flags de segurança ausentes: -10").

**O que mais a resposta traz** (desde o BUG-13, conferido em 2026-11-26, se quiser afirmar):
cada item de `cookieIssues` tem `problems`, a lista de códigos do que falta. `PHPSESSID` →
`COOKIE_SEM_HTTPONLY` e `COOKIE_SEM_SAMESITE`; `connect.sid` → só `COOKIE_SEM_SECURE`.
O texto em `issues` (no mesmo item) depende do idioma; os códigos, não.

**Armadilhas.**
- A ordem da lista segue a dos headers e não é contrato: procure pelo nome, com o `find`
  da lição do SCAN-03.
- O ponto em `connect.sid` só atrapalha fora de aspas. Dentro do closure, como texto
  (`it.name == 'connect.sid'`), é um nome comum. Num caminho solto, o GPath leria
  `connect` e depois `sid`.
- A ausência do `XSRF-TOKEN` é asserção de ausência: afirme antes que `cookieIssues` não
  está vazia. Sem isso, um caminho errado (`null`) deixa o teste verde.
- O título ("2 cookie(s) com problemas de segurança") depende do idioma: afirme `id` e
  `severity`.
- Cache de 2 min por conta e endereço: rodar de novo nesse intervalo devolve o mesmo
  resultado, mais rápido. Não muda o contrato.

**Ver o vermelho.** O 8099 não serve aqui: ele não tem allowlist e barra o `cookies-mock`.
O vermelho do bug pede a imagem do commit `2a18641` (o último antes do fix do BUG-12),
onde sai `XSRF-TOKEN` HIGH e `INSECURE_COOKIES` MEDIUM. Sem buildar, inverta uma
expectativa uma vez para ver a mensagem de falha.

**Conferido em 2026-11-15. Reconferido ao vivo em 2026-11-26** (Backend `11fd610`, qa-pro,
pt-BR): exatamente o contrato. `cookieIssues` tem 4 itens (`PHPSESSID` HIGH, `secure_pref`
LOW com `secure` false, `visitante` LOW, `connect.sid` HIGH), nenhum `XSRF-TOKEN`;
`INSECURE_COOKIES` HIGH; nota "Cookies com flags de segurança ausentes: -10". Scan em 12 s.

---

### 8. SCAN-07 (P1) · `DnsFalsoTest`

**O que prova e por quê.** Que a régua do risco de spoofing de email segue o que o DMARC
faz de fato. `quarantine` com `pct` 100 já bloqueia o e-mail falso (LOW). `pct` parcial não
conta como aplicado. SPF `+all` faz o falso passar alinhado (HIGH). Antes (BUG-10), sair de
`p=none` para `quarantine` não mexia na nota.

**Preparar.** Nada (CoreDNS com a zona `dns-mock.test`).

**Pedido.** `GET /scan?url=http://<domínio>:8080&active=false`, qa-pro, pt-BR.
Parametrizado: `@CsvSource` com domínio e risco.

| Domínio | `emailSpoofingRisk` | Nota | Issue `DNS_EMAIL_SPOOFING` |
|---|---|---|---|
| `quarantine.dns-mock.test` | LOW | nenhuma | nenhuma |
| `quarantine-pct50.dns-mock.test` | MEDIUM | `…(email spoofing risk MEDIUM): -6` | MEDIUM |
| `reject-pct25.dns-mock.test` | LOW | nenhuma | nenhuma |
| `spf-mais-all.dns-mock.test` | HIGH | `…(email spoofing risk HIGH): -12` | HIGH |
| `dmarc-none.dns-mock.test` | MEDIUM | `…(email spoofing risk MEDIUM): -6` | MEDIUM |

Campos em `dnsSecurityResult`.

**Armadilhas.**
- **Primeiro afirme `dnsSecurityResult.lookupFailed` = false.** Se o CoreDNS não
  responder, o risco vira `UNKNOWN` sem nota, e "LOW sem nota" passaria à toa.
- O texto da nota vem do catálogo, nos dois idiomas igual ("DNS Security (email spoofing
  risk …)"), mas afirme pelo fim (`-6`/`-12`).
- **`@CsvSource` é novo nas suas classes** (até aqui só `@ValueSource`). Cada linha do CSV
  é uma execução; o JUnit separa os valores pela vírgula e entrega na ordem dos parâmetros
  do método. **Coluna vazia chega como `null`**, não como texto vazio: as linhas LOW não têm
  nota nem issue, e um `equalTo(severidade)` com `null` não é "ausência". Decida como o
  teste trata essas linhas: uma coluna a mais, ou dois métodos (com issue e sem issue).
- **As linhas LOW são asserções de ausência** (lição do SCAN-09): o id tem de ser exatamente
  `DNS_EMAIL_SPOOFING`, copiado da resposta. Um nome errado deixa as duas linhas LOW
  verdes para sempre.
- O Hamcrest combina matchers: `hasItem` aceita outro matcher dentro (`startsWith`,
  `endsWith`, `allOf`). Assim dá para procurar "a nota que começa com DNS Security" na lista
  `score.notes` sem depender da posição dela.
- O 8099 não serve: ele não tem allowlist e barra os domínios do DNS falso.

**Por que cada linha dá o que dá** (registros em `env/coredns/dns-mock.test.db`):
- `quarantine`: `p=quarantine`, sem `pct` → 100% das mensagens falsas vão para a quarentena.
  É o caso do bug: antes do fix saía MEDIUM, -6.
- `quarantine-pct50`: só metade vai para a quarentena; a outra metade chega à caixa de entrada.
- `reject-pct25`: pela RFC 7489, o que fica fora do `pct` de um `reject` cai em `quarantine`.
  Toda mensagem falsa é barrada de algum jeito → LOW.
- `spf-mais-all`: `+all` autoriza qualquer servidor. O e-mail falso passa no SPF e, por isso,
  até no `p=reject`. Antes do fix saía MEDIUM, -6. O `summary` cita o `+all`.
- `dmarc-none`: `p=none` não manda barrar nada.

**Ver o vermelho.** Imagem do commit `9f9e84e` (antes do BUG-10): `quarantine` sai MEDIUM.
Sem buildar, inverta uma expectativa uma vez (por exemplo, `quarantine` → MEDIUM).

**Conferido em 2026-11-15. Reconferido ao vivo em 2026-11-26** (Backend `11fd610`, qa-pro,
pt-BR): a tabela inteira, com `httpStatus` 200 e `lookupFailed` false nos cinco. Notas exatas:
"DNS Security (email spoofing risk MEDIUM): -6" e "DNS Security (email spoofing risk HIGH): -12".

---

### 9. SCAN-05 (P1) · `LaudoStubsTest`

**O que prova e por quê.** Que um CVE de **extensão** do PHP (o PHP é só a plataforma onde
ela roda) não vira CVE do PHP. Antes (BUG-08), o CVE-2013-2220 (PECL radius) tirava -6 de
todo site com PHP 8.3.36.

**Preparar.** Nada (stub `php-banner-mock`). Depende do **NVD de verdade** (internet do
container, sem API key).

**Pedido.** `GET /scan?url=http://php-banner-mock:8080&active=false`, qa-pro.

**Afirmar.**
- `techFingerprint.detectedVersions.PHP` = `8.3.36` (controle: a versão foi lida).
- `cveFindings` **contém** `CVE-2007-3205`. É a prova de que o NVD respondeu.
- `cveFindings` não contém `CVE-2013-2220`; nenhuma issue com `id` começando em `CVE_`;
  nenhuma nota com "CVE".

**`cveFindings` é uma lista de objetos** (corrige o que esta ficha deixava entender em
2026-11-15). Cada item tem `cveId`, `severity`, `cvssScore`, `description`,
`affectedSoftware`, `publishedDate` e `referenceUrl`. O id está em `cveId`: o caminho é
`cveFindings.cveId`. Um `hasItem("CVE-2007-3205")` direto em `cveFindings` compara o texto
com um objeto inteiro e nunca bate (e a negativa do `CVE-2013-2220` passaria sempre).

**Por que o `CVE-2007-3205` não vira issue nem nota:** o score só desconta CVE com CVSS
≥ 7.0 (HIGH/CRITICAL). Esse tem CVSS 5.0. O `CVE-2013-2220` do bug tinha a issue
`CVE_CVE_2013_2220`. Por isso a ausência de issue `CVE_*` e de nota "CVE" vem junto com a
ausência na lista, não no lugar dela.

**Armadilhas.**
- **O NVD oscila.** Numa rodada de 2026-11-15 veio `cveFindings: []`: o NVD levou 12 s, e o
  timeout do backend é 12 s. Com `[]`, as três negativas passam sem testar nada. Desde o fix
  do BUG-16 (2026-11-22), quando o NVD não responde, `moduleStatus.CVE` sai `ERROR` (antes
  dizia "OK").
- Saída recomendada: uma **pré-condição** com `Assumptions.assumeTrue(...)` do JUnit, de
  duas partes: `moduleStatus.CVE` = `OK` (todas as consultas ao NVD responderam) e
  `cveFindings` contém o `CVE-2007-3205` (a consulta do PHP voltou com dado).
  Sem elas, o teste sai **pulado** ("não deu para testar"), em vez de verde falso ou
  vermelho de rede. A alternativa é afirmar as duas como asserção comum e aceitar um
  vermelho de vez em quando por causa do NVD.
- **O `assumeTrue` pede um `boolean` do Java, não um matcher do RestAssured.** O
  `.body(...)` só afirma; ele não devolve o valor. Para a pré-condição, guarde a resposta
  (`.extract().response()`) e leia os campos com `path(...)`. Uma lista do JSON chega como
  `List` do Java, que tem `contains(...)`. As asserções vêm depois, sobre a resposta guardada
  (`resposta.then().body(...)`).
- Import: `org.junit.jupiter.api.Assumptions` (o `assumeTrue` estático). Teste pulado aparece
  como *skipped* no relatório e na contagem da suíte.
- Combine matchers como no SCAN-07: "nenhum id começando em `CVE_`" é `not(hasItem(...))`
  com um `startsWith` dentro.
- Não fixe a lista inteira de CVEs nem o score: o NVD muda.
- Cache: a rodada vazia fica guardada por 2 min. Rodar de novo logo em seguida devolve o
  mesmo vazio.

**Ver o vermelho.** Imagem do commit `6fd3a95` (o último antes do fix do BUG-08):
`CVE-2013-2220` na lista e a issue `CVE_CVE_2013_2220`.

**Conferido em 2026-11-15.** PHP 8.3.36. Rodada 1: `[]` com CVE "OK". Rodada 2 (2 min
depois): `["CVE-2007-3205"]`. Sem issue nem nota de CVE nas duas. Em 2026-11-22:
`moduleStatus.CVE` OK e `["CVE-2007-3205"]`. **Reconferido ao vivo em 2026-11-26** (Backend
`11fd610`, qa-pro, pt-BR, scan em 4,6 s): PHP `8.3.36`, `moduleStatus.CVE` OK, `cveFindings`
com um item, `cveId` `CVE-2007-3205` (CVSS 5.0, `affectedSoftware` "PHP 8.3.36"); sem issue
`CVE_*` e sem nota com "CVE". (As listas entre colchetes acima são os `cveId`, não o formato.)

---

### 10. SCAN-08 (P2) · `LaudoStubsTest`

**O que prova e por quê.** Que HSTS ausente custa -10 mas é rotulado MEDIUM. O HIGH
acionava o piso de risco, e um site nota 90 sem outro achado saía "risco médio" (BUG-11).

**Pedido.** `GET /scan?url=http://sem-hsts-mock:8080&active=false`, qa-pro, pt-BR.

**Afirmar.** `headers["Strict-Transport-Security"]` = `MISSING`; issue `HSTS_MISSING` com
`severity` MEDIUM; nota de HSTS terminando em `-10` ("HSTS ausente: -10").

**Armadilhas.**
- O piso de risco em si não dá para testar aqui: o alvo sem HTTPS já leva o risco a HIGH
  (SSL inválido -35 e CSP ausente HIGH). Ele está coberto no Backend
  (`HstsAusenteRotuloTest`). Não afirme `score.score` nem `riskLevel`.
- **Chave com hífen no GPath** (corrige o que esta ficha dizia em 2026-11-15): no RestAssured
  5.5 deste projeto, `headers.'Strict-Transport-Security'` e `headers.Strict-Transport-Security`
  funcionam os dois, no `JsonPath` e no `.body()` (conferido em 2026-11-27 contra o 8081). As
  aspas são opcionais; elas deixam explícito que o hífen faz parte do nome.
- **"Termina em -10" não basta** (lição do SCAN-07): "CSP ausente: -10" também termina em
  `-10`. Afirme a nota inteira ("HSTS ausente: -10", em pt-BR) ou amarre o começo e o fim.
- Os seis headers de segurança saem `MISSING` neste stub. A afirmação do caso é só a do HSTS.

**Por que MEDIUM e não HIGH.** Um achado HIGH aciona o piso de risco: a nota pode ser 90,
mas o risco não fica abaixo de MEDIUM. Rotular o HSTS ausente como HIGH (BUG-11) deixava
"risco médio" um site bom que só esquecera o HSTS. O desconto de -10 continua.

**Ver o vermelho.** Imagem do commit `dc1c999` (o último antes do fix do BUG-11, `2a18641`):
`HSTS_MISSING` HIGH. Sem buildar, inverta uma expectativa uma vez. O 8099 não serve: ele
barra o stub.

**Conferido em 2026-11-15.** `MISSING`, MEDIUM, "HSTS ausente: -10". **Reconferido ao vivo
em 2026-11-27** (Backend `11fd610`, qa-pro, pt-BR, scan em 13 s): `headers` com
`Strict-Transport-Security` = `MISSING`, `HSTS_MISSING` MEDIUM, nota "HSTS ausente: -10".
Score 32, `riskLevel` HIGH (pelos outros achados).

---

### 11. SCAN-11 (P2) · `LaudoStubsTest`

**O que prova e por quê.** Que o texto do header sai do catálogo, no idioma do laudo. Antes
(BUG-15), "max-age=0 deletes HSTS" estava escrito no código e saía em inglês num laudo em
português.

**Pedido.** `GET /scan?url=http://textos-mock:8080&active=false`, qa-pro, duas vezes: com
`Accept-Language: pt-BR` e com `en`.

**Afirmar.** `headers["Strict-Transport-Security"]` = `WEAK (max-age=0 apaga o HSTS)` em
pt-BR e `WEAK (max-age=0 deletes HSTS)` em en.

**Armadilhas.** **Correção do HANDOFF:** não precisa reiniciar o backend entre os dois
idiomas. O idioma faz parte da chave do cache (conferido hoje: en → pt-BR → en, sem
restart, cada um no seu idioma). Afirme só o valor do header.

**Conferido em 2026-11-15.** Os dois valores.

---

### 12. SCAN-10 (P2) · `LaudoStubsTest`

**O que prova e por quê.** Que achado LOW custa no máximo 2. Antes (BUG-14), versão
exposta, robots.txt e Referrer-Policy ausente custavam 5 cada, o mesmo que um
X-Frame-Options ausente (MEDIUM).

**Pedido.** `GET /scan?url=http://pesos-mock:8080&active=false`, qa-pro, **pt-BR**.

**Afirmar.** Cada nota procurada pelo começo, com o final exato:

| Começo da nota | Final | Issue (`severity` LOW) |
|---|---|---|
| `Versão de software exposta` | `: -2` | `SERVER_VERSION_EXPOSED` |
| `Paths sensíveis em robots.txt` | `: -2` | `SENSITIVE_ROBOTS_PATHS` |
| `Referrer-Policy ausente` | `: -1` | `REFERRER_POLICY_MISSING` |
| `Permissions-Policy ausente` | `: -1` | `PERMISSIONS_POLICY_MISSING` |
| `security.txt ausente` | `: -1` | `SECURITY_TXT_MISSING` |

**Armadilhas.** "Sem WAF" (-1) não aparece: a detecção de WAF não roda no passivo. Fica
coberto no Backend. As notas não têm `id`, e o texto muda com o idioma: sem o
`Accept-Language` fixo, o teste quebra.

**Conferido em 2026-11-15.** As cinco notas e as cinco issues.

---

### 13. SCAN-06 (P2) · `DnsFalsoTest`

**O que prova e por quê.** Que o DKIM só conta quando há chave publicada de verdade, e que
a Hostinger é reconhecida. Antes (BUG-09), qualquer TXT contava, até chave revogada
(`p=` vazio) e registro curinga.

**Pedido.** `GET /scan?url=http://<domínio>:8080&active=false`, qa-pro.

**Afirmar** (em `dnsSecurityResult`, depois de `lookupFailed` false):

| Domínio | `dkimHintFound` | `dkimSelector` |
|---|---|---|
| `dkim-hostinger.dns-mock.test` | true | `hostingermail-a` |
| `dkim-revogado.dns-mock.test` | false | `NOT_DETECTED` |
| `dkim-curinga.dns-mock.test` | false | `NOT_DETECTED` |

**Armadilhas.** `dkim-revogado` e `dkim-curinga` não têm DMARC, então saem com spoofing
HIGH. É esperado e não é deste caso: não afirme risco nesses dois.

**Ver o vermelho.** Imagem do commit `7b3236d`.

**Conferido em 2026-11-15.** A tabela inteira.

---

### 14. UI-04 (P2) · `ui-04-dmarc.cy.js`

**O que prova e por quê.** Que o card DMARC mostra a política do registro
(`p=quarantine; pct=50`), não o nível que o Backend atribuiu. Antes (BUG-15) saía
"p=medium", valor que não existe no DMARC, logo abaixo de um resumo que dizia o contrário.

**Preparar.** Frontend da imagem atual (`up -d --build frontend` se ainda não rebuildou
depois de 2026-11-14).

**Passos.**
- Entrar como no UI-02: `cy.task('forgeToken', …)` e o token no `localStorage` antes de
  carregar. **Não precisa de login real nem de 2FA.** Hoje conferi por esse caminho e
  funciona; ele também evita colidir com a janela TOTP do UI-01.
- **Limpar o campo de URL** e digitar `http://quarantine-pct50.dns-mock.test:8080`.
- Clicar em "◈ Scan", esperar a nota ("/100") e abrir o módulo **Reconnaissance** no menu
  lateral.
- Ler o valor logo abaixo do título "DMARC".

**Afirmar.** O card mostra `p=quarantine; pct=50` e não contém `p=medium`.

**Armadilhas.**
- **O campo abre com "github.com" escrito** (valor inicial do Frontend, não placeholder). O
  `.type()` do Cypress digita no fim, e o scan vai para `github.comhttp://…`. Use `.clear()`
  antes. É o que quebra o UI-02 (abaixo).
- Para provar o alvo, prenda a requisição de scan e afirme o `url` dela. A tela chama
  `POST /scan/async?url=…&active=false&refresh=true…`. Ou afirme o host completo no
  resultado. "dns-mock" sozinho casaria com um alvo torto.

**Conferido em 2026-11-15.** Token forjado, campo limpo, scan, Reconnaissance → card
"DMARC / p=quarantine; pct=50".

---

### 15. PLAN-03 (P1) · acrescentado em 2026-11-17

**O que prova e por quê.** Que a API key, recurso de plano pago, não sai de graça. A trava
antiga decidia pelo papel, e todo cadastro é OWNER da própria conta, então qualquer FREE
passava (BUG-18).

**Preparar.** Nada. Contas qa-free e qa-pro, token forjado.

**Pedido e afirmar.**
1. qa-free: `POST /api-keys` `{"name":"<único>"}` → **402**, `message` "API Keys requerem
   plano PRO ou Empresa.".
2. Controle: qa-pro, o mesmo pedido → **201** com `plainKey`. Depois,
   `DELETE /api-keys/{id}` → 204. O controle prova que o 402 vem do plano, e não de "API
   key quebrada".

**Armadilhas.**
- Nome único por execução, e revogue a chave do controle (limite de 10 ativas).
- Conta Empresa no FREE e "chave antiga continua valendo" ficam no Backend: o seed não tem
  conta Empresa no FREE, e a chave antiga pede mudar o plano no banco.

**Ver o vermelho.** `base.uri.noAllowlist` (8099): qa-free → 201.

**Conferido em 2026-11-17.** qa-free 402 com a mensagem acima; qa-pro 201 → 204.

---

### 16. PLAN-04 (P1) · acrescentado em 2026-11-17

**O que prova e por quê.** Que o gate de CI não é uma porta dos fundos para a cota e para
o plano. O gate roda um scan inteiro, e antes do fix pulava as travas do `/scan`. Com a
cota do dia gasta, o `/scan` dava 402 e o gate seguia dando 200. Numa conta FREE, o ativo
pulava o "requer PRO" (BUG-19).

**Preparar.** Gastar a cota do qa-free: `GET /scan?url=http://wiremock:8080/health`, token
do qa-free, repetido até vir 402 (no máximo 11 chamadas). Repita até o 402 em vez de contar
10, porque a cota pode já estar gasta (o PLAN-02 gasta a mesma).

Desde o fix do BUG-20 (2026-11-23), o qa-free tem 5 req/min. Um `429` no meio do preparar
não gasta cota: espere 12 s e repita a mesma chamada, sem contar como uma das 11. O
primeiro scan leva uns 20 s; os seguintes voltam do cache em menos de 1 s e esvaziam o
balde. O preparar leva cerca de 1 min e 30 s.

**Pedido e afirmar.**
1. qa-free: `GET /api-keys/ci?url=http://wiremock:8080/health&threshold=0` → **402**,
   `message` começando por "Limite diário de 10 scans atingido.".
2. qa-free: `GET /api-keys/ci?url=http://juice-shop:3000&active=true` → **402**, `message`
   "Scan ativo requer plano PRO ou superior.". Esse vale com ou sem cota: a trava de plano
   vem antes da cota.
3. Controle: qa-pro, `GET /api-keys/ci?url=http://wiremock:8080/health&threshold=0` → **200**
   com `pass: true`; o mesmo com `threshold=100` → **422** com `pass: false`. Prova que
   o gate funciona e que o 402 do passo 1 vem da cota, não de "gate quebrado".

**Armadilhas.**
- Use o token (`Authorization: Bearer`), não `X-Api-Key`. O gate aceita os dois, e o
  qa-free não cria chave desde o BUG-18. O caminho com chave antiga de conta rebaixada está
  coberto no Backend (`ApiKeyCiGateLimitesIntegrationTest`).
- Os limiares 0 e 100 não dependem da nota do alvo: toda nota é ≥ 0, e o `wiremock` sem
  HTTPS nunca chega a 100. Não afirme `score`.
- A cota fica na memória do backend, por conta e por dia. Depois deste caso (e do PLAN-02),
  o qa-free fica sem scan até a meia-noite ou até
  `docker compose -f env/docker-compose.test.yml restart backend`. Deixe-o por último, ou
  na mesma classe do PLAN-02.
- **Espere 13 s antes do passo 1 e de novo antes do passo 2.** O gate também passa pelo
  rate limit, antes da cota e da trava de plano. O preparar deixa o balde do qa-free
  vazio: sem esperar, o passo 1 dá 429 em vez de 402.
- Não afirme o rate limit do gate aqui. Desde o fix do BUG-20 daria para afirmar com o
  qa-free, mas quem cobre o limite por minuto é o RATE-01, e misturar os dois deixa o caso
  frágil. O gate está coberto no Backend (`ApiKeyCiGateLimitesIntegrationTest`, membro de
  conta FREE).
- Não peça ativo no controle PRO: o qa-pro não tem domínio verificado na conta, e daria 403
  `ACCOUNT_DOMAIN_NOT_VERIFIED` por outro motivo.

**Ver o vermelho.** `base.uri.noAllowlist` (8099, imagem antiga). O preparar funciona lá: o
`/scan` dá 403 dez vezes (sem allowlist, o alvo local é barrado, mas a cota conta) e depois
402. Os passos 1 e 2 dão **403** `DOMAIN_BLOCKED` em vez de 402: o gate passou pelas travas
e só parou no SSRF. O controle não roda no 8099 pelo mesmo motivo. A cota do 8099 é
separada da do 8081.

**Conferido em 2026-11-17.** No 8081: passos 1 e 2 → 402 com as mensagens acima; qa-pro 200
e 422. No 8099: `/scan` 403 ×10 e 402; passos 1 e 2 → 403 `DOMAIN_BLOCKED`.

**Conferido em 2026-11-23 (rate limit novo).** Preparar em 90 s: o 1º scan levou 23 s, as 5
seguintes passaram, e daí em diante veio um 429 a cada chamada, com 12 s de espera; a 11ª
deu 402. Passo 1 logo depois do preparar: 429. Com 13 s de espera: 402. Passo 2, mais
13 s: 402.

---

### 17. SCAN-12 (P1) · acrescentado em 2026-11-19

**O que prova e por quê.** Que o cache de scan só devolve resultado guardado para o mesmo
endereço e a mesma conta. A chave era só o host (BUG-17). Por 2 minutos, a busca do Juice
Shop voltava com o scan ativo da home: sem as sondas no parâmetro, sem o SQLi. É falso
negativo no fluxo natural "escaneia a home, depois a busca". E outra conta recebia o
resultado sem o scan entrar no histórico dela.

**Preparar.** Nada no ambiente. Para a parte B, gere um endereço único por execução:
`U = http://juice-shop:3000/?scan12=<System.nanoTime()>`.

**Pedido e afirmar.**

Parte A, endereço (qa-enterprise, ativo):
1. `GET /scan?url=http://juice-shop:3000/&active=true` → 200. Só prepara o cache.
2. Logo em seguida, `GET /scan?url=http://juice-shop:3000/rest/products/search?q=apple&active=true`
   → 200, `url` = a pedida e `dbErrorLeakageSuspected` true.

Parte B, conta (passivo, com o `U`):
3. qa-enterprise: `GET /scan?url=U` → 200.
4. qa-pro, logo depois: `GET /scan?url=U` → 200.
5. qa-pro: `GET /history/juice-shop` → exatamente **1** item com `url` = `U`.
6. Controle: qa-pro de novo, `GET /scan?url=U`; depois `GET /history/juice-shop` → ainda
   **1** item com `url` = `U`. O cache continua existindo (mesma conta, mesmo endereço
   acerta). Então o 1 do passo 5 vem da separação por conta, e não de "cache desligado".

**Armadilhas.**
- Conte os itens com a `url` = `U`, não o total. O `/history/{host}` devolve no máximo 50
  itens, e cada execução deixa uma linha nova no qa-pro e no qa-enterprise: o total satura.
- Passe a URL do alvo com `queryParam("url", ...)`. O RestAssured codifica o `?` e o `&`
  de dentro dela. Concatenada no caminho, a query do alvo vira parâmetro do `/scan`.
- No passo 2, afirme a `url` antes da flag: se falhar, a mensagem já diz que o resultado
  veio de outro endereço.
- O ativo é só com o qa-enterprise, que é da equipe da plataforma e dispensa a prova de
  posse. O qa-pro levaria 403 por outro motivo.
- O passo 1 pode acertar o cache, se outro teste seu fez ativo na home do Juice Shop com o
  qa-enterprise há menos de 2 min. Não muda nada: ele só prepara.
- Visitante fica fora: visitantes dividem uma entrada entre si, por decisão do dono.
- Tempo: o passo 1 leva de 4 a 25 s e o 2 cerca de 3 s. Cabe com folga nos 2 minutos.

**Ver o vermelho.** O 8099 não serve: sem allowlist, ele barra o Juice Shop. O vermelho de
verdade pede o `backend` buildado no commit `256670f`, o último antes do fix. Se quiser,
eu preparo (é infra). Medido antes do fix: passo 2 em 0,23 s, com a `url` da home e
`dbErrorLeakageSuspected` false; passo 4 em 0,24 s, com o resultado do qa-enterprise, e o
histórico do qa-pro sem a linha nova (o passo 5 daria 0).

**Conferido em 2026-11-18 e 2026-11-19.** Parte A: passo 2 em 3 s, `url` certa,
`inputSurfaceDetected` e `dbErrorLeakageSuspected` true. Parte B, com o `U` único: 1 item
do qa-pro depois do passo 4; ainda 1 depois do controle, que voltou em 0,23 s.

---

### 18. SCAN-13 (P1) · acrescentado em 2026-11-22

**O que prova e por quê.** Que um alvo que não existe ou não responde fica sem laudo e sem
nota. Antes (BUG-16), um domínio digitado errado saía com 17/100, CRITICAL, e um host sem
nada na porta saía com 29/100, HIGH. Os 14 módulos marcavam "OK" sem terem falado com
ninguém, e o scan gastava a cota. O caso prova também duas coisas:
- a recusa do domínio inexistente vem antes da cota;
- um site que responde com erro continua sendo escaneado: o 422 não pega quem respondeu.

**Preparar.** Nada. O `.invalid` é reservado (RFC 6761) e nunca resolve. O `wiremock`
resolve na rede do Docker, e nada escuta na porta 9.

**Pedido e afirmar.** Mande `Accept-Language: pt-BR` em todos.

Parte A, domínio que não resolve (qa-free):
1. `GET /auth/me` → guarde o `remainingScans`.
2. `GET /scan?url=http://nao-existe-qa.invalid&active=false` → **422**:
   - `error` = `TARGET_NOT_FOUND`;
   - `host` = `nao-existe-qa.invalid`;
   - `message` contém o domínio.
3. `GET /auth/me` → `remainingScans` igual ao do passo 1.

Parte B, resolve mas não responde (qa-pro):
4. `GET /scan?url=http://wiremock:9&active=false` → **422**:
   - `error` = `TARGET_UNREACHABLE`;
   - `host` = `wiremock`;
   - `message` não contém "null".

Controle (qa-pro):
5. `GET /scan?url=http://wiremock:8080/nao-existe-qa&active=false` → **200** com
   `httpStatus` = 404. O WireMock responde 404 para caminho sem stub. O site respondeu, só
   que com erro, e tem o que auditar.

**Armadilhas.**
- **Cota do qa-free.** O PLAN-02 zera a cota dele. Com a cota zerada, o passo 2 continua
  dando 422, porque a checagem do domínio vem antes da cota, e o passo 3 compara 0 com 0.
  O caso continua valendo: um 402 no passo 2 seria o vermelho. Para ver o "igual antes e
  depois" com um número diferente de zero, rode antes do PLAN-02 ou depois de reiniciar o
  backend (a cota é em memória).
- **Balde do qa-free.** Desde o fix do BUG-20 (2026-11-23), o qa-free tem 5 req/min, e o
  balde é o mesmo do `/scan`, do `/api-keys/ci` e do `verify-check`. Logo depois do
  PLAN-02, do PLAN-04 ou do RATE-01, ele pode estar vazio, e o passo 2 dá 429 em vez de 422.
  Espere 13 s antes do passo 2. O `/auth/me` não passa pelo rate limit.
- **A parte B gasta cota.** Foi decisão do dono: só se descobre que o site não responde
  tentando conectar. Por isso a parte B usa o qa-pro, que não tem limite diário
  (`remainingScans` null). Com o qa-free de cota zerada, o `wiremock:9` dá 402 antes de
  tentar conectar.
- **Afirme o `error`, e não só o 422.** O 422 sozinho não diz qual dos dois casos foi.
- **A `message` sai no idioma pedido.** Não afirme o texto inteiro: afirme que contém o
  host.
- **Nada de `localhost` ou `127.0.0.1` na parte B.** O SsrfGuard dá 403 (`DOMAIN_BLOCKED`)
  antes. O mesmo vale para nomes como `metadata.google.internal`, que não resolvem e
  continuam 403.
- **Tempo.** As partes A e B voltam em menos de 1 s. O controle é um scan inteiro e leva
  cerca de 20 s.

**Ver o vermelho.**
- Parte A no 8099 (imagem antiga, sem allowlist): o `.invalid` passa pelo SsrfGuard (não
  resolve, então não é interno) e volta **200**, 15/100, `httpStatus` 0.
- Parte B no 8099: dá 403, porque o 8099 bloqueia o `wiremock` (rede privada). O vermelho
  da B pede o `backend` buildado no commit `92303a4`, o último antes do fix. Se quiser, eu
  preparo (é infra).
- Medido antes do fix: `wiremock:9` → 200 em 21 s, 29/100, HIGH.

**Conferido em 2026-11-22.**
- A: 422 em 0,28 s, cota 10/10 → 10/10. Com a cota zerada, ainda 422.
- B: 422 em 0,37 s, `host` wiremock.
- Controle: 200, `httpStatus` 404, 32/100.

---

### 19. RATE-01 (P1) · retomado em 2026-11-23

**O que prova e por quê.** Que conta registrada tem limite por minuto, pelo plano. Antes
(BUG-20), o limite isentava o papel `OWNER`, e todo cadastro sai `OWNER`: o qa-free fez 12
chamadas no mesmo minuto sem um 429. O `verify-check` faz uma requisição de saída e não tem
cota diária. Sem o limite, qualquer conta grátis fazia a plataforma bater num host quantas
vezes quisesse. O caso prova também duas coisas:
- o limite volta sozinho, sem reinício: depois da reposição, a chamada passa;
- o balde é por usuário: o 429 de um não trava o outro, mesmo saindo do mesmo IP (todo
  teste sai do mesmo IP).

**Preparar.** O balde do qa-free precisa estar cheio. Ele é o mesmo do `/scan`,
`/scan/async`, `/scan/report`, `/api-keys/ci` e `/scan/verify-check`, e enche de novo depois
de 1 min sem uso (1 ficha a cada 12 s). Dois jeitos, a escolha é sua:
- rodar a classe sozinha logo depois de
  `docker compose -f env/docker-compose.test.yml restart backend`;
- esperar 60 s no começo da classe. A ordem das classes no Maven não é garantida, então
  "rodar antes do PLAN-02" não basta sem fixar a ordem.

**Pedido e afirmar.** `GET /scan/verify-check?host=juice-shop:3000`, uma chamada atrás da
outra. Passe o host com `.queryParam("host", ...)`.
1. qa-free, chamadas 1 a 5 → **200**, `verified` false.
2. qa-free, 6ª → **429**:
   - `error` = `429 TOO_MANY_REQUESTS`;
   - `message` começa por "Muitas requisições.".
3. Controle, logo depois: o mesmo pedido com o token do qa-pro → **200**.
4. Espere 13 s. qa-free → **200**: uma ficha foi reposta.
5. Logo em seguida, qa-free → **429** de novo.

O `juice-shop:3000` é o mesmo controle do SSRF-07, e é ele que dá o vermelho no 8099. O
`wiremock` também serve no 8081, mas o 8099 o barra com 403 já na 1ª chamada.

**Armadilhas.**
- **O balde é um só para tudo que dispara scan ou requisição de saída.** Um `/scan` do
  qa-free 5 s antes do passo 1 deixa 4 fichas. A 5ª chamada daria 429, e o caso cairia sem
  bug. É por isso que o preparar importa.
- **Espere entre 13 e 20 s no passo 4.** O FREE repõe 1 ficha a cada 12 s, de forma
  contínua. Com 12 s cravados, um atraso de milissegundos dá 429. Com 24 s ou mais, já são
  2 fichas, e o passo 5 daria 200.
- **A `message` sai em português mesmo com `Accept-Language: en`.** Hoje é assim em toda
  mensagem desse tipo. Afirme o `error` e o começo da `message`, não o texto inteiro.
- **Não há `Retry-After` no 429.** Não afirme cabeçalho.
- **O qa-enterprise não serve de controle de limite.** Ele está em
  `PLATFORM_STAFF_EMAILS` e fica isento. O qa-pro serve: tem 60/min, e uma chamada não
  chega perto.
- **Visitante não serve para o caso.** O balde dele é por IP, e todo teste sem token sai
  do mesmo IP.

**Ver o vermelho.** `base.uri.noAllowlist` (8099, imagem antiga): as chamadas seguem 200
(conferido até a 8ª), e o passo 2 falha com "expected 429 but was 200". O `juice-shop:3000`
passa lá porque o 8099 não tem o fix do BUG-05 (host com porta). O controle e a espera não
precisam rodar lá.

**Conferido em 2026-11-23.** No 8081, com `juice-shop:3000` e com `wiremock`: 200×5 e 429
na 6ª. qa-pro logo depois: 200. 13 s depois: 200; a seguinte: 429. No 8099:
`juice-shop:3000` 200 nas 8.

---

### 20. SCAN-14 (P2) · acrescentado em 2026-11-23

**O que prova e por quê.** Que o relatório de conformidade (LGPD Art. 49) diz o mesmo que o
card do cookie. Antes (BUG-13), o item "Cookies sem atributo SameSite" nunca aparecia: o
compliance procurava SameSite vazio, e a análise de cookies grava `"MISSING"`. E o item de
HttpOnly cobrava o token CSRF, que o card isenta desde o BUG-12. O cliente lia "sem
SameSite" no card e nada no relatório de conformidade; lia no card que o token CSRF estava
certo sem HttpOnly, e o relatório dizia "cookie sem HttpOnly".

**Preparar.** O stub novo (`env/wiremock/mappings/cookies-csrf.json`) só vale com o WireMock
recarregado: `curl -X POST http://localhost:8082/__admin/mappings/reset` (ou
`docker compose -f env/docker-compose.test.yml restart wiremock`). Para conferir,
`curl -i -H "Host: cookies-mock:8080" http://localhost:8082/so-csrf` devolve
`Set-Cookie: XSRF-TOKEN=qa-csrf; Path=/`.

**Pedido.** qa-pro, `Accept-Language: pt-BR`, `active=false`:
- A: `GET /scan?url=http://cookies-mock:8080/` (o alvo do SCAN-09);
- B: `GET /scan?url=http://cookies-mock:8080/so-csrf` (só um `XSRF-TOKEN`, sem flag nenhuma).

**Afirmar.** Em `compliance.lgpdItems`, ache o item com `reference` = `Art. 49` (procure por
`reference`, não por posição). Em `findings`:
- A: contém "Cookies sem atributo SameSite — vulneráveis a CSRF". Controle: contém também
  "Cookies sem flag HttpOnly — acessíveis via JavaScript" (o `PHPSESSID` não tem HttpOnly).
  É o controle que prova que o item de HttpOnly continua existindo.
- B: contém "Cookies sem flag Secure — transmitidos em conexões HTTP" e "Cookies sem
  atributo SameSite — vulneráveis a CSRF", e **não contém** "Cookies sem flag HttpOnly —
  acessíveis via JavaScript".
- B, em `cookieIssues`: um cookie só, `name` = `XSRF-TOKEN`, `problems` =
  `["COOKIE_SEM_SECURE", "COOKIE_SEM_SAMESITE"]`.

**Armadilhas.**
- **Use o qa-pro.** Para o qa-free e para visitante, `compliance` e `cookieIssues` vêm
  `null`. É a trava de plano, não bug.
- **Idioma.** O texto dos `findings` sai no idioma pedido. Mande `Accept-Language: pt-BR`.
  Em `en`, o de SameSite é "Cookies without the SameSite attribute — exposed to CSRF". O
  `problems` é código e não muda com o idioma.
- **A ausência do HttpOnly é só na B.** Na A ele tem de estar lá.
- **Não afirme `overallScore` nem o `status` do Art. 49.** O Art. 49 já era FAIL pelos
  itens de Secure e HttpOnly, e o placar ficou igual antes e depois (60 na A, 53 na B).
- **`problems` é campo novo e só acrescenta.** O SCAN-09 continua valendo como está.
- **Cache de scan.** A e B são chaves diferentes (o caminho entra na chave desde o BUG-17).
  Repetir o mesmo endereço em menos de 2 min devolve o resultado guardado, que é o mesmo.

**Ver o vermelho.** O 8099 barra o `cookies-mock` (403, sem allowlist). O vermelho de
verdade pede o `backend` buildado no commit `260074a`, o último antes do fix. Se quiser, eu
preparo (é infra). Medido antes do fix: A sem o item de SameSite; B com o de HttpOnly e sem
o de SameSite.

**Conferido em 2026-11-23.** A: Secure, HttpOnly e SameSite. B: Secure e SameSite, sem
HttpOnly; `XSRF-TOKEN` com os dois códigos. O mesmo em `en`. qa-free: `compliance` null.

---

### 21. SCAN-15 (P2) · acrescentado em 2026-11-23

**O que prova e por quê.** Prova que o painel "O que está em jogo" e o card do cookie dão a
mesma resposta para "é cookie de sessão?". Antes do BUG-21, cada um tinha a sua regra.

O painel decidia por fragmentos do nome. Chamava de sessão:
- o `csrftoken`, por "token";
- o `inside_banner`, por "sid";
- o `user_pref`, por "user".

Com o teto de três indícios, esses três ocupavam as vagas e o `laravel_session`, o único
de sessão, sumia do painel.

O card chamava de sessão o `_hjSession_1873402`, analytics do Hotjar, por "session". Ele
saía HIGH no card.

O cliente lia no painel que o token CSRF era a sessão do site e via um cookie de analytics
marcado como risco alto.

**Preparar.** O stub novo (`env/wiremock/mappings/cookies-nomes.json`) só vale com o
WireMock recarregado: `curl -X POST http://localhost:8082/__admin/mappings/reset`. Para
conferir, `curl -i -H "Host: cookies-mock:8080" http://localhost:8082/nomes` devolve cinco
`Set-Cookie`, sem flag nenhuma: `csrftoken`, `inside_banner`, `user_pref`,
`_hjSession_1873402` e `laravel_session`.

**Pedido.** Use o qa-pro, `Accept-Language: pt-BR`, `active=false`:
`GET /scan?url=http://cookies-mock:8080/nomes`.

**Afirmar.**
- Em `impactIndicators`, os itens com `source` = `COOKIES` são **um só**, e o `detail` dele
  é `laravel_session`.
- Em `cookieIssues`, procure pelo `name`, não pela posição:
  - `laravel_session` tem `risk` HIGH;
  - `_hjSession_1873402` tem `risk` LOW;
  - `csrftoken`, `inside_banner` e `user_pref` têm `risk` LOW.
- Em `score.issues`, o item com `id` = `INSECURE_COOKIES` tem `title` começando com
  "1 cookie(s)".

**Armadilhas.**
- **Use o qa-pro.** Para o qa-free e o visitante, o `impactIndicators` vem só com a origem
  (`detail` null) e o `cookieIssues` vem null. É a trava de plano, não bug.
- **Filtre o `impactIndicators` por `source`.** Ele também junta JWT, documentação de API
  e GraphQL. Neste alvo só há cookie, mas não conte com a posição.
- **Não afirme `overallScore`.** A nota é 22 antes e depois. O `laravel_session` já custa
  os 10, e o desconto é o do pior cookie, não a soma. O que muda é o título do achado
  ("2 cookie(s)" → "1 cookie(s)").
- **O nível (`impact`) não muda:** é `SHOWCASE` antes e depois, porque o nível vem só dos
  campos de formulário.
- **Idioma.** O título do achado sai no idioma pedido: em `en` é "1 cookie(s) with
  security problems". Afirmar o começo ("1 cookie(s)") vale nos dois.
- **O card continua listando os cinco cookies.** Os LOW estão lá, como informação. O que
  mudou foi o `risk` do Hotjar e o painel.
- **Cache de scan.** Repetir o mesmo endereço em menos de 2 min devolve o resultado
  guardado, que é o mesmo.

**Ver o vermelho.** O 8099 barra o `cookies-mock` (403, sem allowlist). Para ver o vermelho
de verdade, é preciso o `backend` buildado no commit `0ee80e9`, o último antes do fix. Se
quiser, eu preparo (é infra). Medido antes do fix:
- o painel listava `csrftoken`, `inside_banner` e `user_pref`, sem o `laravel_session`;
- `_hjSession_1873402` HIGH;
- "2 cookie(s)".

**Conferido em 2026-11-23.**
- Painel: só `laravel_session`.
- Card: `_hjSession_1873402` LOW, `laravel_session` HIGH.
- Achado: "1 cookie(s) com problemas de segurança".
- O mesmo em `en`.
- Visitante: `detail` null.
- Na tela (qa-pro), o painel mostra "Cookies de sessão: laravel_session".

---

### 22. SCAN-16 (P2) · acrescentado em 2026-11-23

**O que prova e por quê.** Prova que cookie de analytics com cara de sessão no nome não é
tratado como sessão: sai LOW no card, não desconta da nota e não aparece no painel "O que
está em jogo". Os três do alvo são gravados pelo JavaScript da página e não abrem conta:
- `AMP_TOKEN` (Google Analytics), que contém "token";
- `s_sess` (Adobe Analytics), que contém "sess";
- `_pin_unauth` (tag do Pinterest), que contém "auth".

Antes (BUG-22), os três saíam HIGH, o laudo dizia "3 cookie(s) com problemas de segurança"
e a nota perdia 10 pontos. Isso é um achado falso: o dono do site não tem o que corrigir,
porque não controla como o Google, a Adobe e o Pinterest gravam esses cookies.

**Preparar.** O stub novo (`env/wiremock/mappings/cookies-analytics.json`) só vale com o
WireMock recarregado: `curl -X POST http://localhost:8082/__admin/mappings/reset`. Para
conferir, `curl -i -H "Host: cookies-mock:8080" http://localhost:8082/analytics` devolve
três `Set-Cookie`, sem flag nenhuma: `AMP_TOKEN`, `s_sess` e `_pin_unauth`.

**Pedido.** Use o qa-pro, `Accept-Language: pt-BR`, `active=false`:
`GET /scan?url=http://cookies-mock:8080/analytics`.

**Afirmar,** nesta ordem:
1. **Controle:** `cookieIssues` tem os três cookies (procure pelo `name`), cada um com
   `risk` LOW.
2. Em `score.issues`, **nenhum** item com `id` = `INSECURE_COOKIES`.
3. Em `impactIndicators`, **nenhum** item com `source` = `COOKIES`.

**Por que o controle vem primeiro.** Os passos 2 e 3 afirmam que algo **não** existe. Uma
afirmação de ausência também passa quando o scan não leu cookie nenhum. Por exemplo, se o
WireMock não foi recarregado, o `/analytics` devolve 404 sem cookie: não há achado, o
painel fica vazio, e o teste fica verde sem provar nada. O passo 1 garante que os cookies
foram lidos. Só então "sem achado" quer dizer "não é sessão".

**Armadilhas.**
- **Use o qa-pro.** Para o qa-free e o visitante, `cookieIssues` vem null (trava de
  plano), e o controle falha.
- **Os cookies continuam no card.** O `problems` de cada um continua listando Secure,
  HttpOnly e SameSite. O que muda é o `risk`.
- **Não afirme `overallScore`.** Medido: 22 antes e 32 depois. Mas a nota depende dos
  outros módulos; o que este caso prova é o achado de cookies.
- **Cache de scan.** Repetir o mesmo endereço em menos de 2 min devolve o resultado
  guardado, que é o mesmo.
- **Fora do caso:** o `intercom-session-*` (chat) continua sendo sessão, por decisão do
  dono (dá acesso às conversas do visitante). Isso está no teste do Backend.

**Ver o vermelho.** O 8099 barra o `cookies-mock` (403, sem allowlist). Para ver o
vermelho de verdade, é preciso o `backend` buildado no commit `c4711db`, o último antes do
fix. Se quiser, eu preparo (é infra). Medido antes do fix: os três HIGH, "3 cookie(s)",
-10 na nota, e o painel com `AMP_TOKEN`, `s_sess` e `_pin_unauth`.

**Conferido em 2026-11-23.**
- Os três LOW, com os três códigos em `problems`.
- Sem `INSECURE_COOKIES`.
- `impactIndicators` vazio.
- O mesmo em `en`.
- Na tela (qa-pro), o painel não mostra "Cookies de sessão", e os três cards estão LOW.

---

## Bloco 2 (fichas 23 a 33, montadas em 2026-11-26)

**Infra nova, já aplicada no ambiente local em 2026-11-26** (Backend `11fd610`):
- Aliases do WireMock `limpo-mock`, `critico-mock` e `lento-mock`, na `SSRF_TEST_ALLOWLIST`
  do `backend` (não na do 8099). Stubs `env/wiremock/mappings/limpo.json` e `lento.json`.
  Numa máquina nova: `docker compose -f env/docker-compose.test.yml up -d --no-build wiremock backend`.
- Conta `qa-auth04-ui@cyberaudit.test` no `env/seed.sql` (FREE, mesma senha e TOTP de teste),
  e a chave `emailAuth04Ui` no `cypress.config.js`. Numa máquina nova: `up --no-build seeder`.

### 23. AUTH-05 (P1) · Postman · `collection.json`

**O que prova e por quê.** O mesmo do caso 5: a chave revogada perde o acesso, e o
controle é a mesma chave dando 200 antes. Aqui na collection, que o Newman roda na CI. É o
último caso com Postman pendente na matriz.

**Preparar.** Nada no ambiente. Na collection, quatro requests em sequência, numa pasta
própria. Conceitos do Postman que entram:
- **Variável de coleção** leva um valor de um request para o outro: o script de teste do 1º
  guarda o `id` e a `plainKey` (`pm.collectionVariables.set`), e os seguintes usam
  `{{nomeDaVariavel}}` na URL e no header.
- **Variável dinâmica** dá nome único por execução: `{{$timestamp}}` ou `{{$guid}}` no corpo.

**Pedido e afirmar.** Os mesmos cinco passos da ficha 5, com o `{{jwtPro}}` que a collection
já usa nos PAY:
1. `POST /api-keys` → 201, `plainKey` começando com `ca_`.
2. `GET /api-keys` só com `X-Api-Key` → 200.
3. `DELETE /api-keys/{id}` com Bearer → 204.
4. O `GET` do passo 2 → 401 e `error` = `Autenticação necessária.`.
5. Opcional: `DELETE` de novo → 409 e `message` = `API key já revogada.`.

**Armadilhas.**
- **Os passos 2 e 4 não podem levar o Bearer.** Se o `Authorization` for junto, ele autentica
  sozinho: o passo 2 dá 200 mesmo com a API key quebrada (verde falso do BUG-01), e o 4 dá
  200 no lugar do 401. A collection não tem auth na raiz hoje; mantenha o header só nos
  requests 1, 3 e 5.
- **O 401 só tem `error`**, sem `message`. Os 409 têm o texto em `message`.
- **Diferença boa de saber para o JUnit:** no Newman, um `pm.test` que falha não para a
  execução. O `DELETE` do passo 3 roda mesmo se o passo 2 falhou, então a chave não fica
  ativa. No RestAssured, o teste para na primeira falha (ficha 5).
- O qa-pro é o mesmo do RestAssured: o limite de 10 chaves ativas é um só para os dois.

**Ver o vermelho.** Rode a pasta com o `baseUrl` apontando para o 8099
(`http://localhost:8099`, num environment à parte ou trocando na hora): o passo 2 dá 401.

**Conferido em 2026-11-25** (ficha 5, mesmo contrato, 8081 e 8099).

---

### 24. PAY-03 (P1) · RestAssured · `PagamentoValidacaoTest`

**O que prova e por quê.** Que o checkout de cartão recusa o pedido sem o token do cartão
antes de falar com o Mercado Pago. O `cardTokenId` é o que o SDK do MP gera no navegador:
sem ele não há o que cobrar, e o backend não pode seguir.

**Preparar.** Nada. Token do qa-free ou do qa-pro (os dois dão o mesmo 400).

**Pedido.** `POST /billing/checkout/card`, corpo `{"plan":"PRO"}`, Bearer.

**Afirmar.** 400; `error` = `400 BAD_REQUEST`; `message` contém `cardTokenId ausente`.

**Armadilhas.**
- **Nunca mande `cardTokenId` com a qa-free.** Com um token, o mock do MP aprova e a conta
  vira PRO, o que quebra os casos que contam com ela FREE. Os casos de upgrade têm conta
  própria (PAY-01, PAY-06, PAY-14).
- **Checkout tem rate limit próprio:** 5 por minuto por usuário (PAY-09). Cada 400 conta.
  O PAY-03 e o PAY-05 juntos fazem 2 ou 3 chamadas; laço não cabe aqui.
- A `message` sai em português mesmo com `Accept-Language: en`. Afirme o trecho, não o
  texto inteiro.

**Ver o vermelho.** Inverta uma expectativa uma vez (por exemplo, espere 200). Não há
imagem antiga com outro comportamento.

**Conferido em 2026-11-26.** qa-pro e qa-free: 400, `400 BAD_REQUEST`, "cardTokenId ausente —
gere o token no navegador antes de chamar este endpoint.". O mesmo com `en`.

---

### 25. PAY-05 (P1) · RestAssured · `PagamentoValidacaoTest`

**O que prova e por quê.** Que o checkout Pix recusa CPF com quantidade errada de dígitos
antes de gerar a cobrança. O Pix exige o CPF do pagador.

**Pedido.** `POST /billing/checkout/pix`, corpo `{"plan":"PRO","cpf":"123"}`, Bearer.

**Afirmar.** 400; `error` = `400 BAD_REQUEST`; `message` contém `informe os 11 dígitos`.
Opcional, como `@ParameterizedTest`: `123` (curto) e `123456789012` (12 dígitos) dão o
mesmo 400.

**Armadilhas.** As mesmas do PAY-03: rate limit de checkout, `message` em português, e
nunca um CPF válido com a qa-free (gera Pix de verdade no mock e muda estado).

**Conferido em 2026-11-26.** `123` e `123456789012`: 400, "CPF inválido: informe os 11
dígitos, com ou sem pontuação.". `plan` aceita `PRO` e `Pro`.

---

### 26. SCAN-04 (P1) · RestAssured · `LaudoStubsTest`

**O que prova e por quê.** Que o scan não inventa achado onde está tudo certo. Os SCAN de
até aqui provam que cada falha é achada; este prova o outro lado: um alvo com os seis
headers de segurança certos e `security.txt` sai sem achado nenhum, fora o SSL do ambiente.
Falso positivo custa a confiança no laudo tanto quanto falso negativo.

**Preparar.** Nada (stub `limpo-mock`, infra do bloco 2).

**Pedido.** `GET /scan?url=http://limpo-mock:8080/&active=false`, qa-pro, pt-BR.

**Afirmar.**
- `httpStatus` = 200 (controle: o alvo respondeu, ver SCAN-01).
- Em `headers`, os seis com valor começando por `OK`: `Strict-Transport-Security`,
  `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`,
  `Permissions-Policy`.
- `securityTxtPresent` true.
- Em `score.issues`, a lista de `id` é exatamente `["SSL_INVALID"]`.
- `cookieIssues`, `jwtSecurity` e `dangerousHttpMethods` vazios; `serverVersionExposed` false.

**Armadilhas.**
- **O `SSL_INVALID` (HIGH, -35) é do ambiente, não do alvo.** Nenhum stub tem HTTPS válido.
  É o único achado esperado.
- **"Vazio" precisa do controle.** Lista vazia também passa se o campo não existir
  (`Actual: null`, lição do SCAN-03). O `httpStatus` 200 e os headers `OK` vêm antes e
  provam que a página foi lida.
- O `CERT_TRANSPARENCY` pode sair `ERROR` e entrar na nota de resultado parcial (crt.sh no
  Docker, ver SCAN-01). Não afirme a lista de notas nem a contagem dela.
- Não afirme `score.score`. Hoje é 65 (100 menos os 35 do SSL), mas o SCORE-01 é quem usa a
  nota, e com motivo.

**Ver o vermelho.** Troque o alvo uma vez por `http://critico-mock:8080/` (o caso 27): a
lista de `id` ganha `JWT_CRITICAL`.

**Conferido em 2026-11-26.** 200 em 5 s, `httpStatus` 200, os seis `OK`, `securityTxtPresent`
true, `score.issues` = só `SSL_INVALID/HIGH`, nota 65 MEDIUM, listas vazias.

---

### 27. SCORE-01 (P0) · RestAssured · `LaudoStubsTest`

**O que prova e por quê.** Que um achado CRITICAL não fica escondido atrás de uma nota boa.
O `ScoreService` tem um piso de risco (*severity override*): com issue CRITICAL, o
`riskLevel` não fica abaixo de HIGH, seja qual for a nota. Sem o piso, um site com um
JWT `alg:none` (qualquer um forja a sessão) e o resto em ordem sairia "risco médio".

**Correção do escopo na matriz:** a matriz dizia "nota não fica alta". O piso mexe no
`riskLevel`, não na nota: a nota cai só os 15 do achado. O caso afirma o nível.

**Preparar.** Nada (stubs `critico-mock` e `limpo-mock`, infra do bloco 2). O `critico-mock`
é o `limpo-mock` com um cookie `auth_token` que carrega um JWT `alg:none`. O cookie tem
Secure, HttpOnly e SameSite, para não dar achado de cookie.

**Pedido.** qa-pro, pt-BR, `active=false`:
- `GET /scan?url=http://critico-mock:8080/`
- controle: `GET /scan?url=http://limpo-mock:8080/`

**Afirmar,** nesta ordem:
1. `critico-mock`: em `score.issues`, um item com `id` = `JWT_CRITICAL` e `severity`
   CRITICAL. É o que aciona o piso.
2. `critico-mock`: `score.score` **maior ou igual a 45**. Pela régua, 45 a 69 é MEDIUM. Esta
   asserção prova que a nota sozinha não daria HIGH.
3. `critico-mock`: `score.riskLevel` = `HIGH`. Com os passos 1 e 2, só o piso explica.
4. Controle, `limpo-mock`: `score.riskLevel` = `MEDIUM`. O mesmo alvo sem o CRITICAL não
   sobe de nível; o HIGH do passo 3 vem do JWT, não do ambiente.

**Armadilhas.**
- **A ordem importa.** Se o passo 2 falhar um dia (outro desconto novo derrubou a nota
  abaixo de 45), o HIGH passaria a vir da nota, e o passo 3 ficaria verde sem provar o
  piso. Com o 2 antes, o teste avisa.
- **É a exceção da regra "não afirme `score`"** (seção 0). Aqui o stub foi montado para que o
  único outro desconto seja o SSL do ambiente: 100 − 35 − 15 = 50.
- O SSL inválido é HIGH e tem o seu próprio piso (HIGH → no mínimo MEDIUM). É por isso que
  o controle é MEDIUM, e não LOW ou SECURE.
- Não confunda com o rótulo do achado (BUG-11, `HstsAusenteRotuloTest`): ali o assunto era
  o HIGH acionando o piso. Aqui é o CRITICAL.

**Ver o vermelho.** Não há imagem sem o piso. Para ver a asserção falhar, espere `MEDIUM`
no passo 3 uma vez.

**Conferido em 2026-11-26.** `critico-mock`: 50, HIGH, issues `SSL_INVALID/HIGH` e
`JWT_CRITICAL/CRITICAL`, notas "Certificado SSL inválido/erro: -35" e "JWT inseguro
detectado: -15", `jwtSecurity` = `auth_token`, `none`, CRITICAL. `limpo-mock`: 65, MEDIUM.

---

### 28. SCAN-02 (P1) · RestAssured · `LaudoStubsTest`

**O que prova e por quê.** Que um alvo que aceita a conexão e demora a responder não ganha
laudo "limpo". A busca principal do scan desiste em 8 s; sem os headers, o laudo tem de
dizer que não verificou, e não que está tudo certo. É o lado "lento" do SCAN-13 (que cobre
"não existe" e "não conecta").

**Preparar.** Nada (stub `lento-mock`: a raiz responde depois de 10 s).

**Pedido.** `GET /scan?url=http://lento-mock:8080/&active=false`, qa-pro, pt-BR. Leva
**~55 s**: vários módulos batem na raiz e cada um espera o seu timeout.

**Afirmar.**
- 200. Não é o 422 do SCAN-13: a conexão abriu.
- `httpStatus` = 0 (nenhuma resposta lida).
- `moduleStatus.HTTP_FETCH` = `ERROR`.
- `headers.error` contém `não respondeu a tempo`.
- Em `score.issues`, `HEADERS_UNVERIFIED` com `severity` MEDIUM.
- Em `score.notes`, uma nota começando com `⚠ Resultado parcial` e contendo
  `Busca da página / headers`.

**Armadilhas.**
- **Correção do escopo na matriz:** ela dizia "módulo marcado como timeout". O contrato é
  `ERROR`. O `TIMEOUT` só aparece quando um módulo não termina nos 120 s da fase inteira, e
  nenhuma sonda isolada chega perto disso.
- **O nome na nota depende do idioma** ("Busca da página / headers" em pt-BR). Mande
  `Accept-Language: pt-BR`. O `HTTP_FETCH` do `moduleStatus` é código e não muda.
- **Timeout do RestAssured:** se tiver configurado algum abaixo de 60 s, o teste cai por ele.
- **Cache:** o resultado fica guardado 2 min. Repetir logo em seguida devolve o mesmo, em
  menos de 1 s.
- **Fora do caso, por enquanto: BUG-23.** Atraso no `security.txt` ou no `robots.txt` (e
  não na raiz) hoje sai como "arquivo ausente", com o módulo `OK`. Achado lateral desta
  conferência, aberto. Depois do fix, vira parte deste caso.

**Ver o vermelho.** Troque o alvo uma vez por `http://limpo-mock:8080/`: o `HTTP_FETCH` sai
`OK` e o passo do `ERROR` falha.

**Conferido em 2026-11-26.** 200 em 53 s, `httpStatus` 0, `HTTP_FETCH` `ERROR`, `headers` =
`{"error":"Erro ao conectar: o servidor aceitou a conexão, mas não respondeu a tempo"}`,
`HEADERS_UNVERIFIED/MEDIUM`, "⚠ Resultado parcial — não concluíram: Busca da página /
headers, Certificate Transparency.".

---

### 29. AUTH-04 (P0) · Cypress · `auth-04-replay.cy.js`

**O que prova e por quê.** O mesmo do caso 6, pela tela: quem digita um código TOTP que já
foi usado fica na tela do 2FA, com o aviso, e não entra. O caso 6 prova a regra na API; este
prova que a tela mostra a recusa em vez de travar ou entrar.

**Preparar.**
- Conta `qa-auth04-ui@cyberaudit.test` (infra do bloco 2), no `Cypress.env('emailAuth04Ui')`.
  Senha e segredo TOTP são os de teste que o UI-01 já usa (`password`, `cy.task('totp')`).
- **Login real, não token forjado.** O caso é o login.

**Passos e afirmar.**
1. Abrir o login, e-mail e senha → tela do 2FA.
2. Gerar o código com `cy.task('totp')` e **guardar** (alias ou variável). Digitar e
   confirmar → dashboard. É o controle positivo: o código vale na primeira vez.
3. Sair (ou limpar o token do `localStorage`) e fazer o login de novo → tela do 2FA.
4. Digitar o **mesmo** código guardado → a caixa de erro mostra
   `Código TOTP já utilizado`, e a tela do 2FA continua (o dashboard não aparece).

**Armadilhas.**
- **Não chame o `cy.task('totp')` de novo no passo 4.** Se a janela virou, ele devolve outro
  código, que vale. O teste entra no dashboard, e o replay não foi testado.
- **A janela pode virar entre o passo 2 e o 4.** Não muda nada: o código guardado continua
  usado. Também não precisa de folga no passo 2: o backend aceita o código da janela
  anterior (medido em 2026-11-26, ficha 6).
- Rodar duas vezes na mesma janela de 30 s quebra o passo 2 da segunda execução (código já
  usado). Não é bug.
- A mensagem vem do backend; o `support/e2e.js` fixa pt-BR. Afirme o começo do texto.
- Throttle do 2FA: 5 falhas → 429 por 15 min. Cada execução tem um sucesso antes da falha,
  e o sucesso zera a contagem. O login só conta falhas de senha.

**Ver o vermelho.** O 8099 não tem tela própria. Para ver a asserção falhar, troque o
passo 4 uma vez para esperar o dashboard.

**Conferido em 2026-11-26** (API, conta nova): login → `requires2fa` true, métodos
`["TOTP"]`; código N → 200 com token; login de novo, o mesmo N → 422, `message` "Código TOTP
já utilizado. Aguarde o próximo código no aplicativo.". **Tela conferida no código**
(`App.tsx`, `handle2faSubmit` mostra a `message` do backend na caixa de erro e não sai da
tela do 2FA). Confiro na tela no dia em que passar o caso.

---

### 30. PAY-14 (P1) · Cypress → decisão do dono

**O que a matriz pede.** O checkout de cartão sem CPF pela tela (cliente estrangeiro). O
RestAssured já cobre a API (✅, conta `qa-pay-nocpf`).

**Por que não dá como os outros.** É a mesma parede do PAY-12 (decisão de 2026-10-26). O
número e o CVV ficam nos Secure Fields do Mercado Pago, num iframe de outro domínio, e o
token sai do SDK do MP de verdade, com a chave pública do build. Sem token, o formulário
nunca chega ao `POST /billing/checkout/card`.

**O que a tela faz com o CPF** (lido no `App.tsx`, `CardCheckoutPanel`): vazio passa; com
dígitos, só aceita 11. Esse pedaço daria para testar sem o MP: CPF `123` mostra
`checkout.cpfInvalido` antes de tokenizar. Mas é a validação de formulário, não o "sem CPF
paga".

**Proposta:** fechar o PAY-14 na tela como "coberto por API + manual", igual ao PAY-12, com
o motivo na matriz. Alternativa: um teste só da validação de CPF parcial (curto, sem MP).
A decisão é do dono.

---

### 31. UI-01 (P1) · Selenium → começa pela Fase 5

**O que a matriz pede.** O login completo com 2FA chegando ao dashboard, em Selenium. O
Cypress já cobre (✅).

**Estado.** O Selenium nunca foi montado no repo: não há pasta, pom nem driver. A Fase 5
foi marcada como "opcional conforme vagas-alvo" (HANDOFF §8).

**Antes do caso, decisões do dono:**
1. **Fazer ou não.** O Playwright (Fase 7) também porta o UI-01 e o UI-02. Três ferramentas
   de tela nos mesmos casos é muito para manter. Vale se as vagas-alvo pedem Selenium.
2. **Linguagem:** Java (mesmo Maven do RestAssured, recomendado) ou outra.
3. **Driver:** o Selenium 4 baixa o ChromeDriver sozinho (Selenium Manager). Na CI isso é
   download na hora; a alternativa é fixar a versão.
4. **Page Object:** é do dono (Regra 3). Eu faço o scaffold (módulo, pom, config, conta de
   teste, CI).

**Conta.** Precisa de uma conta própria, pelo mesmo motivo do caso 29 (replay de TOTP entre
suítes na CI). Eu crio no scaffold.

---

### 32. UI-02 (P1) · Selenium

**O que prova.** O scan iniciado pela tela aparece com nota e no histórico, **com o host
digitado**. Vem depois do 31 (mesmo scaffold) e da correção do UI-02 no Cypress (fim do
documento), para não portar o verde falso.

**Armadilha já conhecida:** o campo abre com "github.com". O `sendKeys` do Selenium também
acrescenta no fim; precisa do `clear()` antes, como no Cypress.

---

### 33. DOM-02 (P1) · caso novo → decisão do dono

**O que prova.** O fluxo feliz da posse de domínio: cadastrar o domínio, publicar o token em
`/.well-known/cyberaudit.txt`, verificar e ver o scan ativo liberado nele. Hoje só existe o
negativo (DOM-01). O dono pediu em 2026-10-28 (HANDOFF §9).

**Contrato lido no código** (`DomainController`, `DomainService`, `DomainProtectionService`):
- `POST /domains` `{"host": "..."}` → 201 com o token (PRO ou mais).
- `POST /domains/{id}/verify` → 200 se `https://<host>/.well-known/cyberaudit.txt` tiver
  exatamente o token; senão 417 "Verificação falhou…".
- O token é um HMAC do host: o mesmo host dá sempre o mesmo token.

**Por que não dá hoje no ambiente local.** A verificação busca o arquivo **só por HTTPS**,
na porta 443, com a validação normal de certificado. Nenhum stub tem HTTPS confiável. O
automatizado pede infra nova:
- uma CA de teste;
- o WireMock servindo HTTPS na 443, com certificado dessa CA;
- o `backend` confiando nela.

Isso muda o resultado de SSL dos stubs que passarem a ter HTTPS, então é uma etapa própria,
com conferência dos SCAN já escritos.

**Opções:**
1. **Manual primeiro** (o que o §9 sugeria): na conta PRO real do dono, no domínio dele.
   Registro do passo a passo como caso manual na matriz.
2. **Automatizado depois**, com a infra de HTTPS acima. Fica como a última etapa do bloco,
   ou depois do Playwright.

---

## Revisão: o UI-02 está verde sem testar o que diz (achado 2026-11-15)

Achado ao montar a ficha do UI-04. **É o seu teste, não o produto.** Não mexi nele.

- **O quê.** O spec faz `cy.get('input[placeholder="example.com"]').type('wiremock:8080/health')`.
  O campo já tem "github.com", então o scan vai para `https://github.comwiremock:8080/health`.
  O banco de teste tem **21** desses, nota 15. É o UI-02 rodando.
- **Por que passa.** Duas coisas mascaram:
  1. O host inexistente ganha nota (BUG-16), então aparece "/100".
  2. `cy.contains('wiremock')` no histórico casa com "github.com**wiremock**".
- **O que mudar.** `.clear()` antes do `.type()`, e afirmar o alvo de verdade: o `url` da
  requisição `POST /scan/async` (interceptada) ou o host exato no histórico. Depois disso,
  vale conferir que a nota de `wiremock:8080/health` aparece.
- **Na matriz:** o UI-02 foi de ✅ para ⚠️ até a correção.
- **Depois do fix do BUG-16 (2026-11-22):** `github.comwiremock` não resolve. O
  `POST /scan/async` leva 422 e a tela mostra o aviso de domínio não encontrado, sem nota.
  O spec como está deve falhar no `cy.contains('/100')` (linha 17), depois dos 60 s do
  timeout. É o vermelho verdadeiro, e a correção continua a mesma. Não rodei o spec. O
  aviso eu conferi na tela com `nao-existe-qa.invalid`.

---

## Revisão: o PLAN-02 fica vermelho depois do BUG-20 (2026-11-23)

**É o contrato novo, não regressão.** O teste é seu; não mexi nele.

- **O quê.** O `PlanoCotaScanTest.deveBloquearScanAoAtingirCotaDiariaFree` faz até 15
  `GET /scan` seguidos com o qa-free e espera 403 em cada um até o 402. Desde o fix do
  BUG-20, o FREE tem 5 requisições por minuto. Rodei a suíte em 2026-11-23: 44 / 1 falha /
  2 pulados. A falha é essa: "Tentativa 6 era esperado 403 … but was: <429>".
- **Por que não é bug.** O rate limit vem antes da cota, então o 429 não gasta scan. O caso
  continua provando a mesma coisa: a 11ª chamada que passa do rate limit dá 402. Só não dá
  mais para fazer as 11 em sequência.
- **O que mudar (a direção; a forma é sua).** Tratar o 429 como "espere e repita a mesma
  chamada", sem contar como tentativa. O ritmo possível é 5 de uma vez e depois 1 a cada
  12 s, então as 10 da cota mais a do 402 levam cerca de 1 min e 10 s. Uma asserção a mais,
  se quiser: o `remainingScans` do `/auth/me` não muda com o 429 (o `/auth/me` não passa
  pelo rate limit).
- **Junto com o PLAN-04.** O preparar do PLAN-04 gasta a mesma cota, no mesmo ritmo. Na
  mesma classe, o PLAN-04 reaproveita a cota gasta do PLAN-02 e só precisa de ficha no
  balde para os passos 1 e 2 (ficha 16).
- **Na matriz:** o PLAN-02 foi de ✅ para ⚠️ até a correção.
