# BUG-01 — API key não autentica (LazyInitializationException)

Encontrado em 2026-10-05 pela QA (cyberaudit-qa) ao verificar o contrato do AUTH-05.
Formato conforme `../template-bug.md`. Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

Autenticação por API key está inteira quebrada: QUALQUER request com header
`X-Api-Key` (mesmo uma chave válida recém-criada) retorna 401, porque
`ApiKeyAuthFilter` desreferencia um proxy lazy do Hibernate fora da sessão.

## Severidade

P1 — a feature de API key (acesso programático / gate de CI em `/api-keys/ci`)
não funciona de forma alguma. Falha FECHADA (nega acesso, não concede), então
não é brecha de segurança — mas a funcionalidade está morta.

## Ambiente

- Commit/versão testada: Backend `8d32e2a`
- Ambiente: local (`docker-compose.test.yml`, profile qa-docker) — nunca produção (Regra 1)
- Ferramenta que encontrou: manual (curl) durante a verificação de contrato do AUTH-05; reproduzível em Postman/RestAssured

## Passos para reproduzir

1. Autenticar como qa-pro (token de sessão) e criar uma API key:
   `POST /api-keys` com `{"name":"x"}` → 201, resposta traz `plainKey` (ex. `ca_<32 hex>`).
2. Usar essa chave válida em qualquer endpoint autenticado, no header `X-Api-Key: <plainKey>`:
   `GET /api-keys`, `GET /history/recent` ou o próprio `GET /api-keys/ci?url=...`.
3. Observar a resposta e o log do backend.

## Resultado esperado

Chave válida autentica → 200 (o endpoint responde normalmente). Só uma chave
revogada/inválida deveria dar 401 (é o que o caso AUTH-05 quer provar).

## Resultado obtido

- Toda chamada com `X-Api-Key` (chave válida inclusa) → **401** `{"error":"Autenticação necessária."}`.
- Log do backend, por request:
  `org.hibernate.LazyInitializationException: Could not initialize proxy
  [com.joao.cyberaudit.model.AppUser#<id>] - no session`
  com o stack passando por `ApiKeyAuthFilter.doFilterInternal(ApiKeyAuthFilter.java:54)`
  → `AppUser$HibernateProxy.getAuthorities(...)`.
- Fluxo observado ao vivo: criar (201) → usar chave válida (401) → revogar (204) →
  usar chave revogada (401). O 401 é idêntico antes e depois da revogação.

## Caso de teste relacionado

AUTH-05 (matriz `casos-de-teste.md`). Enquanto o bug existe, o AUTH-05 não pode
ser automatizado como verde: a chave válida também dá 401, então "revogada → 401"
passaria pelo motivo errado (sem controle positivo possível). Caso fica 🐛 até o fix.

## Causa raiz

`ApiKeyService.validate(rawKey)` retorna `k.getCreatedBy()` — associação lazy
(`@ManyToOne(fetch = LAZY)`) do `ApiKey`. A validação roda em transação própria
que já fechou quando o fluxo volta ao filtro. Em `ApiKeyAuthFilter.java:54`:

    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

`user.getAuthorities()` força a inicialização do proxy do `AppUser` SEM sessão
Hibernate aberta → `LazyInitializationException`. A exceção estoura o filtro, a
autenticação não é setada e o entry point responde 401.

Opções de correção (a decidir no Backend):
- Inicializar o `createdBy` ainda dentro da transação de `validate()` (ex.
  `Hibernate.initialize(...)` ou acessar uma propriedade antes de retornar);
- `JOIN FETCH` do `createdBy` na query `findByKeyPrefixAndRevokedAtIsNull`;
- Ou recarregar o `AppUser` por id no filtro, dentro de uma sessão.

## Correção

(pendente — fix no Backend)

## Teste de regressão

(pendente — AUTH-05 em RestAssured/Postman: controle positivo "chave válida → 200"
+ "chave revogada → 401"; só fecha a issue quando o controle positivo passar)
```
