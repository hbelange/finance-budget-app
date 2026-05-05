import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-budget',
  template: `<p>Budget — coming in FBA-10</p>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export default class BudgetComponent {}
