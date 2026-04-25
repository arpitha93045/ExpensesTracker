import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TransactionService } from '../../../core/services/transaction.service';
import { CategoryService } from '../../../core/services/category.service';
import { Transaction, Category, PagedResponse } from '../../../core/models/models';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';

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

  ngOnInit(): void {
    this.loadCategories();
    this.loadTransactions();
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

  exportCsv(): void {
    const url = this.transactionService.exportCsvUrl(
      this.fromDate || undefined,
      this.toDate || undefined,
      this.searchText || undefined,
      this.selectedCategoryId ?? undefined,
      this.selectedType || undefined
    );
    window.open(url, '_blank');
  }

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
