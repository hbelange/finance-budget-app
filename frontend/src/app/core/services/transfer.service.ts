import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Transaction } from './transaction.service';

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  date: string; // yyyy-MM-dd
  amount: number;
  memo: string | null;
  cleared: boolean;
}

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);

  createTransfer(req: TransferRequest): Observable<Transaction[]> {
    return this.http.post<Transaction[]>('/api/transfers', req);
  }

  updateTransfer(id: string, req: TransferRequest): Observable<Transaction[]> {
    return this.http.put<Transaction[]>(`/api/transfers/${id}`, req);
  }

  deleteTransfer(id: string): Observable<void> {
    return this.http.delete<void>(`/api/transfers/${id}`);
  }
}
