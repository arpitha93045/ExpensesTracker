import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Category } from '../models/models';

export interface CategoryRequest {
  name: string;
  icon: string;
  color: string;
}

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/categories`;

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(this.API, { withCredentials: true });
  }

  createCategory(req: CategoryRequest): Observable<Category> {
    return this.http.post<Category>(this.API, req, { withCredentials: true });
  }

  updateCategory(id: number, req: CategoryRequest): Observable<Category> {
    return this.http.put<Category>(`${this.API}/${id}`, req, { withCredentials: true });
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`, { withCredentials: true });
  }
}
