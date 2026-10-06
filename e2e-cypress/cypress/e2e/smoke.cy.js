// SMOKE TEST DE SCAFFOLD (infra) — NAO e um caso da matriz.
//
// So prova que o terreno do Cypress esta de pe: que o Cypress sobe, enxerga o
// Frontend no baseUrl (8090), que a API responde no apiUrl (8081) e que os
// cy.task de infra (totp e forgeToken) funcionam. Os specs dos casos reais
// (UI-01, UI-02, UI-03, PAY-11, PAY-12...) sao de autoria do dono (Regra 3).

describe('scaffold smoke', () => {
  it('carrega o Frontend no baseUrl', () => {
    cy.visit('/');
    cy.get('body').should('be.visible');
  });

  it('a API responde no apiUrl', () => {
    cy.request(`${Cypress.env('apiUrl')}/actuator/health`)
      .its('status')
      .should('eq', 200);
  });

  it('cy.task totp gera um codigo de 6 digitos', () => {
    cy.task('totp', Cypress.env('totpSecret')).then((code) => {
      expect(code).to.match(/^\d{6}$/);
    });
  });

  it('cy.task forgeToken gera um JWT de 3 partes aceito pela API', () => {
    cy.task('forgeToken', {}).then((token) => {
      expect(token.split('.')).to.have.length(3);
      // O token forjado autentica numa rota protegida (prova o bypass de login).
      cy.request({
        url: `${Cypress.env('apiUrl')}/history/recent`,
        headers: { Authorization: `Bearer ${token}` }
      }).its('status').should('eq', 200);
    });
  });
});
