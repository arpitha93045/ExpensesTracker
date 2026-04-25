import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavComponent } from './shared/components/nav/nav.component';
import { AuthService } from './core/services/auth.service';
import { AsyncPipe, NgIf } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavComponent, AsyncPipe, NgIf],
  template: `
    <app-nav *ngIf="authService.isLoggedIn"></app-nav>
    <main class="main-content" [class.with-nav]="authService.isLoggedIn">
      <router-outlet></router-outlet>
    </main>
  `,
  styles: [`
    .main-content {
      min-height: 100vh;
      background: var(--bg-secondary);
    }
    .main-content.with-nav {
      padding-top: 64px;
    }
  `]
})
export class AppComponent {
  constructor(public authService: AuthService) {}
}
