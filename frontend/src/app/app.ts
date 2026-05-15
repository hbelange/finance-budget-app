import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, NavigationEnd, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { filter, map, startWith, catchError, of, switchMap, take } from 'rxjs';
import { MatSidenavContainer, MatSidenav, MatSidenavContent } from '@angular/material/sidenav';
import { MatToolbar } from '@angular/material/toolbar';
import { MatNavList, MatListItem } from '@angular/material/list';
import { MatFormField } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatButton } from '@angular/material/button';
import { AuthService } from '@auth0/auth0-angular';
import { BudgetStateService } from './core/services/budget-state.service';

interface DateBounds { first: string | null; last: string | null; }

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function shiftMonth(ym: string, delta: number): string {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function buildMonthList(first: string | null, last: string | null): string[] {
  if (!first || !last) {
    const curr = currentYearMonth();
    return [shiftMonth(curr, -1), curr, shiftMonth(curr, 1)];
  }
  const months: string[] = [];
  let cursor = first;
  while (cursor <= last) {
    months.push(cursor);
    cursor = shiftMonth(cursor, 1);
  }
  return months;
}

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavContainer, MatSidenav, MatSidenavContent,
    MatToolbar,
    MatNavList, MatListItem,
    MatFormField, MatSelect, MatOption,
    MatButton,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly budgetState = inject(BudgetStateService);
  private readonly auth = inject(AuthService);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(e => (e as NavigationEnd).urlAfterRedirects),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  private readonly dateBounds = toSignal(
    this.auth.isAuthenticated$.pipe(
      filter(Boolean),
      take(1),
      switchMap(() =>
        this.http.get<DateBounds>('/api/transactions/date-bounds').pipe(
          catchError(() => of({ first: null, last: null }))
        )
      )
    ),
    { initialValue: null }
  );

  protected readonly isLoading = toSignal(this.auth.isLoading$, { initialValue: true });
  protected readonly isAuthenticated = toSignal(this.auth.isAuthenticated$, { initialValue: false });

  protected readonly selectedMonth = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly availableMonths = computed(() => {
    const bounds = this.dateBounds();
    return buildMonthList(bounds?.first ?? null, bounds?.last ?? null);
  });

  protected readonly showMonthSelector = computed(() => {
    const url = this.currentUrl() ?? '/';
    return (
      url.startsWith('/budget') ||
      url.startsWith('/dashboard') ||
      /^\/accounts\/[^/]+\/transactions/.test(url)
    );
  });

  protected readonly pageTitle = computed(() => {
    const url = this.currentUrl() ?? '/';
    if (url.startsWith('/dashboard')) return 'Dashboard';
    if (/^\/accounts\/[^/]+\/transactions/.test(url)) return 'Transactions';
    if (url.startsWith('/accounts')) return 'Accounts';
    return 'Budget';
  });

  protected onMonthChange(month: string): void {
    this.budgetState.setMonth(month);
  }

  protected formatMonth(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleString('default', { month: 'long', year: 'numeric' });
  }

  protected login(): void {
    this.auth.loginWithRedirect();
  }

  protected logout(): void {
    this.auth.logout({ logoutParams: { returnTo: window.location.origin } });
  }
}
