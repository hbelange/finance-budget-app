import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Account } from '../core/services/account.service';
import { Transaction } from '../core/services/transaction.service';
import { TransferRequest, TransferService } from '../core/services/transfer.service';

export interface TransferDialogData {
  transfer: Transaction | null;
  currentAccountId: string;
  pairedAccountId: string | null;
  accounts: Account[];
}

function differentAccountsValidator(control: AbstractControl): ValidationErrors | null {
  const from = control.get('fromAccountId')?.value;
  const to = control.get('toAccountId')?.value;
  return from && to && from === to ? { sameAccount: true } : null;
}

function toLocalDate(dateStr: string): Date {
  return new Date(dateStr + 'T00:00:00');
}

function toDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

@Component({
  selector: 'app-transfer-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButton,
    MatFormField, MatLabel, MatError, MatSuffix,
    MatInput,
    MatSelect, MatOption,
    MatCheckbox,
    MatDatepickerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.transfer ? 'Edit Transfer' : 'New Transfer' }}</h2>
    <mat-dialog-content>
      <form id="transfer-form" [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline">
          <mat-label>From Account</mat-label>
          <mat-select formControlName="fromAccountId">
            @for (account of data.accounts; track account.id) {
              <mat-option [value]="account.id">{{ account.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.fromAccountId.hasError('required')) {
            <mat-error>From account is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>To Account</mat-label>
          <mat-select formControlName="toAccountId">
            @for (account of toAccounts(); track account.id) {
              <mat-option [value]="account.id">{{ account.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.toAccountId.hasError('required')) {
            <mat-error>To account is required</mat-error>
          }
          @if (form.hasError('sameAccount')) {
            <mat-error>From and to accounts must be different</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Date</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="date" />
          <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          @if (form.controls.date.hasError('required')) {
            <mat-error>Date is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" min="0.01" />
          @if (form.controls.amount.hasError('required')) {
            <mat-error>Amount is required</mat-error>
          }
          @if (form.controls.amount.hasError('min')) {
            <mat-error>Amount must be greater than 0</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Memo</mat-label>
          <input matInput formControlName="memo" />
        </mat-form-field>
        <mat-checkbox formControlName="cleared">Cleared</mat-checkbox>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button form="transfer-form" type="submit" [disabled]="form.invalid">Save</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content form {
      display: flex;
      flex-direction: column;
      padding-top: 8px;
      min-width: min(400px, 85vw);
      gap: 4px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferDialogComponent {
  protected readonly data = inject<TransferDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TransferDialogComponent>);
  private readonly transferService = inject(TransferService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly initFromId = this.data.transfer
    ? (this.data.transfer.amount < 0 ? this.data.transfer.accountId : this.data.pairedAccountId)
    : this.data.currentAccountId;
  private readonly initToId = this.data.transfer
    ? (this.data.transfer.amount > 0 ? this.data.transfer.accountId : this.data.pairedAccountId)
    : null;

  protected readonly form = new FormGroup({
    fromAccountId: new FormControl<string | null>(this.initFromId, [Validators.required]),
    toAccountId: new FormControl<string | null>(this.initToId, [Validators.required]),
    date: new FormControl<Date | null>(
      this.data.transfer ? toLocalDate(this.data.transfer.date) : null,
      [Validators.required]
    ),
    amount: new FormControl<number | null>(
      this.data.transfer ? Math.abs(this.data.transfer.amount) : null,
      [Validators.required, Validators.min(0.01)]
    ),
    memo: new FormControl(this.data.transfer?.memo ?? '', { nonNullable: true }),
    cleared: new FormControl(this.data.transfer?.cleared ?? false, { nonNullable: true }),
  }, { validators: differentAccountsValidator });

  private readonly selectedFromId = toSignal(this.form.controls.fromAccountId.valueChanges, { initialValue: this.initFromId });
  protected readonly toAccounts = computed(() => this.data.accounts.filter(a => a.id !== this.selectedFromId()));

  protected submit(): void {
    if (this.form.invalid) return;
    const { fromAccountId, toAccountId, date, amount, memo, cleared } = this.form.getRawValue();
    const req: TransferRequest = {
      fromAccountId: fromAccountId!,
      toAccountId: toAccountId!,
      date: toDateStr(date!),
      amount: Number(amount!),
      memo: memo || null,
      cleared,
    };
    const call$ = this.data.transfer
      ? this.transferService.updateTransfer(this.data.transfer.id, req)
      : this.transferService.createTransfer(req);
    call$.subscribe({
      next: legs => this.dialogRef.close(legs),
      error: () => this.snackBar.open('Failed to save transfer.', 'OK', { duration: 5000 }),
    });
  }
}
