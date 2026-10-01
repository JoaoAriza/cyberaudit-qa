# Plano de teste — CyberAudit QA

Transcrito e organizado a partir de `CyberAudit QA — Escopo do projeto de testes.pdf`
(2026-09-29, @João). O PDF continua sendo a fonte original; este arquivo é a versão
versionada, em Markdown, que vive no repositório — ver `docs/casos-de-teste.md` para
a matriz completa e `docs/template-bug.md` para o formato de bug report.

## Objetivo

Entregar um repositório público `cyberaudit-qa` que prove, com código executável e
CI verde, domínio de Postman/Newman, RestAssured, Cypress e Selenium sobre um
sistema real em produção (o próprio CyberAudit).

O que o recrutador precisa encontrar no repositório:

- Quatro suítes rodando no GitHub Actions a cada push, com relatório Allure publicado.
- README com a estratégia de teste, cobertura por área e como rodar tudo localmente
  com um comando.
- Artefatos de processo: plano de teste, casos de teste e bug reports com template.
- Bugs reais encontrados no próprio CyberAudit, registrados como issues e corrigidos.

Prazo estimado: 5–8 semanas (depende das horas semanais disponíveis).

## Regras obrigatórias

Quebrar qualquer uma invalida o projeto como evidência:

1. **Nunca rodar testes contra produção** (`cyberauditapp.com`). Rate limit, 2FA e
   scans ativos são reais lá, e clientes seriam afetados.
2. **Varredura ativa só contra alvos locais próprios**, subidos no ambiente de teste.
3. **Autoria real.** Cada teste é escrito ou reescrito pelo dono do repositório e
   explicável linha a linha. Commits no seu usuário do GitHub.
4. **Sem segredos no repositório.** Credenciais e seeds TOTP apenas de teste, via
   variáveis de ambiente e GitHub Secrets.
5. **CV acompanha o código.** Uma ferramenta só sai de "em estudo" quando a fase
   dela cumprir o critério de pronto.

## Ambiente de teste

`docker-compose.test.yml` com cinco serviços: backend (profile `test`), frontend,
PostgreSQL, OWASP Juice Shop (alvo vulnerável) e WireMock (alvo controlado).

O profile `test` do backend precisa de:

- Um usuário de seed por plano (Free, Pro e acima), com senha e seed TOTP fixos e
  conhecidos.
- Domínios dos alvos locais já marcados como verificados.
- Reset do banco entre execuções (script de truncate ou migração limpa).
- Limites de rate lidos da configuração, para os testes validarem contra o valor
  configurado em vez de um número fixo.

**Ponto crítico:** a guarda anti-SSRF vai bloquear os alvos locais (rede privada do
Docker). Solução: allowlist configurável, ativa só no profile `test` — com um teste
(SSRF-06) provando que essa allowlist não existe fora do profile `test`.

## Estrutura do repositório

```
cyberaudit-qa/
├── docs/            plano de teste, casos de teste, template de bug
├── env/             docker-compose.test.yml, seed.sql, mappings do WireMock
├── api-postman/     collection.json, environment.local.json
├── api-restassured/ Maven, JUnit 5, RestAssured, AssertJ, Allure
├── e2e-cypress/     TypeScript, otplib para gerar o código TOTP
├── e2e-selenium/    Maven, Selenium WebDriver, Page Object Model
└── .github/workflows/
```

Testes unitários/integração do backend (JUnit, Mockito, Testcontainers) pertencem
ao repositório principal (`Cyberaudit/Backend`), não a este.

## Fases

Ordenadas por retorno por hora investida: API antes de interface, Java antes de
JavaScript.

| Fase | Ferramenta | Entregáveis | Pronto quando | Estimativa |
|---|---|---|---|---|
| 0. Planejamento | Markdown | Plano de teste, matriz requisito→caso, template de bug | 30+ casos catalogados com prioridade | 2–3 dias |
| 1. Ambiente | Docker Compose | Compose de teste, seed, alvos locais, allowlist SSRF só no profile `test` | Um comando sobe tudo e o health check passa | 3–5 dias |
| 2. API (collection) | Postman + Newman | Collection por área, environments, testes de status e payload | Newman roda no CI e quebra o build quando um teste falha | 2–4 dias |
| 3. API (código) | RestAssured + JUnit 5 | Suítes de auth, controle de acesso, planos, rate limit, SSRF, scan e relatório; validação por JSON Schema | Todos os casos P0/P1 de API automatizados, CI verde | 1–2 semanas |
| 4. E2E | Cypress | Fluxos de login+2FA, scan, histórico, agendamento e PDF; seletores `data-testid` | 6+ fluxos estáveis, zero falhas intermitentes em 10 execuções seguidas | 1–2 semanas |
| 5. E2E Java | Selenium + POM | 3–4 fluxos críticos, headless | Roda no CI em modo headless | ~1 semana |
| 6. CI e relatório | GitHub Actions + Allure | Pipeline completo, relatório no GitHub Pages, badge no README | Link público do relatório funcionando | 2–3 dias |

Selenium fica por último porque duplica a cobertura do Cypress — só vale se as
vagas-alvo pedirem Selenium.

**Status em 2026-09-30:**

- **Fase 0** ✅ — este arquivo + `casos-de-teste.md` (39 casos, os 26 do PDF
  original + 12 PAY-* + 3 novos) + `template-bug.md`.
- **Fase 1** ✅ confirmada de ponta a ponta, com ambiente real rodando (não só
  arquivo escrito): `docker compose -f env/docker-compose.test.yml up --build`
  sobe os 5 serviços, o `seeder` popula 3 contas, e testei manualmente login +
  2FA (TOTP gerado com o mesmo secret semeado) + um scan ativo real contra o
  Juice Shop (SSRF allowlist e bypass de posse de domínio via
  `PLATFORM_STAFF_EMAILS` funcionando). No caminho apareceram e foram corrigidos
  dois problemas reais de ambiente (não hipotéticos): o guard anti-SSRF
  bloqueava os alvos Docker por padrão (resolvido com uma allowlist que só
  existe sob o profile `qa-docker` — ver `SsrfTestAllowlistConfig` no Backend) e
  o `vite.config.ts` do Frontend recusava buildar apontando pra
  `localhost:8081` (resolvido com `VITE_ALLOW_LOCAL_API=1`, escape hatch que já
  existia pra esse caso exato). Um achado de scanner (não corrigido, ver
  `casos-de-teste.md`, nota do SCAN-01) ficou registrado como candidato a bug
  real pra Fase 3.
- **Fases 2–6** não iniciadas.

## CI, relatórios e marcos do CV

- Pipeline roda a cada push: sobe o ambiente, executa as quatro suítes em
  paralelo, publica relatório Allure unificado no GitHub Pages.
- Em falha: screenshots e vídeos do Cypress como artefatos do job.
- Teste intermitente vai para quarentena com uma issue aberta — nunca é apagado.
- Bug encontrado no CyberAudit vira issue com o template (`template-bug.md`),
  depois correção e teste de regressão.

| Marco | Mudança no CV |
|---|---|
| Fase 2 pronta | Postman sai de "em estudo": testes de API com Postman/Newman integrados ao CI |
| Fase 3 pronta | Novo bullet: suíte RestAssured/JUnit 5 cobrindo autenticação, controle de acesso, rate limiting e anti-SSRF, no GitHub Actions |
| Fase 4 pronta | Cypress sai de "em estudo": testes E2E dos fluxos críticos, incluindo 2FA |
| Fase 5 pronta | Selenium sai de "em estudo", com Page Object Model |
| Fase 6 pronta | Link do repositório e do relatório Allure no CV e no LinkedIn |

Os bullets finais levam números reais: quantidade de casos automatizados e de
bugs encontrados e corrigidos.
