import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDragPreview, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatIcon } from '@angular/material/icon';
import { MatCard, MatCardContent } from '@angular/material/card';
import {
  MatExpansionPanel, MatExpansionPanelHeader,
  MatExpansionPanelTitle, MatExpansionPanelDescription,
} from '@angular/material/expansion';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BudgetStateService } from '../core/services/budget-state.service';
import { AllocationRequest, BudgetCategory, BudgetGroup, BudgetService, BudgetView } from '../core/services/budget.service';
import { CategoryGroup, CategoryService } from '../core/services/category.service';
import { ConfirmDialogComponent } from '../accounts/confirm-dialog.component';
import { NameDialogComponent } from '../shared/name-dialog.component';
import { timer } from 'rxjs';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

@Component({
  selector: 'app-budget',
  providers: [CurrencyPipe],
  imports: [
    CurrencyPipe,
    CdkDropList, CdkDrag, CdkDragHandle, CdkDragPreview,
    MatIcon,
    MatCard, MatCardContent,
    MatExpansionPanel, MatExpansionPanelHeader,
    MatExpansionPanelTitle, MatExpansionPanelDescription,
    MatIconButton, MatButton,
    MatIcon, AppLoadingSpinnerComponent
  ],
  templateUrl: './budget.component.html',
  styleUrl: './budget.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class BudgetComponent {
  private readonly budgetService = inject(BudgetService);
  private readonly categoryService = inject(CategoryService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly currencyPipe = inject(CurrencyPipe);

  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly budgetView = signal<BudgetView | null>(null);
  protected readonly readyToAssign = computed(() => this.budgetView()?.readyToAssign ?? 0);

  private readonly _monthEffect = effect(() => this.loadBudget(this.month()));

  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  protected groupTotal(group: BudgetGroup): number {
    return group.categories.reduce((sum, c) => sum + c.assigned, 0);
  }

  protected abs(n: number): number {
    return Math.abs(n);
  }

  protected onAssignedFocus(event: Event, cat: BudgetCategory): void {
    const input = event.target as HTMLInputElement;
    input.value = cat.assigned === 0 ? '' : String(cat.assigned);
    input.select();
  }

  protected onAssignedEnter(event: Event): void {
    (event.target as HTMLInputElement).blur();
  }

  protected onAssignedBlur(groupId: string, catId: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const newAssigned = parseFloat(input.value) || 0;

    input.value = this.currencyPipe.transform(newAssigned) ?? '$0.00';

    const view = this.budgetView();
    if (!view) return;

    const cat = view.groups.find(g => g.id === groupId)?.categories.find(c => c.id === catId);
    if (!cat || cat.assigned === newAssigned) return;

    const delta = newAssigned - cat.assigned;

    this.budgetView.update(v => !v ? v : {
      ...v,
      readyToAssign: v.readyToAssign - delta,
      groups: v.groups.map(g => g.id !== groupId ? g : {
        ...g,
        categories: g.categories.map(c => c.id !== catId ? c : {
          ...c,
          assigned: newAssigned,
          available: c.available + delta,
        }),
      }),
    });

    const req: AllocationRequest = { categoryId: catId, month: this.month(), assigned: newAssigned };

    this.budgetService.upsertAllocation(req).subscribe({
      error: () => {
        this.budgetView.update(v => !v ? v : {
          ...v,
          readyToAssign: v.readyToAssign + delta,
          groups: v.groups.map(g => g.id !== groupId ? g : {
            ...g,
            categories: g.categories.map(c => c.id !== catId ? c : {
              ...c,
              assigned: cat.assigned,
              available: c.available - delta,
            }),
          }),
        });
        input.value = this.currencyPipe.transform(cat.assigned) ?? '$0.00';
        this.snackBar.open('Failed to save allocation.', 'OK', { duration: 5000 });
      },
    });
  }


  protected onGroupDrop(event: CdkDragDrop<BudgetGroup[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const groups = [...(this.budgetView()?.groups ?? [])];
    moveItemInArray(groups, event.previousIndex, event.currentIndex);
    this.budgetView.update(v => v ? { ...v, groups } : v);
    const items = groups.map((g, i) => ({ id: g.id, sortOrder: i }));
    this.categoryService.reorderGroups(items).subscribe({
      error: () => {
        this.loadBudget(this.month());
        this.snackBar.open('Failed to save group order.', 'OK', { duration: 5000 });
      },
    });
  }

  protected onCategoryDrop(group: BudgetGroup, event: CdkDragDrop<BudgetCategory[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const categories = [...group.categories];
    moveItemInArray(categories, event.previousIndex, event.currentIndex);
    this.budgetView.update(v => v ? {
      ...v,
      groups: v.groups.map(g => g.id === group.id ? { ...g, categories } : g),
    } : v);
    const items = categories.map((c, i) => ({ id: c.id, sortOrder: i }));
    this.categoryService.reorderCategories(group.id, items).subscribe({
      error: () => {
        this.loadBudget(this.month());
        this.snackBar.open('Failed to save category order.', 'OK', { duration: 5000 });
      },
    });
  }
  
  protected openAddGroup(): void {
    this.dialog.open(NameDialogComponent, { data: { title: 'New Category Group' } })
      .afterClosed()
      .subscribe((name: string | undefined) => {
        if (!name) return;
        this.categoryService.createGroup(name).subscribe({
          next: (categoryGroup) => this.budgetView.update(v => !v ? v : { ...v, groups: [...v.groups, { id: categoryGroup.id, name: categoryGroup.name, categories: [] }] }),
          error: () => this.snackBar.open('Failed to create group.', 'OK', { duration: 5000 }),
        });
      });
  }

  protected openRenameGroup(group: BudgetGroup): void {
    this.dialog.open(NameDialogComponent, { data: { title: 'Rename Group', initialValue: group.name } })
      .afterClosed()
      .subscribe((name: string | undefined) => {
        if (!name) return;
        this.budgetView.update(v => !v ? v : {
            ...v,
            groups: v.groups.map(g => g.id === group.id ? { ...g, name } : g),
          });
        this.categoryService.renameGroup(group.id, name).subscribe({
          error: () => {
            this.budgetView.update(v => !v ? v : {
              ...v,
              groups: v.groups.map(g => g.id === group.id ? { ...g, name: group.name } : g),
            });
            this.snackBar.open('Failed to rename group.', 'OK', { duration: 5000 });
          }
        });
      });
  }

  protected confirmDeleteGroup(group: BudgetGroup): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete "${group.name}" and all its categories? This cannot be undone.` },
    }).afterClosed().subscribe((confirmed: boolean | undefined) => {
      if (!confirmed) return;
      const originalIndex = this.budgetView()?.groups.findIndex(g => g.id === group.id);

      // for each category in the group, we need to add its assigned amount back to readyToAssign
      const refund = group.categories.reduce((sum, c) => sum + c.assigned, 0);

      this.budgetView.update(v => !v ? v : {
          readyToAssign: v.readyToAssign + refund,
          groups: v.groups.filter(g => g.id !== group.id),
        });
      this.categoryService.deleteGroup(group.id).subscribe({
        error: (err: HttpErrorResponse) => {
          this.budgetView.update(v => !v ? v : {
            ...v,
            groups: [...v.groups.slice(0, originalIndex!), group, ...v.groups.slice(originalIndex!)]
          });
          const msg = err.status === 409
            ? 'Remove all transactions from this group\'s categories before deleting.'
            : 'Failed to delete group.';
          this.snackBar.open(msg, 'OK', { duration: 5000 });
        },
      });
    });
  }

  protected openAddCategory(group: BudgetGroup): void {
    this.dialog.open(NameDialogComponent, { data: { title: `Add Category to "${group.name}"` } })
      .afterClosed()
      .subscribe((name: string | undefined) => {
        if (!name) return;
        this.categoryService.addCategory(group.id, name).subscribe({
          next: (categoryGroup) => this.budgetView.update(v => !v ? v : {
            ...v,
            groups: v.groups.map(g => g.id === group.id ? { ...g, categories : [...g.categories, {...categoryGroup.categories.slice(-1)[0], assigned: 0, spent: 0, available: 0 } ] } : g)
          }),
          error: () => this.snackBar.open('Failed to create category.', 'OK', { duration: 5000 }),
        });
      });
  }

  protected openRenameCategory(cat: BudgetCategory): void {
    this.dialog.open(NameDialogComponent, { data: { title: 'Rename Category', initialValue: cat.name } })
      .afterClosed()
      .subscribe((name: string | undefined) => {
        if (!name) return;
        this.budgetView.update(v => !v ? v : {
            ...v,
            groups: v.groups.map(g => ({
              ...g,
              categories: g.categories.map(c => c.id === cat.id ? { ...c, name } : c),
            })),
          });
        this.categoryService.renameCategory(cat.id, name).subscribe({
          error: () => {
            this.budgetView.update(v => !v ? v : {
              ...v,
              groups: v.groups.map(g => ({
                ...g,
                categories: g.categories.map(c => c.id === cat.id ? { ...c, name: cat.name } : c),
              })),
            });
            this.snackBar.open('Failed to rename category.', 'OK', { duration: 5000 });
          }
        });
      });
  }

  protected confirmDeleteCategory(cat: BudgetCategory): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { message: `Delete "${cat.name}"? This cannot be undone.` },
    }).afterClosed().subscribe((confirmed: boolean | undefined) => {
      if (!confirmed) return;
      const originalGroup = this.budgetView()?.groups.find(g => g.categories.some(c => c.id === cat.id));
      const originalIndex = originalGroup?.categories.findIndex(c => c.id === cat.id);
    
      this.budgetView.update(v => !v ? v : {
          readyToAssign: v.readyToAssign + cat.assigned,
          groups: v.groups.map(g => ({
            ...g,
            categories: g.categories.filter(c => c.id !== cat.id),
          })),
        });
      this.categoryService.deleteCategory(cat.id).subscribe({
        error: (err: HttpErrorResponse) => {
          this.budgetView.update(v => !v ? v : {
            readyToAssign: v.readyToAssign - cat.assigned,
            groups: v.groups.map(g => {
              if (g.id !== originalGroup?.id) return g;
              const restored = [...g.categories];
              restored.splice(originalIndex!, 0, cat);
              return { ...g, categories: restored };
            })
          });
          const msg = err.status === 409
            ? 'Remove all transactions from this category before deleting.'
            : 'Failed to delete category.';
          this.snackBar.open(msg, 'OK', { duration: 5000 });
        },
      });
    });
  }

  private loadBudget(month: string): void {
    this.isLoading.set(true);

    this.budgetService.getBudget(month).subscribe({
      next: view => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        this.budgetView.set(view);
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadBudget(this.month());
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);  
          this.snackBar.open('Failed to load budget.', 'OK', { duration: 5000 });
        }
      }
    });

    timer(5000).subscribe(() => {
      if (this.isLoading()) {
        this.isWakingUp.set(true);
      }
    });
  }
}
