import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BudgetCategory {
  id: string;
  name: string;
}

export interface CategoryGroup {
  id: string;
  name: string;
  categories: BudgetCategory[];
}

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);

  getCategories(): Observable<CategoryGroup[]> {
    return this.http.get<CategoryGroup[]>('/api/category-groups');
  }
}
