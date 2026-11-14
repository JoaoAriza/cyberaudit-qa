# BUG-14 — Achado LOW custava o mesmo que achado MEDIUM

Encontrado em 2026-11-07 ao conferir o feedback de um cliente, que chamou de "cosméticos"
a versão exposta, o robots.txt e o security.txt e pediu para separar "risco explorável" de
"higiene". A separação já existe na severidade; o peso é que não acompanhava. Formato
conforme `../template-bug.md`. Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

No `ScoreService`, achado LOW custava de 3 a 5 pontos: versão exposta, robots.txt e
Referrer-Policy ausente tiravam 5, o mesmo que X-Frame-Options ausente ou HSTS fraco
(MEDIUM). No primeiro laudo do cliente, três itens de higiene somavam -13, mais que o
cookie de sessão sem HttpOnly (-10).

## Severidade

P2 — calibração: a nota sai mais baixa do que o risco justifica, sem dado falso no laudo.
Atinge todo scan com achado LOW.

## Ambiente

- Commit/versão testada: Backend `6a78573`
- Ambiente: docker-compose de teste, backend 8081, alvo `pesos-mock` (alias novo do
  WireMock: Server com versão, sem Referrer-Policy e Permissions-Policy, robots.txt com
  `/admin/` respondendo 403, sem security.txt). Nunca produção do CyberAudit (Regra 1).
- Ferramenta que encontrou: feedback de cliente + revisão de código

## Passos para reproduzir

1. qa-pro, `GET /scan?url=http://pesos-mock:8080` (passivo).
2. Ler as notas de desconto em `score.notes`.

## Resultado esperado

| Nota | Esperado |
|---|---|
| Versão de software exposta (Server/X-Powered-By) | -2 |
| Paths sensíveis em robots.txt | -2 |
| Referrer-Policy ausente | -1 |
| Permissions-Policy ausente | -1 |
| security.txt ausente | -1 |

Os cinco achados continuam em `score.issues`, com `severity` LOW.

## Resultado obtido

-5, -5, -5, -3 e -3, na ordem da tabela.

## Caso de teste relacionado

SCAN-10 (novo, P2).

## Causa raiz

Cada desconto foi escolhido no seu bloco, sem régua comum: o número não derivava da
severidade que o próprio achado declarava.

## Correção

Corrigido no Backend em 2026-11-13. Nenhum LOW passa de 2, em dois degraus (constantes
`LOW_INFORMA` e `LOW_BOA_PRATICA` no `ScoreService`):

| Achado (LOW) | Antes | Depois | Degrau |
|---|---|---|---|
| Versão exposta (Server/X-Powered-By) | -5 | -2 | entrega informação (alimenta a busca de CVE) |
| Caminhos sensíveis no robots.txt | -5 | -2 | entrega informação (mapa de áreas) |
| Método HTTP LOW (TRACE; método atrás de auth) | -3 | -2 | entrega informação (o eco traz os headers do proxy) |
| UI de documentação de API | -3 por endpoint | -2 por endpoint | entrega informação (mapa de endpoints) |
| Referrer-Policy ausente | -5 | -1 | boa prática |
| Permissions-Policy ausente | -3 | -1 | boa prática |
| security.txt ausente | -3 | -1 | boa prática |
| Sem WAF | -3 | -1 | boa prática |

- Referrer-Policy ausente desceu mais que os outros porque, sem o header, o navegador atual
  (Chrome 85+, Firefox 87+) já aplica `strict-origin-when-cross-origin`: outros sites
  recebem só a origem. O texto de impacto dizia que "URLs internas podem vazar" — reescrito.
  Referrer-Policy **insegura** (`unsafe-url`) segue MEDIUM -5: troca o padrão seguro por um
  pior.
- As notas do catálogo trazem o número escrito ("…: -2"), em pt e en; atualizadas junto.

Verificado ao vivo no docker-compose (backend 8081) em 2026-11-13: na imagem da etapa E
saiu o "Resultado obtido"; depois do rebuild, a tabela do "Resultado esperado", nos dois
idiomas.

## Teste de regressão

- Backend: `PesoLowTest` (21 — custo de cada LOW, nota com o mesmo número em pt e en,
  guardas de MEDIUM/HIGH, e os dois laudos do cliente refeitos) — 19 vermelhos antes do
  fix. `ScoreServiceNaoVerificadoTest` passou a ler o peso da constante. Suíte inteira verde
  (778).
- QA: SCAN-10, a escrever pelo dono.
```

## Decisões que valem registrar

- **robots.txt com 401/403 não zera.** A proposta original da etapa F zerava o robots.txt
  quando todo caminho respondesse 401/403. Retirada: a sonda não segue redirect, então área
  com tela de login responde 302 (ou 200, se o login mora na própria URL), e área com Basic
  auth responde 401. A proteção é a mesma e o que o Disallow revelou ("/admin existe")
  também; zerar só o 401/403 premiaria a forma de responder. Fica -2 para todos. O stub
  `pesos-mock` usa 403 justamente para mostrar isso.
- **Os dois laudos do cliente, refeitos com A–F:** o primeiro sai de 60 para 74 (risco
  MEDIUM, pelo cookie de sessão HIGH da etapa E); o de depois da correção, de 86 para 97
  (SECURE). Os dois viraram teste no `PesoLowTest`.

## O que o fix NÃO resolve

- **O bônus de WAF segue até +8.** Com "sem WAF" a -1, ter ou não ter WAF vale até 9 pontos
  — mais que qualquer LOW. Se 97 parecer generoso, a alavanca é o bônus, não os itens de
  higiene. Decisão do dono.
- **O resto da escada ficou como está.** Acima do MEDIUM 5–6: HTTP sem redirecionar para
  HTTPS (-10), CORS com origem `null` (-10), método HTTP MEDIUM (-8), listagem de diretório
  e arquivo sensível MEDIUM (-8 e -10), certificado perto de expirar (-15) e HSTS ausente
  (-10, mantido de propósito na etapa D). Abaixo do HIGH 10: JWT sem expiração, source map e
  GraphQL playground (-8). E a spec de API (achado MEDIUM) custa -3. Nenhum desses foi
  contestado; mexer muda a nota de todo cliente de novo, então fica para quando o dono
  quiser fechar a escada inteira.
