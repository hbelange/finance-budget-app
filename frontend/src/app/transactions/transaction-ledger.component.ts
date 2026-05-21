import { AfterViewInit, ChangeDetectionStrategy, Component, computed, effect, inject, OnInit, signal, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import {
  MatCell, MatCellDef, MatColumnDef, MatHeaderCell, MatHeaderCellDef,
  MatHeaderRow, MatHeaderRowDef, MatRow, MatRowDef, MatTable, MatTableDataSource,
} from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { Account, AccountService } from '../core/services/account.service';
import { CategoryGroup, CategoryService } from '../core/services/category.service';
import { Transaction, TransactionService } from '../core/services/transaction.service';
import { TransferService } from '../core/services/transfer.service';
import { BudgetStateService } from '../core/services/budget-state.service';
import { LayoutService } from '../core/services/layout.service';
import { TransactionDialogComponent, TransactionDialogData } from './transaction-dialog.component';
import { TransferDialogComponent, TransferDialogData } from './transfer-dialog.component';
import { ConfirmDialogComponent } from '../accounts/confirm-dialog.component';
import { AppLoadingSpinnerComponent } from '../shared/app-loading-spinner';

@Component({
  selector: 'app-transaction-ledger',
  imports: [
    DatePipe, CurrencyPipe,
    MatTable, MatColumnDef,
    MatHeaderCell, MatHeaderCellDef,
    MatCell, MatCellDef,
    MatHeaderRow, MatHeaderRowDef,
    MatRow, MatRowDef,
    MatSort, MatSortHeader,
    MatPaginator,
    MatButton, MatIconButton,
    MatIcon,
    AppLoadingSpinnerComponent,
  ],
  templateUrl: './transaction-ledger.component.html',
  styleUrl: './transaction-ledger.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export default class TransactionLedgerComponent implements OnInit, AfterViewInit {
  private readonly route = inject(ActivatedRoute);
  private readonly accountService = inject(AccountService);
  private readonly categoryService = inject(CategoryService);
  private readonly transactionService = inject(TransactionService);
  private readonly transferService = inject(TransferService);
  private readonly budgetState = inject(BudgetStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly accountId = this.route.snapshot.paramMap.get('id')!;
  private readonly month = toSignal(this.budgetState.month$, { requireSync: true });

  protected readonly account = signal<Account | null>(null);
  protected readonly allAccounts = signal<Account[]>([]);
  protected readonly categories = signal<CategoryGroup[]>([]);
  protected readonly categoryMap = computed(() => {
    const map = new Map<string, string>();
    this.categories().forEach(g => g.categories.forEach(c => map.set(c.id, c.name)));
    return map;
  });
  private readonly layout = inject(LayoutService);
  protected readonly isMobile = this.layout.isMobile;

  protected readonly dataSource = new MatTableDataSource<Transaction>();
  protected readonly displayedColumns = computed(() =>
    this.isMobile()
      ? ['mobile']
      : ['date', 'payee', 'category', 'memo', 'amount', 'cleared', 'actions']
  );
  protected isLoading = signal(false);
  protected isWakingUp = signal(false);

  @ViewChild(MatPaginator) private paginator!: MatPaginator;
  @ViewChild(MatSort) private sort!: MatSort;

  private readonly _monthEffect = effect(() => this.loadTransactions(this.month()));

  ngOnInit(): void {
    this.loadAccounts();
    this.loadCategories();
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  protected openCreateDialog(): void {
    const data: TransactionDialogData = { transaction: null, accountId: this.accountId, categories: this.categories() };
    this.dialog.open(TransactionDialogComponent, { data })
      .afterClosed()
      .subscribe((t: Transaction | undefined) => {
        if (t) this.dataSource.data = [...this.dataSource.data, t];
      });
  }

  protected openTransferDialog(): void {
    const data: TransferDialogData = { transfer: null, currentAccountId: this.accountId, accounts: this.allAccounts() };
    this.dialog.open(TransferDialogComponent, { data })
      .afterClosed()
      .subscribe((legs: Transaction[] | undefined) => {
        if (!legs) return;
        const myLeg = legs.find(l => l.accountId === this.accountId);
        if (myLeg) this.dataSource.data = [...this.dataSource.data, myLeg];
      });
  }

  protected openEditDialog(transaction: Transaction): void {
    if (transaction.transferId != null) {
      const data: TransferDialogData = { transfer: transaction, currentAccountId: this.accountId, accounts: this.allAccounts() };
      this.dialog.open(TransferDialogComponent, { data })
        .afterClosed()
        .subscribe((legs: Transaction[] | undefined) => {
          if (!legs) return;
          const myLeg = legs.find(l => l.accountId === this.accountId);
          if (myLeg) this.dataSource.data = this.dataSource.data.map(t => t.id === transaction.id ? myLeg : t);
        });
    } else {
      const data: TransactionDialogData = { transaction, accountId: this.accountId, categories: this.categories() };
      this.dialog.open(TransactionDialogComponent, { data })
        .afterClosed()
        .subscribe((updated: Transaction | undefined) => {
          if (updated) this.dataSource.data = this.dataSource.data.map(t => t.id === updated.id ? updated : t);
        });
    }
  }

  protected confirmDelete(transaction: Transaction): void {
    this.dialog.open(ConfirmDialogComponent, { data: { message: 'Delete this transaction? This cannot be undone.' } })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (confirmed) this.deleteTransaction(transaction);
      });
  }

  private loadAccounts(): void {
    this.accountService.getAccounts().subscribe({
      next: accounts => {
        this.allAccounts.set(accounts);
        this.account.set(accounts.find(a => a.id === this.accountId) ?? null);
      },
      error: () => this.snackBar.open('Failed to load account.', 'OK', { duration: 5000 }),
    });
  }

  private loadCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: groups => this.categories.set(groups),
      error: () => this.snackBar.open('Failed to load categories.', 'OK', { duration: 5000 }),
    });
  }

  private loadTransactions(month: string): void {
    this.isLoading.set(true);

    this.transactionService.getTransactions(this.accountId, month).subscribe({
      next: transactions => {
        this.isLoading.set(false);
        this.isWakingUp.set(false);
        this.dataSource.data = transactions;
      },
      error: () => {
        if (this.isWakingUp()) {
          this.loadTransactions(month);
        } else {
          this.isLoading.set(false);
          this.isWakingUp.set(false);
          this.snackBar.open('Failed to load transactions.', 'OK', { duration: 5000 });
        }
      },
    });

    timer(5000).subscribe(() => {
      if (this.isLoading()) {
        this.isWakingUp.set(true);
      }
    });
  }

  private deleteTransaction(transaction: Transaction): void {
    const delete$ = transaction.transferId != null
      ? this.transferService.deleteTransfer(transaction.id)
      : this.transactionService.deleteTransaction(transaction.id);

    delete$.subscribe({
      next: () => {
        this.dataSource.data = this.dataSource.data.filter(t =>
          t.id !== transaction.id && t.id !== transaction.transferId
        );
      },
      error: () => this.snackBar.open('Failed to delete transaction.', 'OK', { duration: 5000 }),
    });
  }
}
