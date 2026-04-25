import { Component, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { AnalyticsService } from '../../core/services/analytics.service';
import { AutopsyReport } from '../../core/models/models';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';

Chart.register(...registerables);

@Component({
  selector: 'app-autopsy',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, FormsModule, SpinnerComponent],
  template: `
    <div class="autopsy-page">
      <div class="page-header">
        <div>
          <h1>Financial Autopsy</h1>
          <p class="subtitle">An honest look at your spending story</p>
        </div>
        <div class="controls">
          <input type="month" [(ngModel)]="selectedMonth" class="month-picker">
          <button class="btn btn-primary" (click)="generate()" [disabled]="loading">
            {{ loading ? 'Analyzing...' : 'Generate Report' }}
          </button>
        </div>
      </div>

      <div class="loading-state" *ngIf="loading">
        <app-spinner></app-spinner>
        <p class="loading-msg">Analyzing your spending patterns...</p>
      </div>

      <div class="autopsy-content" *ngIf="!loading && report">

        <!-- Narrative -->
        <div class="narrative-card">
          <div class="narrative-icon">🔍</div>
          <h3>Your Month in Review</h3>
          <p *ngFor="let para of paragraphs" class="narrative-para">{{ para }}</p>
        </div>

        <!-- Weekly Chart + Breakdown -->
        <div class="weekly-section">
          <div class="weekly-chart card">
            <h3>Week-by-Week Spending</h3>
            <div class="chart-wrap">
              <canvas #weeklyChart></canvas>
            </div>
          </div>

          <div class="weekly-breakdown card">
            <h3>Weekly Summary</h3>
            <div class="week-row" *ngFor="let w of report.weeklyBreakdown">
              <div class="week-label">Week {{ w.week }}</div>
              <div class="week-bar-wrap">
                <div class="week-bar" [style.width.%]="getWeekPct(w.totalSpend)"></div>
              </div>
              <div class="week-amount">{{ w.totalSpend | currency:'INR':'symbol':'1.0-0' }}</div>
              <div class="week-cat">{{ w.topCategory }}</div>
            </div>
          </div>
        </div>

        <!-- Highlights -->
        <div class="highlights-section" *ngIf="report.highlights.length > 0">
          <h3>Notable Moments</h3>
          <div class="highlights-grid">
            <div class="highlight-card" *ngFor="let h of report.highlights">
              <div class="hl-header">
                <span class="hl-date">{{ h.date | date:'MMM d' }}</span>
                <span class="hl-amount">{{ h.amount | currency:'INR':'symbol':'1.0-0' }}</span>
              </div>
              <div class="hl-desc">{{ h.description }}</div>
              <div class="hl-insight">💡 {{ h.insight }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div class="empty-state" *ngIf="!loading && !report">
        <span class="empty-icon">🔍</span>
        <h3>Ready to analyze</h3>
        <p>Select a month and click Generate Report to get your financial autopsy</p>
      </div>
    </div>
  `,
  styleUrls: ['./autopsy.component.scss']
})
export class AutopsyComponent {
  @ViewChild('weeklyChart') weeklyChartRef!: ElementRef<HTMLCanvasElement>;

  private analyticsService = inject(AnalyticsService);

  selectedMonth: string = this.getPreviousMonth();
  report: AutopsyReport | null = null;
  paragraphs: string[] = [];
  loading = false;
  private chart: Chart | null = null;
  maxWeekSpend = 1;

  generate(): void {
    this.loading = true;
    this.report = null;
    this.analyticsService.getAutopsy(this.selectedMonth).subscribe({
      next: (r) => {
        this.report = r;
        this.paragraphs = r.narrative.split('\n\n').filter(p => p.trim().length > 0);
        this.maxWeekSpend = Math.max(1, ...r.weeklyBreakdown.map(w => w.totalSpend));
        this.loading = false;
        setTimeout(() => this.renderChart(), 50);
      },
      error: () => { this.loading = false; }
    });
  }

  getWeekPct(amount: number): number {
    return (amount / this.maxWeekSpend) * 100;
  }

  private renderChart(): void {
    if (!this.report || !this.weeklyChartRef?.nativeElement) return;
    if (this.chart) this.chart.destroy();

    const weeks = this.report.weeklyBreakdown;
    this.chart = new Chart(this.weeklyChartRef.nativeElement, {
      type: 'bar',
      data: {
        labels: weeks.map(w => `Week ${w.week}`),
        datasets: [{
          label: 'Spending',
          data: weeks.map(w => w.totalSpend),
          backgroundColor: ['#667eea', '#764ba2', '#f093fb', '#f5576c'],
          borderRadius: 6,
          borderSkipped: false,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, ticks: { callback: (v) => `₹${Number(v).toLocaleString('en-IN')}` } }
        }
      }
    });
  }

  private getPreviousMonth(): string {
    const now = new Date();
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}`;
  }
}
