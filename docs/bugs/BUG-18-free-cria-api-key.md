# BUG-18 — Conta FREE cria API key pela API

Achado lateral em 2026-11-15, ao reconferir o contrato do AUTH-05. **Aberto, não
corrigido.** Formato conforme `../template-bug.md`.

```markdown
## Resumo

A tela diz que API key é recurso do PRO ("disponível no PRO", com link para os planos). Pela
API, uma conta FREE cria a chave normalmente: `POST /api-keys` → 201 com `plainKey`.

## Severidade

P1 — recurso pago (automação por CI/CD) liberado no plano grátis, contornando a trava que a
tela mostra.

## Ambiente

- Commit/versão testada: Backend `d0f9594`
- Ambiente: docker-compose de teste, backend 8081. Nunca produção.
- Ferramenta que encontrou: reconferência ao vivo do contrato do AUTH-05

## Passos para reproduzir

1. qa-free (plano FREE no seed, papel `OWNER` como todo cadastro), token forjado:
   `POST /api-keys` `{"name":"qualquer-nome-unico"}`.

## Resultado esperado

**402** "API Keys requerem plano PRO ou conta Empresa." (a mensagem que já está no código).

## Resultado obtido

**201**, chave ativa. A chave criada na reprodução foi revogada em seguida (`DELETE` → 204).

## Causa raiz

`ApiKeyService.checkApiKeyAccess` sai cedo para `Role.OWNER` e `Role.ADMIN`, antes de olhar
o plano. Só que `OWNER` é o papel do dono da conta, e todo cadastro nasce com ele
(`AuthService`, nos dois caminhos de cadastro). O ramo do FREE só alcança `FREE_EMPLOYEE`
fora de conta Empresa, ou seja, ninguém. O javadoc ("OWNER/ADMIN, COMPANY, PRO INDIVIDUAL;
FREE INDIVIDUAL bloqueado") lê `OWNER` como se fosse papel da plataforma.

O contrato da Etapa 1 do HANDOFF ("qa-free não cria chave, FREE → 402") veio da mesma
leitura errada do código, que pulou o retorno antecipado. Ao vivo nunca tinha sido
conferido.

## Correção

A fazer. Sugestão: tirar o retorno por papel e decidir só por plano/tipo de conta (equipe
da plataforma pelo `PlatformStaffService`, se for o caso), com teste contra uma conta FREE
criada pelo cadastro real.

## Teste de regressão

A escrever junto com a correção. Caso de QA candidato: qa-free cria chave → 402.
```
