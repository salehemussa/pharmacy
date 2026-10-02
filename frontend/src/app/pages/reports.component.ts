import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { PharmacyService } from '../core/pharmacy.service';
import { FeedbackService } from '../core/feedback.service';
import { ManagementSummary, ReportTable } from '../core/models';
import { errorMessage, money } from '../core/format';

interface ReportDef { id: string; label: string; path: string; dates: boolean; extra?: 'granularity' | 'stock' | 'rx'; management?: boolean; }

const REPORTS: ReportDef[] = [
  { id: 'sales', label: 'Sales by period', path: '/reports/sales', dates: true, extra: 'granularity' },
  { id: 'sales-med', label: 'Sales by medicine', path: '/reports/sales/by-medicine', dates: true },
  { id: 'sales-user', label: 'Sales by user', path: '/reports/sales/by-user', dates: true },
  { id: 'sales-pay', label: 'Sales by payment method', path: '/reports/sales/by-payment-method', dates: true },
  { id: 'stock', label: 'Stock', path: '/reports/stock', dates: false, extra: 'stock' },
  { id: 'moves', label: 'Stock movements', path: '/reports/stock/movements', dates: true },
  { id: 'adj', label: 'Stock adjustments', path: '/reports/stock/adjustments', dates: true },
  { id: 'purchases', label: 'Purchases', path: '/reports/purchases', dates: true },
  { id: 'purchases-sup', label: 'Purchases by supplier', path: '/reports/purchases/by-supplier', dates: true },
  { id: 'rx', label: 'Prescriptions', path: '/reports/prescriptions', dates: true, extra: 'rx' },
  { id: 'dispense', label: 'Dispensing history', path: '/reports/dispensing', dates: true },
  { id: 'mgmt', label: 'Management summary', path: '/reports/management', dates: true, management: true }
];

@Component({
  selector: 'app-reports',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Reports</h1><p>Figures come from recorded transactions. Cancelled sales are excluded from sales totals.</p></div></div>
      <div class="toolbar">
        <label class="field"><span>Report</span>
          <p-select [options]="reports" [(ngModel)]="reportId" optionLabel="label" optionValue="id" [fluid]="true" appendTo="body" />
        </label>
        @if (current().dates) {
          <label class="field"><span>From</span><input pInputText type="date" [(ngModel)]="from" /></label>
          <label class="field"><span>To</span><input pInputText type="date" [(ngModel)]="to" /></label>
        }
        @if (current().extra === 'granularity') {
          <label class="field"><span>Period</span>
            <p-select [options]="periods" [(ngModel)]="granularity" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
        }
        @if (current().extra === 'stock') {
          <label class="field"><span>Stock status</span>
            <p-select [options]="stockOptions" [(ngModel)]="stockStatus" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
        }
        @if (current().extra === 'rx') {
          <label class="field"><span>Status</span>
            <p-select [options]="rxOptions" [(ngModel)]="rxStatus" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
        }
        <p-button label="Run" (onClick)="run()" />
        @if (!current().management) { <p-button label="Download CSV" [outlined]="true" (onClick)="csv()" /> }
      </div>
      @if (summary(); as item) {
        <div class="cards">
          <div class="metric"><span>Gross sales</span><strong>{{ money(item.grossSales) }}</strong></div>
          <div class="metric"><span>Approved returns</span><strong>{{ money(item.approvedReturns) }}</strong></div>
          <div class="metric"><span>Net sales</span><strong>{{ money(item.netSales) }}</strong></div>
          <div class="metric"><span>Confirmed purchases</span><strong>{{ money(item.confirmedPurchases) }}</strong></div>
          <div class="metric"><span>Sellable stock</span><strong>{{ money(item.sellableStockValue) }}</strong></div>
          <div class="metric"><span>Stock movements</span><strong>{{ item.stockMovements }}</strong></div>
        </div>
        <p>{{ item.from }} to {{ item.to }}</p>
      }
      @if (table(); as data) {
        <div class="panel">
          <table class="data">
            <tr>@for (column of data.columns; track column) { <th>{{ column }}</th> }</tr>
            @for (row of data.rows; track $index) {
              <tr>@for (column of data.columns; track column) { <td>{{ row[column] }}</td> }</tr>
            } @empty { <tr><td class="empty">No rows for this filter.</td></tr> }
          </table>
        </div>
      }
    </section>
  `
})
export class ReportsComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly reports = REPORTS;
  readonly periods = [{ label: 'Daily', value: 'day' }, { label: 'Weekly', value: 'week' }, { label: 'Monthly', value: 'month' }];
  readonly stockOptions = [{ label: 'All', value: 'ALL' }, { label: 'Low', value: 'LOW' }, { label: 'Out', value: 'OUT' }, { label: 'Expired', value: 'EXPIRED' }, { label: 'Near expiry', value: 'NEAR' }];
  readonly rxOptions = [{ label: 'All', value: '' }, { label: 'Pending', value: 'PENDING' }, { label: 'Reviewed', value: 'REVIEWED' }, { label: 'Partial', value: 'PARTIALLY_DISPENSED' }, { label: 'Dispensed', value: 'FULLY_DISPENSED' }];
  reportId = 'sales';
  from = ''; to = ''; granularity = 'day'; stockStatus = 'ALL'; rxStatus = '';
  readonly table = signal<ReportTable | null>(null);
  readonly summary = signal<ManagementSummary | null>(null);
  currency = 'USD';
  money = (value: number) => money(value, this.currency);

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
  }

  current(): ReportDef { return this.reports.find(item => item.id === this.reportId) ?? this.reports[0]; }

  run(): void {
    const report = this.current();
    const query = this.query();
    this.table.set(null);
    this.summary.set(null);
    if (report.management) {
      this.pharmacy.management(query).subscribe({ next: summary => this.summary.set(summary), error: error => this.feedback.error(errorMessage(error)) });
      return;
    }
    this.pharmacy.report(report.path, query).subscribe({ next: table => this.table.set(table), error: error => this.feedback.error(errorMessage(error)) });
  }

  csv(): void {
    const report = this.current();
    this.pharmacy.reportCsv(report.path, this.query()).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = report.id + '.csv';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  private query() {
    const report = this.current();
    return {
      from: report.dates ? this.from : null,
      to: report.dates ? this.to : null,
      granularity: report.extra === 'granularity' ? this.granularity : null,
      status: report.extra === 'stock' ? this.stockStatus : report.extra === 'rx' ? this.rxStatus : null
    };
  }
}
