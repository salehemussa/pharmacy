import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Dispensing, PrescriptionSummary } from '../core/models';
import { errorMessage, statusClass, statusLabel, when } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-prescriptions',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, TabsModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Prescriptions</h1><p>Review, then dispense from available batches.</p></div>
        @if (auth.has('PRESCRIPTION_MANAGE')) { <a routerLink="/prescriptions/new" pButton>New prescription</a> }
      </div>
      <p-tabs value="0">
        <p-tablist>
          <p-tab value="0">Prescriptions</p-tab>
          <p-tab value="1">Dispensing history</p-tab>
        </p-tablist>
        <p-tabpanels>
        <p-tabpanel value="0">
          <div class="toolbar">
            <label class="field grow"><span>Search reference or patient</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
            <label class="field"><span>Status</span>
              <p-select [options]="statuses" [(ngModel)]="status" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
            </label>
            <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
          </div>
          <div class="panel">
            <table class="data">
              <tr><th>Reference</th><th>Patient</th><th>Date</th><th>Prescriber</th><th>Status</th></tr>
              @for (item of rows(); track item.id) {
                <tr>
                  <td><a [routerLink]="['/prescriptions', item.id]">{{ item.reference }}</a></td>
                  <td>{{ item.customerName }}</td><td>{{ item.prescriptionDate }}</td><td>{{ item.prescriberName }}</td>
                  <td><span class="badge" [class]="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
                </tr>
              } @empty { <tr><td colspan="5" class="empty">No prescriptions.</td></tr> }
            </table>
            <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
          </div>
        </p-tabpanel>
        <p-tabpanel value="1">
          <div class="panel">
            <table class="data">
              <tr><th>Reference</th><th>Prescription</th><th>Pharmacist</th><th>When</th></tr>
              @for (item of history(); track item.id) {
                <tr>
                  <td>{{ item.reference }}</td>
                  <td><a [routerLink]="['/prescriptions', item.prescriptionId]">{{ item.prescriptionReference }}</a></td>
                  <td>{{ item.dispensedBy }}</td><td>{{ when(item.dispensedAt) }}</td>
                </tr>
              } @empty { <tr><td colspan="4" class="empty">No dispensing records.</td></tr> }
            </table>
            <app-pager [page]="hPage()" [totalPages]="hPages()" [total]="hTotal()" (pageChange)="loadHistory($event)" />
          </div>
        </p-tabpanel>
        </p-tabpanels>
      </p-tabs>
    </section>
  `
})
export class PrescriptionsComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<PrescriptionSummary[]>([]);
  readonly history = signal<Dispensing[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  readonly hPage = signal(0); readonly hPages = signal(0); readonly hTotal = signal(0);
  q = ''; status = '';
  statusClass = statusClass; statusLabel = statusLabel; when = when;
  readonly statuses = [
    { label: 'All', value: '' }, { label: 'Pending', value: 'PENDING' }, { label: 'Reviewed', value: 'REVIEWED' },
    { label: 'Partially dispensed', value: 'PARTIALLY_DISPENSED' }, { label: 'Fully dispensed', value: 'FULLY_DISPENSED' }, { label: 'Cancelled', value: 'CANCELLED' }
  ];
  constructor() { this.load(0); this.loadHistory(0); }
  load(page: number): void {
    this.pharmacy.prescriptions({ q: this.q, status: this.status, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
  loadHistory(page: number): void {
    this.pharmacy.dispensings({ page, size: 20 }).subscribe({
      next: result => { this.history.set(result.content); this.hPage.set(result.page); this.hPages.set(result.totalPages); this.hTotal.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
