describe('PLAN-01: Restrição de exportação de PDF para usuários Free na interface', () => {

    it('Deve desabilitar o botão de PDF e exibir o aviso de requisito PRO para contas Free', () =>{
        const email = Cypress.env('emailFree');
        const userId = Cypress.env('userIdFree');

        cy.task('forgeToken', { email, userId}).then((token) => {
            cy.visit('/', {
                onBeforeLoad: (win) => {
                    win.localStorage.setItem(Cypress.env('tokenKey'), token);
                }
            });
        });

        cy.contains('button', 'PDF')
        .should('be.disabled')
        .and('have.attr', 'title', 'Exportar PDF requer plano PRO ou superior');
    });

    it('Deve manter o botão de PDF habilitado para contas PRO (Controle Positivo)', () => {
        const email = Cypress.env('emailPro');
        const userId = Cypress.env('userIdPro');

        cy.task('forgeToken', { email, userId }).then((token) => {
            cy.visit('/', {
                onBeforeLoad: (win) => {
                    win.localStorage.setItem(Cypress.env('tokenKey'), token);
                }
            });
        });

        cy.contains('button', 'PDF')
        .should('not.be.disabled')
        .and('have.attr', 'title', 'No plano Pessoal Pro, o PDF vale apenas para domínios verificados na sua conta');
    });
});