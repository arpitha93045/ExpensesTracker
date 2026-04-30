import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TwoFactorStatus {
  totpEnabled: boolean;
}

export interface TotpSetup {
  secret: string;
  qrDataUri: string;
}

@Injectable({ providedIn: 'root' })
export class TwoFactorService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/auth/2fa`;

  getStatus(): Observable<TwoFactorStatus> {
    return this.http.get<TwoFactorStatus>(`${this.API}/status`, { withCredentials: true });
  }

  setupTotp(): Observable<TotpSetup> {
    return this.http.post<TotpSetup>(`${this.API}/totp/setup`, {}, { withCredentials: true });
  }

  confirmTotp(code: string): Observable<void> {
    return this.http.post<void>(`${this.API}/totp/confirm`, { code }, { withCredentials: true });
  }

  disableTotp(): Observable<void> {
    return this.http.delete<void>(`${this.API}/totp`, { withCredentials: true });
  }
}
