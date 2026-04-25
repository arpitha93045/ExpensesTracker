import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { AnalyticsService } from '../../../core/services/analytics.service';
import { InflationReport } from '../../../core/models/models';

@Component({
  selector: 'app-inflation-card',
  standalone: true,
  imports: [CommonModule, CurrencyPipe],
  template: `
    <div class="table-card inflation-card" *ngIf="report && (report.categories.length > 0 || report.newSubscriptions.length > 0)">
      <div class="inflation-header">
        <h3>Lifestyle Inflation</h3>
        <span class="inflation-badge" [class.bad]="report.overallInflationPercent > 0" [class.good]="report.overallInflationPercent < 0">
          {{ report.overallInflationPercent > 0 ? '+' : '' }}{{ report.overallInflationPercent | number:'1.1-1' }}%
        </span>
      </div>
      <p class="inflation-sub" *ngIf="report.totalLifestyleCreep > 0">
        Lifestyle creep is costing you <strong>{{ report.totalLifestyleCreep | currency:'INR':'symbol':'1.0-0' }}/month</strong> more vs 6 months ago
      </p>

      <div class="inflation-list" *ngIf="report.categories.length > 0">
        <div class="inflation-row" *ngFor="let c of report.categories.slice(0, 6)">
          <div class="inf-name">{{ c.name }}</div>
          <div class="inf-values">
            <span class="inf-then">{{ c.then | currency:'INR':'symbol':'1.0-0' }}</span>
            <span class="inf-arrow" [class.up]="c.changePercent > 0" [class.down]="c.changePercent < 0">
              {{ c.changePercent > 0 ? '▲' : '▼' }}
            </span>
            <span class="inf-now" [class.higher]="c.changePercent > 0">{{ c.now | currency:'INR':'symbol':'1.0-0' }}</span>
          </div>
          <div class="inf-pct" [class.bad]="c.changePercent > 15" [class.warn]="c.changePercent > 0 && c.changePercent <= 15" [class.good]="c.changePercent < 0">
            {{ c.changePercent > 0 ? '+' : '' }}{{ c.changePercent | number:'1.0-0' }}%
          </div>
        </div>
      </div>

      <div class="new-subs" *ngIf="report.newSubscriptions.length > 0">
        <div class="new-subs-title">New this period</div>
        <div class="new-sub-chip" *ngFor="let s of report.newSubscriptions.slice(0, 5)">
          {{ s.merchant }} <span>{{ s.monthlyAmount | currency:'INR':'symbol':'1.0-0' }}/mo</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .inflation-card { margin-bottom: 24px; }
    .inflation-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
    .inflation-header h3 { margin: 0; font-size: 1rem; font-weight: 600; color: var(--text-primary); }
    .inflation-badge { font-size: 0.8rem; font-weight: 700; padding: 3px 10px; border-radius: 12px; }
    .inflation-badge.bad { background: #fee2e2; color: #dc2626; }
    .inflation-badge.good { background: #dcfce7; color: #15803d; }
    .inflation-sub { font-size: 0.85rem; color: var(--text-secondary); margin: 0 0 16px; }
    .inflation-list { display: flex; flex-direction: column; gap: 8px; }
    .inflation-row { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 12px; padding: 6px 0; border-bottom: 1px solid var(--border); font-size: 0.875rem; }
    .inflation-row:last-child { border-bottom: none; }
    .inf-name { color: var(--text-primary); font-weight: 500; }
    .inf-values { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 0.8rem; }
    .inf-arrow { font-size: 0.7rem; }
    .inf-arrow.up { color: #dc2626; }
    .inf-arrow.down { color: #059669; }
    .inf-now.higher { color: #dc2626; font-weight: 600; }
    .inf-pct { font-size: 0.8rem; font-weight: 700; min-width: 48px; text-align: right; }
    .inf-pct.bad { color: #dc2626; }
    .inf-pct.warn { color: #d97706; }
    .inf-pct.good { color: #059669; }
    .new-subs { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border); }
    .new-subs-title { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-secondary); margin-bottom: 10px; }
    .new-sub-chip { display: inline-flex; align-items: center; gap: 6px; background: #fef3c7; color: #92400e; padding: 4px 10px; border-radius: 12px; font-size: 0.78rem; font-weight: 600; margin: 0 6px 6px 0; }
    .new-sub-chip span { font-weight: 400; }
  `]
})
export class InflationCardComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  report: InflationReport | null = null;

  ngOnInit(): void {
    this.analyticsService.getInflation().subscribe({
      next: (r) => this.report = r,
      error: () => {}
    });
  }
}
