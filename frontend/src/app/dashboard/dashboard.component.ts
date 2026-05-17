import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { forkJoin, timer } from 'rxjs';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BudgetStateService } from '../core/services/budget-state.service';
import { DashboardDto, ReportService, SpendingByCategoryDto } from '../core/services/report.service';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

interface SpendingRow extends SpendingByCategoryDto {
  progress: number;
}

interface DashboardData {
  dashboard: DashboardDto;
  spending: SpendingRow[];
}

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, MatCard, MatCardContent, MatProgressBar, AppLoadingSpinnerComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class DashboardComponent {
  private readonly reportService = inject(ReportService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly dashboardData = signal<DashboardData | null>(null);
  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  private readonly _monthEffect = effect(() => this.loadData(this.month()));

  private loadData(month: string): void {
    this.isLoading.set(true);

    forkJoin({
      dashboard: this.reportService.getDashboard(month),
      spending: this.reportService.getSpendingByCategory(month),
    }).subscribe({
      next: ({ dashboard, spending }) => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        const total = spending.reduce((sum, c) => sum + c.spent, 0);
        const rows: SpendingRow[] = spending.map(c => ({
          ...c,
          progress: total === 0 ? 0 : (c.spent / total) * 100,
        }));
        this.dashboardData.set({ dashboard, spending: rows });
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadData(month);
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);
          this.snackBar.open('Failed to load dashboard.', 'OK', { duration: 5000 });
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
