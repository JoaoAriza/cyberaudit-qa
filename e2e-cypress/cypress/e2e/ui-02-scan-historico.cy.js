describe('UI-02: Iniciar scan, visualizar resultado e verificar histórico', () => {

    beforeEach(() => {
        cy.task('forgeToken', {email: Cypress.env('emailPro') }).then((token) => {
            cy.visit('/', {
                onBeforeLoad: (win) => {
                    win.localStorage.setItem(Cypress.env('tokenKey'), token);
                }
            });
        });
    });

    it('Deve executar o scan de um alvo, exibir a pontuação e registrá-lo no histórico', () => {
        cy.get('input[placeholder="example.com"]').type('wiremock:8080/health');
        cy.contains('button', '◈ Scan').click();

        cy.contains('/100', {timeout: 60000}).should('be.visible');

        cy.contains('button', 'Histórico').click();
        cy.contains('wiremock').should('be.visible');
    });
});