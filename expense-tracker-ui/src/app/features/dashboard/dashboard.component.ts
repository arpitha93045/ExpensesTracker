import { Component, OnInit, inject, ElementRef, ViewChild } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import { AnalyticsService } from '../../core/services/analytics.service';
import { BudgetService } from '../../core/services/budget.service';
import { AnalyticsSummary, Budget, Category } from '../../core/models/models';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { RouterLink } from '@angular/router';
import { CategoryService } from '../../core/services/category.service';
import { BillsCardComponent } from './bills-card/bills-card.component';
import { InflationCardComponent } from './inflation-card/inflation-card.component';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, FormsModule, SpinnerComponent, RouterLink, BillsCardComponent, InflationCardComponent],
  template: `
    <div class="dashboard">
      <!-- Header -->
      <div class="page-header">
        <div>
          <h1>Dashboard</h1>
          <p class="subtitle">Your financial overview</p>
        </div>
        <div class="date-controls">
          <input type="month" [(ngModel)]="selectedMonth" (change)="loadData()" class="month-picker">
          <a routerLink="/upload" class="btn btn-primary">+ Upload Statement</a>
        </div>
      </div>

      <app-spinner *ngIf="loading"></app-spinner>

      <ng-container *ngIf="!loading && summary">
        <!-- KPI Cards -->
        <div class="kpi-grid">
          <div class="kpi-card kpi-expenses">
            <div class="kpi-icon">💸</div>
            <div class="kpi-label">Total Expenses</div>
            <div class="kpi-value">{{ summary.totalExpenses | currency:'INR':'symbol':'1.0-0' }}</div>
          </div>
          <div class="kpi-card kpi-income">
            <div class="kpi-icon">💰</div>
            <div class="kpi-label">Total Income</div>
            <div class="kpi-value">{{ summary.totalIncome | currency:'INR':'symbol':'1.0-0' }}</div>
          </div>
          <div class="kpi-card" [class.kpi-positive]="summary.netSavings >= 0" [class.kpi-negative]="summary.netSavings < 0">
            <div class="kpi-icon">{{ summary.netSavings >= 0 ? '📈' : '📉' }}</div>
            <div class="kpi-label">Net Savings</div>
            <div class="kpi-value">{{ summary.netSavings | currency:'INR':'symbol':'1.0-0' }}</div>
          </div>
          <div class="kpi-card kpi-neutral">
            <div class="kpi-icon">🔢</div>
            <div class="kpi-label">Transactions</div>
            <div class="kpi-value">{{ summary.transactionCount }}</div>
          </div>
          <div class="kpi-card kpi-savings-rate" *ngIf="summary.totalIncome > 0">
            <div class="kpi-icon">🎯</div>
            <div class="kpi-label">Savings Rate</div>
            <div class="kpi-value">{{ savingsRate }}%</div>
            <div class="kpi-sub" [class.good]="savingsRate >= 20" [class.warn]="savingsRate > 0 && savingsRate < 20" [class.bad]="savingsRate < 0">
              {{ savingsRate >= 30 ? 'Excellent' : savingsRate >= 20 ? 'Good' : savingsRate >= 10 ? 'Fair' : 'Low' }}
            </div>
          </div>
          <div class="kpi-card kpi-top-category" *ngIf="topCategory">
            <div class="kpi-icon">🏆</div>
            <div class="kpi-label">Top Spending</div>
            <div class="kpi-value kpi-value-sm">{{ topCategory.category }}</div>
            <div class="kpi-sub">{{ topCategory.total | currency:'INR':'symbol':'1.0-0' }} ({{ topCategory.percentage.toFixed(0) }}%)</div>
          </div>
        </div>

        <!-- Category Breakdown Bars -->
        <div class="table-card breakdown-card" *ngIf="summary.categoryBreakdown.length > 0">
          <h3>Spending Breakdown</h3>
          <div class="breakdown-list">
            <div class="breakdown-row" *ngFor="let b of summary.categoryBreakdown.slice(0, 8)">
              <div class="breakdown-label">{{ b.category }}</div>
              <div class="breakdown-bar-wrap">
                <div class="breakdown-bar" [style.width.%]="b.percentage" [style.background]="getCategoryColor(b.category)"></div>
              </div>
              <div class="breakdown-amount">{{ b.total | currency:'INR':'symbol':'1.0-0' }}</div>
              <div class="breakdown-pct">{{ b.percentage.toFixed(0) }}%</div>
            </div>
          </div>
        </div>

        <!-- Charts Row -->
        <div class="charts-grid">
          <div class="chart-card">
            <h3>Expenses by Category</h3>
            <div class="chart-wrapper">
              <canvas #donutChart></canvas>
            </div>
          </div>
          <div class="chart-card chart-wide">
            <h3>Monthly Trend</h3>
            <div class="chart-wrapper">
              <canvas #lineChart></canvas>
            </div>
          </div>
        </div>

        <!-- MoM Comparison Card -->
        <div class="mom-card" *ngIf="summary.momComparison">
          <h3>vs Last Month</h3>
          <div class="mom-grid">
            <div class="mom-item">
              <div class="mom-label">Expenses</div>
              <div class="mom-value">{{ summary.momComparison.currentExpenses | currency:'INR':'symbol':'1.0-0' }}</div>
              <div class="mom-change" [class.up]="summary.momComparison.expensesChangePercent > 0"
                                       [class.down]="summary.momComparison.expensesChangePercent < 0">
                {{ summary.momComparison.expensesChangePercent > 0 ? '▲' : '▼' }}
                {{ summary.momComparison.expensesChangePercent | number:'1.1-1' }}%
                vs {{ summary.momComparison.previousExpenses | currency:'INR':'symbol':'1.0-0' }}
              </div>
            </div>
            <div class="mom-item">
              <div class="mom-label">Income</div>
              <div class="mom-value">{{ summary.momComparison.currentIncome | currency:'INR':'symbol':'1.0-0' }}</div>
              <div class="mom-change" [class.up]="summary.momComparison.incomeChangePercent > 0"
                                       [class.down]="summary.momComparison.incomeChangePercent < 0">
                {{ summary.momComparison.incomeChangePercent > 0 ? '▲' : '▼' }}
                {{ summary.momComparison.incomeChangePercent | number:'1.1-1' }}%
                vs {{ summary.momComparison.previousIncome | currency:'INR':'symbol':'1.0-0' }}
              </div>
            </div>
          </div>
        </div>

        <!-- Insights -->
        <div class="insights-card" *ngIf="summary.insights.length > 0">
          <h3>AI Insights</h3>
          <ul class="insights-list">
            <li *ngFor="let insight of summary.insights" class="insight-item">
              <span class="insight-icon">💡</span>
              {{ insight }}
            </li>
          </ul>
        </div>

        <!-- Lifestyle Inflation -->
        <app-inflation-card></app-inflation-card>

        <!-- Upcoming Bills -->
        <app-bills-card></app-bills-card>

        <!-- Budget Tracker -->
        <div class="table-card budget-card" *ngIf="budgets.length > 0 || showBudgetForm">
          <div class="budget-header">
            <h3>Monthly Budgets</h3>
            <button class="btn-link" (click)="showBudgetForm = !showBudgetForm">
              {{ showBudgetForm ? '✕ Cancel' : '+ Add Budget' }}
            </button>
          </div>

          <!-- Add budget form -->
          <div class="budget-form" *ngIf="showBudgetForm">
            <select [(ngModel)]="newBudgetCategoryId" class="budget-select">
              <option [ngValue]="null">Select category...</option>
              <option *ngFor="let cat of categories" [ngValue]="cat.id">{{ cat.name }}</option>
            </select>
            <span>₹</span>
            <input type="number" [(ngModel)]="newBudgetAmount" placeholder="Amount" class="budget-amount-input" min="1">
            <button class="btn btn-primary btn-sm" (click)="saveBudget()">Save</button>
          </div>

          <!-- Budget progress rows -->
          <div class="budget-list">
            <div class="budget-row" *ngFor="let b of budgets">
              <div class="budget-cat">
                <span class="budget-dot" [style.background]="b.categoryColor"></span>
                {{ b.categoryName }}
              </div>
              <div class="budget-bar-wrap">
                <div class="budget-bar"
                     [style.width.%]="Math.min(b.percentUsed, 100)"
                     [class.warn]="b.percentUsed >= 80 && b.percentUsed < 100"
                     [class.over]="b.percentUsed >= 100"></div>
              </div>
              <div class="budget-amounts">
                <span [class.over-text]="b.percentUsed >= 100">
                  {{ b.spentAmount | currency:'INR':'symbol':'1.0-0' }}
                </span>
                <span class="budget-limit"> / {{ b.budgetAmount | currency:'INR':'symbol':'1.0-0' }}</span>
              </div>
              <div class="budget-pct" [class.over-text]="b.percentUsed >= 100">
                {{ b.percentUsed | number:'1.0-0' }}%
              </div>
              <button class="icon-btn-sm" (click)="deleteBudget(b)" title="Remove">✕</button>
            </div>
          </div>
        </div>

        <div class="add-budget-cta" *ngIf="budgets.length === 0 && !showBudgetForm">
          <button class="btn btn-outline-sm" (click)="showBudgetForm = true">+ Set Monthly Budgets</button>
        </div>

        <!-- Top Merchants -->
        <div class="table-card" *ngIf="summary.topMerchants.length > 0">
          <h3>Top Merchants</h3>
          <table class="data-table">
            <thead>
              <tr>
                <th>Merchant</th>
                <th>Transactions</th>
                <th>Total Spent</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let m of summary.topMerchants">
                <td>{{ m.merchant }}</td>
                <td>{{ m.count }}</td>
                <td>{{ m.total | currency:'INR':'symbol':'1.0-0' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </ng-container>

      <!-- Empty state -->
      <div class="empty-state" *ngIf="!loading && !summary">
        <span class="empty-icon">📊</span>
        <h3>No data yet</h3>
        <p>Upload your first bank statement to see your expense analytics</p>
        <a routerLink="/upload" class="btn btn-primary">Upload Statement</a>
      </div>
    </div>
  `,
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  @ViewChild('donutChart') donutChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('lineChart') lineChartRef!: ElementRef<HTMLCanvasElement>;

  private analyticsService = inject(AnalyticsService);
  private budgetService = inject(BudgetService);
  private categoryService = inject(CategoryService);

  summary: AnalyticsSummary | null = null;
  loading = false;
  selectedMonth: string = this.getCurrentMonth();

  budgets: Budget[] = [];
  categories: Category[] = [];
  showBudgetForm = false;
  newBudgetCategoryId: number | null = null;
  newBudgetAmount: number | null = null;

  readonly Math = Math;

  private donutChart: Chart | null = null;
  private lineChart: Chart | null = null;

  ngOnInit(): void {
    this.loadData();
    this.loadBudgets();
    this.categoryService.getCategories().subscribe(cats => this.categories = cats);
  }

  loadBudgets(): void {
    this.budgetService.getBudgets(this.selectedMonth).subscribe(b => this.budgets = b);
  }

  saveBudget(): void {
    if (!this.newBudgetCategoryId || !this.newBudgetAmount) return;
    this.budgetService.upsert(this.newBudgetCategoryId, this.newBudgetAmount, this.selectedMonth).subscribe(() => {
      this.showBudgetForm = false;
      this.newBudgetCategoryId = null;
      this.newBudgetAmount = null;
      this.loadBudgets();
    });
  }

  deleteBudget(b: Budget): void {
    if (!b.id) return;
    this.budgetService.delete(b.id).subscribe(() => this.loadBudgets());
  }

  loadData(): void {
    const [year, month] = this.selectedMonth.split('-');
    const from = `${year}-${month}-01`;
    const lastDay = new Date(+year, +month, 0).getDate();
    const to = `${year}-${month}-${lastDay}`;

    this.loading = true;
    this.analyticsService.getSummary(from, to).subscribe({
      next: (data) => {
        this.summary = data;
        this.loading = false;
        this.loadBudgets();
        setTimeout(() => this.renderCharts(), 50);
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  private renderCharts(): void {
    if (!this.summary) return;
    this.renderDonutChart();
    this.renderLineChart();
  }

  private renderDonutChart(): void {
    if (this.donutChart) this.donutChart.destroy();
    if (!this.donutChartRef?.nativeElement) return;

    const breakdown = this.summary!.categoryBreakdown.slice(0, 8);
    const colors = ['#FF6B6B','#4ECDC4','#45B7D1','#96CEB4','#FFEAA7','#DDA0DD','#98D8C8','#F7DC6F'];

    this.donutChart = new Chart(this.donutChartRef.nativeElement, {
      type: 'doughnut',
      data: {
        labels: breakdown.map(b => b.category),
        datasets: [{
          data: breakdown.map(b => b.total),
          backgroundColor: colors,
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        plugins: {
          legend: { position: 'bottom', labels: { padding: 16, usePointStyle: true } },
          tooltip: {
            callbacks: {
              label: (ctx) => ` ${ctx.label}: ₹${(ctx.raw as number).toLocaleString('en-IN', {maximumFractionDigits: 0})} (${breakdown[ctx.dataIndex].percentage.toFixed(1)}%)`
            }
          }
        }
      }
    });
  }

  private renderLineChart(): void {
    if (this.lineChart) this.lineChart.destroy();
    if (!this.lineChartRef?.nativeElement) return;

    const trends = this.summary!.monthlyTrend;

    this.lineChart = new Chart(this.lineChartRef.nativeElement, {
      type: 'line',
      data: {
        labels: trends.map(t => t.month),
        datasets: [
          {
            label: 'Expenses',
            data: trends.map(t => t.expenses),
            borderColor: '#FF6B6B',
            backgroundColor: 'rgba(255,107,107,0.1)',
            fill: true,
            tension: 0.4,
            pointRadius: 4
          },
          {
            label: 'Income',
            data: trends.map(t => t.income),
            borderColor: '#58D68D',
            backgroundColor: 'rgba(88,214,141,0.1)',
            fill: true,
            tension: 0.4,
            pointRadius: 4
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        plugins: { legend: { position: 'top' } },
        scales: {
          y: {
            beginAtZero: true,
            ticks: { callback: (v) => `₹${Number(v).toLocaleString('en-IN')}` }
          }
        }
      }
    });
  }

  get savingsRate(): number {
    if (!this.summary || this.summary.totalIncome <= 0) return 0;
    return Math.round((this.summary.netSavings / this.summary.totalIncome) * 100);
  }

  get topCategory() {
    return this.summary?.categoryBreakdown?.[0] ?? null;
  }

  private readonly CATEGORY_COLORS: Record<string, string> = {
    'Food & Dining': '#FF6B6B',
    'Transportation': '#4ECDC4',
    'Shopping': '#45B7D1',
    'Entertainment': '#96CEB4',
    'Healthcare': '#FFEAA7',
    'Utilities': '#DDA0DD',
    'Travel': '#98D8C8',
    'Education': '#F7DC6F',
    'Groceries': '#82E0AA',
    'Subscriptions': '#AEB6BF',
    'Rent & Housing': '#F8C471',
    'Insurance': '#A9CCE3',
    'Income': '#58D68D',
    'Other': '#BDC3C7',
  };

  getCategoryColor(name: string): string {
    return this.CATEGORY_COLORS[name] ?? '#BDC3C7';
  }

  private getCurrentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }
}
