import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UploadJob } from '../models/models';

@Injectable({ providedIn: 'root' })
export class UploadService {
  private readonly http = inject(HttpClient);
  private readonly API = `${environment.apiUrl}/uploads`;

  uploadFile(file: File, pdfPassword?: string): Observable<{ jobId: string; message: string }> {
    const formData = new FormData();
    formData.append('file', file);
    // Password is sent as multipart field — over HTTPS only; never stored on the server
    if (pdfPassword) {
      formData.append('pdfPassword', pdfPassword);
    }
    return this.http.post<{ jobId: string; message: string }>(this.API, formData, {
      withCredentials: true
    });
  }

  getJobStatus(jobId: string): Observable<UploadJob> {
    return this.http.get<UploadJob>(`${this.API}/${jobId}`, { withCredentials: true });
  }
}
