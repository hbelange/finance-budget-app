import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';

export interface NameDialogData {
  title: string;
  initialValue?: string;
}

@Component({
  selector: 'app-name-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButton, MatFormField, MatLabel, MatError, MatInput],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline">
        <mat-label>Name</mat-label>
        <input matInput [formControl]="nameControl" (keydown.enter)="submit()" />
        @if (nameControl.hasError('required')) {
          <mat-error>Name is required</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button (click)="submit()" [disabled]="nameControl.invalid">Save</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content {
      display: flex;
      flex-direction: column;
      padding-top: 8px;
      min-width: min(320px, 85vw);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NameDialogComponent {
  protected readonly data = inject<NameDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<NameDialogComponent>);

  protected readonly nameControl = new FormControl(this.data.initialValue ?? '', {
    nonNullable: true,
    validators: [Validators.required],
  });

  protected submit(): void {
    if (this.nameControl.invalid) return;
    this.dialogRef.close(this.nameControl.value);
  }
}
