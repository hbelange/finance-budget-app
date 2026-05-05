import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  template: `<p>Dashboard — coming in FBA-11</p>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export default class DashboardComponent {}
