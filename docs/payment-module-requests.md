# Módulo de pagamento — checkout transparente (Pix + cartão)

Registrado em 2026-09-29, conforme implementado no Backend (`BillingController` /
`BillingService` / `MercadoPagoService`). Este documento existe para você importar
no Postman e escrever os casos REST Assured contra o comportamento real — não é
espec, é o que o código faz hoje.

**Atualizado em 2026-09-30 — Pix e cartão testados ao vivo, em produção, com
dinheiro real.** Pix confirmado de ponta a ponta (pagamento aprovado → webhook →
plano liberado, automático). Cartão confirmado até a rejeição (cartão de teste
público do MP recusado em produção, como esperado); autorização de verdade ainda
não testada — falta credencial `TEST-` ou um cartão real. Ver
`Backend/HANDOFF.md` seção 2 para o relato completo, incluindo três problemas de
configuração no painel do MP que não tinham nada a ver com o código (URL do
webhook errada, evento "Pagamentos" não assinado, secret desatualizado no Render).

---

## Autenticação

Todos os endpoints de checkout exigem `Authorization: Bearer <JWT>`, obtido via:

```http
POST /auth/login
Content-Type: application/json

{ "email": "cliente@example.com", "password": "senha" }
```

A resposta traz `token` — use em todas as chamadas abaixo.

---

## 1. `POST /billing/checkout/card` — assinatura via cartão tokenizado

Sem redirecionamento: o `cardTokenId` já vem gerado pelo SDK do Mercado Pago no
navegador (`mp.cardForm()` → `createCardToken`). Este endpoint nunca recebe número
de cartão, CVV ou validade — só o token.

**Request**

```http
POST /billing/checkout/card
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "plan": "PRO",
  "cardTokenId": "e3ed6f098462036dd2cbabe314b9de2a"
}
```

- `plan`: opcional — `PRO` ou `ENTERPRISE`. Omitido, usa o padrão do tipo de conta
  (COMPANY → ENTERPRISE, resto → PRO).
- `cardTokenId`: obrigatório. Token de uso único, expira em 7 dias.

**Response 200 (authorized na hora — caso comum com cartão de teste aprovado)**

```json
{
  "id": "<uuid da Subscription>",
  "plan": "PRO",
  "status": "AUTHORIZED",
  "paymentMethod": "CARD",
  "currentPeriodEnd": null,
  "amount": 19.99,
  "currency": "BRL",
  "createdAt": "2026-09-29T14:00:00",
  "updatedAt": "2026-09-29T14:00:00"
}
```

`status` também pode vir `PENDING` (raro com cartão, mas possível). Se vier
`PENDING`, o plano NÃO foi liberado ainda — só o webhook (`type=preapproval`)
libera depois.

**Erros esperados**

| Cenário | Status | Corpo |
|---|---|---|
| `cardTokenId` ausente/vazio | 400 | `{"error":"400 BAD_REQUEST","message":"cardTokenId ausente..."}` |
| Plano inválido (`"plan":"FOO"`) | 400 | `{"error":"400 BAD_REQUEST","message":"Plano inválido: FOO..."}` |
| Conta já está no plano pedido | 409 | `{"error":"409 CONFLICT","message":"Sua conta já está no plano PRO."}` |
| ENTERPRISE tentando assinar PRO | 409 | `{"error":"409 CONFLICT","message":"Para trocar de ENTERPRISE para PRO..."}` |
| Sem token (não logado) | 401 | — |
| > 5 tentativas/minuto por usuário | 429 | `{"error":"429 TOO_MANY_REQUESTS","message":"Muitas tentativas de checkout..."}` |
| `MP_ACCESS_TOKEN` não configurado | 503 | `{"error":"503 SERVICE_UNAVAILABLE","message":"Pagamentos indisponíveis..."}` |
| Mercado Pago recusa o cartão (rejected) | 502 | `{"error":"502 BAD_GATEWAY","message":"Mercado Pago retornou ..."}` |

---

## 2. `POST /billing/checkout/pix` — pagamento único (1 ciclo de 30 dias)

Pix comum não tem débito automático — cada mês é um pagamento novo. O plano só é
liberado quando o webhook confirmar `status=approved` no Mercado Pago.

**Request**

```http
POST /billing/checkout/pix
Authorization: Bearer {{jwt}}
Content-Type: application/json

{
  "plan": "PRO",
  "cpf": "111.444.777-35"
}
```

- `cpf`: com ou sem pontuação — o backend normaliza (remove tudo que não é
  dígito), exige exatamente 11 dígitos **e valida o dígito verificador** (módulo
  11, `CpfUtil`, desde 2026-09-29 — antes só checava o tamanho). Ver PAY-13.

**Response 200**

```json
{
  "subscriptionId": "<uuid>",
  "paymentId": "5466310457",
  "status": "pending",
  "qrCode": "00020126600014br.gov.bcb.pix...",
  "qrCodeBase64": "iVBORw0KGgoAAAANSU...",
  "ticketUrl": "https://www.mercadopago.com.br/payments/5466310457/ticket..."
}
```

`status` vem sempre `"pending"` (minúsculo — é o valor cru que o MP devolve, não
passa pelo enum `SubscriptionStatus`). O plano da conta continua `FREE` até o
webhook confirmar.

**Erros esperados**

| Cenário | Status |
|---|---|
| CPF com tamanho ≠ 11 dígitos (após normalizar) | 400 |
| Mesmas regras de plano/conflito do checkout de cartão | 400/409 |
| MP não devolve QR code (`qr_code` e `qr_code_base64` nulos) | 502 |
| > 5 tentativas/minuto por usuário | 429 |

---

## 3. `GET /billing/subscription` — estado atual (sem mudança de endpoint, campos novos)

```http
GET /billing/subscription
Authorization: Bearer {{jwt}}
```

Resposta agora inclui `paymentMethod` (`CARD`/`PIX`) e `currentPeriodEnd` (só
preenchido para Pix, `null` para cartão — cartão renova sozinho, não tem prazo).
`204 No Content` se a conta nunca teve assinatura.

---

## 4. Webhook — `POST /billing/webhook` (dois topics agora)

Endpoint público, protegido por HMAC (`x-signature`). Simular localmente exige
calcular a assinatura manualmente — ver script de exemplo abaixo.

**Topic `payment` (Pix) — novo**

```http
POST /billing/webhook?type=payment&data.id=5466310457
x-signature: ts=1735500000,v1=<hmac calculado>
x-request-id: <qualquer valor>
```

Dispara `BillingService.handlePaymentWebhook`, que consulta
`GET /v1/payments/{id}` no Mercado Pago (nunca confia no corpo da notificação) e
só libera o plano se `status == "approved"` E o valor cobrir o preço da tabela.

**Topic `preapproval` (cartão) — já existia, sem mudança de comportamento**

```http
POST /billing/webhook?type=preapproval&data.id=<preapproval_id>
```

**Cálculo da assinatura para teste local** (mesmo algoritmo que `BillingController.verifySignature`):

```
manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";"
v1 = HMAC-SHA256(manifest, MP_WEBHOOK_SECRET) em hex minúsculo
header x-signature = "ts=" + ts + ",v1=" + v1
```

---

## Casos da matriz

Os casos PAY-01 a PAY-15 (os 12 originais + PAY-13/14/15, novos a partir de bugs
reais corrigidos em 2026-09-30) agora vivem consolidados em
`docs/casos-de-teste.md`, junto com os 26 casos do escopo original — essa tabela
aqui foi removida pra não ter duas fontes divergentes.

---

## O que ainda falta validar

1. **Cartão autorizado de verdade** — confirmado até a rejeição (cartão de teste
   público do MP, recusado em produção de propósito). Falta uma credencial `TEST-`
   de sandbox (aba "Credenciais de teste" do painel MP) ou um cartão real de
   alguém pra ver `status: AUTHORIZED` de verdade.
2. **Cartões de teste do MP Brasil** (número, CVV, validade, e os que simulam
   recusa) — confirmar a URL certa da documentação antes da Fase 3.
3. **Webhook em ambiente de teste/sandbox** — em produção, precisou corrigir três
   coisas no painel do MP que não têm nada a ver com código: URL do webhook
   apontando pro Frontend em vez da API, evento "Pagamentos" não habilitado (só
   "Planos e assinaturas" estava), e `MP_WEBHOOK_SECRET` desatualizado no Render.
   Replicar essa configuração (URL, eventos, secret) pro ambiente/app de teste
   quando a Fase 1 (Docker) for montada — não assumir que "credencial de teste
   configurada" é suficiente sem checar os três.
4. ~~CSP do frontend precisa liberar sdk.mercadopago.com...~~ — feito, confirmado
   em produção (`Frontend/vite.config.ts`, `cspPlugin`). Vale como referência pra
   um teste de segurança automatizado (SSRF-06 já cobre a allowlist; um caso
   equivalente pra CSP do checkout é candidato a entrar na matriz).
