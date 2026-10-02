import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DialogService } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Sale, SaleSummary } from '../core/models';
import { errorMessage, money, statusClass, statusLabel, when } from '../core/format';
import { PagerComponent } from '../shared/pager.component';
import { ReceiptDialogComponent } from './receipt-dialog.component';

@Component({
  selector: 'app-sales',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Sales</h1><p>Completed transactions. Cancellation reverses stock.</p></div></div>
      <div class="toolbar">
        <label class="field grow"><span>Reference</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <label class="field"><span>Status</span>
          <p-select [options]="statuses" [(ngModel)]="status" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
        </label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Reference</th><th>Customer</th><th>Total</th><th>Cashier</th><th>When</th><th>Status</th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td><a [routerLink]="['/sales', item.id]">{{ item.reference }}</a></td>
              <td>{{ item.customerName || 'Walk-in' }}</td><td>{{ money(item.totalAmount) }}</td>
              <td>{{ item.cashier }}</td><td>{{ when(item.createdAt) }}</td>
              <td><span class="badge" [class]="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
            </tr>
          } @empty { <tr><td colspan="6" class="empty">No sales.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
    </section>
  `,
})
export class SalesComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly rows = signal<SaleSummary[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = ''; status = ''; currency = 'USD';
  money = (value: number) => money(value, this.currency); when = when; statusClass = statusClass; statusLabel = statusLabel;
  readonly statuses = [{ label: 'All', value: '' }, { label: 'Completed', value: 'COMPLETED' }, { label: 'Cancelled', value: 'CANCELLED' }];
  constructor() {
    this.q = inject(ActivatedRoute).snapshot.queryParamMap.get('q') ?? '';
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.load(0);
  }
  load(page: number): void {
    this.pharmacy.sales({ q: this.q, status: this.status, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}

@Component({
  selector: 'app-sale-detail',
  imports: [RouterLink, ButtonModule],
  template: `
    <section class="page">
      @if (sale(); as current) {
        <div class="page-head">
          <div><h1>{{ current.reference }}</h1><p><span class="badge" [class]="statusClass(current.status)">{{ statusLabel(current.status) }}</span> · {{ when(current.createdAt) }} · {{ current.cashier }}</p></div>
          <a routerLink="/sales" pButton [text]="true" label="Back"></a>
        </div>
        <div class="due"><span>Total · stock updated</span><strong>{{ money(current.totalAmount) }}</strong></div>
        <p>Customer {{ current.customerName || 'Walk-in' }} · Subtotal {{ money(current.subtotal) }} · Discount {{ money(current.discountAmount) }}</p>
        @if (current.cancellationReason) { <p>Cancelled: {{ current.cancellationReason }}</p> }
        <div class="panel">
          <table class="data">
            <tr><th>Medicine</th><th>Batch</th><th>Expiry</th><th>Qty</th><th>Returned</th><th>Line</th></tr>
            @for (item of current.items; track item.id) {
              <tr><td>{{ item.medicineName }}</td><td>{{ item.batchNumber }}</td><td>{{ item.expiryDate }}</td><td>{{ item.quantity }}</td><td>{{ item.quantityReturned }}</td><td>{{ money(item.lineTotal) }}</td></tr>
            }
          </table>
        </div>
        <div class="panel">
          <table class="data">
            <tr><th>Method</th><th>Amount</th><th>Tendered</th><th>Change</th><th>Reference</th></tr>
            @for (pay of current.payments; track pay.id) {
              <tr><td>{{ pay.methodName }}</td><td>{{ money(pay.amount) }}</td><td>{{ money(pay.tenderedAmount) }}</td><td>{{ money(pay.changeAmount) }}</td><td>{{ pay.reference }}</td></tr>
            }
          </table>
        </div>
        <div class="actions">
          <p-button label="Receipt" [outlined]="true" (onClick)="receipt()" />
          @if (auth.has('SALE_CANCEL') && current.status === 'COMPLETED') { <p-button label="Cancel sale" severity="danger" [text]="true" (onClick)="cancel()" /> }
          @if (auth.has('RETURN_CREATE') && current.status === 'COMPLETED') { <a pButton [text]="true" [routerLink]="['/returns']" [queryParams]="{ saleId: current.id }">Return items</a> }
        </div>
      }
    </section>
  `
})
export class SaleDetailComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(DialogService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly sale = signal<Sale | null>(null);
  currency = 'USD';
  money = (value: number | null) => money(value, this.currency); when = when; statusClass = statusClass; statusLabel = statusLabel;
  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.reload();
  }
  receipt(): void {
    const current = this.sale();
    if (!current) { return; }
    this.pharmacy.receipt(current.id).subscribe({
      next: receipt => this.dialog.open(ReceiptDialogComponent, { header: 'Receipt ' + receipt.sale.reference, data: receipt, width: '420px', modal: true }),
      error: error => this.feedback.error(errorMessage(error))
    });
  }
  cancel(): void {
    const current = this.sale();
    if (!current) { return; }
    this.feedback.reason('Cancel sale', 'Stock will be returned to the original batches. This is blocked if a return is already pending or approved.').subscribe(reason => {
      if (!reason) { return; }
      this.pharmacy.cancelSale(current.id, reason).subscribe({
        next: sale => { this.sale.set(sale); this.feedback.success('Sale cancelled.'); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
  private reload(): void {
    this.pharmacy.sale(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: sale => this.sale.set(sale),
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
