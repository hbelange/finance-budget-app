import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-transaction-ledger',
  template: `<p>Transactions — coming in FBA-9</p>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export default class TransactionLedgerComponent {}
