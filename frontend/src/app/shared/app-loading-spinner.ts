import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

@Component({
    selector: 'app-loading-spinner',
    imports: [MatProgressSpinner],
    template: `
        @if (isLoading() && isWakingUp()) {
            <div class="waking-up">
                <mat-spinner diameter="50"></mat-spinner>
                <p>Waking up the server...</p>
            </div>
            } @else if (isLoading()) {
            <div class="loading">
                <mat-spinner diameter="50"></mat-spinner>
                <p>Loading budget...</p>
            </div>
        }
    `,
    styles: [`
        .loading,
        .waking-up {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 16px;
            min-height: 240px;
            color: var(--mat-sys-on-surface-variant);
        }
    `],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppLoadingSpinnerComponent {
    protected isLoading = input.required<boolean>();
    protected isWakingUp = input.required<boolean>();

}