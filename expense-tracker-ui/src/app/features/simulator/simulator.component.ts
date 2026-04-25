import { Component, inject } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalyticsService } from '../../core/services/analytics.service';
import { WhatIfResult } from '../../core/models/models';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';

@Component({
  selector: 'app-simulator',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, FormsModule, SpinnerComponent],
  template: `
    <div class="simulator-page">
      <div class="page-header">
        <h1>What-If Simulator</h1>
        <p class="subtitle">See how small changes compound into big savings</p>
      </div>

      <div class="sim-layout">
        <!-- Input Panel -->
        <div class="sim-inputs card">
          <h3>Configure your scenario</h3>

          <div class="form-group">
            <label>Merchant or Category</label>
            <input type="text" [(ngModel)]="term" placeholder="e.g. Swiggy, Food & Dining, Uber"
                   class="form-input" (keyup.enter)="calculate()">
            <span class="hint">Type a merchant name or spending category</span>
          </div>

          <div class="form-group">
            <label>Cut spending by <strong>{{ cutPercent }}%</strong></label>
            <input type="range" [(ngModel)]="cutPercent" min="10" max="100" step="5" class="range-input">
            <div class="range-labels"><span>10%</span><span>50%</span><span>100%</span></div>
          </div>

          <div class="form-group">
            <label>Calculate based on last</label>
            <div class="period-options">
              <button *ngFor="let m of [3, 6, 12]" class="period-btn"
                      [class.active]="lookbackMonths === m" (click)="lookbackMonths = m">
                {{ m }} months
              </button>
            </div>
          </div>

          <button class="btn btn-primary calc-btn" (click)="calculate()" [disabled]="!term.trim() || loading">
            {{ loading ? 'Calculating...' : 'Calculate Savings' }}
          </button>

          <div *ngIf="noData" class="no-data-msg">
            No spending found for "{{ term }}" in the last {{ lookbackMonths }} months.
          </div>
        </div>

        <!-- Results Panel -->
        <div class="sim-results" *ngIf="result && result.currentMonthlyAvg > 0">
          <div class="result-hero card">
            <div class="hero-label">You currently spend</div>
            <div class="hero-amount">{{ result.currentMonthlyAvg | currency:'INR':'symbol':'1.0-0' }}/month</div>
            <div class="hero-sub">on <strong>{{ term }}</strong></div>
            <div class="hero-saving">
              Cutting {{ cutPercent }}% saves
              <span class="saving-amount">{{ result.savedPerMonth | currency:'INR':'symbol':'1.0-0' }}/month</span>
            </div>
          </div>

          <div class="projection-grid">
            <div class="proj-card" *ngFor="let p of projections">
              <div class="proj-period">{{ p.label }}</div>
              <div class="proj-amount">{{ p.amount | currency:'INR':'symbol':'1.0-0' }}</div>
              <div class="proj-sub">saved</div>
            </div>
          </div>

          <div class="goals-section card" *ngIf="result.goalEquivalents.length > 0">
            <h3>What could you achieve?</h3>
            <div class="goals-grid">
              <div class="goal-card" *ngFor="let g of result.goalEquivalents">
                <div class="goal-name">{{ g.name }}</div>
                <div class="goal-cost">{{ g.cost | currency:'INR':'symbol':'1.0-0' }}</div>
                <div class="goal-time">in {{ g.achievableInMonths | number:'1.0-1' }} months</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state when no results yet -->
        <div class="sim-empty" *ngIf="!result && !loading">
          <div class="empty-icon">🎯</div>
          <p>Enter a merchant or category to simulate your savings potential</p>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./simulator.component.scss']
})
export class SimulatorComponent {
  private analyticsService = inject(AnalyticsService);

  term = '';
  cutPercent = 50;
  lookbackMonths = 6;
  result: WhatIfResult | null = null;
  loading = false;
  noData = false;

  get projections() {
    if (!this.result) return [];
    return [
      { label: '1 month',   amount: this.result.savedIn1Month  },
      { label: '3 months',  amount: this.result.savedIn3Months },
      { label: '6 months',  amount: this.result.savedIn6Months },
      { label: '12 months', amount: this.result.savedIn12Months },
    ];
  }

  calculate(): void {
    if (!this.term.trim()) return;
    this.loading = true;
    this.result = null;
    this.noData = false;
    this.analyticsService.getWhatIf(this.term.trim(), this.cutPercent, this.lookbackMonths).subscribe({
      next: (r) => {
        this.result = r;
        this.noData = r.currentMonthlyAvg === 0;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
