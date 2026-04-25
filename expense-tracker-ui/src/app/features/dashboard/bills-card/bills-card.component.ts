import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { AnalyticsService } from '../../../core/services/analytics.service';
import { Bill } from '../../../core/models/models';

@Component({
  selector: 'app-bills-card',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe],
  template: `
    <div class="table-card bills-card">
      <div class="bills-header">
        <h3>Upcoming Bills</h3>
        <span class="bills-subtitle">Next 30 days</span>
      </div>

      <div *ngIf="loading" class="bills-loading">Detecting recurring bills...</div>

      <div *ngIf="!loading && bills.length === 0" class="bills-empty">
        No recurring bills detected in the next 30 days
      </div>

      <div class="bills-list" *ngIf="!loading && bills.length > 0">
        <div class="bill-row" *ngFor="let bill of bills">
          <div class="bill-merchant">
            <span class="bill-name">{{ bill.merchant }}</span>
            <span class="bill-freq" [class]="'freq-' + bill.frequency.toLowerCase()">{{ bill.frequency }}</span>
          </div>
          <div class="bill-meta">
            <span class="bill-date">{{ bill.nextDueDate | date:'MMM d' }}</span>
            <span class="bill-days" [class]="getUrgencyClass(bill.nextDueDate)">
              in {{ getDaysUntil(bill.nextDueDate) }}d
            </span>
          </div>
          <div class="bill-amount">{{ bill.amount | currency:'INR':'symbol':'1.0-0' }}</div>
          <div class="bill-confidence">
            <span class="conf-dot" [class.filled]="bill.confidence > 0.3"></span>
            <span class="conf-dot" [class.filled]="bill.confidence > 0.6"></span>
            <span class="conf-dot" [class.filled]="bill.confidence > 0.8"></span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .bills-card { margin-bottom: 24px; }
    .bills-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .bills-header h3 { margin: 0; font-size: 1rem; font-weight: 600; color: var(--text-primary); }
    .bills-subtitle { font-size: 0.75rem; color: var(--text-secondary); background: var(--bg-secondary); padding: 2px 8px; border-radius: 12px; }
    .bills-loading, .bills-empty { text-align: center; padding: 24px; color: var(--text-secondary); font-size: 0.875rem; }
    .bills-list { display: flex; flex-direction: column; gap: 10px; }
    .bill-row { display: grid; grid-template-columns: 1fr auto auto auto; align-items: center; gap: 16px; padding: 10px 0; border-bottom: 1px solid var(--border); }
    .bill-row:last-child { border-bottom: none; }
    .bill-merchant { display: flex; align-items: center; gap: 8px; }
    .bill-name { font-weight: 600; font-size: 0.875rem; color: var(--text-primary); }
    .bill-freq { font-size: 0.65rem; font-weight: 700; letter-spacing: 0.05em; padding: 2px 6px; border-radius: 4px; text-transform: uppercase; }
    .freq-monthly { background: #dbeafe; color: #1d4ed8; }
    .freq-weekly { background: #dcfce7; color: #15803d; }
    .freq-biweekly { background: #fef3c7; color: #92400e; }
    .bill-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
    .bill-date { font-size: 0.8rem; color: var(--text-secondary); }
    .bill-days { font-size: 0.75rem; font-weight: 700; padding: 2px 6px; border-radius: 10px; }
    .bill-days.urgent { background: #fee2e2; color: #dc2626; }
    .bill-days.soon { background: #fef3c7; color: #d97706; }
    .bill-days.normal { background: var(--bg-secondary); color: var(--text-secondary); }
    .bill-amount { font-weight: 700; font-size: 0.875rem; color: var(--text-primary); white-space: nowrap; }
    .bill-confidence { display: flex; gap: 3px; }
    .conf-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--border); }
    .conf-dot.filled { background: var(--primary); }
  `]
})
export class BillsCardComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  bills: Bill[] = [];
  loading = false;

  ngOnInit(): void {
    this.loading = true;
    this.analyticsService.getBillPredictions().subscribe({
      next: (r) => { this.bills = r.bills; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  getDaysUntil(dateStr: string): number {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const due = new Date(dateStr); due.setHours(0, 0, 0, 0);
    return Math.ceil((due.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
  }

  getUrgencyClass(dateStr: string): string {
    const days = this.getDaysUntil(dateStr);
    if (days <= 3) return 'urgent';
    if (days <= 7) return 'soon';
    return 'normal';
  }
}
