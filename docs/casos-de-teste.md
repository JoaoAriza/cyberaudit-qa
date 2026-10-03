# Matriz de casos de teste — CyberAudit QA

Consolida os 26 casos originais do escopo (`CyberAudit QA — Escopo do projeto de
testes.pdf`, 2026-09-29) com os 12 casos do módulo de pagamento (já catalogados em
`payment-module-requests.md` durante a validação do checkout transparente) e 1 caso
novo (PAY-13), depois que o cartão virou opcional sem CPF. 39 casos: 22 P0, 14 P1, 3 P2.

P0 cobre segurança e regras de negócio centrais — **se um P0 falhar, é bug a
registrar (ver `template-bug.md`), não teste a ajustar.**

Os códigos HTTP citados são o esperado pelo padrão de mercado ou o que o código do
CyberAudit faz hoje (nos casos PAY-*, confirmado contra o código-fonte real do
Backend). Confirme o que a API retorna antes de fixar nos testes de qualquer forma.

## Legenda de Status

| Status | Significado |
|---|---|
| 🔲 A fazer | ainda não escrito |
| ✅ Passou | automatizado e verde |
| 🐛 Bug | achou um bug real — linkar a issue |
| ⚠️ Parcial | uma ferramenta feita (ex. Postman), outra(s) ainda não |

## Autenticação e 2FA

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| AUTH-01 | Autenticação | Login com credenciais válidas | JWT emitido, acesso liberado | Postman, RestAssured | P0 | ✅ Passou — Postman (2026-10-01) e RestAssured (2026-10-03) |
| AUTH-02 | Autenticação | Senha errada e usuário inexistente | 401 com a mesma mensagem nos dois casos, sem revelar se o usuário existe | RestAssured | P0 | ✅ Passou — RestAssured (2026-10-03) |
| AUTH-03 | Autenticação | JWT expirado, assinatura adulterada, `alg: none` | 401 em todos | RestAssured | P0 | 🔲 A fazer |
| AUTH-04 | 2FA | Sem código TOTP, código inválido, código reutilizado | Acesso negado nos três | RestAssured, Cypress | P0 | 🔲 A fazer |
| AUTH-05 | API key | Chamada com API key revogada | 401 | Postman, RestAssured | P1 | 🔲 A fazer |
| AUTHZ-01 | Controle de acesso | Usuário A consulta scan do usuário B pelo ID | 403 ou 404, nunca os dados | RestAssured | P0 | 🔲 A fazer |

## Planos e limites

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| PLAN-01 | Planos | Usuário Free solicita relatório PDF | Bloqueado (recurso Pro) | RestAssured, Cypress | P0 | 🔲 A fazer |
| PLAN-02 | Planos | Exceder a cota de scans do plano | Bloqueado com mensagem clara | RestAssured | P1 | 🔲 A fazer |
| RATE-01 | Rate limit | Exceder o limite configurado por usuário/IP | 429; volta a 200 após a janela | RestAssured | P1 | 🔲 A fazer |

## Anti-SSRF

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| SSRF-01 | Anti-SSRF | Alvo `127.0.0.1`, `localhost`, `::1` | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-02 | Anti-SSRF | Alvo `169.254.169.254`, `10.x`, `192.168.x` | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-03 | Anti-SSRF | Formas alternativas: `2130706433`, `0x7f000001`, `127.1` | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-04 | Anti-SSRF | Hostname público que resolve para IP privado | Rejeitado | RestAssured | P0 | 🔲 A fazer |
| SSRF-05 | Anti-SSRF | Alvo externo que redireciona para IP interno | Redirecionamento não seguido | RestAssured + WireMock | P1 | 🔲 A fazer |
| SSRF-06 | Anti-SSRF | Allowlist de teste fora do profile `test` | Allowlist inexistente | RestAssured | P0 | 🔲 A fazer |

## Domínio e motor de scan

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| DOM-01 | Domínio | Varredura ativa em domínio não verificado | Recusada | Postman, RestAssured | P0 | 🔲 A fazer |
| SCAN-01 | Motor | Varredura passiva no alvo controlado | Módulos esperados com status OK | RestAssured | P0 | 🐛 Bug suspeito já observado manualmente (ver nota abaixo) — teste formal pendente |
| SCAN-02 | Motor | Alvo com atraso forçado no WireMock | Módulo marcado como timeout, relatório indica resultado parcial | RestAssured + WireMock | P1 | 🔲 A fazer |
| SCAN-03 | Motor | Varredura ativa no Juice Shop | XSS e SQL injection detectados | RestAssured | P0 | 🔲 A fazer |
| SCAN-04 | Motor | Alvo limpo com headers corretos | Nenhum falso positivo nesses módulos | RestAssured + WireMock | P1 | 🔲 A fazer |
| SCORE-01 | Pontuação | Scan com achado crítico e o resto limpo | Nota não fica alta (override de severidade) | RestAssured | P0 | 🔲 A fazer |

## Relatório e interface

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| REP-01 | Relatório | Download do PDF por usuário Pro | `application/pdf` válido, contém os achados do scan (checar com PDFBox) | RestAssured | P1 | 🔲 A fazer |
| REP-02 | Relatório | Achado conhecido no PDF | Mapeamento LGPD/ISO 27001 presente | RestAssured | P2 | 🔲 A fazer |
| UI-01 | Interface | Login completo com 2FA | Chega ao dashboard | Cypress, Selenium | P1 | 🔲 A fazer |
| UI-02 | Interface | Iniciar scan, acompanhar e ver no histórico | Resultado e nota exibidos | Cypress, Selenium | P1 | 🔲 A fazer |
| UI-03 | Interface | Criar, editar e remover agendamento recorrente | Alterações persistem após recarregar | Cypress | P2 | 🔲 A fazer |

## Pagamento (checkout transparente — Pix + cartão)

Confirmado ao vivo em produção em 2026-09-30, com dinheiro real — ver
`Backend/HANDOFF.md` seção 2 para o histórico completo (webhook mal configurado no
painel do MP, CSP em 3 rodadas, `getSubscription` com bug de seleção, CPF virou
opcional no cartão). Os casos abaixo continuam válidos para automatizar contra o
ambiente de teste local (nunca contra produção — Regra 1).

| ID | Área | Cenário | Resultado esperado | Ferramenta | Prioridade | Status |
|---|---|---|---|---|---|---|
| PAY-01 | Pagamento | Checkout cartão com token válido (sandbox, cartão de teste aprovado) | 200, status AUTHORIZED, plano liberado na hora | RestAssured | P0 | 🔲 A fazer |
| PAY-02 | Pagamento | Checkout cartão com token de cartão de teste recusado | 502, plano continua FREE | RestAssured | P0 | 🔲 A fazer |
| PAY-03 | Pagamento | Checkout cartão sem cardTokenId | 400 | Postman, RestAssured | P1 | 🔲 A fazer |
| PAY-04 | Pagamento | Checkout Pix com CPF válido | 200, QR code presente, plano continua FREE até confirmação | RestAssured | P0 | 🔲 A fazer |
| PAY-05 | Pagamento | Checkout Pix com CPF de tamanho inválido | 400 | Postman, RestAssured | P1 | 🔲 A fazer |
| PAY-06 | Pagamento | Webhook `payment` approved libera o plano e seta currentPeriodEnd | Plano liberado, período ~30 dias | RestAssured | P0 | 🔲 A fazer |
| PAY-07 | Pagamento | Webhook `payment` rejected não derruba assinatura Pix já ativa | Status/plano inalterados | RestAssured | P1 | 🔲 A fazer |
| PAY-08 | Pagamento | Webhook sem x-signature válido | 401, plano não muda | RestAssured | P0 | 🔲 A fazer |
| PAY-09 | Pagamento | > 5 tentativas de checkout/minuto pelo mesmo usuário | 429 | RestAssured | P1 | 🔲 A fazer |
| PAY-10 | Pagamento | Job de expiração: assinatura Pix com currentPeriodEnd vencido | Conta rebaixada para FREE | RestAssured (chamada direta ao método, ou expor endpoint de teste) | P1 | 🔲 A fazer |
| PAY-11 | Interface | Fluxo completo Pix na tela: QR aparece, copia-e-cola funciona, polling detecta confirmação | Tela mostra "plano ativo" sem reload manual | Cypress | P1 | 🔲 A fazer |
| PAY-12 | Interface | Fluxo completo cartão na tela: Secure Fields do MP carregam, submit gera token, checkout confirma | Tela mostra "plano ativo" | Cypress | P1 | 🔲 A fazer |
| PAY-13 | Pagamento | Checkout Pix com CPF de 11 dígitos mas dígito verificador inválido | 400 (`CpfUtil` rejeita desde 2026-09-29) | RestAssured | P1 | 🔲 A fazer |
| PAY-14 | Pagamento | Checkout cartão sem CPF nenhum (cliente estrangeiro) | 200/token gerado — CPF é opcional desde 2026-09-30, backend nunca recebeu CPF nesse fluxo | RestAssured, Cypress | P1 | 🔲 A fazer |
| PAY-15 | Pagamento | `GET /billing/subscription` com um Pix AUTHORIZED mais antigo e um Pix PENDING mais novo na mesma conta | Devolve o AUTHORIZED, não o mais recente por data — regressão do bug corrigido em 2026-09-30 (`currentSubscription()`) | RestAssured | P0 | 🔲 A fazer |

## Notas de status (2026-09-30)

- Cartão: autorização de verdade (não só rejeição) ainda não testada — falta
  credencial `TEST-` de sandbox ou um cartão real. PAY-01 permanece não confirmado
  de ponta a ponta; PAY-02 confirmado (cartão de teste público do MP rejeitado em
  produção, como esperado).
- Pix: PAY-04 e PAY-06 confirmados de ponta a ponta em produção, com pagamento
  real. A configuração do webhook no painel do MP (URL, evento "Pagamentos"
  subscrito, secret correto) precisa ser replicada no ambiente de teste/sandbox
  quando a Fase 1 (Docker) for montada — não é algo que o código resolve sozinho.
- PAY-13, PAY-14 e PAY-15 são casos novos, criados a partir de bugs reais
  encontrados e corrigidos nesta sessão — bons candidatos a "bug real encontrado
  no CyberAudit" para o marco do CV (ver `plano-de-teste.md`).
- **SCAN-01, possível bug real encontrado montando a Fase 1 (2026-09-30).**
  Rodei `GET /scan?url=http://juice-shop:3000&active=true` de verdade contra o
  ambiente Docker (login + 2FA reais, `PLATFORM_STAFF_EMAILS` pra pular a posse
  de domínio). O scan completou (200), mas com `httpStatus:0`,
  `sslInfo.message:"Erro ao verificar certificado: Unsupported or unrecognized
  SSL message"` e `headers.error:"HTTP/1.1 header parser received no bytes"` —
  parece que o scanner tenta HTTPS no host:porta independente do esquema da URL
  de entrada, e quando isso falha contra um alvo HTTP puro numa porta
  não-padrão (como o Juice Shop, 3000), os módulos de headers/TLS erram em
  cascata em vez de cair pro HTTP puro. Também demorou ~2min por causa de
  `CrtShService` tentando consultar `crt.sh`/`certspotter` de verdade pra um
  hostname que não existe na internet (timeout real, não bug — mas vale
  considerar mockar isso via WireMock nesse ambiente). Não investiguei a causa
  raiz nem mexi no código do motor de scan — é exatamente o tipo de achado que
  SCAN-01 deveria registrar como bug, não ajustar o teste pra esperar isso.

## 2026-10-01 — Fase 2 (Postman) iniciada

AUTH-01 escrito e rodando verde via Newman (`newman run collection.json -e
environment.local.json`): 1 request, 3 assertions, 0 falhas. Primeiro caso real
da Fase 2, autoria do usuário.

## 2026-10-03 — Fase 3 (RestAssured) iniciada

Projeto Maven em `api-restassured/` montado (`pom.xml`, `TestConfig` como classe-base
com `@BeforeAll` que fixa o `baseURI`, `config.properties`). `AuthTest` com 3 testes
verdes contra o ambiente Docker: AUTH-01 (login válido → 200 + `requires2fa: true`) e
AUTH-02 (senha errada e usuário inexistente → 401, ambos com `{"error":"Autenticação
necessária."}`). O login válido funciona também como controle do AUTH-02: um path
inexistente devolve o mesmo 401 com o mesmo corpo, então só o 200 no path certo prova
que o 401 é de credencial rejeitada. Autoria do usuário.
