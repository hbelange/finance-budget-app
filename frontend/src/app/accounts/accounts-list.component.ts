import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { timer } from 'rxjs';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatCellDef, MatHeaderRowDef, MatRowDef, MatHeaderCell, MatCell, MatHeaderRow, MatRow } from '@angular/material/table';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Account, AccountService, ACCOUNT_TYPE_LABELS } from '../core/services/account.service';
import { LayoutService } from '../core/services/layout.service';
import { AccountDialogComponent } from './account-dialog.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

@Component({
  selector: 'app-accounts-list',
  imports: [RouterLink, CurrencyPipe, MatTable, MatColumnDef, MatHeaderCellDef, MatCellDef, MatHeaderRowDef, MatRowDef, MatHeaderCell, MatCell, MatHeaderRow, MatRow, MatButton, MatIconButton, MatIcon, AppLoadingSpinnerComponent],
  templateUrl: './accounts-list.component.html',
  styleUrl: './accounts-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class AccountsListComponent implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly layout = inject(LayoutService);
  protected readonly isMobile = this.layout.isMobile;

  protected readonly accounts = signal<Account[]>([]);
  protected readonly displayedColumns = computed(() =>
    this.isMobile() ? ['mobile'] : ['name', 'type', 'balance', 'actions']
  );
  protected readonly typeLabels: Record<string, string> = ACCOUNT_TYPE_LABELS;
  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  ngOnInit(): void {
    this.loadAccounts();
  }

  protected openCreateDialog(): void {
    this.dialog.open(AccountDialogComponent, { data: null })
      .afterClosed()
      .subscribe((account: Account | undefined) => {
        if (account) this.accounts.update(list => [...list, account]);
      });
  }

  protected openEditDialog(account: Account): void {
    this.dialog.open(AccountDialogComponent, { data: account })
      .afterClosed()
      .subscribe((updated: Account | undefined) => {
        if (updated) this.accounts.update(list => list.map(a => a.id === updated.id ? updated : a));
      });
  }

  protected confirmDelete(account: Account): void {
    this.dialog.open(ConfirmDialogComponent, { data: { message: `Delete "${account.name}"? This cannot be undone.` } })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (confirmed) this.deleteAccount(account);
      });
  }

  private deleteAccount(account: Account): void {
    this.accountService.deleteAccount(account.id).subscribe({
      next: () => this.accounts.update(list => list.filter(a => a.id !== account.id)),
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.snackBar.open('Remove all transactions from this account before deleting.', 'OK', { duration: 5000 });
        }
      },
    });
  }

  private loadAccounts(): void {
    this.isLoading.set(true);

    this.accountService.getAccounts().subscribe({
      next: accounts => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        this.accounts.set(accounts);
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadAccounts();
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);
          this.snackBar.open('Failed to load accounts.', 'OK', { duration: 5000 });
        }
      },
    });

    timer(5000).subscribe(() => {
      if (this.isLoading()) {
        this.isWakingUp.set(true);
      }
    });
  }
}
