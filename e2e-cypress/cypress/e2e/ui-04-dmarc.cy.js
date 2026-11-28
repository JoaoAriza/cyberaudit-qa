describe('UI-04: Exibição correta da política DMARC no card de Reconnaissance', () => {
    it('Deve exibir p=quarantine; pct=50 no card DMARC para o domínio quarantine-pct50.dns-mock.test', () => {

        cy.task('forgeToken', { email: Cypress.env('emailPro') }).then((token) => {
            cy.window().then((win) => {
                win.localStorage.setItem(Cypress.env('tokenKey'), token);
            });
        });

        cy.intercept('POST', '**/scan/async*').as('scanAsyncRequest');

        cy.visit('/');

        cy.contains('button, a', 'PDF', { timeout: 10000 }).should('be.visible');

        cy.get('input[placeholder="example.com"]')
            .clear()
            .type('http://quarantine-pct50.dns-mock.test:8080');

        cy.contains('button', '◈ Scan').click();

        cy.wait('@scanAsyncRequest').then((interception) => {
            const fullUrl = interception.request.url;
            const decodedTargetUrl = new URL(fullUrl).searchParams.get('url');
            expect(decodedTargetUrl).to.eq('http://quarantine-pct50.dns-mock.test:8080');
        });

        cy.contains(/\/100/, { timeout: 60000 }).should('be.visible');

        cy.contains('nav a, div a, span', 'Reconnaissance').click();

        cy.contains('h3, h4, h5, div, span', /^DMARC$/)
            .next()
            .should('have.text', 'p=quarantine; pct=50');
    });
});