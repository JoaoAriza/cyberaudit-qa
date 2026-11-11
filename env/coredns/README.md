# CoreDNS — DNS falso do ambiente

Serve a zona `dns-mock.test` (`dns-mock.test.db`) e repassa todo o resto para o DNS
do Docker. Existe para os casos que dependem de registros de email (SPF, DMARC, MX,
DKIM) que a gente controla, sem escanear domínio de terceiro: hoje SCAN-06 (BUG-09)
e SCAN-07 (BUG-10).

## Como o backend chega aqui

- O módulo de DNS do backend usa o dnsjava, que lê `-Ddns.server=coredns`
  (`JAVA_TOOL_OPTIONS` no compose). `DNS_RESOLVER=system` impede o
  `DnsResolverConfig` de trocar para DoH público no boot.
- O resto da JVM (o HTTP do scan, o `SsrfGuard`) continua no DNS do Docker. Por isso
  cada domínio da zona também é **network alias do WireMock** (dá o A; o stub
  `wiremock/mappings/dns-mock.json` responde a raiz) e está na `SSRF_TEST_ALLOWLIST`.
- Só o serviço `backend` aponta para o CoreDNS. O `backend-noallowlist` não.

Domínio novo na zona = três lugares: o registro aqui, o alias do WireMock e a
allowlist. Mudou o compose, recrie `wiremock` e `backend`.

## Domínios

| Domínio | Caso | Esperado |
|---|---|---|
| `dkim-hostinger.dns-mock.test` | SCAN-06 | `dkimHintFound: true`, `dkimSelector: hostingermail-a` |
| `dkim-revogado.dns-mock.test` | SCAN-06 | `dkimHintFound: false`, `dkimSelector: NOT_DETECTED` |
| `dkim-curinga.dns-mock.test` | SCAN-06 | `dkimHintFound: false`, `dkimSelector: NOT_DETECTED` |
| `quarantine.dns-mock.test` | SCAN-07 | risco `LOW`, sem desconto |
| `quarantine-pct50.dns-mock.test` | SCAN-07 | risco `MEDIUM`, -6 |
| `reject-pct25.dns-mock.test` | SCAN-07 | risco `LOW`, sem desconto |
| `spf-mais-all.dns-mock.test` | SCAN-07 | risco `HIGH`, -12, resumo cita o `+all` |
| `dmarc-none.dns-mock.test` | SCAN-07 | risco `MEDIUM`, -6 |

O porquê de cada um está comentado no `dns-mock.test.db`.

## Conferir a zona

A imagem do CoreDNS não tem shell. Pergunte de um container descartável na mesma rede:

```bash
docker run --rm --network env_default --entrypoint nslookup postgres:16-alpine -type=TXT _dmarc.quarantine.dns-mock.test coredns
```

Editou o `.db`: incremente o serial do SOA. O CoreDNS relê a zona sozinho (até 1 min);
o TTL é 5 s, mas o scan tem cache por host — reinicie o `backend` para escanear de novo
o mesmo domínio com o registro novo.
