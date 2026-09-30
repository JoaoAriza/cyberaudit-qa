# Template de bug report

Usar este formato pra todo bug encontrado no CyberAudit durante os testes —
como issue no GitHub (`cyberaudit-qa` ou no repositório principal, o que fizer
mais sentido pro bug em questão), com este conteúdo no corpo.

Bug real, corrigido, com teste de regressão é o que rende a melhor história de
entrevista do projeto (ver `plano-de-teste.md`, seção CI/CV) — vale o cuidado de
preencher direito, não só como formalidade.

---

```markdown
## Resumo

Uma frase: o que quebra e onde.

## Severidade

P0 / P1 / P2 — mesma escala da matriz de casos (`casos-de-teste.md`).

## Ambiente

- Commit/versão testada:
- Ambiente: local (`docker-compose.test.yml`) / staging — nunca produção (Regra 1)
- Ferramenta que encontrou: Postman / RestAssured / Cypress / Selenium / manual

## Passos para reproduzir

1.
2.
3.

## Resultado esperado

O que o comportamento correto seria.

## Resultado obtido

O que acontece de fato — payload, status HTTP, screenshot, o que for evidência.

## Caso de teste relacionado

ID da matriz (`casos-de-teste.md`) que cobre ou deveria cobrir isso, se existir.
Se o bug veio de um caso novo, criar o ID aqui e adicionar na matriz.

## Causa raiz (preencher após investigar)

Onde no código, e por quê.

## Correção

Link do commit/PR que corrigiu.

## Teste de regressão

Link do teste (RestAssured/Cypress/etc.) que agora cobre esse cenário — sem
isso a issue não fecha.
```

---

## Exemplo preenchido (referência de tom e nível de detalhe)

Baseado num bug real encontrado e corrigido em 2026-09-30 durante a validação do
checkout Pix (ver `Backend/HANDOFF.md` seção 2, item 9):

```markdown
## Resumo

GET /billing/subscription devolve a assinatura mais recente por data de criação,
não a mais recente autorizada — um Pix pago fica invisível se um Pix não-pago for
gerado depois.

## Severidade

P0 — esconde uma assinatura paga, e cancelSubscription tinha o mesmo problema:
podia rebaixar uma conta pagante pra FREE por engano.

## Ambiente

- Commit testado: 5f5cb24 (antes da correção)
- Ambiente: produção, testado com dinheiro real durante validação do checkout
- Ferramenta que encontrou: manual, testando o fluxo Pix de ponta a ponta

## Passos para reproduzir

1. Gerar um Pix e pagar de verdade (ou aguardar um AUTHORIZED existente)
2. Antes do webhook confirmar, gerar um SEGUNDO Pix pra mesma conta, sem pagar
3. Chamar GET /billing/subscription

## Resultado esperado

Devolver a assinatura AUTHORIZED (a paga), já que ela reflete o estado real da
conta.

## Resultado obtido

Devolve a segunda (PENDING, não paga) — `findFirstByAccountOrderByCreatedAtDesc`
ordena só por `createdAt`, sem considerar status.

## Caso de teste relacionado

PAY-15 (novo, criado a partir deste bug)

## Causa raiz

`BillingService.getSubscription`/`cancelSubscription` usavam
`findFirstByAccountOrderByCreatedAtDesc` sem filtrar por status.

## Correção

Commit "fix(billing): getSubscription e cancelSubscription priorizam a
assinatura authorized, nao a mais recente" — novo `currentSubscription()`
(prefere AUTHORIZED mais recente, cai pra qualquer status se não houver nenhuma).

## Teste de regressão

3 testes novos em `BillingServiceTest` (Backend) cobrindo o cenário — mas isso é
teste unitário do repo principal, não substitui PAY-15 em RestAssured contra a
API de verdade.
```
