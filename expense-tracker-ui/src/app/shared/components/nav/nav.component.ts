import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ThemeService } from '../../../core/services/theme.service';
import { AsyncPipe, NgIf } from '@angular/common';

@Component({
  selector: 'app-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AsyncPipe, NgIf],
  template: `
    <nav class="navbar">
      <div class="navbar-brand">
        <span class="brand-icon">💳</span>
        <span class="brand-name">SmartExpense</span>
      </div>
      <ul class="navbar-links">
        <li><a routerLink="/dashboard" routerLinkActive="active">Dashboard</a></li>
        <li><a routerLink="/transactions" routerLinkActive="active">Transactions</a></li>
        <li><a routerLink="/calendar" routerLinkActive="active">Calendar</a></li>
        <li><a routerLink="/simulator" routerLinkActive="active">Simulator</a></li>
        <li><a routerLink="/autopsy" routerLinkActive="active">Autopsy</a></li>
        <li><a routerLink="/upload" routerLinkActive="active">Upload</a></li>
      </ul>
      <div class="navbar-user" *ngIf="(authService.currentUser$ | async) as user">
        <button class="btn-theme" (click)="themeService.toggle()" [title]="themeService.isDark() ? 'Switch to light mode' : 'Switch to dark mode'">
          {{ themeService.isDark() ? '☀️' : '🌙' }}
        </button>
        <span class="user-name">{{ $any(user).fullName }}</span>
        <button class="btn-logout" (click)="authService.logout()">Logout</button>
      </div>
    </nav>
  `,
  styles: [`
    .navbar {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      height: 64px;
      background: var(--surface);
      box-shadow: 0 2px 8px rgba(0,0,0,0.12);
      display: flex;
      align-items: center;
      padding: 0 24px;
      gap: 32px;
      z-index: 100;
    }
    .navbar-brand {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 1.2rem;
      font-weight: 700;
      color: var(--primary);
    }
    .navbar-links {
      list-style: none;
      display: flex;
      gap: 8px;
      margin: 0;
      padding: 0;
    }
    .navbar-links a {
      padding: 8px 16px;
      border-radius: 8px;
      text-decoration: none;
      color: var(--text-secondary);
      font-weight: 500;
      transition: all 0.2s;
    }
    .navbar-links a:hover, .navbar-links a.active {
      background: var(--primary-light);
      color: var(--primary);
    }
    .navbar-user {
      margin-left: auto;
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .user-name {
      color: var(--text-secondary);
      font-size: 0.9rem;
    }
    .btn-logout {
      padding: 6px 16px;
      border: 1px solid var(--border);
      background: transparent;
      border-radius: 8px;
      cursor: pointer;
      color: var(--text-secondary);
      font-size: 0.85rem;
      transition: all 0.2s;
    }
    .btn-logout:hover {
      background: var(--danger-light);
      color: var(--danger);
      border-color: var(--danger);
    }
    .btn-theme {
      padding: 6px 10px;
      border: 1px solid var(--border);
      background: transparent;
      border-radius: 8px;
      cursor: pointer;
      font-size: 1rem;
      line-height: 1;
      transition: background 0.2s;
    }
    .btn-theme:hover {
      background: var(--primary-light);
    }
  `]
})
export class NavComponent {
  authService = inject(AuthService);
  themeService = inject(ThemeService);
}
