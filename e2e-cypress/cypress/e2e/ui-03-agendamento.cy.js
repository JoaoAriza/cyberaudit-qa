/// <reference types="cypress" />

describe('UI-03: Crud de agendamento recorrente', () => {

    const targetHost = `example-${Date.now()}.com`;
    beforeEach(() => {

        cy.task('forgeToken', { email: Cypress.env('emailPro') }).then((token) => {
            cy.visit('/', {
                onBeforeLoad: (win) => {
                    win.localStorage.setItem(Cypress.env('tokenKey'), token);
                }
            });
        });
        cy.contains('button', 'Agendamentos').click();
    });

    it('Deve criar, pausar, persistir após reload e remover um agendamento recorrente', () => {

        cy.get('input[placeholder="example.com"]').type(targetHost);
        cy.contains('button', '+ Agendar').click();

        cy.contains(targetHost).should('be.visible');
        cy.contains('tr', 'Agendado').should('be.visible');

        cy.contains('button', 'Pausar').click();

        cy.contains('Pausado').should('be.visible');
        cy.contains('button', 'Retomar').should('be.visible');

        cy.reload();
        cy.contains('button', 'Agendamentos').click();

        cy.contains(targetHost).should('be.visible');

        cy.contains(targetHost).click();
        cy.contains('button', 'Remover').click();

        cy.contains(targetHost).should('not.exist');
    })
})