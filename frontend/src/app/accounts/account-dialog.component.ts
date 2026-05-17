import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel, MatError } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Account, AccountRequest, AccountService, ACCOUNT_TYPE_LABELS, AccountType } from '../core/services/account.service';

const ACCOUNT_TYPES = Object.keys(ACCOUNT_TYPE_LABELS) as AccountType[];

@Component({
  selector: 'app-account-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButton,
    MatFormField, MatLabel, MatError,
    MatInput,
    MatSelect, MatOption,
  ],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Edit Account' : 'New Account' }}</h2>
    <mat-dialog-content>
      <form id="account-form" [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline">
          <mat-label>Account Name</mat-label>
          <input matInput formControlName="name" />
          @if (form.controls.name.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Account Type</mat-label>
          <mat-select formControlName="type">
            @for (type of accountTypes; track type) {
              <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
            }
          </mat-select>
          @if (form.controls.type.hasError('required')) {
            <mat-error>Type is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button form="account-form" type="submit" [disabled]="form.invalid">Save</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content form {
      display: flex;
      flex-direction: column;
      padding-top: 8px;
      min-width: min(320px, 85vw);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDialogComponent {
  protected readonly data = inject<Account | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AccountDialogComponent>);
  private readonly accountService = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly accountTypes = ACCOUNT_TYPES;
  protected readonly typeLabels: Record<string, string> = ACCOUNT_TYPE_LABELS;

  protected readonly form = new FormGroup({
    name: new FormControl(this.data?.name ?? '', { nonNullable: true, validators: [Validators.required] }),
    type: new FormControl<AccountType | null>(this.data?.type ?? null, [Validators.required]),
  });

  protected submit(): void {
    if (this.form.invalid) return;
    const { name, type } = this.form.getRawValue();
    const req: AccountRequest = { name, type: type! };
    const call$ = this.data
      ? this.accountService.updateAccount(this.data.id, req)
      : this.accountService.createAccount(req);
    call$.subscribe({
      next: account => this.dialogRef.close(account),
      error: () => this.snackBar.open('Failed to save account. Please try again.', 'OK', { duration: 5000 }),
    });
  }
}
