import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-accounts-list',
  template: `<p>Accounts — coming in FBA-8</p>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export default class AccountsListComponent {}
