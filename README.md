# cyberaudit-qa

Suíte de testes (Postman/Newman, RestAssured, Cypress, Selenium) contra o
[CyberAudit](https://cyberauditapp.com), rodando num ambiente Docker local — nunca
contra produção (ver Regra 1 em `docs/plano-de-teste.md`).

Escopo completo, fases e critérios de pronto: `docs/plano-de-teste.md`.
Matriz de casos: `docs/casos-de-teste.md`. Template de bug: `docs/template-bug.md`.

> Este README cobre só o ambiente (Fase 1). A versão completa — estratégia de
> teste, cobertura por área, badge de CI — é entregável da Fase 6.

## Ambiente de teste (Fase 1)

Pressupõe `Backend/` e `Frontend/` como pastas irmãs deste repositório (mesmo
nível, ex. `C:\Projetos\Cyberaudit\{Backend,Frontend,cyberaudit-qa}`).

```bash
docker compose -f env/docker-compose.test.yml up --build
```

Sobe cinco serviços mais um seeder (roda uma vez e sai):

| Serviço | Porta local | O que é |
|---|---|---|
| `postgres` | 5433 | Banco do backend de teste (schema criado pelo próprio backend via `ddl-auto=update`) |
| `backend` | 8081 | CyberAudit API, profile `qa-docker` — ver `SsrfTestAllowlistConfig` no repo principal |
| `frontend` | 8090 | SPA do CyberAudit, servida via nginx |
| `juice-shop` | 3001 | OWASP Juice Shop — alvo vulnerável pra varredura ativa |
| `wiremock` | 8082 | Alvo controlado/mockável — ver `env/wiremock/README.md` |

O `seeder` popula três usuários (Free/Pro/Enterprise), senha e segredo TOTP
fixos, domínios locais (`juice-shop`, `wiremock`) já verificados — ver
`env/seed.sql` para os valores exatos.

**Health check:** `curl http://localhost:8081/actuator/health` deve responder
`{"status":"UP"}` depois que o `backend` sobe. Rodar de novo com o volume do
Postgres já populado é seguro — tudo no `seed.sql` é idempotente
(`ON CONFLICT ... DO NOTHING`).

**Reset completo:**

```bash
docker compose -f env/docker-compose.test.yml down -v
```
