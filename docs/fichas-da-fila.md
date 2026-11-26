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

---

### 6. AUTH-04 (P0) · `TotpReplayTest`

**O que prova e por quê.** Que um código TOTP vale uma vez. Quem observar um código (por
cima do ombro, num log, num proxy) não consegue abrir uma segunda sessão com ele.

**Preparar.**
- Dependência nova no `pom.xml` do RestAssured, escopo `test`:
  `dev.samstevens.totp:totp:1.7.1`. É a mesma família que o Backend usa (lá é o starter do
  Spring; aqui é a lib pura). Conceito: o pom é a lista de bibliotecas que o Maven baixa. Sem
  a linha, o `import` não compila.
- Gerar o código: `DefaultCodeGenerator` com `HashingAlgorithm.SHA1`, método
  `generate(segredo, contador)`. O contador é `segundos desde 1970 / 30`, que é a janela
  de 30 s.
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

**Armadilhas.**
- **A janela pode virar entre gerar e usar.** Se faltar menos de ~5 s para virar a janela,
  espere a próxima antes de gerar o N. O replay só é replay se for o mesmo código.
- Rodar o teste duas vezes em menos de 30 s quebra o passo 3 da segunda execução (o N já foi
  usado). Não é bug.
- Throttle: 5 falhas → 429 por 15 min para o usuário. A ordem acima (falha, sucesso, falha,
  falha) não chega lá, porque um sucesso zera a contagem. `restart backend` também zera.
- Na CI, RestAssured → Newman → Cypress rodam no mesmo backend. Usuários diferentes evitam
  colisão de janela. Não troque para o qa-pro.

**Ver o vermelho.** `base.uri.noAllowlist`: o passo 4 dá 200 (BUG-04). Mesmo banco, mesmo
usuário.

**Conferido em 2026-11-15.** Os cinco passos com os status e mensagens acima.

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

**Armadilhas.** A ordem da lista segue a dos headers e não é contrato: procure pelo nome. O
título ("2 cookie(s) com problemas de segurança") depende do idioma; afirme `id` e
`severity`.

**Ver o vermelho.** Imagem do commit `2a18641` (o último antes do fix do BUG-12):
`XSRF-TOKEN` HIGH e `INSECURE_COOKIES` MEDIUM. Sem buildar, inverta uma expectativa uma
vez para ver a mensagem de falha.

**Conferido em 2026-11-15.** Exatamente o contrato.

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

**Ver o vermelho.** Imagem do commit `9f9e84e` (antes do BUG-10): `quarantine` sai MEDIUM.

**Conferido em 2026-11-15.** A tabela inteira.

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
- Não fixe a lista inteira de CVEs nem o score: o NVD muda.
- Cache: a rodada vazia fica guardada por 2 min. Rodar de novo logo em seguida devolve o
  mesmo vazio.

**Ver o vermelho.** Imagem do commit `6fd3a95` (o último antes do fix do BUG-08):
`CVE-2013-2220` na lista e a issue `CVE_CVE_2013_2220`.

**Conferido em 2026-11-15.** PHP 8.3.36. Rodada 1: `[]` com CVE "OK". Rodada 2 (2 min
depois): `["CVE-2007-3205"]`. Sem issue nem nota de CVE nas duas. Em 2026-11-22:
`moduleStatus.CVE` OK e `["CVE-2007-3205"]`.

---

### 10. SCAN-08 (P2) · `LaudoStubsTest`

**O que prova e por quê.** Que HSTS ausente custa -10 mas é rotulado MEDIUM. O HIGH
acionava o piso de risco, e um site nota 90 sem outro achado saía "risco médio" (BUG-11).

**Pedido.** `GET /scan?url=http://sem-hsts-mock:8080&active=false`, qa-pro, pt-BR.

**Afirmar.** `headers["Strict-Transport-Security"]` = `MISSING`; issue `HSTS_MISSING` com
`severity` MEDIUM; nota de HSTS terminando em `-10` ("HSTS ausente: -10").

**Armadilhas.** O piso de risco em si não dá para testar aqui: o alvo sem HTTPS já leva o
risco a HIGH. Ele está coberto no Backend (`HstsAusenteRotuloTest`). No GPath do
RestAssured, a chave com hífen vai entre aspas: `headers.'Strict-Transport-Security'`.

**Conferido em 2026-11-15.** `MISSING`, MEDIUM, "HSTS ausente: -10".

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
