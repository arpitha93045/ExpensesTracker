import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Budget } from '../models/models';

@Injectable({ providedIn: 'root' })
export class BudgetService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/budgets`;

  getBudgets(yearMonth: string): Observable<Budget[]> {
    return this.http.get<Budget[]>(this.API, {
      params: new HttpParams().set('yearMonth', yearMonth),
      withCredentials: true
    });
  }

  upsert(categoryId: number, amount: number, yearMonth: string): Observable<Budget> {
    return this.http.put<Budget>(this.API, { categoryId, amount, yearMonth }, { withCredentials: true });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`, { withCredentials: true });
  }
}
