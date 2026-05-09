import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BudgetCategory {
  id: string;
  name: string;
  assigned: number;
  spent: number;
  available: number;
}

export interface BudgetGroup {
  id: string;
  name: string;
  categories: BudgetCategory[];
}

export interface BudgetView {
  readyToAssign: number;
  groups: BudgetGroup[];
}

export interface AllocationRequest {
  categoryId: string;
  month: string;
  assigned: number;
}

@Injectable({ providedIn: 'root' })
export class BudgetService {
  private readonly http = inject(HttpClient);

  getBudget(month: string): Observable<BudgetView> {
    return this.http.get<BudgetView>('/api/budget', { params: { month } });
  }

  upsertAllocation(req: AllocationRequest): Observable<void> {
    return this.http.put<void>('/api/budget/allocations', req);
  }
}
