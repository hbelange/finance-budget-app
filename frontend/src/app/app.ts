import { Component, signal, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('frontend');
  protected readonly healthStatus = signal<string>('checking...');

  private readonly http = inject(HttpClient);

  ngOnInit(): void {
    this.http.get('/api/health', { responseType: 'text' }).subscribe({
      next: (res) => this.healthStatus.set(res),
      error: () => this.healthStatus.set('error')
    });
  }
}
