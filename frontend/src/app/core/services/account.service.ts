import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'INVESTMENT' | 'CASH';

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: 'Checking',
  SAVINGS: 'Savings',
  CREDIT_CARD: 'Credit Card',
  INVESTMENT: 'Investment',
  CASH: 'Cash',
};

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  balance: number;
}

export interface AccountRequest {
  name: string;
  type: AccountType;
}

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  getAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }

  createAccount(req: AccountRequest): Observable<Account> {
    return this.http.post<Account>('/api/accounts', req);
  }

  updateAccount(id: string, req: AccountRequest): Observable<Account> {
    return this.http.put<Account>(`/api/accounts/${id}`, req);
  }

  deleteAccount(id: string): Observable<void> {
    return this.http.delete<void>(`/api/accounts/${id}`);
  }
}
