describe('UI-01: Login com 2FA', () => {
    it('Deve realizar o login com e-mail, senha e código TOTP, validando o acesso ao dashboard', () =>{

        cy.visit('/');
        cy.contains('button', 'Entrar ou criar conta').click();

        cy.get('input[type=email]').type(Cypress.env('emailPro'));
        cy.get('input[type=password]').type(Cypress.env('password'));

        cy.get('form').contains('button', 'Entrar').click();

        cy.task('totp').then((code) => {
            cy.get('input[placeholder="000000"]').type(code);
            cy.contains('button', 'Confirmar').click();
        });

        cy.contains('Histórico').should('be.visible');

        cy.window().then((w) => {
            expect(w.localStorage.getItem(Cypress.env('tokenKey'))).to.be.ok;
        });
    });
});