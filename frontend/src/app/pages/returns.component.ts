import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { PaymentMethod, Sale, SaleReturn } from '../core/models';
import { errorMessage, money, statusClass, statusLabel, when } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-returns',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Returns</h1><p>Returns reference the original sale. Approved returns restore stock.</p></div></div>
      <div class="toolbar">
        <label class="field grow"><span>Search reference</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <label class="field"><span>Status</span>
          <p-select [options]="statuses" [(ngModel)]="status" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
        </label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Reference</th><th>Sale</th><th>Status</th><th>Refund</th><th>By</th><th>When</th><th></th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td>{{ item.reference }}</td><td>{{ item.saleReference }}</td>
              <td><span class="badge" [class]="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td>
              <td>{{ money(item.refundAmount) }}</td><td>{{ item.createdBy }}</td><td>{{ when(item.createdAt) }}</td>
              <td class="actions">
                @if (auth.has('RETURN_APPROVE') && item.status === 'PENDING') {
                  <p-button label="Approve" [text]="true" (onClick)="approve(item)" />
                  <p-button label="Reject" [text]="true" severity="danger" (onClick)="reject(item)" />
                }
              </td>
            </tr>
          } @empty { <tr><td colspan="7" class="empty">No returns.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
      @if (auth.has('RETURN_CREATE')) {
        <h2>New return</h2>
        <div class="toolbar">
          <label class="field"><span>Sale id</span><input pInputText type="number" [(ngModel)]="saleId" /></label>
          <p-button label="Load sale" [outlined]="true" (onClick)="loadSale()" />
        </div>
        @if (sale(); as current) {
          <p>{{ current.reference }} · {{ current.customerName || 'Walk-in' }} · {{ money(current.totalAmount) }}</p>
          <div class="panel">
            <table class="data">
              <tr><th>Medicine</th><th>Batch</th><th>Sold</th><th>Already returned</th><th>Return qty</th></tr>
              @for (item of current.items; track item.id) {
                <tr>
                  <td>{{ item.medicineName }}</td><td>{{ item.batchNumber }}</td><td>{{ item.quantity }}</td><td>{{ item.quantityReturned }}</td>
                  <td><input type="number" [(ngModel)]="returnQty[item.id]" min="0" [max]="item.quantity - item.quantityReturned"></td>
                </tr>
              }
            </table>
          </div>
          <div class="toolbar">
            <label class="field grow"><span>Reason</span><input pInputText [(ngModel)]="reason" /></label>
            <label class="field"><span>Refund method</span>
              <p-select [options]="methods()" [(ngModel)]="refundMethod" optionLabel="name" optionValue="code" [fluid]="true" appendTo="body" />
            </label>
            <label class="field"><span>Refund reference</span><input pInputText [(ngModel)]="refundReference" /></label>
            <p-button label="Submit return" (onClick)="create()" />
          </div>
        }
      }
    </section>
  `
})
export class ReturnsComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<SaleReturn[]>([]);
  readonly sale = signal<Sale | null>(null);
  readonly methods = signal<PaymentMethod[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = ''; status = ''; saleId: number | null = null; reason = ''; refundMethod = 'CASH'; refundReference = '';
  readonly statuses = [{ label: 'All', value: '' }, { label: 'Pending', value: 'PENDING' }, { label: 'Approved', value: 'APPROVED' }, { label: 'Rejected', value: 'REJECTED' }];
  returnQty: Record<number, number> = {};
  currency = 'USD';
  money = (value: number) => money(value, this.currency); when = when; statusClass = statusClass; statusLabel = statusLabel;

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.pharmacy.paymentMethods(true).subscribe(methods => this.methods.set(methods));
    this.route.queryParamMap.subscribe(params => {
      const id = params.get('saleId');
      if (id) { this.saleId = Number(id); this.loadSale(); }
    });
    this.load(0);
  }

  load(page: number): void {
    this.pharmacy.returns({ q: this.q, status: this.status, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  loadSale(): void {
    if (!this.saleId) { return; }
    this.pharmacy.sale(this.saleId).subscribe({
      next: sale => { this.sale.set(sale); this.returnQty = {}; },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  create(): void {
    const current = this.sale();
    if (!current) { return; }
    const items = current.items.filter(item => (this.returnQty[item.id] ?? 0) > 0).map(item => ({ saleItemId: item.id, quantity: Number(this.returnQty[item.id]) }));
    if (!this.reason.trim() || items.length === 0) { this.feedback.error('Enter a reason and at least one quantity.'); return; }
    this.pharmacy.createReturn({ saleId: current.id, reason: this.reason.trim(), refundMethodCode: this.refundMethod, refundReference: this.refundReference || null, items }).subscribe({
      next: created => { this.feedback.success('Return ' + created.reference + ' recorded.'); this.sale.set(null); this.reason = ''; this.load(0); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  approve(item: SaleReturn): void {
    this.feedback.confirm('Approve return', 'Stock will be restored to the original batches.').subscribe(ok => {
      if (!ok) { return; }
      this.pharmacy.approveReturn(item.id).subscribe({
        next: () => { this.feedback.success('Return approved.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }

  reject(item: SaleReturn): void {
    this.feedback.reason('Reject return', 'The return will be rejected and stock will not change.').subscribe(reason => {
      if (!reason) { return; }
      this.pharmacy.rejectReturn(item.id, reason).subscribe({
        next: () => { this.feedback.success('Return rejected.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
}
