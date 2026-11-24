# BUG-22 — Cookie de analytics com "token" ou "sess" no nome conta como sessão

Achado lateral em 2026-11-23, na conferência ao vivo do fix do BUG-21 (Etapa N). **✅
Corrigido em 2026-11-23** (HANDOFF §12, Etapa O), junto com o `_pin_unauth`, achado na
leitura. Formato conforme `../template-bug.md`.

```markdown
## Resumo

O card do cookie decide "é sessão?" pelo nome (`CookieSecurityService.tipo`). A regra é:
- o nome contém `session`, `token`, `auth`, `jwt`, `login` ou `sess`; ou
- é `sid` como nome inteiro ou sufixo.

Cookie de sessão sem flag sai HIGH e custa 10 pontos na nota.

Três cookies comuns de analytics e anúncio caem nessa regra:
- `AMP_TOKEN` (Google Analytics), por "token";
- `s_sess` (Adobe Analytics), por "sess";
- `_pin_unauth` (tag do Pinterest), por "auth". Ele não estava no registro: achei na
  leitura, antes da correção.

Os três são gravados pelo JavaScript da página, então não podem ter HttpOnly. Nenhum dá
acesso a conta. O `_pin_unauth`, segundo o Pinterest, é o identificador do visitante
que a tag não conseguiu ligar a uma conta do Pinterest.

O painel "O que está em jogo" usa a mesma regra desde o BUG-21, então também os listava
como "Cookies de sessão".

## Severidade

P2. O card mostrava um achado HIGH falso, e a nota perdia 10 pontos num site em que o
único cookie "de sessão" sem flag era de analytics. O dono do site não tem como corrigir:
quem grava o cookie é o script do fornecedor.

## Ambiente

- Commit/versão testada: Backend `c4711db` (fix do BUG-21)
- Ambiente: docker-compose de teste, backend 8081 e tela 8090; alvo `cookies-mock` (local).
  Nunca produção.
- Ferramenta que encontrou: conferência ao vivo, com um mapeamento temporário do WireMock;
  reproduzido de novo com o stub novo em 2026-11-23

## Passos para reproduzir

1. qa-pro: `GET /scan?url=http://cookies-mock:8080/analytics&active=false`. O stub novo
   `env/wiremock/mappings/cookies-analytics.json` devolve `AMP_TOKEN`, `s_sess` e
   `_pin_unauth`, todos sem flag.
2. Ler o `risk` de cada cookie em `cookieIssues`, o achado `INSECURE_COOKIES` e
   `impactIndicators`.

## Resultado esperado

Os três cookies são analytics: saem LOW, sem desconto na nota e fora do painel.

## Resultado obtido

- Os três saíam HIGH.
- O achado `INSECURE_COOKIES` dizia "3 cookie(s)", com -10 na nota (22).
- `impactIndicators` trazia `AMP_TOKEN`, `s_sess` e `_pin_unauth`.

## Causa raiz

A regra decide pelo fragmento do nome, mas o nome não diz para que o cookie serve. O
BUG-21 tirou o Hotjar pelo prefixo `_hj`. Outros fornecedores usam os mesmos fragmentos.

## Correção

Decisão do dono (2026-11-23):
- Os nomes conhecidos de analytics entram numa lista, junto com o Hotjar. Cada nome novo
  entra com teste.
- O `intercom-session-*` (chat) continua sessão. Segundo a própria Intercom, ele dá acesso
  às conversas do visitante.

O que mudou (Backend):
- `CookieSecurityService` ganhou `ANALYTICS_COM_NOME_DE_SESSAO`: `amp_token`, `s_sess` e
  `_pin_unauth`. A comparação é pelo nome inteiro, sem caixa.
- O `tipo` devolve "não é sessão" para esses nomes. O card e o painel leem a mesma regra,
  então os dois mudam juntos.
- Num site em que esses eram os únicos cookies "de sessão" sem flag, a nota sobe 10.

## Teste de regressão

- Backend, `CookieDeSessaoTest`:
  - A tabela passou de 26 para 30 nomes: os três, mais o `intercom-session-qa123` como
    sessão.
  - O alvo `/analytics` inteiro. O controle confere que os três continuam no card (o
    cookie foi lido); depois, que o painel está vazio.
- `CookieAtributosTest.analyticsComNomeDeSessaoNaoCusta`: os três sem flag, sem achado,
  nota 100.
- Contra o código antigo: 8 de 103 vermelhos. O controle do Intercom é verde nos dois.
- Suíte do Backend: 977 → 987, verde. Container `backend` rebuildado.
- Ao vivo (8081, qa-pro):

  | `/analytics` | Antes | Depois |
  |---|---|---|
  | card | os três HIGH | os três LOW |
  | achado `INSECURE_COOKIES` | "3 cookie(s)", -10 | não aparece |
  | nota | 22 | 32 |
  | painel | `AMP_TOKEN`, `s_sess`, `_pin_unauth` | vazio |

  Também conferido depois do fix:
  - O mesmo em inglês.
  - `/nomes` (SCAN-15) igual: painel com `laravel_session` e "1 cookie(s)".
  - Raiz do `cookies-mock` (SCAN-09) igual.
  - Na tela (8090, qa-pro): o painel sem "Cookies de sessão" e os três cards LOW.
  - RestAssured do dono: 44 / 1 falha / 2 pulados. A falha é o PLAN-02, já conhecida
    (BUG-20, Etapa L).
- Caso de QA: **SCAN-16** (P2, 🔲): contrato no HANDOFF §12, Etapa O; ficha 22.
```
