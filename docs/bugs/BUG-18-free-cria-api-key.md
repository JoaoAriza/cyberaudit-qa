# BUG-18 — Conta FREE cria API key

Achado lateral em 2026-11-15, ao reconferir o contrato do AUTH-05. Corrigido em
2026-11-17 (primeira etapa da rodada da HANDOFF §12). Formato conforme `../template-bug.md`.

```markdown
## Resumo

Toda conta FREE cria API key: `POST /api-keys` → 201 com `plainKey`, e a tela mostra o
painel liberado com "+ Gerar Key". O código dos dois lados tem uma trava de plano ("API
Keys requerem plano PRO…", cartão "Disponível para planos PRO e contas Empresa" com selo
PRO), mas ela nunca é alcançada.

## Severidade

P1 — recurso que o código trata como pago (automação por CI/CD) liberado no plano grátis.

## Ambiente

- Commit/versão testada: Backend `d0f9594`, Frontend `c7006bc`
- Ambiente: docker-compose de teste, backend 8081, frontend 8090. Nunca produção.
- Ferramenta que encontrou: reconferência ao vivo do contrato do AUTH-05

## Passos para reproduzir

1. qa-free (plano FREE no seed, papel `OWNER` como todo cadastro), token forjado:
   `POST /api-keys` `{"name":"qualquer-nome-unico"}`.
2. Na tela, logado como qa-free: "⚙ Segurança" → cartão "API Keys / CI-CD".

## Resultado esperado

1. **402** "API Keys requerem plano PRO ou Empresa.".
2. O cartão travado (selo PRO, "Disponível para planos PRO e contas Empresa.").

## Resultado obtido

1. **201**, chave ativa.
2. O painel liberado, com "+ Gerar Key".

## Causa raiz

A mesma leitura errada dos dois lados: tratar `OWNER` como papel da plataforma. `OWNER` é
o dono da conta, e todo cadastro nasce com ele (`AuthService`, nos dois caminhos).

- Backend: `ApiKeyService.checkApiKeyAccess` saía cedo para `OWNER`/`ADMIN` e para conta
  `COMPANY` em qualquer plano. O ramo do FREE só alcançava `FREE_EMPLOYEE` fora de conta
  Empresa, ou seja, ninguém.
- Frontend: `canApiKeys = isAdmin() || COMPANY || PRO || ENTERPRISE`, e o `isAdmin()` do
  `AuthContext` é `role === "OWNER" || role === "ADMIN"`.
- Além disso, "Empresa" em qualquer plano era um segundo desvio: o cadastro deixa escolher
  conta Empresa no FREE.

O `PlanLimitService.checkActiveScan` e o javadoc do `Plan` já avisavam ("NÃO usar
Role.OWNER aqui", "O role NÃO promove plano"); a API key ficou de fora.

O contrato da Etapa 1 do HANDOFF ("qa-free não cria chave, FREE → 402") e a primeira versão
deste bug ("a tela mostra disponível no PRO") vieram da mesma leitura do código, sem
conferir ao vivo. Na tela, o FREE via o painel liberado.

## Correção

Corrigido em 2026-11-17. Regra decidida pelo dono: **criar chave é de plano pago**.

- Backend: `checkApiKeyAccess` decide só por `planLimitService.effectivePlan(user)`: FREE →
  402. PRO e ENTERPRISE criam; a equipe da plataforma também (o `effectivePlan` dela é
  ENTERPRISE). Papel e tipo de conta não entram.
- Frontend: `src/apiKeys.ts`, com a mesma regra (`podeCriarApiKey`) e o estado do painel
  (`painelApiKey`):
  - `completo` para quem pode criar;
  - `so-lista` para quem não pode, mas tem chave ativa (sem o formulário, com o aviso e o
    "Revogar");
  - `travado` quando não há chave ativa.
- **Chave que já existe continua valendo** (decisão do dono, para não quebrar pipeline de
  quem já usa). Ela autentica, aparece na lista e pode ser revogada; só a criação é travada.

Verificado ao vivo no docker-compose em 2026-11-17:

- Antes do rebuild: qa-free → 201 e painel liberado.
- Depois do rebuild: qa-free → 402; qa-pro e qa-enterprise → 201.
- Chave antiga: qa-free posto em PRO, cria chave, volta para FREE. A chave autentica (200),
  uma chave nova é recusada (402) e a tela mostra só a lista, com "Revogar". Revogada a
  chave, a tela volta ao cartão travado. Os planos do seed foram restaurados.

## Teste de regressão

- Backend: `ApiKeyPlanoIntegrationTest` (6, rota real com a cadeia de filtros, todo usuário
  OWNER como no cadastro). Contra o código antigo, 3 vermelhos: FREE pessoal, Empresa no
  FREE e "criar de novo depois de voltar para o FREE". Os controles PRO, membro ENTERPRISE
  e equipe passam nos dois. Suíte inteira verde (793).
- Frontend: `apiKeys.test.ts` (9). Vitest 40 verdes; build (`tsc -b`) ok.
- QA: PLAN-03, a escrever pelo dono. Vermelho no 8099 (imagem antiga): qa-free → 201.
```

## O que o fix NÃO resolve

- **Chave antiga de conta FREE continua valendo, por decisão.** Inclui quem rebaixa de PRO
  para FREE. Até o BUG-19 ser corrigido (2026-11-17), essa chave rodava scan sem cota pelo
  gate de CI; agora o gate cobra a cota do plano da conta.
- O `isAdmin()` do Frontend continua com o mesmo nome e a mesma regra para outras telas (aba
  Admin, compliance). Lá ele quer dizer "dono ou admin da conta", que é o que essas telas
  pedem. Só a API key decidia plano por ele.
- O cardápio de planos (`/billing/plans`) não lista API key em nenhum plano. Se quiser que
  apareça na página de planos, é uma feature nova no catálogo.
