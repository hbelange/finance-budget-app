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
import { Transaction, TransactionRequest, TransactionService } from '../core/services/transaction.service';
import { TransferRequest, TransferService } from '../core/services/transfer.service';
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
      .subscribe((req: TransactionRequest | undefined) => {
        if (!req) return;
        const tempId = crypto.randomUUID();
        const optimistic: Transaction = { ...req, id: tempId, transferId: null };
        this.dataSource.data = this.sortedByDate([...this.dataSource.data, optimistic]);
        this.adjustBalance(req.amount);
        this.transactionService.createTransaction(req).subscribe({
          next: t => {
            this.dataSource.data = this.dataSource.data.map(x => x.id === tempId ? t : x);
            this.loadAccounts();
          },
          error: () => {
            this.dataSource.data = this.dataSource.data.filter(x => x.id !== tempId);
            this.adjustBalance(-req.amount);
            this.snackBar.open('Failed to save transaction.', 'OK', { duration: 5000 });
          },
        });
      });
  }

  protected openTransferDialog(): void {
    const data: TransferDialogData = { transfer: null, currentAccountId: this.accountId, pairedAccountId: null, accounts: this.allAccounts() };
    this.dialog.open(TransferDialogComponent, { data })
      .afterClosed()
      .subscribe((req: TransferRequest | undefined) => {
        if (!req) return;
        const isFrom = req.fromAccountId === this.accountId;
        const myAmount = isFrom ? -req.amount : req.amount;
        const otherAccountId = isFrom ? req.toAccountId : req.fromAccountId;
        const otherAccountName = this.allAccounts().find(a => a.id === otherAccountId)?.name ?? '';
        const tempId = crypto.randomUUID();
        const optimistic: Transaction = {
          id: tempId, accountId: this.accountId, date: req.date, payee: otherAccountName,
          categoryId: null, amount: myAmount, memo: req.memo ?? null, cleared: req.cleared, transferId: null,
        };
        this.dataSource.data = this.sortedByDate([...this.dataSource.data, optimistic]);
        this.adjustBalance(myAmount);
        this.transferService.createTransfer(req).subscribe({
          next: legs => {
            const myLeg = legs.find(l => l.accountId === this.accountId) ?? null;
            this.dataSource.data = myLeg
              ? this.dataSource.data.map(t => t.id === tempId ? myLeg : t)
              : this.dataSource.data.filter(t => t.id !== tempId);
            this.loadAccounts();
          },
          error: () => {
            this.dataSource.data = this.dataSource.data.filter(t => t.id !== tempId);
            this.adjustBalance(-myAmount);
            this.snackBar.open('Failed to save transfer.', 'OK', { duration: 5000 });
          },
        });
      });
  }

  protected openEditDialog(transaction: Transaction): void {
    if (transaction.transferId != null) {
      const pairedAccount = this.allAccounts().find(a => a.name === transaction.payee);
      const data: TransferDialogData = {
        transfer: transaction,
        currentAccountId: this.accountId,
        pairedAccountId: pairedAccount?.id ?? null,
        accounts: this.allAccounts(),
      };
      this.dialog.open(TransferDialogComponent, { data })
        .afterClosed()
        .subscribe((req: TransferRequest | undefined) => {
          if (!req) return;
          const isFrom = req.fromAccountId === this.accountId;
          const myAmount = isFrom ? -req.amount : req.amount;
          const otherAccountId = isFrom ? req.toAccountId : req.fromAccountId;
          const otherAccountName = this.allAccounts().find(a => a.id === otherAccountId)?.name ?? '';
          const optimistic: Transaction = {
            ...transaction, date: req.date, payee: otherAccountName,
            amount: myAmount, memo: req.memo ?? null, cleared: req.cleared,
          };
          const balanceDelta = myAmount - transaction.amount;
          const prevData = this.dataSource.data;
          this.dataSource.data = this.sortedByDate(prevData.map(t => t.id === transaction.id ? optimistic : t));
          this.adjustBalance(balanceDelta);
          this.transferService.updateTransfer(transaction.id, req).subscribe({
            next: legs => {
              const myLeg = legs.find(l => l.accountId === this.accountId);
              if (myLeg) {
                this.dataSource.data = this.dataSource.data.map(t => t.id === transaction.id ? myLeg : t);
              }
              this.loadAccounts();
            },
            error: () => {
              this.dataSource.data = prevData;
              this.adjustBalance(-balanceDelta);
              this.snackBar.open('Failed to save transfer.', 'OK', { duration: 5000 });
            },
          });
        });
    } else {
      const data: TransactionDialogData = { transaction, accountId: this.accountId, categories: this.categories() };
      this.dialog.open(TransactionDialogComponent, { data })
        .afterClosed()
        .subscribe((req: TransactionRequest | undefined) => {
          if (!req) return;
          const optimistic: Transaction = { ...transaction, ...req };
          const balanceDelta = req.amount - transaction.amount;
          const prevData = this.dataSource.data;
          this.dataSource.data = this.sortedByDate(prevData.map(t => t.id === transaction.id ? optimistic : t));
          this.adjustBalance(balanceDelta);
          this.transactionService.updateTransaction(transaction.id, req).subscribe({
            next: updated => {
              this.dataSource.data = this.dataSource.data.map(t => t.id === updated.id ? updated : t);
              this.loadAccounts();
            },
            error: () => {
              this.dataSource.data = prevData;
              this.adjustBalance(-balanceDelta);
              this.snackBar.open('Failed to save transaction.', 'OK', { duration: 5000 });
            },
          });
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
        this.dataSource.data = this.sortedByDate(transactions);
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
    const prevData = this.dataSource.data;
    this.dataSource.data = prevData.filter(t =>
      t.id !== transaction.id && t.id !== transaction.transferId
    );
    this.adjustBalance(-transaction.amount);

    const delete$ = transaction.transferId != null
      ? this.transferService.deleteTransfer(transaction.id)
      : this.transactionService.deleteTransaction(transaction.id);

    delete$.subscribe({
      next: () => this.loadAccounts(),
      error: () => {
        this.dataSource.data = prevData;
        this.adjustBalance(transaction.amount);
        this.snackBar.open('Failed to delete transaction.', 'OK', { duration: 5000 });
      },
    });
  }

  private adjustBalance(delta: number): void {
    this.account.update(a => a ? { ...a, balance: +(a.balance + delta).toFixed(2) } : null);
  }

  private sortedByDate(data: Transaction[]): Transaction[] {
    return [...data].sort((a, b) => (a.date > b.date ? -1 : a.date < b.date ? 1 : 0));
  }
}
