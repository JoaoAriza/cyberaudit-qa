# BUG-22 — Cookie de analytics com "token" ou "sess" no nome conta como sessão

Achado lateral em 2026-11-23, na conferência ao vivo do fix do BUG-21 (Etapa N). **Aberto,
não corrigido.** Formato conforme `../template-bug.md`.

```markdown
## Resumo

O card do cookie decide "é sessão?" pelo nome (`CookieSecurityService.tipo`): contém
`session`, `token`, `auth`, `jwt`, `login` ou `sess`, ou é `sid` como nome inteiro ou
sufixo. Cookie de sessão sem flag sai HIGH e custa 10 pontos na nota.

Dois cookies de analytics comuns caem nessa regra:
- `AMP_TOKEN` (Google Analytics), por "token";
- `s_sess` (Adobe Analytics), por "sess".

Os dois são gravados pelo JavaScript da página (não podem ter HttpOnly) e não dão acesso a
conta nenhuma. O painel "O que está em jogo" usa a mesma regra desde o BUG-21, então também
os lista como "Cookies de sessão".

## Severidade

P2. Achado HIGH falso no card e -10 na nota de um site cujo único cookie "de sessão" sem
flag é de analytics. É o mesmo defeito que o BUG-21 corrigiu para o Hotjar.

## Ambiente

- Commit/versão testada: Backend com o fix do BUG-21 (HANDOFF §12, Etapa N)
- Ambiente: docker-compose de teste, backend 8081; alvo `cookies-mock` (local). Nunca
  produção.
- Ferramenta que encontrou: conferência ao vivo, com um mapeamento temporário do WireMock
  (criado por `POST /__admin/mappings` e apagado pelo reset)

## Passos para reproduzir

1. Um alvo local que devolva, sem flag nenhuma:
   `_hjSession_1873402=qa-hotjar; Path=/`, `AMP_TOKEN=%24NOT_FOUND; Path=/` e
   `s_sess=%20s_cc%3Dtrue; Path=/`.
2. qa-pro: `GET /scan?url=<alvo>&active=false`.
3. Ler o `risk` de cada cookie em `cookieIssues`, o achado `INSECURE_COOKIES` e
   `impactIndicators`.

## Resultado esperado

Os três são analytics: LOW, sem desconto, fora do painel.

## Resultado obtido

- `_hjSession_1873402` LOW (corrigido no BUG-21).
- `AMP_TOKEN` e `s_sess` HIGH; `INSECURE_COOKIES` "2 cookie(s)", -10.
- `impactIndicators`: `AMP_TOKEN` e `s_sess`.

## Causa raiz

A regra é por fragmento do nome, e o nome não diz para que o cookie serve. O BUG-21 tirou
o Hotjar pelo prefixo `_hj`. Outros fornecedores de analytics usam os mesmos fragmentos.

## Correção

A fazer. Decisão do dono: listar os nomes conhecidos de analytics que caem na regra (como
o BUG-21 fez com o Hotjar), ou outra abordagem. Uma lista de nomes nunca fica completa.

## Teste de regressão

A escrever junto com a correção.
```
