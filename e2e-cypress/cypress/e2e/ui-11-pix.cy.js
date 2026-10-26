describe('UI-11: Fluxo Pix completo na tela com polling', () => {
    const email = Cypress.env('emailPay11');
    const userId = Cypress.env('userIdPay11');
    const accountId = Cypress.env('accountIdPay11');

    beforeEach(() => {
        cy.task('resetFree', { accountId });

        cy.task('forgeToken', {email, userId }).then((token) => {
            cy.visit('/',{
              onBeforeLoad: (win) => {
                win.localStorage.setItem(Cypress.env('tokenKey'), token);
                cy.stub(win.navigator.clipboard, 'writeText').as('copyStub');
              }  
            });
        });
    });

    it ('Deve gerar o Pix, exibir o QR Code e atualizar o status para PRO via polling após confirmação', () => {
        cy.contains('button', 'FREE').click();

        cy.contains('[class*=planCard]', 'Pessoal Pro').within(() => {
            cy.contains('button', 'Assinar').click();
        });

        cy.intercept('POST', '**/billing/checkout/pix').as('checkoutPix');

        cy.get('input[placeholder="000.000.000-00"]').type('111.444.777-35');
        cy.contains('button', 'Gerar Pix').click();

        cy.wait('@checkoutPix').then((interception) => {
            const paymentId = interception.response.body.paymentId;

            cy.get('img[alt="QR code Pix"]').should('be.visible');
            cy.contains('code', 'br.gov.bcb.pix').should('be.visible');
            cy.contains('Aguardando confirmação do pagamento...').should('be.visible');

            cy.contains('button', 'Copiar').click();
            cy.get('@copyStub').should('have.been.called');
            cy.contains('button', 'Copiado').should('be.visible');

            cy.task('signMpWebhook', { paymentId }).then((webhookPayLoad) => {
                cy.request({
                    method: 'POST',
                    url: `${Cypress.env('apiUrl')}/billing/webhook`,
                    headers: webhookPayLoad.headers,
                    body: webhookPayLoad.body
                }).then((response) => {
                    expect(response.status).to.eq(200);
                    expect(response.body).to.eq('ok');
                });
            });

            cy.contains('[class*=planCard]', 'Pessoal Pro').within(() => {
                cy.contains('button', 'Cancelar assinatura', { timeout: 15000 }).should('be.visible');
            });
            cy.get('input[placeholder="000.000.000-00"]').should('not.exist');
        });
    });
});