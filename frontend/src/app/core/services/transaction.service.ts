import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface Transaction {
  id: string;
  accountId: string;
  date: string; // yyyy-MM-dd
  payee: string | null;
  categoryId: string | null;
  amount: number;
  memo: string | null;
  cleared: boolean;
}

export interface TransactionRequest {
  accountId: string;
  date: string; // yyyy-MM-dd
  payee: string | null;
  categoryId: string | null;
  amount: number;
  memo: string | null;
  cleared: boolean;
}

export interface Page<T> {
  content: T[];
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);

  getTransactions(accountId: string, month: string): Observable<Transaction[]> {
    return this.http.get<Page<Transaction>>('/api/transactions', {
      params: { accountId, month, size: '1000' },
    }).pipe(map(page => page.content));
  }

  createTransaction(req: TransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>('/api/transactions', req);
  }

  updateTransaction(id: string, req: TransactionRequest): Observable<Transaction> {
    return this.http.put<Transaction>(`/api/transactions/${id}`, req);
  }

  deleteTransaction(id: string): Observable<void> {
    return this.http.delete<void>(`/api/transactions/${id}`);
  }
}
