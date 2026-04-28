import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TaxReportService, TaxReport, DeductibleCategory } from '../../core/services/tax-report.service';

@Component({
  selector: 'app-tax-report',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tax-report.component.html',
  styleUrls: ['./tax-report.component.scss']
})
export class TaxReportComponent implements OnInit {
  private taxReportService = inject(TaxReportService);

  selectedYear = new Date().getFullYear();
  availableYears: number[] = [];
  report: TaxReport | null = null;
  loading = false;
  error = '';
  expandedCategory: string | null = null;

  ngOnInit(): void {
    const currentYear = new Date().getFullYear();
    for (let y = currentYear; y >= currentYear - 4; y--) {
      this.availableYears.push(y);
    }
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.expandedCategory = null;
    this.taxReportService.getSummary(this.selectedYear).subscribe({
      next: data => { this.report = data; this.loading = false; },
      error: () => { this.error = 'Failed to load tax report.'; this.loading = false; }
    });
  }

  toggleCategory(name: string): void {
    this.expandedCategory = this.expandedCategory === name ? null : name;
  }

  exportPdf(): void {
    window.open(this.taxReportService.exportPdfUrl(this.selectedYear), '_blank');
  }

  exportExcel(): void {
    window.open(this.taxReportService.exportExcelUrl(this.selectedYear), '_blank');
  }

  share(cat: DeductibleCategory): number {
    if (!this.report || this.report.totalDeductible === 0) return 0;
    return (cat.total / this.report.totalDeductible) * 100;
  }
}
