import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { forkJoin, timer } from 'rxjs';
import { MatIcon } from '@angular/material/icon';
import {
  MatExpansionPanel, MatExpansionPanelHeader,
  MatExpansionPanelTitle,
} from '@angular/material/expansion';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BudgetStateService } from '../core/services/budget-state.service';
import { BudgetService } from '../core/services/budget.service';
import { Goal, GoalService } from '../core/services/goal.service';
import { ConfirmDialogComponent } from '../accounts/confirm-dialog.component';
import { GoalDialogComponent, GoalDialogData } from './goal-dialog.component';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

interface GoalCategoryView {
  id: string;
  name: string;
  goal: Goal | null;
}

interface GoalGroupView {
  id: string;
  name: string;
  categories: GoalCategoryView[];
}

@Component({
  selector: 'app-goals',
  imports: [
    CurrencyPipe,
    MatIcon,
    MatExpansionPanel, MatExpansionPanelHeader,
    MatExpansionPanelTitle,
    MatIconButton, MatButton,
    AppLoadingSpinnerComponent,
  ],
  templateUrl: './goals.component.html',
  styleUrl: './goals.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class GoalsComponent {
  private readonly budgetService = inject(BudgetService);
  private readonly goalService = inject(GoalService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly groups = signal<GoalGroupView[]>([]);
  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  private readonly _monthEffect = effect(() => this.loadGoals(this.month()));

  protected openAddGoal(cat: GoalCategoryView): void {
    const data: GoalDialogData = { categoryId: cat.id, categoryName: cat.name, goal: null };
    this.dialog.open(GoalDialogComponent, { data })
      .afterClosed()
      .subscribe((goal: Goal | undefined) => {
        if (!goal) return;
        this.applyGoal(cat.id, goal);
      });
  }

  protected openEditGoal(cat: GoalCategoryView): void {
    if (!cat.goal) return;
    const data: GoalDialogData = { categoryId: cat.id, categoryName: cat.name, goal: cat.goal };
    this.dialog.open(GoalDialogComponent, { data })
      .afterClosed()
      .subscribe((goal: Goal | undefined) => {
        if (!goal) return;
        this.applyGoal(cat.id, goal);
      });
  }

  protected confirmDeleteGoal(cat: GoalCategoryView): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete the goal for "${cat.name}"? This cannot be undone.` },
    }).afterClosed().subscribe((confirmed: boolean | undefined) => {
      if (!confirmed) return;
      const previousGoal = cat.goal;
      this.applyGoal(cat.id, null);
      this.goalService.deleteGoal(cat.id).subscribe({
        error: () => {
          this.applyGoal(cat.id, previousGoal);
          this.snackBar.open('Failed to delete goal.', 'OK', { duration: 5000 });
        },
      });
    });
  }

  private applyGoal(categoryId: string, goal: Goal | null): void {
    this.groups.update(groups => groups.map(g => ({
      ...g,
      categories: g.categories.map(c => c.id === categoryId ? { ...c, goal } : c),
    })));
  }

  private loadGoals(month: string): void {
    this.isLoading.set(true);

    forkJoin({
      budget: this.budgetService.getBudget(month),
      goals: this.goalService.getAllGoals(),
    }).subscribe({
      next: ({ budget, goals }) => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        const goalsByCategoryId = new Map(goals.map(g => [g.categoryId, g]));
        this.groups.set(
          budget.groups
            .map(g => ({
              id: g.id,
              name: g.name,
              categories: g.categories
                .filter(c => !c.systemManaged)
                .map(c => ({ id: c.id, name: c.name, goal: goalsByCategoryId.get(c.id) ?? null })),
            }))
            .filter(g => g.categories.length > 0)
        );
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadGoals(month);
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);
          this.snackBar.open('Failed to load goals.', 'OK', { duration: 5000 });
        }
      },
    });

    timer(5000).subscribe(() => {
      if (this.isLoading()) {
        this.isWakingUp.set(true);
      }
    });
  }
}
