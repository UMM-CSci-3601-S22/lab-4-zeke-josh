import { Todo } from 'src/app/todos/todo';
import { AddTodoPage } from '../support/add-todo.po';

describe('Add todo', () => {
  const page = new AddTodoPage();

  beforeEach(() => {
    page.navigateTo();
  });

  it('Should have the correct title', () => {
    page.getTitle().should('have.text', 'New Todo');
  });

  it('Should enable and disable the add todo button', () => {
    // ADD TODO button should be disabled until all the necessary fields
    // are filled. Once the last (`#status`) is filled, then the button should
    // become enabled.
    page.addTodoButton().should('be.disabled');
    page.getFormField('owner').type('test');
    page.addTodoButton().should('be.disabled');
    page.getFormField('category').type('test');
    page.addTodoButton().should('be.disabled');
    page.getFormField('body').type('test');
    page.addTodoButton().should('be.disabled');
    page.getFormField('body').clear().type('This is a test body.');
    page.addTodoButton().should('be.disabled');
    page.getFormField('status').get('mat-select[formControlName=status]').click().get('mat-option').contains('Complete').click();
    // all the required fields have valid input, then it should be enabled
    page.addTodoButton().should('be.enabled');
  });

  it('Should show error messages for invalid inputs', () => {
    // Before doing anything there shouldn't be an error
    cy.get('[data-test=ownerError]').should('not.exist');
    // Just clicking the owner field without entering anything should cause an error message
    page.getFormField('owner').click().blur();
    cy.get('[data-test=ownerError]').should('exist').and('be.visible');
    // Some more tests for various invalid owner inputs
    page.getFormField('owner').type('J').blur();
    cy.get('[data-test=ownerError]').should('exist').and('be.visible');
    page.getFormField('owner').clear().type('This is a very long owner that goes beyond the 50 character limit').blur();
    cy.get('[data-test=ownerError]').should('exist').and('be.visible');
    // Entering a valid owner should remove the error.
    page.getFormField('owner').clear().type('Smith').blur();
    cy.get('[data-test=ownerError]').should('not.exist');

    // Before doing anything there shouldn't be an error
    cy.get('[data-test=categoryError]').should('not.exist');
    // Just clicking the category field without entering anything should cause an error message
    page.getFormField('category').click().blur();
    cy.get('[data-test=categoryError]').should('exist').and('be.visible');
    // Some more tests for various invalid category inputs
    page.getFormField('category').type('J').blur();
    cy.get('[data-test=categoryError]').should('exist').and('be.visible');
    page.getFormField('category').clear().type('This is a very long category that goes beyond the 50 character limit').blur();
    cy.get('[data-test=categoryError]').should('exist').and('be.visible');
    // Entering a valid category should remove the error.
    page.getFormField('category').clear().type('Smith').blur();
    cy.get('[data-test=categoryError]').should('not.exist');

    // Before doing anything there shouldn't be an error
    cy.get('[data-test=bodyError]').should('not.exist');
    // Just clicking the body field without entering anything should cause an error message
    page.getFormField('body').click().blur();
    cy.get('[data-test=bodyError]').should('exist').and('be.visible');
    // Some more tests for various invalid body inputs
    page.getFormField('body').type('J').blur();
    cy.get('[data-test=bodyError]').should('exist').and('be.visible');
    page.getFormField('body').clear().type('This is a very long body that goes beyond the 150 character limit.'.repeat(3)).blur();
    cy.get('[data-test=bodyError]').should('exist').and('be.visible');
    // Entering a valid body should remove the error.
    page.getFormField('body').clear().type('Smith').blur();
    cy.get('[data-test=bodyError]').should('not.exist');

  });

  describe('Adding a new Todo', () => {

    beforeEach(() => {
      cy.task('seed:database');
    });

    it('Should go to the right page, and have the right info', () => {
      const todo: Todo = {
        _id: null,
        owner: 'Test todo',
        category: 'Test category',
        body: 'Test body',
        status: true,
      };

      page.addTodo(todo);

      // New URL should end in the 24 hex character Mongo ID of the newly added Todo
      cy.url()
        .should('match', /\/todos\/[0-9a-fA-F]{24}$/)
        .should('not.match', /\/todos\/new$/);

      // The new Todo should have all the same attributes as we entered
      cy.get('.todo-card-owner').should('have.text', todo.owner);
      cy.get('.todo-card-category').should('have.text', todo.category);
      cy.get('.todo-card-body').should('have.text', todo.body);
      cy.get('.todo-card-status').should('have.text', todo.status.toString());

      // We should see the confirmation message at the bottom of the screen
      cy.get('.mat-simple-snackbar').should('contain', `Added Todo ${todo.owner}`);
    });
  });

});
