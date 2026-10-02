import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { PurchaseSummary, Supplier } from '../core/models';
import { errorMessage, money, statusClass, statusLabel } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-purchases',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Purchases</h1><p>Receive stock by confirming a purchase. Drafts do not change stock.</p></div>
        @if (auth.has('PURCHASE_MANAGE')) { <a routerLink="/purchases/new" pButton>New purchase</a> }
      </div>
      <div class="toolbar">
        <label class="field grow"><span>Reference</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <label class="field"><span>Supplier</span>
          <p-select [options]="supplierOptions()" [(ngModel)]="supplierId" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
        </label>
        <label class="field"><span>Status</span>
          <p-select [options]="statuses" [(ngModel)]="status" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
        </label>
        <label class="field"><span>From</span><input pInputText type="date" [(ngModel)]="from" /></label>
        <label class="field"><span>To</span><input pInputText type="date" [(ngModel)]="to" /></label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Reference</th><th>Supplier</th><th>Date</th><th>Status</th><th>Payment</th><th>Total</th><th>Paid</th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td><a [routerLink]="['/purchases', item.id]">{{ item.reference }}</a></td>
              <td>{{ item.supplierName }}</td><td>{{ item.purchaseDate }}</td>
              <td><span class="badge" [class]="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
              <td><span class="badge" [class]="statusClass(item.paymentStatus)">{{ statusLabel(item.paymentStatus) }}</span></td><td>{{ money(item.totalAmount) }}</td><td>{{ money(item.amountPaid) }}</td>
            </tr>
          } @empty { <tr><td colspan="7" class="empty">No purchases.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
    </section>
  `,
})
export class PurchasesComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<PurchaseSummary[]>([]);
  readonly suppliers = signal<Supplier[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = ''; supplierId: number | null = null; status = ''; from = ''; to = '';
  currency = 'USD';
  money = (value: number) => money(value, this.currency);
  statusClass = statusClass;
  statusLabel = statusLabel;
  readonly statuses = [{ label: 'All', value: '' }, { label: 'Draft', value: 'DRAFT' }, { label: 'Confirmed', value: 'CONFIRMED' }, { label: 'Cancelled', value: 'CANCELLED' }];
  supplierOptions() { return [{ label: 'All', value: null }, ...this.suppliers().map(item => ({ label: item.name, value: item.id }))]; }

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.pharmacy.suppliers({ page: 0, size: 100, active: true }).subscribe(result => this.suppliers.set(result.content));
    this.route.queryParamMap.subscribe(params => {
      const supplier = params.get('supplierId');
      this.supplierId = supplier ? Number(supplier) : null;
      this.load(0);
    });
  }

  load(page: number): void {
    this.pharmacy.purchases({ q: this.q, supplierId: this.supplierId, status: this.status, from: this.from, to: this.to, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
