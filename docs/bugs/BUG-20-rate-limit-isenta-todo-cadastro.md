# BUG-20 — Rate limit por minuto isenta OWNER, e todo cadastro é OWNER

Achado lateral em 2026-11-17, durante o fix do BUG-19 (o gate de CI passou a chamar o rate
limit, e o rate limit não freia as contas do seed). **Aberto, não corrigido.** Formato
conforme `../template-bug.md`.

```markdown
## Resumo

`RateLimitService.allow` devolve `true` direto quando o papel é `OWNER` ("OWNER não tem
rate limit de requests por minuto"). Todo cadastro nasce `OWNER` da própria conta (os
dois caminhos de cadastro do `AuthService`). Então nenhuma
conta registrada tem limite por minuto. Só têm limite o visitante (5/min por IP) e os
membros de equipe (`FREE_EMPLOYEE` 60/min, `ADMIN` 120/min).

É a mesma raiz do BUG-18 e da promoção por papel que o `PlanLimitService.effectivePlan` já
deixou de fazer: o código trata `OWNER` como "dono da plataforma", mas `OWNER` é o dono da
conta. A equipe da plataforma sai do `PlatformStaffService`.

A isenção já estava anotada na matriz em 2026-10-05 (RATE-01 adiado), mas como contrato.
Naquela época ainda não se sabia que todo cadastro é `OWNER`.

## Severidade

P2. A cota diária continua freando o scan do FREE (10/dia). O que se perde é o freio por
minuto:

- `GET /scan/verify-check` faz uma requisição de saída e não tem cota diária. Sem limite
  por minuto, qualquer conta grátis manda a plataforma buscar o `/.well-known` de um host
  público quantas vezes quiser.
- PRO e ENTERPRISE têm cota ilimitada: só o limitador de concorrência segura o disparo
  de scans.

## Ambiente

- Commit/versão testada: Backend com o fix do BUG-19 (HANDOFF §12, Etapa I)
- Ambiente: docker-compose de teste, backend 8081; alvo `wiremock` (local). Nunca produção.
- Ferramenta que encontrou: leitura do código no fix do BUG-19, confirmada ao vivo

## Passos para reproduzir

1. Visitante: `GET /scan/verify-check?host=wiremock` oito vezes no mesmo minuto.
2. qa-free (papel `OWNER`, plano FREE, token forjado): o mesmo pedido doze vezes.

## Resultado esperado

2. Um limite por minuto também para conta registrada. O valor é decisão do dono (abaixo).

## Resultado obtido

1. 200 cinco vezes, depois 429 nas três seguintes.
2. **200 nas doze.**

## Causa raiz

`RateLimitService.allow`, primeira linha: `if (currentUser != null && currentUser.getRole()
== Role.OWNER) return true;`. O `resolveRpm` também decide por papel: quem não é `ADMIN` nem
`FREE_EMPLOYEE` cai nos 5/min do visitante. Sem a isenção, todo cadastro ficaria com 5/min,
inclusive o PRO.

## Correção

A fazer. **Pede decisão do dono:** quantas requisições por minuto para cada plano. Sugestão:
decidir pelo plano efetivo (`effectivePlan`), como as outras travas, e não pelo papel.
Exemplo: FREE 5/min, PRO 60/min, ENTERPRISE 120/min, equipe da plataforma isenta.

Efeitos no QA quando for corrigido:
- A armadilha do SSRF-07 ("use o token do qa-pro, que é OWNER e não tem limite por
  minuto", HANDOFF §10 e ficha 2) muda de motivo. Com o PRO a 60/min, as variantes ainda
  cabem no minuto; com o FREE a 5/min, a 6ª daria 429.
- O RATE-01 fica observável sem semear usuário novo (decisão B de 2026-10-05). O
  `verify-check` não tem cota diária, então não há colisão: com o qa-free, a 6ª chamada no
  minuto dá 429, e depois do refill volta 200.

## Teste de regressão

A escrever junto com a correção. Caso de QA candidato: RATE-01, retomado com o qa-free.
```
