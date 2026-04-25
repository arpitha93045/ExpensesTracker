import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { User } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly API = `${environment.apiUrl}/auth`;

  private _currentUser = new BehaviorSubject<User | null>(this.loadUser());
  currentUser$ = this._currentUser.asObservable();

  get isLoggedIn(): boolean {
    return this._currentUser.value !== null;
  }

  register(fullName: string, email: string, password: string): Observable<User> {
    return this.http
      .post<User>(`${this.API}/register`, { fullName, email, password }, { withCredentials: true })
      .pipe(tap(user => this.setUser(user)));
  }

  login(email: string, password: string): Observable<User> {
    return this.http
      .post<User>(`${this.API}/login`, { email, password }, { withCredentials: true })
      .pipe(tap(user => this.setUser(user)));
  }

  refresh(): Observable<User> {
    return this.http
      .post<User>(`${this.API}/refresh`, {}, { withCredentials: true })
      .pipe(tap(user => this.setUser(user)));
  }

  logout(): void {
    this.http.post(`${this.API}/logout`, {}, { withCredentials: true }).subscribe({
      complete: () => {
        this.clearUser();
        this.router.navigate(['/login']);
      },
      error: () => {
        this.clearUser();
        this.router.navigate(['/login']);
      }
    });
  }

  private setUser(user: User): void {
    this._currentUser.next(user);
    sessionStorage.setItem('user', JSON.stringify(user));
  }

  private clearUser(): void {
    this._currentUser.next(null);
    sessionStorage.removeItem('user');
  }

  private loadUser(): User | null {
    try {
      const raw = sessionStorage.getItem('user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }
}
