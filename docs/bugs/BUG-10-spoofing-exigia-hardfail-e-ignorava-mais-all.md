# BUG-10 — Risco de spoofing exigia SPF `-all`, ignorava `pct=` e não via o `+all`

Encontrado em 2026-11-07 a partir do feedback de um cliente: o domínio dele trocou o DMARC
de `p=none` para `p=quarantine` e o laudo não se moveu (MEDIUM, -6, antes e depois).
Conferido no código e no DNS público em 2026-11-10. Formato conforme `../template-bug.md`.
Colar como issue no GitHub quando for abrir.

```markdown
## Resumo

O `calculateRisk` do `DnsSecurityService` só dava LOW com SPF `-all` E DMARC `p=reject`.
Três defeitos saíam dessa régua:

1. **Falso positivo.** SPF `~all` + DMARC `p=quarantine` (o caso do cliente) ou `p=reject`
   saía MEDIUM, -6, com o texto "Reforce SPF com -all". Com o DMARC em bloqueio, o
   qualificador do SPF não muda o destino do email falso: fail, softfail e neutral
   reprovam do mesmo jeito no DMARC, e é o `p=` que manda o receptor barrar.
2. **`pct=` ignorado.** `p=quarantine; pct=50` contava como quarantine inteiro. Pela RFC
   7489 §6.6.4, a metade fora do `pct` não sofre política nenhuma.
3. **`+all` não era quebrado.** `v=spf1 +all` ao lado de `p=reject` saía MEDIUM. O `+all`
   dá PASS a qualquer servidor: o email falso com envelope no próprio domínio passa no
   SPF alinhado e, por isso, passa no DMARC. `all` sem qualificador (que é `+all`) saía
   MEDIUM, e `include:spf-all.exemplo.com ~all` saía STRONG (a política era lida por
   substring).

De quebra: com SPF duplicado (inválido) e DMARC correto, o resumo HIGH mandava "Configure
DMARC" — o parâmetro olhava `isSpfPresent()`, e o SPF duplicado está presente.

## Severidade

P1 — desconto errado no score e issue no laudo, diante do cliente, nos dois sentidos:
-6 em domínio protegido (defeito 1, o mais comum: `~all` + quarantine é uma configuração
corriqueira) e risco subestimado em domínio desprotegido (defeito 3).

## Ambiente

- Commit/versão testada: Backend `9f9e84e`
- Ambiente: docker-compose de teste, backend 8081, com o DNS falso novo (`env/coredns/`).
  Registros reais do cliente conferidos só por consulta DNS pública. Nunca produção do
  CyberAudit (Regra 1).
- Ferramenta que encontrou: feedback de cliente + revisão de código + `nslookup`

## Passos para reproduzir

1. Subir o ambiente com o `coredns` (zona `dns-mock.test`).
2. qa-pro, `GET /scan?url=http://quarantine.dns-mock.test:8080` — SPF
   `v=spf1 include:_spf.mail.hostinger.com ~all`, DMARC `v=DMARC1; p=quarantine`.
3. O mesmo com `spf-mais-all.dns-mock.test` — SPF `v=spf1 +all`, DMARC `p=reject`.

## Resultado esperado

- `quarantine`: `dnsSecurityResult.emailSpoofingRisk` LOW, nenhuma nota de spoofing.
- `spf-mais-all`: HIGH, nota `-12`, resumo citando o `+all`.

## Resultado obtido

- `quarantine`: MEDIUM, nota `DNS Security (email spoofing risk MEDIUM): -6`, resumo
  "Reforce SPF com -all e DMARC com p=reject".
- `spf-mais-all`: MEDIUM, -6.

## Caso de teste relacionado

SCAN-07 (novo, P1).

## Causa raiz

A régua tratava "melhor prática" como "risco": `-all` e `reject` são o topo, mas LOW é
sobre o email falso ser barrado, e isso o DMARC em bloqueio já garante. Do outro lado, a
classificação do SPF por substring nunca separou o `+all`, que é o único qualificador que
anula o DMARC.

## Correção

Corrigido no Backend em 2026-11-10:

- `calculateRisk`: LOW = SPF válido + DMARC em bloqueio (`reject` com qualquer `pct`, ou
  `quarantine` com `pct` 100); SPF que autoriza todos (`+all`, `all`) conta como SPF
  quebrado — HIGH com DMARC, CRITICAL sem DMARC e com MX;
- `politicaSpf`/`qualificadorDoAll`: a política sai do qualificador do primeiro termo `all`
  (RFC 7208 §4.6.2 e §5.1), não de substring;
- `pctDmarc`: `pct=` ausente ou inválido vale 100 (RFC 7489 §6.3);
- textos: MEDIUM explica `p=none`/`pct`, sem pedir `-all`; LOW diz o que garante; texto novo
  `DNS_SPOOFING_SPF_TODOS` para o `+all`; recomendação aceita `~all` e proíbe `+all`
  (pt-BR e en); HIGH com SPF duplicado aponta o SPF.

Verificado ao vivo no ambiente, com o DNS falso (oito domínios, `env/coredns/README.md`):
`quarantine` LOW sem desconto; `quarantine-pct50` MEDIUM -6; `reject-pct25` LOW;
`spf-mais-all` HIGH -12 com o resumo do `+all`; `dmarc-none` MEDIUM -6. No DNS público, o
domínio do cliente tem exatamente `~all` + `p=quarantine`: sai de MEDIUM -6 para LOW.

## Teste de regressão

- Backend: `SpoofingEmBloqueioTest` (30 — bloqueio, `pct`, `+all`, a tabela de política do
  SPF e os textos nos dois idiomas) — 16 vermelhos antes do fix. Suíte inteira verde (732).
- QA: SCAN-07, a escrever pelo dono.
```

## Decisões que valem registrar

- **`reject` com `pct<100` segue LOW.** A RFC manda tratar a fração fora do `pct` como
  `quarantine`: todo email falso é barrado, parte rejeitada e parte no spam. Quem quiser
  ser mais rígido aqui precisa de outro argumento que não a RFC.
- **`?all` e SPF sem `all` (inclusive `redirect=`) entram no LOW com DMARC em bloqueio.**
  Neutral reprova no DMARC como softfail. O `redirect=` aponta o `all` para outro registro,
  que o scanner não segue — não dá para presumir `+all` ali.
- **`+all` com DMARC fica HIGH, não CRITICAL.** Na prática o DMARC não protege nada, mas a
  matriz trata "um dos dois quebrado" como HIGH; subir para CRITICAL é decisão de régua,
  não correção. O texto do laudo diz com todas as letras que o `+all` anula o DMARC.

## O que o fix NÃO resolve

- SPF ausente + DMARC `p=reject` continua HIGH. Tecnicamente o DMARC sozinho (com DKIM no
  email legítimo) já barra o falso; rever isso muda a régua de todo cliente sem SPF e fica
  fora desta etapa.
- O tag `t=y` do DMARCbis (o sucessor do `pct`) não é lido.
- O `p=`/`sp=` continua sem aceitar espaço em volta do `=` (o `pct=` novo aceita).
- O card de DMARC do frontend mostra `p=strong`/`p=medium` (o nível, não a política) — é
  cosmético e fica para a etapa G ou depois.
