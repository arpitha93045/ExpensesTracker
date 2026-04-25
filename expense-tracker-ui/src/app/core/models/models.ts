// Core models
export interface User {
  userId: string;
  email: string;
  fullName: string;
  role: string;
}

export interface Transaction {
  id: string;
  description: string;
  amount: number;
  currency: string;
  transactionDate: string;
  transactionType: 'DEBIT' | 'CREDIT';
  merchant?: string;
  category?: Category;
  aiCategorized: boolean;
  aiConfidence?: number;
  categorizationNote?: string;
  createdAt: string;
}

export interface Category {
  id: number;
  name: string;
  icon: string;
  color: string;
}

export interface UploadJob {
  id: string;
  fileName: string;
  fileType: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  totalRows?: number;
  processedRows: number;
  errorMessage?: string;
  createdAt: string;
  completedAt?: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface CategoryBreakdown {
  category: string;
  icon?: string;
  color?: string;
  total: number;
  count: number;
  percentage: number;
  momChange?: number;
}

export interface MomComparison {
  currentExpenses: number;
  previousExpenses: number;
  expensesChangePercent: number;
  currentIncome: number;
  previousIncome: number;
  incomeChangePercent: number;
}

export interface AnalyticsSummary {
  totalExpenses: number;
  totalIncome: number;
  netSavings: number;
  transactionCount: number;
  categoryBreakdown: CategoryBreakdown[];
  monthlyTrend: MonthlyTrend[];
  topMerchants: TopMerchant[];
  insights: string[];
  momComparison?: MomComparison;
}

export interface MonthlyTrend {
  month: string;
  expenses: number;
  income: number;
}

export interface TopMerchant {
  merchant: string;
  count: number;
  total: number;
}

export interface Budget {
  id?: number;
  categoryId: number;
  categoryName: string;
  categoryColor: string;
  budgetAmount: number;
  spentAmount: number;
  percentUsed: number;
  yearMonth: string;
}

// ── Bill Due Predictor ───────────────────────────────────────
export interface Bill {
  merchant: string;
  amount: number;
  frequency: 'MONTHLY' | 'WEEKLY' | 'BIWEEKLY' | 'IRREGULAR';
  lastCharged: string;
  nextDueDate: string;
  confidence: number;
}

export interface BillPredictions {
  bills: Bill[];
}

// ── Lifestyle Inflation ──────────────────────────────────────
export interface CategoryInflation {
  name: string;
  then: number;
  now: number;
  change: number;
  changePercent: number;
}

export interface NewSubscription {
  merchant: string;
  since: string;
  monthlyAmount: number;
}

export interface InflationReport {
  overallInflationPercent: number;
  categories: CategoryInflation[];
  newSubscriptions: NewSubscription[];
  totalLifestyleCreep: number;
}

// ── What-If Simulator ────────────────────────────────────────
export interface GoalEquivalent {
  name: string;
  cost: number;
  achievableInMonths: number;
}

export interface WhatIfResult {
  currentMonthlyAvg: number;
  savedPerMonth: number;
  savedIn1Month: number;
  savedIn3Months: number;
  savedIn6Months: number;
  savedIn12Months: number;
  goalEquivalents: GoalEquivalent[];
}

// ── Cash Flow Calendar ───────────────────────────────────────
export interface CalendarDay {
  date: string;
  totalDebit: number;
  totalCredit: number;
  netFlow: number;
  transactionCount: number;
}

// ── Financial Autopsy ────────────────────────────────────────
export interface AutopsyHighlight {
  date: string;
  description: string;
  amount: number;
  insight: string;
}

export interface AutopsyWeeklyBreakdown {
  week: number;
  totalSpend: number;
  topCategory: string;
}

export interface AutopsyReport {
  narrative: string;
  highlights: AutopsyHighlight[];
  weeklyBreakdown: AutopsyWeeklyBreakdown[];
}


export interface Transaction {
  id: string;
  description: string;
  amount: number;
  currency: string;
  transactionDate: string;
  transactionType: 'DEBIT' | 'CREDIT';
  merchant?: string;
  category?: Category;
  aiCategorized: boolean;
  aiConfidence?: number;
  categorizationNote?: string;
  createdAt: string;
}

export interface Category {
  id: number;
  name: string;
  icon: string;
  color: string;
}

export interface UploadJob {
  id: string;
  fileName: string;
  fileType: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  totalRows?: number;
  processedRows: number;
  errorMessage?: string;
  createdAt: string;
  completedAt?: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface CategoryBreakdown {
  category: string;
  icon?: string;
  color?: string;
  total: number;
  count: number;
  percentage: number;
  momChange?: number;
}

export interface MomComparison {
  currentExpenses: number;
  previousExpenses: number;
  expensesChangePercent: number;
  currentIncome: number;
  previousIncome: number;
  incomeChangePercent: number;
}

export interface AnalyticsSummary {
  totalExpenses: number;
  totalIncome: number;
  netSavings: number;
  transactionCount: number;
  categoryBreakdown: CategoryBreakdown[];
  monthlyTrend: MonthlyTrend[];
  topMerchants: TopMerchant[];
  insights: string[];
  momComparison?: MomComparison;
}

export interface MonthlyTrend {
  month: string;
  expenses: number;
  income: number;
}

export interface TopMerchant {
  merchant: string;
  count: number;
  total: number;
}

export interface Budget {
  id?: number;
  categoryId: number;
  categoryName: string;
  categoryColor: string;
  budgetAmount: number;
  spentAmount: number;
  percentUsed: number;
  yearMonth: string;
}
