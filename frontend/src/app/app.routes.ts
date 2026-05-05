import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: 'accounts', loadComponent: () => import('./accounts/accounts-list.component') },
  { path: 'accounts/:id/transactions', loadComponent: () => import('./transactions/transaction-ledger.component') },
  { path: 'budget', loadComponent: () => import('./budget/budget.component') },
  { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.component') },
  { path: '', redirectTo: 'budget', pathMatch: 'full' }
];
