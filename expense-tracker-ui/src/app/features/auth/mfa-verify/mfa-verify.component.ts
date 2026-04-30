import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-mfa-verify',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-header">
          <span class="brand-icon">🔐</span>
          <h1>Two-Factor Authentication</h1>
          <p>Enter the 6-digit code from your authenticator app</p>
        </div>

        <div class="otp-form">
          <div class="form-group">
            <label for="code">Verification Code</label>
            <input
              id="code"
              type="text"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
              [(ngModel)]="code"
              placeholder="000000"
              class="otp-input"
              (keyup.enter)="verify()"
            />
          </div>

          <div class="alert alert-error" *ngIf="errorMsg">{{ errorMsg }}</div>

          <button class="btn btn-primary" (click)="verify()" [disabled]="loading || code.length !== 6">
            {{ loading ? 'Verifying…' : 'Verify' }}
          </button>

          <a class="back-link" (click)="goBack()">← Back to login</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .auth-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--bg);
      padding: 24px;
    }
    .auth-card {
      background: var(--surface);
      border-radius: 16px;
      padding: 40px 36px;
      width: 100%;
      max-width: 400px;
      box-shadow: 0 4px 24px rgba(0,0,0,0.10);
    }
    .auth-header {
      text-align: center;
      margin-bottom: 28px;
      .brand-icon { font-size: 2.5rem; }
      h1 { font-size: 1.5rem; font-weight: 700; color: var(--text-primary); margin: 8px 0 4px; }
      p { color: var(--text-secondary); font-size: 0.9rem; margin: 0; }
    }
    .otp-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .form-group {
      label { display: block; font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px; }
    }
    .otp-input {
      width: 100%;
      padding: 12px;
      font-size: 1.5rem;
      letter-spacing: 0.5em;
      text-align: center;
      border: 1.5px solid var(--border);
      border-radius: 8px;
      background: var(--bg);
      color: var(--text-primary);
      box-sizing: border-box;
      &:focus { outline: none; border-color: var(--primary); }
    }
    .alert-error {
      padding: 10px 14px;
      border-radius: 8px;
      font-size: 0.875rem;
      background: var(--danger-light);
      color: var(--danger);
    }
    .btn {
      padding: 11px;
      border: none;
      border-radius: 8px;
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
      width: 100%;
      transition: all 0.2s;
    }
    .btn-primary {
      background: var(--primary);
      color: white;
      &:hover:not(:disabled) { background: var(--primary-dark); }
      &:disabled { opacity: 0.6; cursor: not-allowed; }
    }
    .back-link {
      text-align: center;
      color: var(--text-secondary);
      font-size: 0.875rem;
      cursor: pointer;
      &:hover { color: var(--primary); }
    }
  `]
})
export class MfaVerifyComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  code = '';
  challengeId = '';
  loading = false;
  errorMsg = '';

  ngOnInit(): void {
    const challenge = this.authService.pendingChallenge;
    if (!challenge) {
      this.router.navigate(['/login']);
      return;
    }
    this.challengeId = challenge.challengeId;
  }

  verify(): void {
    if (this.code.length !== 6) return;
    this.loading = true;
    this.errorMsg = '';

    this.authService.verifyMfa(this.challengeId, this.code).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err) => {
        this.loading = false;
        this.errorMsg = err.error?.message ?? 'Invalid or expired code. Please try again.';
        this.code = '';
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/login']);
  }
}
