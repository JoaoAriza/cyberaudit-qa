# BUG-15 — Textos do laudo: "null" no WAF, porta 80 que só redireciona, idioma errado e p=medium

Encontrado em 2026-11-07 ao conferir o laudo de um cliente. Nenhum dos defeitos muda a
nota; todos aparecem diante do cliente. Formato conforme `../template-bug.md`. Colar como
issue no GitHub quando for abrir.

```markdown
## Resumo

Cinco textos errados no laudo e na tela:

1. **"null" no bônus de WAF.** WAF achado só porque bloqueou o payload, sem header que
   diga qual é, tem `provider` nulo. A nota saía "WAF detectado (null, probe bloqueado): +5"
   — e a mudança entre scans, "WAF/CDN passou a ser detectado: null". No PDF, a linha do
   provedor sumia.
2. **Porta 80 LOW quando só redireciona.** A sonda da porta lia status e `Server`, mas não o
   `Location`. Uma porta 80 que só responde 301 para https:// saía LOW, com "HTTP exposto" e
   "Redirecione para HTTPS" — pedindo o que ela já faz. Não custava ponto (o score ignora
   80/443); custava credibilidade.
3. **Módulos no idioma do servidor.** O pool dos hosts relacionados nascia sem o idioma de
   quem pediu o scan: num laudo em português, "max-age far too short: 86,400s". O pool do
   takeover de subdomínio tinha o mesmo defeito (monta vulnerabilidade e evidência pelo
   catálogo).
4. **HSTS `max-age=0` em inglês fixo.** "WEAK (max-age=0 deletes HSTS)" estava escrito no
   código, fora do catálogo — saía em inglês nos dois idiomas.
5. **Card DMARC com o nível no lugar da política.** O Frontend escrevia `p=` na frente do
   `dmarcPolicy`, que é o NÍVEL que o Backend atribuiu (STRONG/MEDIUM/WEAK): saía
   "p=medium", valor que não existe no DMARC, logo abaixo do resumo que dizia "com
   p=quarantine aplicado a menos de 100%".

## Severidade

P2 — texto errado diante do cliente, sem mudar nota nem risco.

## Ambiente

- Commit/versão testada: Backend `4555c80`, Frontend `d99c460`
- Ambiente: docker-compose de teste — backend 8081, frontend 8090; alvos `textos-mock`
  (alias novo do WireMock, HSTS `max-age=0`) e `quarantine-pct50.dns-mock.test` (DNS falso).
  Nunca produção do CyberAudit (Regra 1).
- Ferramenta que encontrou: laudo de cliente + revisão de código

## Passos para reproduzir

1. qa-pro, `GET /scan?url=http://textos-mock:8080` com `Accept-Language: pt-BR`; ler
   `headers["Strict-Transport-Security"]`.
2. Na tela (qa-pro), scan passivo de `http://quarantine-pct50.dns-mock.test:8080`; abrir o
   módulo Reconnaissance e ler o card DMARC.

## Resultado esperado

1. `WEAK (max-age=0 apaga o HSTS)` (com `en`: `WEAK (max-age=0 deletes HSTS)`).
2. Card DMARC: `p=quarantine; pct=50`.

## Resultado obtido

1. `WEAK (max-age=0 deletes HSTS)` com pt-BR.
2. Card DMARC: `p=medium`.

## Caso de teste relacionado

SCAN-11 (novo, P2) e UI-04 (novo, P2). Os itens 1–3 não aparecem num scan passivo do
ambiente (WAF e porta só rodam no ativo; host relacionado precisa de `www.<domínio>` em
HTTPS na 443) — ficam na regressão do Backend.

## Causa raiz

Texto montado fora do catálogo (4), dado nulo virando texto sem fallback (1), sonda que não
lia o header que decide (2), pool criado sem a fábrica de idioma (3) e um campo de nível
lido como se fosse o valor do registro (5).

## Correção

Corrigido em 2026-11-14:

- Backend: provedor ausente vira "provedor não identificado" / "unidentified provider" na
  nota e na mudança entre scans, e "unidentified" no PDF; a sonda da porta HTTP lê o
  `Location`, e status 30x para `https://` vira achado INFO com texto próprio ("a porta
  atende quem digita o endereço sem https://, como deve"); os três pools que faltavam
  (hosts relacionados, takeover, enumeração de subdomínios) usam `IdiomaThreads.fabrica`;
  `max-age=0` sai do catálogo.
- Frontend: `politicaDmarc()` lê o `p=` do registro, com o `pct=` quando é menor que 100;
  registro duplicado (INVALID) continua mostrando o nível, como o card do SPF.

Verificado ao vivo no docker-compose em 2026-11-14: nas imagens anteriores saiu o
"Resultado obtido"; depois do rebuild, o "Resultado esperado" — o HSTS nos dois idiomas e o
card na tela (frontend 8090, qa-pro).

## Teste de regressão

- Backend: `TextosDoLaudoTest` (9 — WAF na nota e na mudança, porta 80 em cinco cenários,
  HSTS nos dois idiomas, e uma guarda que varre `src/main/java`: todo `Executors.new…` usa a
  fábrica de idioma). Os 4 de WAF/HSTS/pools vermelhos contra o código antigo (os 5 da porta
  usam API nova). Suíte inteira verde (787).
- Frontend: `dmarc.test.ts` (6). Vitest 31 verdes; build ok.
- QA: SCAN-11 e UI-04, a escrever pelo dono.
```

## Decisões que valem registrar

- **O takeover entrou junto, sem bug próprio.** A regra é registrar achado lateral à parte;
  aqui não é lateral, é o mesmo defeito (pool sem idioma) num segundo lugar, achado pela
  guarda que fecha o item 3. O pool de enumeração não monta texto e entrou só para a guarda
  valer em todo pool.
- **Porta 80 que serve conteúdo continua LOW.** Só vira INFO com status 30x E destino
  absoluto em `https://`. Redirect para `http://` ou para caminho relativo é a porta servindo
  em texto claro.

## O que o fix NÃO resolve

- A guarda olha `Executors.new…`. `CompletableFuture.supplyAsync(…)` sem executor cai no
  pool comum, também sem idioma, e passaria por ela. Hoje não há nenhum no código principal.
- O PDF segue com rótulos em inglês por desenho (relatório exportável não é bilíngue); só o
  valor do provedor deixou de sumir.
