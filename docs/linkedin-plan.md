# Série de LinkedIn — jornada do portfólio de QA (CyberAudit)

Série de 6 posts contando a jornada do projeto. Cadência: **2 posts/semana (ter/qui), de
manhã (9h–11h)**, concluir a postagem até **segunda, 09**. Cada post tem imagem(ns);
as geradas por diagrama estão descritas; onde há print real, está indicado.

Status (2026-10-28): Posts 1–5 agendados; Post 6 por agendar. A postagem segue o ritmo
da série (não publicar tudo de uma vez).

Dicas gerais: gancho na 1ª linha; blocos curtos; 1–4 imagens; sentença no fim (pergunta/CTA);
3–5 hashtags; **prova > adjetivo** (print real > arte); no Post 2+, linkar o anterior nos
comentários. Nunca afirmar "achei falha grave em sistema de terceiro" — é o projeto/produto
de portfólio do próprio dono.

---

## Post 1 — O projeto (lançamento)
**Imagem:** diagrama "escopo" (áreas testadas em teal, ferramentas em cinza, caixa verde
"59 testes no CI · 100% verde"). Separar **plano** (39 casos) de **resultado** (59 testes/100%).

> **A maioria dos portfólios de QA testa um "to-do app". Eu resolvi testar um scanner de segurança de verdade.**
>
> Montei um projeto de QA de ponta a ponta sobre o CyberAudit — um SaaS que faz varredura de postura de segurança de sites. A ideia não era "fazer um teste passar", era simular o trabalho real de um QA/SDET num produto com autenticação, 2FA, pagamento e um motor de scan.
>
> Comecei pelo que vem antes do código — o plano: um catálogo de 39 casos priorizados por risco (P0 a P2), cobrindo autenticação, autorização, limites de plano, anti-SSRF, o motor de scan, pagamento e interface.
>
> Três regras que me mantiveram honesto o projeto inteiro:
> → Autoria real — cada teste escrito e explicável por mim, linha a linha.
> → Nunca contra produção — ambiente próprio, isolado, com alvos vulneráveis controlados.
> → Um P0 que falha é bug a registrar, não teste a ajustar.
>
> As ferramentas no caminho: Postman/Newman, RestAssured e Cypress, tudo orquestrado por um pipeline de CI (GitHub Actions + Allure).
>
> O resultado até aqui: a maior parte dos casos já automatizada e rodando sozinha no CI — 59 testes, 100% verde, com relatório publicado. (E não, não são todos os 39 ainda: alguns estão parados por bugs reais que encontrei — mas essa é a história do próximo post. 👀)
>
> Vou documentar a jornada aqui nas próximas semanas: a arquitetura, os bugs, e os tropeços (teve um clássico "passa local, quebra no CI"). Bora acompanhar?
>
> #QA #SDET #TestAutomation #QualidadeDeSoftware #QualityEngineering

---

## Post 2 — Ambiente de teste (Docker)
**Imagem:** diagrama do docker-compose (Backend/Frontend = sistema; Postgres/WireMock/Seeder =
infra/mock; Juice Shop = alvo vulnerável).

> **Um teste só vale se roda igual pra todo mundo. Então, antes de escrever qualquer teste, eu construí o ambiente.**
>
> No projeto de QA que comecei a compartilhar aqui, o passo seguinte ao plano foi montar um ambiente de teste reprodutível — um comando sobe o sistema inteiro.
>
> Com docker-compose eu subo cinco serviços + um seeder de uma vez:
> → Backend + Frontend — o sistema sob teste.
> → Postgres com dados semeados (usuários de teste, planos, 2FA já configurado).
> → WireMock mockando o Mercado Pago — pra testar o checkout (Pix, cartão, webhook) sem dinheiro real e sem flakiness.
> → Juice Shop como alvo deliberadamente vulnerável pro motor de scan ter o que encontrar.
> → um seeder que roda uma vez e popula o banco.
>
> Por que isso é o alicerce de tudo:
> → Reprodutível — na minha máquina, na sua, no CI, é o mesmo ambiente.
> → Isolado — nunca testo contra produção; os alvos vulneráveis são controlados.
> → Determinístico — dados semeados + reset de estado entre testes = adeus "funciona às vezes".
>
> Foi esse ambiente que, mais tarde, deixou o pipeline de CI rodar as 3 suítes sozinho — e foi ele que me ajudou a achar bugs reais no produto. Essa é a história do próximo post. 👀
>
> #QA #SDET #Docker #TestAutomation #QualityEngineering

---

## Post 3 — Bugs reais (o mais forte)
**Imagens:** 1) board dos 4 bugs (BUG-04 destacado); 2) print REAL do código — `verifyEmailOtp`
com `otp.setUsed(true)` destacado; 3) print REAL do `verifyLoginCode` (TOTP) sem o setUsed;
4) print do doc BUG-04 "Resultado esperado vs obtido". (A ilustração do replay é opcional.)

> 🐛 **O melhor sinal de um bom QA não é o teste verde. É o bug que ele acha.**
>
> Testando o CyberAudit, o ambiente controlado me deixou ir fundo — e apareceram 4 bugs reais no produto. O mais sério foi um de segurança:
>
> Replay de TOTP (segundo fator reusável).
> No login com 2FA, o mesmo código TOTP podia ser usado mais de uma vez dentro da janela de validade — e gerava uma nova sessão a cada vez. Um código OTP deveria valer uma vez só. Na prática: quem interceptasse um código válido (ombro, phishing, um log) conseguia reusá-lo pra cunhar sessões.
>
> Como achei? Lendo o contrato antes de automatizar — não foi um teste que falhou. Reparei que o caminho do Email OTP marcava o código como "usado", e o do TOTP não. Reproduzi: verify com código válido → 200 + sessão; verify de novo, mesmo código → 200 + sessão outra vez.
>
> Os outros 3 (no board acima):
> → SSRF — a forma hexadecimal do IP (0x7f000001) não era normalizada e passava pelo guard.
> → O scanner não conectava em HTTP puro em porta não-padrão (voltava httpStatus 0).
> → A API key não autenticava de jeito nenhum (LazyInitializationException).
>
> O que isso diz sobre QA:
> → Entender o contrato > repetir o clique. O bug mais sério estava numa assimetria do código que só vi lendo — não num passo-a-passo.
> → Um P0 que falha é bug a registrar, não teste a ajustar. Documentei cada um: reprodução, causa raiz e um teste de regressão @Disabled, esperando o fix pra ficar verde.
>
> Achar bug não é "quebrar" o produto. É proteger o usuário antes que ele chegue lá.
>
> #QA #SDET #Seguranca #TestAutomation #BugHunting

---

## Post 4 — Pagamento sem o gateway real
**Imagem:** diagrama (Backend → mp.base-url → WireMock mock; Mercado Pago real tracejado
"fora do teste"; webhook assinado HMAC → Backend). Print real opcional: stub do wiremock
ou o teste do webhook.

> **Como testar pagamento — Pix, cartão, webhook — sem dinheiro real e sem testes que "funcionam às vezes".**
>
> Pagamento é das partes mais chatas de testar: depende de um gateway externo (Mercado Pago), tem webhook assíncrono e muta o estado da conta. No projeto de QA do CyberAudit, resolvi em 4 frentes:
>
> 🔁 Troquei o gateway real por um mock.
> Um patch deixou a URL base do MP configurável; no ambiente de teste ela aponta pro WireMock, que devolve respostas canned (preapproval aprovado/recusado, payment Pix). Zero chamada ao MP real, zero flakiness de rede.
>
> ✍️ Simulei o webhook — assinado de verdade.
> O backend valida a assinatura HMAC-SHA256 do webhook. Então o teste gera a assinatura correta e dispara o POST, provando os dois lados: o caminho feliz (pagamento aprovado libera o plano) e o de segurança (assinatura inválida → 401).
>
> ♻️ Isolei o estado.
> Checkout de upgrade muta a conta (vira PRO, persiste). Cada teste usa uma conta FREE dedicada + um reset no beforeEach → idempotente, roda 100x igual.
>
> 🚧 E soube onde parar.
> O cartão na tela usa os Secure Fields do MP num iframe cross-origin — o Cypress não digita ali. Em vez de forçar, documentei: coberto por API + verificação manual. Saber o limite da ferramenta é parte do trabalho.
>
> Testar bem não é simular tudo. É simular a borda certa, isolar o estado e saber o que não dá pra automatizar.
>
> #QA #SDET #TestAutomation #Payments #WireMock

---

## Post 5 — War story: "passa local, quebra no CI"
**Imagem:** diagrama do contraste PT/EN (teste procura "Histórico"; local pt-BR passa;
CI en-US mostra "History" e falha; causa: navigator.language). Print real opcional: o log do
CI com o diff da asserção.

> **O teste passava na minha máquina. Quebrava no CI. A história de como achei o porquê — e por que isso é QA de verdade.**
>
> Montei o pipeline, rodei, e... o Cypress quebrou. Só o smoke passava; todos os testes de interface falhavam com "elemento não encontrado". Na minha máquina, 100% verde.
>
> Em vez de chutar, fui por eliminação:
> → O RestAssured passava no CI → backend e token estão OK.
> → O smoke (que não faz login) passava; os testes logados falhavam → é algo da interface.
> → O detalhe que destravou: os textos que o Cypress não achava eram todos em português ("Histórico", "Agendamentos").
>
> Caiu a ficha: o app escolhe o idioma pelo navigator.language. Na minha máquina (Windows, pt-BR) ele renderiza em português, e os seletores casam. No runner do GitHub (Linux, en-US), o app renderiza em inglês — e os seletores em PT não encontram nada.
>
> O fix foi de uma linha (fixar o idioma pt-BR nos testes). Mas o que importa é o método:
> → Reproduzir antes de consertar. Simulei navigator.language = en-US localmente, vi o mesmo erro, apliquei o fix e provei que resolvia — tudo antes de empurrar pro CI.
> → Eliminação > chute. Cada evidência descartou uma hipótese até sobrar uma.
>
> "Passa local, quebra no CI" quase nunca é azar. É uma diferença de ambiente esperando ser encontrada.
>
> #QA #SDET #CI #Debugging #TestAutomation

---

## Post 6 — O fecho: CI + Allure ao vivo
**Imagens:** 1) **print REAL do relatório Allure** (59 testes, 100%, joaoariza.github.io/...) —
é a capa; 2) diagrama do pipeline (push → ambiente → 3 suítes → Allure publicado).
**Conferir os dois links antes de postar.**

> **Da ideia a um pipeline que roda sozinho e publica o resultado. O fecho da série.**
>
> Nas últimas semanas, compartilhei aqui a jornada de um projeto de QA de ponta a ponta testando o CyberAudit. Hoje, o encerramento.
>
> Um único git push na main dispara o GitHub Actions, que:
> → sobe o ambiente Docker inteiro (5 serviços + seed),
> → roda as 3 suítes — RestAssured, Newman e Cypress — contra ele,
> → gera um relatório Allure unificado,
> → e publica no GitHub Pages.
>
> Resultado, ao vivo e clicável: 59 testes, 100% verde. 👇
>
> Recapitulando a série:
> → Um plano por risco (39 casos, P0–P2).
> → Um ambiente reprodutível em Docker.
> → 4 bugs reais encontrados (incluindo um replay de 2FA).
> → Pagamento testado sem o gateway real.
> → Um "passa local, quebra no CI" resolvido por eliminação.
> → E tudo rodando sozinho no CI, com relatório publicado.
>
> 🔗 Repositório: github.com/JoaoAriza/cyberaudit-qa
> 🔗 Relatório Allure ao vivo: joaoariza.github.io/cyberaudit-qa
>
> Se você trabalha com QA/SDET ou está contratando, bora conectar. 🙌
>
> #QA #SDET #TestAutomation #CI #QualityEngineering

---

## Bônus (pós-série)
Consolidar a jornada num **LinkedIn Article** ou num **README** do repo — material de
portfólio que não some na timeline.
