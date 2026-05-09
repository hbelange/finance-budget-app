import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCard, MatCardContent } from '@angular/material/card';
import {
  MatExpansionPanel, MatExpansionPanelHeader,
  MatExpansionPanelTitle, MatExpansionPanelDescription,
} from '@angular/material/expansion';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BudgetStateService } from '../core/services/budget-state.service';
import { AllocationRequest, BudgetGroup, BudgetService, BudgetView } from '../core/services/budget.service';

@Component({
  selector: 'app-budget',
  imports: [
    CurrencyPipe,
    MatCard, MatCardContent,
    MatExpansionPanel, MatExpansionPanelHeader,
    MatExpansionPanelTitle, MatExpansionPanelDescription,
  ],
  templateUrl: './budget.component.html',
  styleUrl: './budget.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class BudgetComponent {
  private readonly budgetService = inject(BudgetService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly budgetView = signal<BudgetView | null>(null);
  protected readonly readyToAssign = computed(() => this.budgetView()?.readyToAssign ?? 0);

  private readonly _monthEffect = effect(() => this.loadBudget(this.month()));

  protected groupTotal(group: BudgetGroup): number {
    return group.categories.reduce((sum, c) => sum + c.assigned, 0);
  }

  protected abs(n: number): number {
    return Math.abs(n);
  }

  protected onAssignedBlur(groupId: string, catId: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const newAssigned = parseFloat(input.value) || 0;
    const view = this.budgetView();
    if (!view) return;

    const cat = view.groups.find(g => g.id === groupId)?.categories.find(c => c.id === catId);
    if (!cat || cat.assigned === newAssigned) return;

    const req: AllocationRequest = { categoryId: catId, month: this.month(), assigned: newAssigned };

    this.budgetService.upsertAllocation(req).subscribe({
      next: () => this.loadBudget(this.month()),
      error: () => {
        // Create a new signal reference to force re-render and revert the input
        this.budgetView.update(v => v ? { ...v } : v);
        this.snackBar.open('Failed to save allocation.', 'OK', { duration: 5000 });
      },
    });
  }

  private loadBudget(month: string): void {
    this.budgetService.getBudget(month).subscribe({
      next: view => this.budgetView.set(view),
      error: () => this.snackBar.open('Failed to load budget.', 'OK', { duration: 5000 }),
    });
  }
}
