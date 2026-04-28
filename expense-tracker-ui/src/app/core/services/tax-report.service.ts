import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TaxTransaction {
  date: string;
  description: string;
  merchant: string;
  amount: number;
  currency: string;
}

export interface DeductibleCategory {
  name: string;
  icon: string;
  color: string;
  total: number;
  transactionCount: number;
  transactions: TaxTransaction[];
}

export interface TaxReport {
  taxYear: number;
  totalDeductible: number;
  categories: DeductibleCategory[];
}

@Injectable({ providedIn: 'root' })
export class TaxReportService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/tax-report`;

  getSummary(year: number): Observable<TaxReport> {
    return this.http.get<TaxReport>(this.API, {
      params: new HttpParams().set('year', year.toString()),
      withCredentials: true
    });
  }

  exportPdfUrl(year: number): string {
    return `${this.API}/export/pdf?year=${year}`;
  }

  exportExcelUrl(year: number): string {
    return `${this.API}/export/excel?year=${year}`;
  }
}
