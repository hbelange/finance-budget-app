# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Full-stack Spring Boot application for personal finance and budget tracking.

## Stack

- **Backend**: Java / Spring Boot, built with Maven
- **Frontend**: Angular (with Angular Material, SCSS), built with Angular CLI

## Commands

```bash
# Backend build
./mvnw clean package

# Backend run
./mvnw spring-boot:run

# Backend test
./mvnw test

# Single backend test
./mvnw test -Dtest=MyTestClass#myMethod

# Frontend install
cd frontend && npm install

# Frontend serve (proxies /api to localhost:8080)
cd frontend && npx ng serve

# Frontend build
cd frontend && npx ng build

# Accessing Postgres Docker Instance
docker exec -it budget-postgres psql -U budget
```

## Planning

All project documentation will be kept in the `planning` directory

The key document is PLAN.md included here:

@planning/PLAN.md

## Learning

After you make an addition or a change for me to approve, before asking be to approve the change, please explain every piece of that change in detail, step by step.
I do not blindly accept code - I need to understand before I approve.

### Example

You should respond similar to this after every edit: 
"""
Let me walk through account-dialog.component.ts section by section.
                                   
  ---                          
  Imports                     
  import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
  Angular's reactive forms library. FormGroup holds the whole form, FormControl represents one field, Validators provides built-in validation rules like "required".
                                         
  import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
  - MAT_DIALOG_DATA — the data passed in when the dialog was opened (the account being edited, or null for a new one)                                                                        
  - MatDialogRef — a handle to this dialog instance, used to close it programmatically                               
  - MatDialogModule — provides the mat-dialog-title, mat-dialog-content, mat-dialog-actions directives used in the template                                                                  
                                                                                                                                                                                           
  ---                                                                                                                                                                                      
  The form                                
  protected readonly form = new FormGroup({                                                                                                                                                  
    name: new FormControl(this.data?.name ?? '', { nonNullable: true, validators: [Validators.required] }),                                                                                  
    type: new FormControl<AccountType | null>(this.data?.type ?? null, [Validators.required]),                                                                                               
  });                                                                                                                                                                                        
  - If this.data is an existing account (edit mode), the fields are pre-filled with its values. Otherwise they start empty.                                                                  
  - nonNullable: true on name means getRawValue() returns string, never string | null.                                                                                                       
  - type allows null as its initial state (meaning "nothing selected yet"), which is what triggers the required validation error.                                                            
                                                                                                                                                                                             
  ---                                                                                                                                                                                        
  The template — form tag                                                                                                                                                                  
  <form id="account-form" [formGroup]="form" (ngSubmit)="submit()">                                                                                                                          
  The id="account-form" is how the Save button outside the form connects to it — via form="account-form" on the button. This lets the button live in <mat-dialog-actions> (outside the form
  tag) while still submitting it.                                                                                                                                                            
                                                                                                                                                                                           
  ---                                                                                                                                                                                        
  Error display                                                                                                                                                                              
  @if (form.controls.name.hasError('required')) {                                                                                                                                            
    <mat-error>Name is required</mat-error>                                                                                                                                                  
  }                                                                                                                                                                                          
  Angular Material shows <mat-error> automatically when the field is invalid and touched. The @if just controls which message to show (useful when a field has multiple validators).       
                                                                                                                                                                                             
  ---                                                                                                                                                                                        
  The Save button                                                                                                                                                                            
  [disabled]="form.invalid"                                                                                                                                                                  
  Disables the button until both fields pass validation. The user can't submit an incomplete form.                                                                                           
                                          
  ---                                                                                                                                                                                        
  submit()                      
  const call$ = this.data                                                                                                                                                                    
    ? this.accountService.updateAccount(this.data.id, req)                                                                                                                                   
    : this.accountService.createAccount(req);             
  call$.subscribe(account => this.dialogRef.close(account));                                                                                                                                 
  One ternary picks the right API call based on whether we're editing or creating. On success, dialogRef.close(account) closes the dialog and passes the saved account back to whoever opened
   it — so the list component can update itself without re-fetching everything.
                                                                                                                                                                                             
  ---                           
  Ready to continue with the list component?
"""


