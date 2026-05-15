import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'accounts', loadComponent: () => import('./accounts/accounts-list.component'), canActivate: [authGuard] },
  { path: 'accounts/:id/transactions', loadComponent: () => import('./transactions/transaction-ledger.component'), canActivate: [authGuard] },
  { path: 'budget', loadComponent: () => import('./budget/budget.component'), canActivate: [authGuard] },
  { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.component'), canActivate: [authGuard] },
  { path: '', redirectTo: 'budget', pathMatch: 'full' }
];
