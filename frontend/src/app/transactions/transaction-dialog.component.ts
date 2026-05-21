import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOptgroup, MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { CategoryGroup } from '../core/services/category.service';
import { Transaction, TransactionRequest } from '../core/services/transaction.service';

export interface TransactionDialogData {
  transaction: Transaction | null;
  accountId: string;
  categories: CategoryGroup[];
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
  selector: 'app-transaction-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButton,
    MatFormField, MatLabel, MatError, MatSuffix,
    MatInput,
    MatSelect, MatOption, MatOptgroup,
    MatCheckbox,
    MatDatepickerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.transaction ? 'Edit Transaction' : 'New Transaction' }}</h2>
    <mat-dialog-content>
      <form id="transaction-form" [formGroup]="form" (ngSubmit)="submit()">
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
          <mat-label>Payee</mat-label>
          <input matInput formControlName="payee" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryId">
            <mat-option [value]="null">None</mat-option>
            @for (group of data.categories; track group.id) {
              <mat-optgroup [label]="group.name">
                @for (cat of group.categories; track cat.id) {
                  <mat-option [value]="cat.id">{{ cat.name }}</mat-option>
                }
              </mat-optgroup>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" />
          @if (form.controls.amount.hasError('required')) {
            <mat-error>Amount is required</mat-error>
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
      <button mat-flat-button form="transaction-form" type="submit" [disabled]="form.invalid">Save</button>
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
export class TransactionDialogComponent {
  protected readonly data = inject<TransactionDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TransactionDialogComponent>);

  protected readonly form = new FormGroup({
    date: new FormControl<Date | null>(
      this.data.transaction ? toLocalDate(this.data.transaction.date) : null,
      [Validators.required]
    ),
    payee: new FormControl(this.data.transaction?.payee ?? '', { nonNullable: true }),
    categoryId: new FormControl<string | null>(this.data.transaction?.categoryId ?? null),
    amount: new FormControl<number | null>(this.data.transaction?.amount ?? null, [Validators.required]),
    memo: new FormControl(this.data.transaction?.memo ?? '', { nonNullable: true }),
    cleared: new FormControl(this.data.transaction?.cleared ?? false, { nonNullable: true }),
  });

  protected submit(): void {
    if (this.form.invalid) return;
    const { date, payee, categoryId, amount, memo, cleared } = this.form.getRawValue();
    const req: TransactionRequest = {
      accountId: this.data.accountId,
      date: toDateStr(date!),
      payee: payee || null,
      categoryId,
      amount: Number(amount!),
      memo: memo || null,
      cleared,
    };
    this.dialogRef.close(req);
  }
}
