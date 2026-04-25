import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UploadService } from '../../core/services/upload.service';
import { UploadJob } from '../../core/models/models';
import { Router } from '@angular/router';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.scss']
})
export class UploadComponent {
  private uploadService = inject(UploadService);
  private router = inject(Router);

  isDragOver = false;
  selectedFile: File | null = null;
  uploading = false;
  uploadJob: UploadJob | null = null;
  errorMsg = '';

  // PDF password support
  pdfPassword = '';
  showPassword = false;

  private pollInterval: ReturnType<typeof setInterval> | null = null;

  get isPdf(): boolean {
    return this.selectedFile?.name.toLowerCase().endsWith('.pdf') ?? false;
  }

  onDragOver(e: DragEvent): void {
    e.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(): void {
    this.isDragOver = false;
  }

  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.isDragOver = false;
    const file = e.dataTransfer?.files[0];
    if (file) this.selectFile(file);
  }

  onFileSelected(e: Event): void {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) this.selectFile(input.files[0]);
  }

  selectFile(file: File): void {
    this.errorMsg = '';
    this.pdfPassword = '';   // clear previous password when a new file is selected
    const name = file.name.toLowerCase();
    if (!name.endsWith('.pdf') && !name.endsWith('.csv')) {
      this.errorMsg = 'Only PDF and CSV files are supported';
      return;
    }
    if (file.size > 20 * 1024 * 1024) {
      this.errorMsg = 'File size must be under 20MB';
      return;
    }
    this.selectedFile = file;
    this.uploadJob = null;
  }

  upload(): void {
    if (!this.selectedFile) return;
    this.uploading = true;
    this.errorMsg = '';

    this.uploadService.uploadFile(
      this.selectedFile,
      this.isPdf && this.pdfPassword ? this.pdfPassword : undefined
    ).subscribe({
      next: (res) => {
        this.uploading = false;
        this.pdfPassword = '';   // clear password from memory immediately after sending
        this.pollJobStatus(res.jobId);
      },
      error: (err) => {
        this.uploading = false;
        this.errorMsg = err.error?.detail ?? 'Upload failed. Please try again.';
      }
    });
  }

  private pollJobStatus(jobId: string): void {
    this.pollInterval = setInterval(() => {
      this.uploadService.getJobStatus(jobId).subscribe({
        next: (job) => {
          this.uploadJob = job;
          if (job.status === 'COMPLETED' || job.status === 'FAILED') {
            clearInterval(this.pollInterval!);
            this.pollInterval = null;
            if (job.status === 'COMPLETED') {
              setTimeout(() => this.router.navigate(['/transactions']), 2000);
            }
          }
        },
        error: () => {
          clearInterval(this.pollInterval!);
          this.pollInterval = null;
        }
      });
    }, 2000);
  }

  resetUpload(): void {
    if (this.pollInterval) clearInterval(this.pollInterval);
    this.selectedFile = null;
    this.uploadJob = null;
    this.errorMsg = '';
    this.pdfPassword = '';
    this.showPassword = false;
  }

  get progressPercent(): number {
    if (!this.uploadJob?.totalRows) return 0;
    return Math.round((this.uploadJob.processedRows / this.uploadJob.totalRows) * 100);
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }
}
