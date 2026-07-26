import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatFormField, MatLabel, MatError } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Goal, GoalRequest, GoalService, ROLLOVER_TYPE_LABELS, RolloverType } from '../core/services/goal.service';

const ROLLOVER_TYPES = Object.keys(ROLLOVER_TYPE_LABELS) as RolloverType[];

export interface GoalDialogData {
  categoryId: string;
  categoryName: string;
  goal: Goal | null;
}

@Component({
  selector: 'app-goal-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButton,
    MatFormField, MatLabel, MatError,
    MatInput,
    MatSelect, MatOption,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.goal ? 'Edit Goal: ' + data.categoryName : 'Add Goal: ' + data.categoryName }}</h2>
    <mat-dialog-content>
      <form id="goal-form" [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" />
          @if (form.controls.amount.hasError('required')) {
            <mat-error>Amount is required</mat-error>
          }
          @if (form.controls.amount.hasError('min')) {
            <mat-error>Amount must be zero or greater</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Day of Month</mat-label>
          <input matInput type="number" formControlName="dayOfMonth" />
          @if (form.controls.dayOfMonth.hasError('required')) {
            <mat-error>Day of month is required</mat-error>
          }
          @if (form.controls.dayOfMonth.hasError('min') || form.controls.dayOfMonth.hasError('max')) {
            <mat-error>Day of month must be between 1 and 31</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Rollover Type</mat-label>
          <mat-select formControlName="rolloverType">
            @for (type of rolloverTypes; track type) {
              <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
            }
          </mat-select>
          @if (form.controls.rolloverType.hasError('required')) {
            <mat-error>Rollover type is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button form="goal-form" type="submit" [disabled]="form.invalid">Save</button>
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
export class GoalDialogComponent {
  protected readonly data = inject<GoalDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<GoalDialogComponent>);
  private readonly goalService = inject(GoalService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly rolloverTypes = ROLLOVER_TYPES;
  protected readonly typeLabels: Record<string, string> = ROLLOVER_TYPE_LABELS;

  protected readonly form = new FormGroup({
    amount: new FormControl<number | null>(this.data.goal?.amount ?? null, [Validators.required, Validators.min(0)]),
    dayOfMonth: new FormControl<number | null>(this.data.goal?.dayOfMonth ?? null, [Validators.required, Validators.min(1), Validators.max(31)]),
    rolloverType: new FormControl<RolloverType | null>(this.data.goal?.rolloverType ?? null, [Validators.required]),
  });

  protected submit(): void {
    if (this.form.invalid) return;
    const { amount, dayOfMonth, rolloverType } = this.form.getRawValue();
    const req: GoalRequest = {
      categoryId: this.data.categoryId,
      amount: Number(amount!),
      dayOfMonth: Number(dayOfMonth!),
      rolloverType: rolloverType!,
    };
    const call$ = this.data.goal
      ? this.goalService.updateGoal(this.data.categoryId, req)
      : this.goalService.createGoal(req);
    call$.subscribe({
      next: goal => this.dialogRef.close(goal),
      error: () => this.snackBar.open('Failed to save goal. Please try again.', 'OK', { duration: 5000 }),
    });
  }
}
