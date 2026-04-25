import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-header">
          <span class="brand-icon">💳</span>
          <h1>SmartExpense</h1>
          <p>Create your account</p>
        </div>

        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <div class="form-group">
            <label for="fullName">Full Name</label>
            <input id="fullName" type="text" formControlName="fullName"
                   placeholder="John Doe"
                   [class.error]="isFieldInvalid('fullName')">
            <span class="field-error" *ngIf="isFieldInvalid('fullName')">
              Name must be at least 2 characters
            </span>
          </div>

          <div class="form-group">
            <label for="email">Email</label>
            <input id="email" type="email" formControlName="email"
                   placeholder="you@example.com"
                   [class.error]="isFieldInvalid('email')">
            <span class="field-error" *ngIf="isFieldInvalid('email')">
              Valid email is required
            </span>
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <input id="password" type="password" formControlName="password"
                   placeholder="Min 8 characters"
                   [class.error]="isFieldInvalid('password')">
            <span class="field-error" *ngIf="isFieldInvalid('password')">
              Password must be at least 8 characters
            </span>
          </div>

          <div class="alert alert-error" *ngIf="errorMsg">{{ errorMsg }}</div>

          <button type="submit" class="btn btn-primary" [disabled]="loading">
            <span *ngIf="!loading">Create Account</span>
            <span *ngIf="loading">Creating...</span>
          </button>
        </form>

        <p class="auth-footer">
          Already have an account? <a routerLink="/login">Sign in</a>
        </p>
      </div>
    </div>
  `,
  styleUrls: ['../auth.styles.scss']
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  form: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  loading = false;
  errorMsg = '';

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMsg = '';

    const { fullName, email, password } = this.form.value;
    this.authService.register(fullName, email, password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err) => {
        this.loading = false;
        this.errorMsg = err.error?.detail ?? 'Registration failed. Please try again.';
      }
    });
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control?.invalid && control?.touched);
  }
}
