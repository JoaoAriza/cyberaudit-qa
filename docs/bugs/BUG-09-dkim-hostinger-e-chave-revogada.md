# BUG-09 — DKIM da Hostinger não detectado, e chave revogada contada como DKIM

Encontrado em 2026-11-07 a partir do feedback de um cliente, que apontou "DKIM não
detectado" num domínio que tem DKIM. Conferido no DNS público em 2026-11-09, que revelou
também o segundo defeito (chave revogada). Formato conforme `../template-bug.md`. Colar
como issue no GitHub quando for abrir.

```markdown
## Resumo

Dois defeitos na sonda de DKIM do `DnsSecurityService`:

1. A Hostinger não estava na tabela de provedores nem na lista de seletores. Um domínio
   com SPF `include:_spf.mail.hostinger.com`, MX `mx1/mx2.hostinger.com` e DKIM em
   `hostingermail-a._domainkey` saía com `dkimSelector: NOT_DETECTED`.
2. A sonda contava como "DKIM encontrado" qualquer TXT no nome do seletor. A Hostinger
   publica `hostingermail-b` e `-c` como `v=DKIM1;p=` — chave revogada (RFC 6376 §3.6.1).
   Um TXT curinga (`*.dominio TXT "v=spf1 ..."`) faria todo seletor "existir".

## Severidade

P2 — dado errado no laudo, diante do cliente, mas sem efeito no score: o DKIM não entra no
cálculo do risco de spoofing (`NOT_DETECTED` já é "inconclusivo"). O defeito 2 vai no
sentido perigoso (afirma uma assinatura que nenhum receptor aceita), só que depende de o
domínio ter só seletores revogados ou TXT curinga.

## Ambiente

- Commit/versão testada: Backend `7b3236d`
- Ambiente: o `DnsSecurityService` real, rodado da máquina contra o DNS público (só
  consultas DNS, nenhum acesso HTTP ao site). Nunca produção do CyberAudit (Regra 1).
- Ferramenta que encontrou: feedback de cliente + revisão de código + `nslookup`

## Passos para reproduzir

1. `nslookup -type=TXT hostingermail-a._domainkey.<dominio>` → CNAME para
   `hostingermail-a.dkim.mail.hostinger.com`, TXT `v=DKIM1;k=rsa;p=MIIBIjAN...`.
2. O mesmo para `-b` e `-c` → CNAME para a Hostinger, TXT `v=DKIM1;p=` (revogada).
3. Rodar `DnsSecurityService.scan("<dominio>")`.

## Resultado esperado

`dkimHintFound: true`, `dkimSelector: hostingermail-a`. Seletor revogado ou TXT que não é
chave DKIM não contam.

## Resultado obtido

`dkimHintFound: false`, `dkimSelector: NOT_DETECTED` (defeito 1). Com a Hostinger na
tabela mas sem o defeito 2 corrigido, `-b` e `-c` contariam como encontrados.

## Caso de teste relacionado

SCAN-06 (novo, P2) — depende de um DNS falso no ambiente (ver HANDOFF §11, Etapa B).

## Causa raiz

1. Tabela incompleta: a Hostinger, uma das hospedagens mais usadas no Brasil, ficou de fora.
2. `sondarSeletores` decidia por `records.length > 0`, sem ler o conteúdo do TXT.

## Correção

Corrigido no Backend em 2026-11-09:

- provedor `Hostinger` na tabela (marcadores `_spf.mail.hostinger.com`, `mx1.hostinger.com`,
  `mx2.hostinger.com`; seletores `hostingermail-a/-b/-c`), e os três na lista de reserva;
- `temChaveDkim`: só conta TXT com `p=` não vazio, e descarta registro cujo `v=` (primeira
  tag) não é `DKIM1`. Espaço dentro do `p=` é aceito, e `v=` ausente também (é opcional).

Verificado ao vivo contra o DNS público: o mesmo domínio → `dkimHintFound: true`,
`dkimSelector: hostingermail-a`; aplicando a regra aos TXT reais, `-a` conta e `-b`/`-c`
não. Container `backend` rebuildado; suíte RestAssured do dono: 44, 0 falhas, 2 pulados.

## Teste de regressão

- Backend: `DkimChavePublicadaTest` (12 — chave válida, 4 formas de revogada, sem `v=`,
  espaço no `p=`, TXT curinga de SPF, verificação de site, `v=DKIM2`, revogada ao lado de
  válida, lista vazia) e `DkimProviderHintTest` +2 (SPF e MX da Hostinger) — 9 vermelhos
  antes do fix. O teste de coerência da tabela passou a incluir a Hostinger. Suíte
  inteira verde (702).
- QA: SCAN-06, a escrever pelo dono quando o DNS falso existir.
```

## O que o fix NÃO resolve

- Seletor customizado ou com data (Google `20230601`, etc.) continua não adivinhável:
  `NOT_DETECTED` segue significando "inconclusivo", não "ausente".
- Domínio só com seletores revogados sai `NOT_DETECTED`, não "revogado". Diferenciar
  seria informação a mais no laudo, não correção — fica de fora.
