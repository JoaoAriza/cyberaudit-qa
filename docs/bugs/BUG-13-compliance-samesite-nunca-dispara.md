# BUG-13 — Compliance: "Cookies sem SameSite" nunca dispara

Achado lateral da etapa E (BUG-12), em 2026-11-12, por leitura de código. **Aberto — não
corrigido**: fica fora do commit do BUG-12 (regra da rodada: achado lateral vira bug
próprio). Formato conforme `../template-bug.md`.

```markdown
## Resumo

`ComplianceMappingService.hasCookiesWithoutSameSite` procura cookie com `sameSite` nulo ou
vazio. O `CookieSecurityService` nunca produz isso: SameSite ausente sai como a string
`"MISSING"`. O item "Cookies sem atributo SameSite — vulneráveis a CSRF" (LGPD Art. 49)
nunca aparece no laudo, nem para um cookie de sessão sem SameSite.

## Severidade

P2 — item de compliance omitido no laudo; não mexe no score (o score já desconta o
SameSite ausente pelo `INSECURE_COOKIES`).

## Ambiente

- Commit/versão testada: Backend `2a18641` (o defeito é anterior à rodada)
- Ferramenta que encontrou: revisão de código

## Passos para reproduzir

1. Escanear um alvo com `sessionid=x; Secure; HttpOnly` (sem SameSite).
2. Em `compliance`, procurar o item "Cookies sem atributo SameSite" no Art. 49 da LGPD.

## Resultado esperado

O item aparece.

## Resultado obtido

Não aparece. (Esperado pela leitura do código; reproduzir ao vivo quando for corrigir.)

## Causa raiz

Contrato implícito divergente: um serviço representa ausência com `"MISSING"`, o outro
espera `null`/vazio.

## Correção

A fazer. Sugestão: comparar com `"MISSING"` (ou expor um enum), com teste que monte o
`ScanResult` a partir do `CookieSecurityService` real — não de um `CookieFinding` à mão,
que é como a divergência passou.

## Teste de regressão

A escrever junto com a correção.
```
