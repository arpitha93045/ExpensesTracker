import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PagedResponse, Transaction } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/transactions`;

  getTransactions(
    page = 0,
    size = 20,
    from?: string,
    to?: string,
    search?: string,
    categoryId?: number,
    type?: 'DEBIT' | 'CREDIT'
  ): Observable<PagedResponse<Transaction>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (from)       params = params.set('from', from);
    if (to)         params = params.set('to', to);
    if (search)     params = params.set('search', search);
    if (categoryId) params = params.set('categoryId', categoryId.toString());
    if (type)       params = params.set('type', type);
    return this.http.get<PagedResponse<Transaction>>(this.API, { params, withCredentials: true });
  }

  exportCsvUrl(from?: string, to?: string, search?: string, categoryId?: number, type?: string): string {
    let params = new HttpParams();
    if (from)       params = params.set('from', from);
    if (to)         params = params.set('to', to);
    if (search)     params = params.set('search', search);
    if (categoryId) params = params.set('categoryId', categoryId.toString());
    if (type)       params = params.set('type', type);
    return `${this.API}/export/csv?${params.toString()}`;
  }

  exportExcelUrl(from?: string, to?: string, search?: string, categoryId?: number, type?: string): string {
    let params = new HttpParams();
    if (from)       params = params.set('from', from);
    if (to)         params = params.set('to', to);
    if (search)     params = params.set('search', search);
    if (categoryId) params = params.set('categoryId', categoryId.toString());
    if (type)       params = params.set('type', type);
    return `${this.API}/export/excel?${params.toString()}`;
  }

  exportPdfUrl(from?: string, to?: string, search?: string, categoryId?: number, type?: string): string {
    let params = new HttpParams();
    if (from)       params = params.set('from', from);
    if (to)         params = params.set('to', to);
    if (search)     params = params.set('search', search);
    if (categoryId) params = params.set('categoryId', categoryId.toString());
    if (type)       params = params.set('type', type);
    return `${this.API}/export/pdf?${params.toString()}`;
  }

  updateCategory(transactionId: string, categoryId: number, note?: string): Observable<Transaction> {
    return this.http.patch<Transaction>(
      `${this.API}/${transactionId}/category`,
      { categoryId, categorizationNote: note },
      { withCredentials: true }
    );
  }

  deleteTransaction(transactionId: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/${transactionId}`, { withCredentials: true });
  }
}
