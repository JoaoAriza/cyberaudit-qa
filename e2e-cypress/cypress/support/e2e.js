// Arquivo de suporte do Cypress — carregado antes de cada spec.
//
// Comandos customizados (ex.: cy.login com 2FA, cy.bypassLogin via localStorage)
// ficam em cypress/support/commands.js e sao de autoria do dono (Regra 3).
// Quando criar o commands.js, descomente a linha abaixo:
// import './commands';

// Allure (Fase 6): registra os comandos/eventos que escrevem os resultados do Cypress.
require('@shelex/cypress-allure-plugin');

// Fixa o idioma do app em pt-BR em TODAS as specs, antes de cada carga de pagina.
// Sem isto, o app segue o navigator.language (ver Frontend i18n/catalog.ts,
// idiomaAtual): no runner da CI (Linux, locale en-US) ele renderiza em INGLES, e os
// seletores de texto em portugues dos specs ("Agendamentos", "Historico", "Exportar
// PDF requer plano PRO...") nao casam -> todos os specs logados quebram; so o smoke,
// que nao asserta texto PT, passa. O app le esta chave do localStorage ANTES do
// navigator.language, entao isto forca o PT. window:before:load roda antes de cada
// cy.visit, em todas as specs, sem precisar tocar nelas (Regra 3). Reproduzido e
// validado localmente sobrescrevendo navigator.language para en-US.
Cypress.on('window:before:load', (win) => {
  try {
    win.localStorage.setItem('cyberaudit.lang', 'pt-BR');
  } catch (e) {
    /* localStorage indisponivel: segue sem fixar o idioma */
  }
});
