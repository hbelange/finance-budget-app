import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DashboardDto {
  netWorth: number;
  incomeThisMonth: number;
  spentThisMonth: number;
}

export interface SpendingByCategoryDto {
  categoryId: string;
  categoryName: string;
  spent: number;
}

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  getDashboard(month: string): Observable<DashboardDto> {
    return this.http.get<DashboardDto>('/api/reports/dashboard', { params: { month } });
  }

  getSpendingByCategory(month: string): Observable<SpendingByCategoryDto[]> {
    return this.http.get<SpendingByCategoryDto[]>('/api/reports/spending-by-category', { params: { month } });
  }
}
