# BUG-11 — HSTS ausente rotulado HIGH travava o risco em MEDIUM

Encontrado em 2026-11-07 ao conferir o feedback de um cliente, que contestou o HSTS
ausente. O argumento dele estava invertido (ver "O que o cliente disse" abaixo), mas a
revisão achou o exagero real: o rótulo. Formato conforme `../template-bug.md`. Colar como
issue no GitHub quando for abrir.

```markdown
## Resumo

O `ScoreService` registrava `HSTS_MISSING` com severidade HIGH. Os -10 da nota estão
certos, mas qualquer issue HIGH aciona o piso de risco do score ("severity override"):
o `riskLevel` não pode ficar abaixo de MEDIUM. Resultado: um site sem nenhum outro achado,
nota 90 (faixa SECURE), saía "risco médio" só por não mandar HSTS.

Junto, os textos se contradiziam: o HSTS ausente recomendava `max-age=31536000`, o HSTS
fraco recomendava "max-age >= 2592000" — o mesmo laudo dava duas metas.

## Severidade

P2 — rótulo e nível de risco errados no laudo, diante do cliente, sem mudar a nota. Atinge
todo site sem HSTS que não tenha outro achado HIGH.

## Ambiente

- Commit/versão testada: Backend `dc1c999`
- Ambiente: docker-compose de teste, backend 8081, alvo `sem-hsts-mock` (alias novo do
  WireMock). Nunca produção do CyberAudit (Regra 1).
- Ferramenta que encontrou: feedback de cliente + revisão de código

## Passos para reproduzir

1. qa-pro, `GET /scan?url=http://sem-hsts-mock:8080` (passivo).
2. Procurar `HSTS_MISSING` em `score.issues`.

## Resultado esperado

`HSTS_MISSING` com `severity` MEDIUM; nota `HSTS ausente: -10` em `score.notes`.

## Resultado obtido

`severity` HIGH (a nota já era -10). O alvo não tem HTTPS válido, então o risco geral sai
HIGH por outros achados e o efeito do piso não aparece aqui; ele aparece no teste do
Backend, com um site que só não tem HSTS (nota 90 → `riskLevel` MEDIUM em vez de SECURE).

## Caso de teste relacionado

SCAN-08 (novo, P2).

## Causa raiz

Severidade e peso andavam juntos por hábito: o achado custa 10, como os HIGH, e ganhou o
rótulo HIGH. Só que o rótulo tem efeito próprio — o piso de risco —, pensado para achado
explorável (XSS, erro de banco, CORS com credencial). HSTS ausente é endurecimento de
transporte: o ataque exige posição de rede (Wi-Fi público, proxy) e um acesso que comece
em http://. As ferramentas de mercado o classificam entre Low e Medium.

## Correção

Corrigido no Backend em 2026-11-11:

- `HSTS_MISSING` com severidade MEDIUM; o desconto continua -10;
- textos (pt-BR e en): as duas recomendações apontam o mesmo alvo, `max-age=31536000`
  (1 ano), e o mesmo piso, 2592000 (30 dias), abaixo do qual o laudo conta como fraco — o
  mesmo limite do `HeaderService`. O impacto do ausente explica a janela de verdade (todo
  acesso em http:// passa pelo redirecionamento em texto claro); o do fraco, que a proteção
  expira com o max-age.

Verificado ao vivo: `sem-hsts-mock` → `HSTS_MISSING` MEDIUM, nota -10, score igual (24).
Antes do fix, no mesmo alvo e na imagem anterior: HIGH.

## Teste de regressão

- Backend: `HstsAusenteRotuloTest` (6 — rótulo MEDIUM, -10 mantido, nota 90 fica SECURE,
  piso continua valendo para CSP ausente, HSTS fraco inalterado, textos alinhados nos dois
  idiomas) — 3 vermelhos antes do fix. Suíte inteira verde (738).
- QA: SCAN-08, a escrever pelo dono.
```

## O que o cliente disse, e por que não muda a correção

O feedback dizia que, sem HSTS, "sobra só a janela do primeiro acesso". É o contrário: sem
HSTS, **todo** acesso que começa em http:// (digitado, link antigo, favorito) passa pelo
redirecionamento 301 em texto claro. É o HSTS que reduz a janela ao primeiro acesso. Por
isso o achado continua valendo -10, e o texto do impacto agora diz isso.

## Decisões que valem registrar

- **-10 com rótulo MEDIUM.** Na etapa F a escada de pesos põe MEDIUM em 5–6; o HSTS ausente
  fica como exceção deliberada (é o achado de transporte mais comum e o Mozilla Observatory
  tira até mais). Se a etapa F quiser a escada sem exceção, o número a rever é este.

## O que o fix NÃO resolve

- `CSP_MISSING` também é HIGH (-10) e aciona o mesmo piso. O argumento é parecido (o CSP é
  defesa em profundidade contra XSS, não falha em si), mas não estava nesta etapa —
  decisão do dono.
- O status `WEAK (max-age=0 deletes HSTS)` do `HeaderService` é literal em inglês, sem
  catálogo; sai assim no laudo em português. Cosmético, para a etapa G.
