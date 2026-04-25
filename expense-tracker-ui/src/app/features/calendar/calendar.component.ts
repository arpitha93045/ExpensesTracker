import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalyticsService } from '../../core/services/analytics.service';
import { CalendarDay, Transaction } from '../../core/models/models';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, FormsModule, SpinnerComponent],
  template: `
    <div class="calendar-page">
      <div class="page-header">
        <div>
          <h1>Cash Flow Calendar</h1>
          <p class="subtitle">Daily spending patterns at a glance</p>
        </div>
        <input type="month" [(ngModel)]="selectedMonth" (change)="loadData()" class="month-picker">
      </div>

      <app-spinner *ngIf="loading"></app-spinner>

      <div class="calendar-wrap" *ngIf="!loading">
        <div class="legend">
          <span class="legend-item"><span class="legend-dot surplus"></span> Net positive</span>
          <span class="legend-item"><span class="legend-dot deficit"></span> Net negative</span>
          <span class="legend-item"><span class="legend-dot empty"></span> No transactions</span>
        </div>

        <div class="calendar-grid">
          <!-- Day headers -->
          <div class="day-header" *ngFor="let d of dayHeaders">{{ d }}</div>

          <!-- Calendar cells -->
          <ng-container *ngFor="let week of calendarGrid">
            <div *ngFor="let day of week"
                 class="calendar-cell"
                 [class.empty]="!day"
                 [class.no-txn]="day && day.transactionCount === 0"
                 [class.surplus]="day && day.netFlow > 0"
                 [class.deficit]="day && day.netFlow < 0"
                 [class.has-txn]="day && day.transactionCount > 0"
                 [style.--intensity]="day ? getIntensity(day) : 0"
                 (click)="day && day.transactionCount > 0 && openDay(day)">
              <span class="cell-date" *ngIf="day">{{ day.date | date:'d' }}</span>
              <span class="cell-amount" *ngIf="day && day.transactionCount > 0">
                {{ day.netFlow | currency:'INR':'symbol':'1.0-0' }}
              </span>
            </div>
          </ng-container>
        </div>

        <div class="calendar-summary" *ngIf="calendarDays.length > 0">
          <div class="summary-stat">
            <span class="stat-label">Total Spent</span>
            <span class="stat-value red">{{ totalDebit | currency:'INR':'symbol':'1.0-0' }}</span>
          </div>
          <div class="summary-stat">
            <span class="stat-label">Total Income</span>
            <span class="stat-value green">{{ totalCredit | currency:'INR':'symbol':'1.0-0' }}</span>
          </div>
          <div class="summary-stat">
            <span class="stat-label">Active Days</span>
            <span class="stat-value">{{ activeDays }}</span>
          </div>
          <div class="summary-stat">
            <span class="stat-label">Avg Daily Spend</span>
            <span class="stat-value">{{ avgDailySpend | currency:'INR':'symbol':'1.0-0' }}</span>
          </div>
        </div>
      </div>

      <!-- Day detail slide-in panel -->
      <div class="panel-overlay" [class.open]="panelOpen" (click)="closePanel()"></div>
      <div class="day-panel" [class.open]="panelOpen">
        <div class="panel-header">
          <h3>{{ selectedDay?.date | date:'MMMM d, yyyy' }}</h3>
          <button class="close-btn" (click)="closePanel()">✕</button>
        </div>
        <div class="panel-summary" *ngIf="selectedDay">
          <span class="ps-debit">Spent: {{ selectedDay.totalDebit | currency:'INR':'symbol':'1.0-0' }}</span>
          <span class="ps-credit">Income: {{ selectedDay.totalCredit | currency:'INR':'symbol':'1.0-0' }}</span>
        </div>
        <app-spinner *ngIf="panelLoading"></app-spinner>
        <div class="panel-txns" *ngIf="!panelLoading">
          <div class="panel-txn" *ngFor="let t of dayTransactions">
            <div class="ptxn-left">
              <div class="ptxn-desc">{{ t.description }}</div>
              <div class="ptxn-meta">{{ t.category?.name || 'Uncategorized' }}</div>
            </div>
            <div class="ptxn-amount" [class.debit]="t.transactionType === 'DEBIT'" [class.credit]="t.transactionType === 'CREDIT'">
              {{ t.transactionType === 'DEBIT' ? '-' : '+' }}{{ t.amount | currency:'INR':'symbol':'1.0-0' }}
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./calendar.component.scss']
})
export class CalendarComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  selectedMonth: string = this.getCurrentMonth();
  calendarDays: CalendarDay[] = [];
  calendarGrid: (CalendarDay | null)[][] = [];
  dayMap = new Map<string, CalendarDay>();
  loading = false;
  maxAbsFlow = 1;

  selectedDay: CalendarDay | null = null;
  dayTransactions: Transaction[] = [];
  panelOpen = false;
  panelLoading = false;

  readonly dayHeaders = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  ngOnInit(): void { this.loadData(); }

  loadData(): void {
    const [year, month] = this.selectedMonth.split('-').map(Number);
    const from = `${year}-${String(month).padStart(2, '0')}-01`;
    const lastDay = new Date(year, month, 0).getDate();
    const to = `${year}-${String(month).padStart(2, '0')}-${lastDay}`;
    this.loading = true;
    this.analyticsService.getCalendarFlow(from, to).subscribe({
      next: (days) => {
        this.calendarDays = days;
        this.dayMap = new Map(days.map(d => [d.date, d]));
        this.maxAbsFlow = Math.max(1, ...days.map(d => Math.abs(d.netFlow)));
        this.buildCalendarGrid(year, month);
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  buildCalendarGrid(year: number, month: number): void {
    const firstDay = new Date(year, month - 1, 1).getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const slots: (CalendarDay | null)[] = [];

    for (let i = 0; i < firstDay; i++) slots.push(null);
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      slots.push(this.dayMap.get(dateStr) ?? { date: dateStr, totalDebit: 0, totalCredit: 0, netFlow: 0, transactionCount: 0 });
    }
    while (slots.length % 7 !== 0) slots.push(null);

    this.calendarGrid = [];
    for (let i = 0; i < slots.length; i += 7) {
      this.calendarGrid.push(slots.slice(i, i + 7));
    }
  }

  getIntensity(day: CalendarDay): number {
    return Math.min(1, Math.abs(day.netFlow) / this.maxAbsFlow);
  }

  openDay(day: CalendarDay): void {
    this.selectedDay = day;
    this.panelOpen = true;
    this.panelLoading = true;
    this.analyticsService.getDayTransactions(day.date).subscribe({
      next: (txns) => { this.dayTransactions = txns; this.panelLoading = false; },
      error: () => { this.panelLoading = false; }
    });
  }

  closePanel(): void { this.panelOpen = false; this.selectedDay = null; this.dayTransactions = []; }

  get totalDebit(): number { return this.calendarDays.reduce((s, d) => s + d.totalDebit, 0); }
  get totalCredit(): number { return this.calendarDays.reduce((s, d) => s + d.totalCredit, 0); }
  get activeDays(): number { return this.calendarDays.filter(d => d.transactionCount > 0).length; }
  get avgDailySpend(): number {
    const active = this.calendarDays.filter(d => d.totalDebit > 0);
    return active.length > 0 ? active.reduce((s, d) => s + d.totalDebit, 0) / active.length : 0;
  }

  private getCurrentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }
}
