import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

@Injectable({ providedIn: 'root' })
export class BudgetStateService {
  readonly month$ = new BehaviorSubject<string>(currentMonth());

  setMonth(month: string): void {
    this.month$.next(month);
  }
}
