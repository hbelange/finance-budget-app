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

  createGroup(name: string): Observable<CategoryGroup> {
    return this.http.post<CategoryGroup>('/api/category-groups', { name });
  }

  renameGroup(id: string, name: string): Observable<CategoryGroup> {
    return this.http.put<CategoryGroup>(`/api/category-groups/${id}`, { name });
  }

  deleteGroup(id: string): Observable<void> {
    return this.http.delete<void>(`/api/category-groups/${id}`);
  }

  addCategory(groupId: string, name: string): Observable<CategoryGroup> {
    return this.http.post<CategoryGroup>(`/api/category-groups/${groupId}/categories`, { name });
  }

  renameCategory(id: string, name: string): Observable<void> {
    return this.http.put<void>(`/api/categories/${id}`, { name });
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`/api/categories/${id}`);
  }
}
