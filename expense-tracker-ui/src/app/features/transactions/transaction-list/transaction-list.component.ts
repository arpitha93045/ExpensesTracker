import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TransactionService } from '../../../core/services/transaction.service';
import { CategoryService } from '../../../core/services/category.service';
import { Transaction, Category, PagedResponse } from '../../../core/models/models';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';

interface FilterPreset {
  name: string;
  fromDate: string;
  toDate: string;
  searchText: string;
  categoryId: number | null;
  type: 'DEBIT' | 'CREDIT' | '';
}

const PRESETS_KEY = 'tx_filter_presets';
const HISTORY_KEY = 'tx_search_history';
const HISTORY_MAX = 8;

@Component({
  selector: 'app-transaction-list',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe, FormsModule, SpinnerComponent],
  templateUrl: './transaction-list.component.html',
  styleUrls: ['./transaction-list.component.scss']
})
export class TransactionListComponent implements OnInit {
  private transactionService = inject(TransactionService);
  private categoryService = inject(CategoryService);

  transactions: Transaction[] = [];
  categories: Category[] = [];
  loading = false;
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 20;

  // Filters
  fromDate = '';
  toDate = '';
  searchText = '';
  selectedCategoryId: number | null = null;
  selectedType: 'DEBIT' | 'CREDIT' | '' = '';

  // Inline edit
  editingId: string | null = null;
  editCategoryId: number | null = null;

  // Presets
  presets: FilterPreset[] = [];

  // Search history
  searchHistory: string[] = [];
  showHistory = false;

  ngOnInit(): void {
    this.loadCategories();
    this.loadTransactions();
    this.presets = this.readPresets();
    this.searchHistory = this.readHistory();
  }

  loadTransactions(): void {
    this.loading = true;
    this.transactionService.getTransactions(
      this.currentPage, this.pageSize,
      this.fromDate || undefined,
      this.toDate || undefined,
      this.searchText || undefined,
      this.selectedCategoryId ?? undefined,
      this.selectedType || undefined
    ).subscribe({
      next: (res: PagedResponse<Transaction>) => {
        this.transactions = res.content;
        this.totalPages = res.totalPages;
        this.totalElements = res.totalElements;
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }

  loadCategories(): void {
    this.categoryService.getCategories().subscribe(cats => (this.categories = cats));
  }

  applyFilter(): void {
    this.currentPage = 0;
    if (this.searchText.trim()) this.pushHistory(this.searchText.trim());
    this.showHistory = false;
    this.loadTransactions();
  }

  clearFilter(): void {
    this.fromDate = '';
    this.toDate = '';
    this.searchText = '';
    this.selectedCategoryId = null;
    this.selectedType = '';
    this.applyFilter();
  }

  // ── Presets ──────────────────────────────────────────────────

  get hasActiveFilter(): boolean {
    return !!(this.fromDate || this.toDate || this.searchText || this.selectedCategoryId || this.selectedType);
  }

  savePreset(): void {
    const name = prompt('Name this filter preset:')?.trim();
    if (!name) return;
    const preset: FilterPreset = {
      name,
      fromDate: this.fromDate,
      toDate: this.toDate,
      searchText: this.searchText,
      categoryId: this.selectedCategoryId,
      type: this.selectedType
    };
    this.presets = [...this.presets.filter(p => p.name !== name), preset];
    this.writePresets();
  }

  applyPreset(preset: FilterPreset): void {
    this.fromDate = preset.fromDate;
    this.toDate = preset.toDate;
    this.searchText = preset.searchText;
    this.selectedCategoryId = preset.categoryId;
    this.selectedType = preset.type;
    this.applyFilter();
  }

  deletePreset(preset: FilterPreset, event: Event): void {
    event.stopPropagation();
    this.presets = this.presets.filter(p => p.name !== preset.name);
    this.writePresets();
  }

  private readPresets(): FilterPreset[] {
    try {
      return JSON.parse(localStorage.getItem(PRESETS_KEY) ?? '[]');
    } catch { return []; }
  }

  private writePresets(): void {
    localStorage.setItem(PRESETS_KEY, JSON.stringify(this.presets));
  }

  // ── Search History ────────────────────────────────────────────

  get filteredHistory(): string[] {
    const q = this.searchText.trim().toLowerCase();
    return q
      ? this.searchHistory.filter(h => h.toLowerCase().includes(q) && h !== this.searchText.trim())
      : this.searchHistory;
  }

  pickHistory(term: string): void {
    this.searchText = term;
    this.showHistory = false;
    this.applyFilter();
  }

  hideHistoryDelayed(): void {
    setTimeout(() => (this.showHistory = false), 150);
  }

  removeHistory(term: string, event: Event): void {
    event.stopPropagation();
    this.searchHistory = this.searchHistory.filter(h => h !== term);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(this.searchHistory));
  }

  clearHistory(): void {
    this.searchHistory = [];
    localStorage.removeItem(HISTORY_KEY);
  }

  private pushHistory(term: string): void {
    this.searchHistory = [term, ...this.searchHistory.filter(h => h !== term)].slice(0, HISTORY_MAX);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(this.searchHistory));
  }

  private readHistory(): string[] {
    try {
      return JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]');
    } catch { return []; }
  }

  // ── Export ───────────────────────────────────────────────────

  exportCsv(): void {
    window.open(this.transactionService.exportCsvUrl(
      this.fromDate || undefined, this.toDate || undefined,
      this.searchText || undefined, this.selectedCategoryId ?? undefined,
      this.selectedType || undefined
    ), '_blank');
  }

  exportExcel(): void {
    window.open(this.transactionService.exportExcelUrl(
      this.fromDate || undefined, this.toDate || undefined,
      this.searchText || undefined, this.selectedCategoryId ?? undefined,
      this.selectedType || undefined
    ), '_blank');
  }

  exportPdf(): void {
    window.open(this.transactionService.exportPdfUrl(
      this.fromDate || undefined, this.toDate || undefined,
      this.searchText || undefined, this.selectedCategoryId ?? undefined,
      this.selectedType || undefined
    ), '_blank');
  }

  // ── Inline edit ──────────────────────────────────────────────

  startEdit(tx: Transaction): void {
    this.editingId = tx.id;
    this.editCategoryId = tx.category?.id ?? null;
  }

  saveCategory(tx: Transaction): void {
    if (!this.editCategoryId) return;
    this.transactionService.updateCategory(tx.id, this.editCategoryId).subscribe({
      next: (updated) => {
        const idx = this.transactions.findIndex(t => t.id === tx.id);
        if (idx >= 0) this.transactions[idx] = updated;
        this.editingId = null;
      }
    });
  }

  cancelEdit(): void {
    this.editingId = null;
  }

  deleteTransaction(tx: Transaction): void {
    if (!confirm(`Delete transaction: ${tx.description}?`)) return;
    this.transactionService.deleteTransaction(tx.id).subscribe({
      next: () => {
        this.transactions = this.transactions.filter(t => t.id !== tx.id);
        this.totalElements--;
      }
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadTransactions();
  }

  get pages(): number[] {
    const start = Math.max(0, this.currentPage - 4);
    const end = Math.min(this.totalPages, start + 10);
    return Array.from({ length: end - start }, (_, i) => start + i);
  }
}
