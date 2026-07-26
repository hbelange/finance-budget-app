import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type RolloverType = 'REFILL' | 'ACCUMULATE';

export const ROLLOVER_TYPE_LABELS: Record<RolloverType, string> = {
  REFILL: 'Refill each month',
  ACCUMULATE: 'Accumulate over time',
};

export interface Goal {
  id: string;
  categoryId: string;
  amount: number;
  dayOfMonth: number;
  rolloverType: RolloverType;
}

export interface GoalRequest {
  categoryId: string;
  amount: number;
  dayOfMonth: number;
  rolloverType: RolloverType;
}

@Injectable({ providedIn: 'root' })
export class GoalService {
  private readonly http = inject(HttpClient);

  getAllGoals(): Observable<Goal[]> {
    return this.http.get<Goal[]>('/api/goals');
  }

  createGoal(req: GoalRequest): Observable<Goal> {
    return this.http.post<Goal>('/api/goals', req);
  }

  updateGoal(categoryId: string, req: GoalRequest): Observable<Goal> {
    return this.http.put<Goal>(`/api/goals/${categoryId}`, req);
  }

  deleteGoal(categoryId: string): Observable<void> {
    return this.http.delete<void>(`/api/goals/${categoryId}`);
  }
}
