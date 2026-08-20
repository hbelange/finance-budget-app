import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'accounts', title: 'Accounts - Finance Budget App', loadComponent: () => import('./accounts/accounts-list.component'), canActivate: [authGuard] },
  { path: 'accounts/:id/transactions', title: 'Transactions - Finance Budget App', loadComponent: () => import('./transactions/transaction-ledger.component'), canActivate: [authGuard] },
  { path: 'budget', title: 'Budget - Finance Budget App', loadComponent: () => import('./budget/budget.component'), canActivate: [authGuard] },
  { path: 'goals', title: 'Goals - Finance Budget App', loadComponent: () => import('./goals/goals.component'), canActivate: [authGuard] },
  { path: 'dashboard', title: 'Dashboard - Finance Budget App', loadComponent: () => import('./dashboard/dashboard.component'), canActivate: [authGuard] },
  { path: '', redirectTo: 'budget', pathMatch: 'full' }
];
