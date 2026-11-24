# BUG-21 — "O que está em jogo" chama o token CSRF de cookie de sessão

Achado lateral em 2026-11-23, na conferência na tela do fix do BUG-13 (Etapa M). **Aberto,
não corrigido.** Formato conforme `../template-bug.md`.

```markdown
## Resumo

O painel "O que está em jogo" (rótulo de impacto) lista, como indício, os "Cookies de
sessão" do site. Para o `XSRF-TOKEN`, que é um token anti-CSRF, ele diz "Cookies de
sessão: XSRF-TOKEN".

O `ImpactLabelService` tem a sua própria lista de nomes de sessão (`sess`, `sid`, `auth`,
`token`, `login`, `usuario`, `user`, por "contém"). É uma terceira cópia da regra "que tipo
de cookie é este", depois do `CookieSecurityService.tipo` e das duas que o BUG-13 tirou. O
BUG-12 corrigiu essa regra no `CookieSecurityService`: o token CSRF é verificado antes de
sessão, e `sid` só vale como nome inteiro ou sufixo. A cópia do rótulo de impacto ficou
com a regra velha.

## Severidade

P3. É texto errado no painel. O nível do rótulo não muda, porque vem só dos campos de
formulário (senha, pagamento, dado pessoal); os cookies entram como indício.

## Ambiente

- Commit/versão testada: Backend com o fix do BUG-13 (HANDOFF §12, Etapa M)
- Ambiente: docker-compose de teste, backend 8081 e tela 8090; alvo `cookies-mock` (local).
  Nunca produção.
- Ferramenta que encontrou: conferência na tela, confirmada na API e no código

## Passos para reproduzir

1. qa-pro: `GET /scan?url=http://cookies-mock:8080/so-csrf&active=false` (um `XSRF-TOKEN`
   sem flags).
2. Ler `impactIndicators`. Na tela, o painel "O que está em jogo".

## Resultado esperado

O `XSRF-TOKEN` não aparece como cookie de sessão.

## Resultado obtido

`impactIndicators`: `[{"source":"COOKIES","detail":"XSRF-TOKEN"}]`. Na tela: "Indícios no
domínio: Cookies de sessão; Cookies de sessão: XSRF-TOKEN". Na raiz do `cookies-mock`, os
indícios são `PHPSESSID` e `connect.sid`, que estão certos.

Pela lista do código, também entrariam como sessão `csrftoken`, `inside_banner` (contém
`sid`) e `user_pref` (contém `user`). Isso é leitura do código; não reproduzi ao vivo.

## Causa raiz

`ImpactLabelService.cookiesDeSessao` decide "sessão" pelo nome com uma lista própria, em
vez de usar a classificação do `CookieSecurityService` (`tipo`).

## Correção

A fazer. Sugestão: uma fonte só para o tipo do cookie. Por exemplo, o `CookieFinding` passa
a trazer o tipo (como já traz `problems` desde o BUG-13), e o rótulo de impacto lê de lá.
Há um ponto para o dono decidir: o rótulo hoje só vê cookies com problema (`cookieIssues`),
então um cookie de sessão bem configurado não conta como indício.

## Teste de regressão

A escrever junto com a correção.
```
