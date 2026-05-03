import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AnalyticsSummary, BillPredictions, InflationReport,
  WhatIfResult, CalendarDay, Transaction
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/analytics`;

  getSummary(from?: string, to?: string): Observable<AnalyticsSummary> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<AnalyticsSummary>(`${this.API}/summary`, { params, withCredentials: true });
  }

  getBillPredictions(): Observable<BillPredictions> {
    return this.http.get<BillPredictions>(`${this.API}/bills`, { withCredentials: true });
  }

  getInflation(): Observable<InflationReport> {
    return this.http.get<InflationReport>(`${this.API}/inflation`, { withCredentials: true });
  }

  getWhatIf(merchantOrCategory: string, cutPercent: number, months: number): Observable<WhatIfResult> {
    const params = new HttpParams()
      .set('merchantOrCategory', merchantOrCategory)
      .set('cutPercent', cutPercent.toString())
      .set('months', months.toString());
    return this.http.get<WhatIfResult>(`${this.API}/whatif`, { params, withCredentials: true });
  }

  getCalendarFlow(from: string, to: string): Observable<CalendarDay[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<CalendarDay[]>(`${this.API}/calendar`, { params, withCredentials: true });
  }

  getDayTransactions(date: string): Observable<Transaction[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<Transaction[]>(`${this.API}/calendar/day`, { params, withCredentials: true });
  }
}

