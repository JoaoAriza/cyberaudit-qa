# WireMock — alvo controlado

Sobe junto no `docker-compose.test.yml`, porta `127.0.0.1:8082`. `mappings/` e
`__files/` são montados como volume — editar aqui reflete no container sem
rebuild (mas precisa reiniciar o serviço `wiremock`, ou usar a admin API de
reload).

## O que já existe

- `mappings/health.json` — stub mínimo só pra confirmar que o serviço sobe e
  responde (`GET /health` → 200). Não é um caso de teste, é sanity check do
  ambiente.

## O que falta (Fase 3, RestAssured) — não escrevo isso, é código de teste

Mapeamentos pros cenários da matriz (`docs/casos-de-teste.md`) que citam WireMock:

- **SCAN-02** — alvo com atraso forçado (`fixedDelayMilliseconds` alto), pro
  motor marcar como timeout.
- **SCAN-04** — alvo limpo com headers de segurança corretos (CSP, HSTS,
  X-Frame-Options...), pra confirmar zero falso positivo.
- **SSRF-05** — resposta 3xx com `Location` apontando pra um IP interno, pra
  confirmar que o redirecionamento não é seguido.

Referência rápida do formato de mapping:
<https://wiremock.org/docs/stubbing/>
