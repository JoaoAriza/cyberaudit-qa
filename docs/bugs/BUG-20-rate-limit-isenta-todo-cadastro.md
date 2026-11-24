# BUG-20 — Rate limit por minuto isenta OWNER, e todo cadastro é OWNER

Achado lateral em 2026-11-17, durante o fix do BUG-19 (o gate de CI passou a chamar o rate
limit, e o rate limit não freia as contas do seed). **✅ Corrigido em 2026-11-23** (HANDOFF
§12, Etapa L). Formato conforme `../template-bug.md`.

```markdown
## Resumo

`RateLimitService.allow` devolvia `true` direto quando o papel era `OWNER` ("OWNER não tem
rate limit de requests por minuto"). Todo cadastro nasce `OWNER` da própria conta (os
dois caminhos de cadastro do `AuthService`). Então nenhuma
conta registrada tinha limite por minuto. Só tinham limite o visitante (5/min por IP) e os
membros de equipe (`FREE_EMPLOYEE` 60/min, `ADMIN` 120/min), e esses pelo papel, não pelo
plano: um membro de conta FREE tinha 60/min.

É a mesma raiz do BUG-18 e da promoção por papel que o `PlanLimitService.effectivePlan` já
deixou de fazer: o código trata `OWNER` como "dono da plataforma", mas `OWNER` é o dono da
conta. A equipe da plataforma sai do `PlatformStaffService`.

A isenção já estava anotada na matriz em 2026-10-05 (RATE-01 adiado), mas como contrato.
Naquela época ainda não se sabia que todo cadastro é `OWNER`.

## Severidade

P2. A cota diária continua freando o scan do FREE (10/dia). O que se perdia era o freio
por minuto:

- `GET /scan/verify-check` faz uma requisição de saída e não tem cota diária. Sem limite
  por minuto, qualquer conta grátis mandava a plataforma buscar o `/.well-known` de um host
  público quantas vezes quisesse.
- PRO e ENTERPRISE têm cota ilimitada: só o limitador de concorrência segurava o disparo
  de scans.

## Ambiente

- Commit/versão testada: Backend `efb73db` (fix do BUG-16, HANDOFF §12, Etapa K)
- Ambiente: docker-compose de teste, backend 8081; alvo `wiremock` (local). Nunca produção.
- Ferramenta que encontrou: leitura do código no fix do BUG-19, confirmada ao vivo

## Passos para reproduzir

1. Visitante: `GET /scan/verify-check?host=wiremock` oito vezes no mesmo minuto.
2. qa-free (papel `OWNER`, plano FREE, token forjado): o mesmo pedido doze vezes.
3. qa-pro (PRO) e qa-enterprise: o mesmo pedido 65 e 130 vezes.

## Resultado esperado

Um limite por minuto também para conta registrada, pelo plano da conta.

## Resultado obtido

Medido em 2026-11-23, antes do fix:
1. Visitante: 200 cinco vezes, depois 429 nas três seguintes.
2. qa-free: **200 nas doze.**
3. qa-pro: 200 nas 65. qa-enterprise: 200 nas 130.

## Causa raiz

`RateLimitService.allow`, primeira linha: `if (currentUser != null && currentUser.getRole()
== Role.OWNER) return true;`. O `resolveRpm` também decidia por papel: quem não era `ADMIN`
nem `FREE_EMPLOYEE` caía nos 5/min do visitante. Sem a isenção, todo cadastro ficaria com
5/min, inclusive o PRO.

## Correção

Decisões do dono (2026-11-23):

- **Valores:** FREE 5/min, PRO 60/min, ENTERPRISE 120/min. Visitante continua com 5/min
  por IP.
- **Membros de equipe:** seguem o plano da conta, como a cota diária. O papel não muda
  mais o limite.
- **Equipe da plataforma** (`PLATFORM_STAFF_EMAILS`): isenta.

O que mudou no Backend (`RateLimitService`):
- O limite sai do `PlanLimitService.effectivePlan(user)`, a mesma fonte da cota diária e
  das travas de plano. A isenção é `PlanLimitService.isPlatformStaff(user)`. Saíram a
  isenção do `OWNER` e os limites por papel (`EMPLOYEE_RPM`, `ADMIN_RPM`).
- `rpmDoPlano` é um `switch` sem `default`: um plano novo no enum não compila até alguém
  decidir o limite dele.
- O limite entra na chave do balde (`user:<id>:<limite>`). Quem troca de plano passa a
  usar o balde do plano novo na próxima requisição, sem esperar reinício.
- O limite genérico do billing (`allow(chave, máximo, janela)`) não mudou.

Como o balde funciona (Bucket4j, inalterado): capacidade = o limite, reposição contínua
de 1 ficha a cada 60/limite segundos (12 s no FREE, 1 s no PRO, 0,5 s no ENTERPRISE). Um
balde por usuário, dividido entre `/scan`, `/scan/async`, `/scan/report`,
`/scan/report/pdf`, `/api-keys/ci` e `/scan/verify-check`. O rate limit roda antes da
cota, então um 429 não gasta scan. Os baldes ficam na memória: o restart do backend zera.

## Teste de regressão

- Backend:
  - `RateLimitPorPlanoIntegrationTest` (8), de ponta a ponta pelo `/scan/verify-check` e
    pelo `/scan`. O alvo é `127.0.0.1`: o SsrfGuard responde 403 logo depois do rate limit,
    então 403 = "passou", 429 = "barrou", e nada sai da máquina. Contra o código antigo
    (HEAD `efb73db`, constantes trocadas pelos números), 6 ficam vermelhos e os 2
    controles ficam verdes nos dois:
    - dono FREE: a 6ª dá 429 (antes: "nenhum 429 em 15 chamadas");
    - membro `FREE_EMPLOYEE` de conta FREE: a 6ª dá 429 (antes: nenhum em 15);
    - dono PRO: 60 passam e o 429 vem logo depois (antes: nenhum em 80);
    - membro de conta ENTERPRISE: 120 passam (antes: 429 na 61ª, o limite do papel);
    - FREE que esgotou o minuto e assina o PRO volta a passar na hora;
    - no `/scan`, a 6ª do FREE dá 429 e a cota fica em 5 (o 429 não gasta scan);
    - controles: a equipe da plataforma faz 130 sem 429; o visitante segue com 5 por IP.
  - No PRO e no ENTERPRISE o teste afirma "as N primeiras passam e o 429 vem em até 20
    a mais". O balde repõe 1 ficha por segundo no PRO, e uma rodada lenta de MockMvc ganha
    1 ou 2 no meio. No FREE (12 s por ficha) a 6ª é exata.
  - `RateLimitServiceTest` (13): os mesmos limites direto no serviço, com contagem exata
    (os laços levam microssegundos), mais ADMIN de conta PRO (60, não 120), conta sem
    plano gravado (FREE), downgrade PRO → FREE, balde por usuário e o limite do billing.
    Usa o construtor novo e não compila contra o código antigo.
  - `ApiKeyCiGateLimitesIntegrationTest.rateLimitValeNoGate` (BUG-19) trocou o membro de
    conta ENTERPRISE a 60/min por um membro de conta FREE: a 6ª no gate dá 429.
  - Suíte do Backend: 838 → 859, verde.
- Ao vivo (8081). Antes: imagem do Backend `efb73db`. As rajadas "depois" usam um curl só,
  com várias URLs na mesma conexão. Um curl por chamada leva ~150 ms no Windows, e o PRO
  repõe 1 ficha por segundo: a medida lenta nunca chega ao 429.

  | Chamadas no mesmo minuto ao `verify-check` | Antes | Depois |
  |---|---|---|
  | Visitante, 8 | 200×5, 429×3 | 200×5, 429×3 |
  | qa-free (FREE), 12 | **200×12** | 200×5, 429×7 (1º na 6ª) |
  | qa-pro (PRO), 65 / 75 | 200×65 | 200×60, 429×15 (1º na 61ª), 75 em 1,2 s |
  | qa-enterprise (equipe da plataforma), 130 / 150 | 200×130 | 200×150 |
  | qa-free no `/scan` logo depois da rajada | 403 (passou) | **429**, cota 10/10 intacta |

  Também conferido depois do fix:
  - O 429 do qa-free: `{"error":"429 TOO_MANY_REQUESTS","message":"Muitas requisições.
    Tente novamente em alguns segundos."}`, sem `Retry-After`. O texto sai em português
    mesmo com `Accept-Language: en`, como todas as mensagens desse tipo hoje.
  - O qa-pro logo depois do 429 do qa-free: 200 (o balde é por usuário, não por IP).
  - 13 s depois do 429: 200 (uma ficha reposta); a chamada seguinte, 429 de novo.
  - `juice-shop:3000` no lugar do `wiremock`: o mesmo, 200×5 e 429 na 6ª.
  - No 8099 (imagem antiga): qa-free com `juice-shop:3000`, 200 nas 8. É o vermelho do
    RATE-01 sem buildar nada. O `wiremock` não serve lá: o 8099 barra com 403 já na 1ª.
  - RestAssured do dono: 44 / **1 falha** / 2 pulados. A falha é o PLAN-02
    (`PlanoCotaScanTest`): "Tentativa 6 era esperado 403 … but was: <429>". É o contrato
    novo, não regressão. Ver "O que mudou nas fichas".
- Caso de QA: **RATE-01** (P1), retomado com o qa-free, sem seed novo: contrato no
  HANDOFF §12, Etapa L; ficha 19.
```

## O que mudou nas fichas com o fix

- **PLAN-02 (verde → vermelho):** o `PlanoCotaScanTest` faz 15 `/scan` seguidos com o
  qa-free e espera 403 até o 402. A 6ª agora dá 429. A correção é do dono: tratar o 429
  como "espere e repita a mesma chamada", sem contar como tentativa. O 429 não gasta cota,
  e o ritmo possível é 5 de uma vez e depois 1 a cada 12 s, então o caso passa a levar
  cerca de 1 min e 10 s.
- **PLAN-04:** o preparar (gastar a cota do qa-free) segue o mesmo ritmo, e os passos 1 e
  2 precisam de ficha no balde. A armadilha "não afirme o rate limit do gate" mudou de
  motivo: agora daria para afirmar, mas é o RATE-01 que cobre.
- **SSRF-07:** continua com o qa-pro, por outro motivo. As 8 chamadas cabem nos 60/min do
  PRO. Com o qa-free (5/min), a 6ª daria 429.
- **SCAN-13:** a parte A usa o qa-free. Logo depois do PLAN-02 ou do PLAN-04, o balde dele
  pode estar vazio: espere 12 s.
- **RATE-01:** ficha nova (19). Sai do ⏸️ sem a decisão B (semear membro de equipe): o
  `verify-check` não tem cota diária, então o qa-free mostra o 429 e a volta a 200 sem
  colidir com nada.
