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
- Depois de reiniciar o Docker, `juice-shop` e `backend-noallowlist` não sobem sozinhos:
  `docker compose -f env/docker-compose.test.yml up -d --no-build juice-shop backend-noallowlist`.

**Ver o vermelho dos quatro casos de segurança.** O `backend-noallowlist` (8099,
`base.uri.noAllowlist`) roda uma imagem de antes da rodada de bugs. Conferido hoje:

| Caso | No 8081 (corrigido) | No 8099 (imagem antiga) |
|---|---|---|
| SSRF-03 hex | 403 `DOMAIN_BLOCKED` | **200**, o scan roda em `0x7f000001` |
| SSRF-07 | 403 `DOMAIN_BLOCKED` | **200** com `127.0.0.1:8081` e com `127.0.0.1#` |
| AUTH-05 | chave válida → 200 | chave válida → **401** (BUG-01) |
| AUTH-04 | código repetido → 422 | código repetido → **200** (BUG-04) |

Rode cada um desses uma vez contra o 8099, veja falhar e volte para o 8081. **Só depois**
rebuilde o 8099 (`up -d --build backend-noallowlist`): o rebuild apaga esse vermelho. Os
casos de scan não dão para ver assim: o 8099 não tem allowlist e barra todo alvo local.

Para os casos de scan, quando a ficha diz "imagem do commit X", o vermelho de verdade
pede o `backend` buildado no commit de antes do fix. Isso é trabalhoso: dar checkout do
commit no Backend, rebuildar, rodar e depois voltar ao HEAD e rebuildar de novo. É
opcional. Se quiser essa prova num caso, peça que eu preparo (é infra). Fora isso, inverter
uma expectativa uma vez mostra que a asserção sabe falhar.

**Cache de scan.** Corrige o que o HANDOFF dizia:
- A chave é **host + modo + idioma** e vale **2 minutos**, não "até reiniciar o backend".
  O mesmo host em pt-BR e em en não pede reinício.
- Caminho e query **ficam fora da chave** (BUG-17, aberto). `juice-shop:3000/` e
  `juice-shop:3000/rest/products/search?q=apple` no mesmo modo dividem o resultado por 2 min.
- Por isso, repetir uma classe de scan em menos de 2 min devolve o resultado guardado. É
  rápido, mas não testa de novo. Para testar de novo: espere 2 min ou
  `docker compose -f env/docker-compose.test.yml restart backend`. O restart também zera o
  throttle do 2FA e os rate limits.
- A tela chama `POST /scan/async?…&refresh=true`, que fura o cache. O UI-04 não sofre com isso.

**Lendo o resultado do scan.**
- `score` é um objeto: `score.score`, `score.riskLevel`, `score.notes` (lista de texto) e
  `score.issues` (lista com `id`, `severity` e `title`).
- Nota e título saem no idioma da requisição. Mande `Accept-Language` em todo scan e afirme
  issue pelo `id`; nota, pelo começo do texto e pelo número do fim.
- `moduleStatus` "OK" **não prova** que o módulo falou com o alvo (BUG-16, aberto). Um host
  que não existe dá 14 módulos "OK". A prova de contato é `httpStatus`.
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

**Ver o vermelho.** `base.uri.noAllowlist`: 200 (o scan roda).

**Conferido em 2026-11-15.** `0x7f000001` e `0177.0.0.1` → 403 `DOMAIN_BLOCKED`.

---

### 2. SSRF-07 (P1) · `SsrfVerifyCheckTest`

**O que prova e por quê.** Que o guard valida o mesmo host em que o servidor vai conectar.
O guard lia `127.0.0.1:8081` como "não resolve, deixa passar", e o cliente HTTP lia como
"127.0.0.1 na porta 8081". O endpoint é público, então qualquer visitante fazia o servidor
bater na rede interna.

**Preparar.** Nada. Use o token do qa-pro: visitante tem 5 req/min por IP, e a 6ª variante
já levaria 429. O qa-pro é `OWNER` e não tem limite por minuto.

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
- Todo valor de `moduleStatus` = `OK`. Hoje são 15 módulos; afirme "nenhum diferente de
  OK", não a contagem.

**Armadilhas.**
- **Só "todo módulo OK" não basta** (BUG-16): `http://nao-existe-qa.invalid` também dá 14
  módulos OK, com `httpStatus` 0. Sem a asserção do `httpStatus`, o caso passa sem o alvo
  ter respondido.
- `openPorts: []` é esperado (a 3000 não está na lista de portas de risco). Não afirme
  porta aberta.

**Ver o vermelho.** Não há imagem antiga à mão. Para ver a asserção falhar, troque o alvo
uma vez por `http://juice-shop:3999/` (porta fechada): `httpStatus` 0.

**Conferido em 2026-11-15.** `httpStatus` 200, 15 módulos OK, 22 s.

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

- Em todos, antes das flags: `url` = a URL pedida (por causa do BUG-17).
- No controle, o `xssProbePerformed` true é o que separa "a sonda rodou e viu o escape" de
  "a sonda nem rodou". Sem ele, o false passa à toa.

**Armadilhas.**
- O teste atual afirma XSS **e** SQLi no Juice Shop. A parte de XSS está errada ali, e o
  produto está certo: o `/rest/products/search` não ecoa o `q` no servidor (o XSS de busca
  do Juice Shop é DOM, no navegador). Lá, `reflectedXssSuspected` é false.
- **Cache (BUG-17):** se outro teste da execução escanear o Juice Shop em modo ativo em
  outro caminho até 2 min antes, o SQLi recebe o resultado dele (`url` errada, SQLi
  false). Medido: 9 ms, sem sondar. A asserção da `url` transforma esse falso negativo em
  falha clara.
- O stub responde só em `/busca` e só para os dois hosts. Outro caminho dá 404 e
  `inputSurfaceDetected` false, e aí as sondas nem rodam.
- Notas em pt-BR (se for afirmar): "Possível exposição de erro de banco (modo ativo): -15" e
  "Suspeita de Reflected XSS (marcador refletido): -25".

**Ver o vermelho.** O controle é o próprio vermelho da sonda: o mesmo teste do XSS
apontado para `eco-escapado-mock` falha (false).

**Conferido em 2026-11-15.** SQLi true (XSS false ali); `eco-mock` → XSS true, HIGH, -25;
`eco-escapado-mock` → `xssProbePerformed` true, XSS false, sem issue. O diário do WireMock
mostra a sonda chegando nos dois (`/busca?q=applecyberaudit<id><">`).

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
- **O NVD oscila. Hoje uma rodada deu `cveFindings: []`.** O NVD levou 12 s, e o timeout do
  backend é 12 s. O `moduleStatus.CVE` disse "OK" mesmo assim (BUG-16). Com `[]`, as três
  negativas passam sem testar nada.
- Saída recomendada: tratar a presença do `CVE-2007-3205` como **pré-condição** com
  `Assumptions.assumeTrue(...)` do JUnit. Sem ela, o teste sai **pulado** ("não deu para
  testar"), em vez de verde falso ou vermelho de rede. A alternativa é afirmar a presença
  como asserção comum e aceitar um vermelho de vez em quando por causa do NVD.
- Não fixe a lista inteira de CVEs nem o score: o NVD muda.
- Cache: a rodada vazia fica guardada por 2 min. Rodar de novo logo em seguida devolve o
  mesmo vazio.

**Ver o vermelho.** Imagem do commit `6fd3a95` (o último antes do fix do BUG-08):
`CVE-2013-2220` na lista e a issue `CVE_CVE_2013_2220`.

**Conferido em 2026-11-15.** PHP 8.3.36. Rodada 1: `[]` com CVE "OK". Rodada 2 (2 min
depois): `["CVE-2007-3205"]`. Sem issue nem nota de CVE nas duas.

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
