import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserProfile } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/profile`;

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(this.API, { withCredentials: true });
  }

  updateProfile(fullName: string, email: string): Observable<UserProfile> {
    return this.http.put<UserProfile>(this.API, { fullName, email }, { withCredentials: true });
  }

  updatePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.API}/password`, { currentPassword, newPassword }, { withCredentials: true });
  }

  updatePreferences(notificationsEnabled: boolean, defaultCurrency: string): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.API}/preferences`, { notificationsEnabled, defaultCurrency }, { withCredentials: true });
  }
}
