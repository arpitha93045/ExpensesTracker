import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProfileService } from '../../core/services/profile.service';
import { AuthService } from '../../core/services/auth.service';
import { TwoFactorService, TwoFactorStatus, TotpSetup } from '../../core/services/two-factor.service';
import { UserProfile } from '../../core/models/models';

const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'JPY'];

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="profile-page">
      <div class="page-header">
        <h1>Profile & Preferences</h1>
        <p class="subtitle">Manage your account details and notification settings</p>
      </div>

      <div *ngIf="loadError" class="alert alert-error">{{ loadError }}</div>
      <div *ngIf="loading" class="loading">Loading profile…</div>

      <ng-container *ngIf="profile && !loading">

        <!-- Personal Info -->
        <div class="card">
          <h2 class="card-title">Personal Information</h2>
          <div *ngIf="infoSuccess" class="alert alert-success">{{ infoSuccess }}</div>
          <div *ngIf="infoError" class="alert alert-error">{{ infoError }}</div>
          <div class="field-group">
            <label>Full Name</label>
            <input type="text" [(ngModel)]="infoForm.fullName" maxlength="100" />
          </div>
          <div class="field-group">
            <label>Email</label>
            <input type="email" [(ngModel)]="infoForm.email" />
            <p *ngIf="infoForm.email !== profile.email" class="field-hint">
              Changing your email will sign out all other active sessions.
            </p>
          </div>
          <div class="card-actions">
            <button class="btn btn-primary" (click)="saveInfo()" [disabled]="savingInfo">
              {{ savingInfo ? 'Saving…' : 'Save Changes' }}
            </button>
          </div>
        </div>

        <!-- Change Password -->
        <div class="card">
          <h2 class="card-title">Change Password</h2>
          <div *ngIf="pwSuccess" class="alert alert-success">{{ pwSuccess }}</div>
          <div *ngIf="pwError" class="alert alert-error">{{ pwError }}</div>
          <div class="field-group">
            <label>Current Password</label>
            <input type="password" [(ngModel)]="pwForm.current" autocomplete="current-password" />
          </div>
          <div class="field-group">
            <label>New Password</label>
            <input type="password" [(ngModel)]="pwForm.next" minlength="8" autocomplete="new-password" />
          </div>
          <div class="field-group">
            <label>Confirm New Password</label>
            <input type="password" [(ngModel)]="pwForm.confirm" autocomplete="new-password" />
            <p *ngIf="pwForm.confirm && pwForm.next !== pwForm.confirm" class="field-hint field-hint--error">
              Passwords do not match.
            </p>
          </div>
          <div class="card-actions">
            <button class="btn btn-primary" (click)="savePassword()" [disabled]="savingPw || !pwValid">
              {{ savingPw ? 'Updating…' : 'Update Password' }}
            </button>
          </div>
        </div>

        <!-- Preferences -->
        <div class="card">
          <h2 class="card-title">Preferences</h2>
          <div *ngIf="prefsSuccess" class="alert alert-success">{{ prefsSuccess }}</div>
          <div *ngIf="prefsError" class="alert alert-error">{{ prefsError }}</div>
          <div class="field-group">
            <label>Default Display Currency</label>
            <select [(ngModel)]="prefsForm.currency">
              <option *ngFor="let c of currencies" [value]="c">{{ c }}</option>
            </select>
            <p class="field-hint">Used for display formatting. Does not change how imported transactions are stored.</p>
          </div>
          <div class="field-group field-group--toggle">
            <div>
              <label>Email Notifications</label>
              <p class="field-hint">Budget alerts, upload confirmations, and monthly summaries.</p>
            </div>
            <label class="toggle">
              <input type="checkbox" [(ngModel)]="prefsForm.notifications" />
              <span class="toggle-track"></span>
            </label>
          </div>
          <div class="card-actions">
            <button class="btn btn-primary" (click)="savePrefs()" [disabled]="savingPrefs">
              {{ savingPrefs ? 'Saving…' : 'Save Preferences' }}
            </button>
          </div>
        </div>

        <!-- Two-Factor Authentication -->
        <div class="card">
          <h2 class="card-title">Two-Factor Authentication</h2>
          <p class="field-hint" style="margin-bottom:16px">Add an extra layer of security to your account.</p>

          <div *ngIf="mfaError" class="alert alert-error">{{ mfaError }}</div>
          <div *ngIf="mfaSuccess" class="alert alert-success">{{ mfaSuccess }}</div>

          <!-- TOTP section -->
          <div class="tfa-row">
            <div>
              <label>Authenticator App (TOTP)</label>
              <p class="field-hint">Google Authenticator, Authy, or any TOTP app.</p>
            </div>
            <div class="tfa-actions">
              <span *ngIf="mfaStatus?.totpEnabled" class="badge badge--success">Enabled</span>
              <button *ngIf="!mfaStatus?.totpEnabled && !totpSetup" class="btn btn-primary btn-sm" (click)="startTotpSetup()" [disabled]="savingMfa">
                Set up
              </button>
              <button *ngIf="mfaStatus?.totpEnabled" class="btn btn-danger btn-sm" (click)="disableTotp()" [disabled]="savingMfa">
                Disable
              </button>
            </div>
          </div>

          <!-- TOTP setup flow -->
          <div *ngIf="totpSetup && !mfaStatus?.totpEnabled" class="totp-setup">
            <p class="field-hint">Scan this QR code with your authenticator app, or enter the key manually.</p>
            <img [src]="totpSetup.qrDataUri" alt="TOTP QR code" class="qr-code" />
            <div class="secret-key">
              <span class="field-hint">Manual key:</span>
              <code>{{ totpSetup.secret }}</code>
            </div>
            <div class="field-group" style="margin-top:12px">
              <label>Confirm 6-digit code from app</label>
              <input type="text" inputmode="numeric" maxlength="6" [(ngModel)]="totpConfirmCode" placeholder="000000" />
            </div>
            <div class="card-actions">
              <button class="btn btn-ghost btn-sm" (click)="totpSetup = null; totpConfirmCode = ''">Cancel</button>
              <button class="btn btn-primary btn-sm" (click)="confirmTotp()" [disabled]="savingMfa || totpConfirmCode.length !== 6">
                {{ savingMfa ? 'Enabling…' : 'Enable TOTP' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Account Info (read-only) -->
        <div class="card card--muted">
          <h2 class="card-title">Account</h2>
          <div class="meta-row"><span>Member since</span><span>{{ profile.createdAt | date:'mediumDate' }}</span></div>
          <div class="meta-row"><span>Role</span><span class="badge">User</span></div>
        </div>
      </ng-container>
    </div>
  `,
  styles: [`
    .profile-page {
      max-width: 640px;
      margin: 0 auto;
      padding: 32px 24px;
    }
    .page-header {
      margin-bottom: 28px;
      h1 { font-size: 1.75rem; font-weight: 700; color: var(--text-primary); margin: 0 0 4px; }
      .subtitle { color: var(--text-secondary); margin: 0; font-size: 0.9rem; }
    }
    .loading { color: var(--text-secondary); padding: 24px 0; }
    .alert {
      padding: 10px 16px;
      border-radius: 8px;
      margin-bottom: 16px;
      font-size: 0.875rem;
    }
    .alert-success { background: var(--success-light, #dcfce7); color: var(--success, #16a34a); }
    .alert-error { background: var(--danger-light); color: var(--danger); }
    .card {
      background: var(--surface);
      border-radius: 12px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
      margin-bottom: 24px;
    }
    .card--muted { box-shadow: none; border: 1px solid var(--border); }
    .card-title {
      font-size: 1rem;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0 0 20px;
    }
    .field-group {
      margin-bottom: 16px;
      label { display: block; font-size: 0.85rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px; }
      input, select {
        width: 100%;
        padding: 9px 12px;
        border: 1.5px solid var(--border);
        border-radius: 8px;
        font-size: 0.9rem;
        background: var(--bg);
        color: var(--text-primary);
        box-sizing: border-box;
        transition: border-color 0.2s;
        &:focus { outline: none; border-color: var(--primary); }
      }
    }
    .field-group--toggle {
      display: flex;
      align-items: center;
      justify-content: space-between;
      label:first-child { display: block; }
    }
    .field-hint {
      margin: 4px 0 0;
      font-size: 0.78rem;
      color: var(--text-secondary);
    }
    .field-hint--error { color: var(--danger); }
    .card-actions {
      margin-top: 20px;
      display: flex;
      justify-content: flex-end;
    }
    .btn {
      padding: 9px 20px;
      border: none;
      border-radius: 8px;
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-primary {
      background: var(--primary);
      color: white;
      &:hover:not(:disabled) { background: var(--primary-dark); }
      &:disabled { opacity: 0.6; cursor: not-allowed; }
    }
    /* Toggle switch */
    .toggle {
      position: relative;
      display: inline-block;
      width: 44px;
      height: 24px;
      flex-shrink: 0;
      input { opacity: 0; width: 0; height: 0; }
    }
    .toggle-track {
      position: absolute;
      inset: 0;
      background: var(--border);
      border-radius: 24px;
      cursor: pointer;
      transition: background 0.2s;
      &::before {
        content: '';
        position: absolute;
        width: 18px;
        height: 18px;
        left: 3px;
        top: 3px;
        background: white;
        border-radius: 50%;
        transition: transform 0.2s;
      }
    }
    .toggle input:checked + .toggle-track { background: var(--primary); }
    .toggle input:checked + .toggle-track::before { transform: translateX(20px); }
    /* Meta rows */
    .meta-row {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      font-size: 0.875rem;
      color: var(--text-secondary);
      border-bottom: 1px solid var(--border);
      &:last-child { border-bottom: none; }
      span:last-child { color: var(--text-primary); font-weight: 500; }
    }
    .badge {
      background: var(--primary-light);
      color: var(--primary);
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 600;
    }
    .badge--success {
      background: var(--success-light, #dcfce7);
      color: var(--success, #16a34a);
    }
    .btn-sm { padding: 6px 14px; font-size: 0.82rem; }
    .btn-ghost {
      background: transparent;
      color: var(--primary);
      border: 1.5px solid var(--primary);
      &:hover:not(:disabled) { background: var(--primary-light); }
      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }
    .btn-danger {
      background: var(--danger);
      color: white;
      &:hover:not(:disabled) { filter: brightness(0.9); }
      &:disabled { opacity: 0.5; cursor: not-allowed; }
    }
    .tfa-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      label { display: block; font-size: 0.9rem; font-weight: 600; color: var(--text-primary); }
    }
    .tfa-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
    .totp-setup {
      margin-top: 16px;
      padding: 16px;
      background: var(--bg);
      border-radius: 8px;
      border: 1px solid var(--border);
    }
    .qr-code {
      display: block;
      width: 180px;
      height: 180px;
      margin: 12px auto;
      border-radius: 8px;
    }
    .secret-key {
      display: flex;
      align-items: center;
      gap: 8px;
      justify-content: center;
      code {
        font-size: 0.8rem;
        background: var(--surface);
        padding: 4px 8px;
        border-radius: 4px;
        border: 1px solid var(--border);
        letter-spacing: 0.05em;
      }
    }
  `]
})
export class ProfileComponent implements OnInit {
  private profileService = inject(ProfileService);
  private authService = inject(AuthService);
  private twoFactorService = inject(TwoFactorService);

  currencies = CURRENCIES;
  profile: UserProfile | null = null;
  loading = true;
  loadError = '';

  infoForm = { fullName: '', email: '' };
  savingInfo = false;
  infoSuccess = '';
  infoError = '';

  pwForm = { current: '', next: '', confirm: '' };
  savingPw = false;
  pwSuccess = '';
  pwError = '';

  prefsForm = { currency: 'INR', notifications: true };
  savingPrefs = false;
  prefsSuccess = '';
  prefsError = '';

  mfaStatus: TwoFactorStatus | null = null;
  totpSetup: TotpSetup | null = null;
  totpConfirmCode = '';
  savingMfa = false;
  mfaSuccess = '';
  mfaError = '';

  get pwValid(): boolean {
    return !!this.pwForm.current && this.pwForm.next.length >= 8 && this.pwForm.next === this.pwForm.confirm;
  }

  ngOnInit(): void {
    this.profileService.getProfile().subscribe({
      next: p => {
        this.profile = p;
        this.infoForm = { fullName: p.fullName, email: p.email };
        this.prefsForm = { currency: p.defaultCurrency, notifications: p.notificationsEnabled };
        this.loading = false;
      },
      error: () => { this.loadError = 'Failed to load profile.'; this.loading = false; }
    });
    this.twoFactorService.getStatus().subscribe({
      next: s => this.mfaStatus = s,
      error: () => {}
    });
  }

  saveInfo(): void {
    this.infoSuccess = '';
    this.infoError = '';
    this.savingInfo = true;
    this.profileService.updateProfile(this.infoForm.fullName, this.infoForm.email).subscribe({
      next: p => {
        this.profile = p;
        this.infoForm = { fullName: p.fullName, email: p.email };
        this.infoSuccess = 'Profile updated.';
        this.savingInfo = false;
        const current = this.authService['_currentUser'].value;
        if (current) {
          const updated = { ...current, fullName: p.fullName, email: p.email };
          this.authService['_currentUser'].next(updated);
          sessionStorage.setItem('user', JSON.stringify(updated));
        }
      },
      error: err => {
        this.infoError = err?.error?.message ?? 'Failed to update profile.';
        this.savingInfo = false;
      }
    });
  }

  savePassword(): void {
    this.pwSuccess = '';
    this.pwError = '';
    this.savingPw = true;
    this.profileService.updatePassword(this.pwForm.current, this.pwForm.next).subscribe({
      next: () => {
        this.pwSuccess = 'Password updated successfully.';
        this.pwForm = { current: '', next: '', confirm: '' };
        this.savingPw = false;
      },
      error: err => {
        this.pwError = err?.error?.message ?? 'Failed to update password.';
        this.savingPw = false;
      }
    });
  }

  savePrefs(): void {
    this.prefsSuccess = '';
    this.prefsError = '';
    this.savingPrefs = true;
    this.profileService.updatePreferences(this.prefsForm.notifications, this.prefsForm.currency).subscribe({
      next: p => {
        this.profile = p;
        this.prefsForm = { currency: p.defaultCurrency, notifications: p.notificationsEnabled };
        this.prefsSuccess = 'Preferences saved.';
        this.savingPrefs = false;
        const current = this.authService['_currentUser'].value;
        if (current) {
          const updated = { ...current, defaultCurrency: p.defaultCurrency, notificationsEnabled: p.notificationsEnabled };
          this.authService['_currentUser'].next(updated);
          sessionStorage.setItem('user', JSON.stringify(updated));
        }
      },
      error: err => {
        this.prefsError = err?.error?.message ?? 'Failed to save preferences.';
        this.savingPrefs = false;
      }
    });
  }

  startTotpSetup(): void {
    this.mfaError = '';
    this.savingMfa = true;
    this.twoFactorService.setupTotp().subscribe({
      next: s => { this.totpSetup = s; this.savingMfa = false; },
      error: () => { this.mfaError = 'Failed to start TOTP setup.'; this.savingMfa = false; }
    });
  }

  confirmTotp(): void {
    this.mfaError = '';
    this.savingMfa = true;
    this.twoFactorService.confirmTotp(this.totpConfirmCode).subscribe({
      next: () => {
        this.mfaSuccess = 'Authenticator app enabled successfully.';
        this.totpSetup = null;
        this.totpConfirmCode = '';
        this.savingMfa = false;
        if (this.mfaStatus) this.mfaStatus = { totpEnabled: true };
      },
      error: err => {
        this.mfaError = err?.error?.message ?? 'Invalid code. Please try again.';
        this.savingMfa = false;
        this.totpConfirmCode = '';
      }
    });
  }

  disableTotp(): void {
    this.mfaError = '';
    this.savingMfa = true;
    this.twoFactorService.disableTotp().subscribe({
      next: () => {
        this.mfaSuccess = 'Authenticator app disabled.';
        this.savingMfa = false;
        if (this.mfaStatus) this.mfaStatus = { totpEnabled: false };
      },
      error: () => { this.mfaError = 'Failed to disable TOTP.'; this.savingMfa = false; }
    });
  }
}
