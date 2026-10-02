import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DialogService } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { DisplayProfile, PaymentMethod, Receipt, StockRow } from '../core/models';
import { errorMessage, money } from '../core/format';
import { ReceiptDialogComponent } from './receipt-dialog.component';

interface CartLine {
  medicineId: number;
  name: string;
  batchId: number;
  batchNumber: string;
  expiryDate: string;
  unitPrice: number;
  quantity: number;
  max: number;
  discount: number;
}

interface PayLine { code: string; amount: number; tendered: number | null; reference: string; }

@Component({
  selector: 'app-pos',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, CheckboxModule],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Point of sale</h1><p>Search or scan a barcode, then take payment. Stock is reduced when the sale is completed.</p></div></div>
      <div class="pos">
        <div>
          <div class="toolbar">
            <label class="field grow"><span>Medicine or barcode</span><input pInputText [(ngModel)]="q" (keyup.enter)="search()" autofocus /></label>
            <p-button label="Search" [outlined]="true" (onClick)="search()" />
          </div>
          <div class="panel">
            @for (item of results(); track item.medicineId) {
              <button class="hit" type="button" (click)="add(item)">
                <strong>{{ item.name }}</strong> {{ item.strength }}
                <span>{{ item.sellableQuantity }} {{ item.unit }} sellable · {{ item.locationPath || 'No location' }}</span>
              </button>
            } @empty { <div class="empty"><strong>Find a medicine</strong><p>Search by name or scan a barcode.</p></div> }
          </div>
        </div>
        <div class="panel cart">
          <table class="data">
            <tr><th>Item</th><th>Qty</th><th>Price</th>@if (canDiscount) { <th>Discount</th> }<th>Line</th><th></th></tr>
            @for (line of cart(); track line.batchId) {
              <tr>
                <td>{{ line.name }}<br>{{ line.batchNumber }} exp {{ line.expiryDate }}</td>
                <td><input type="number" [ngModel]="line.quantity" (ngModelChange)="setQty(line, $event)" min="1" [max]="line.max"></td>
                <td>{{ money(line.unitPrice) }}</td>
                @if (canDiscount) { <td><input type="number" [(ngModel)]="line.discount" min="0" step="0.01"></td> }
                <td>{{ money(line.quantity * line.unitPrice - line.discount) }}</td>
                <td><p-button label="Remove" [text]="true" severity="danger" (onClick)="remove(line)" /></td>
              </tr>
            } @empty { <tr><td colspan="6" class="empty"><strong>Cart is empty</strong>Search a medicine or scan a barcode to start the sale.</td></tr> }
          </table>
          <div class="due"><span>Amount due</span><strong>{{ money(due()) }}</strong></div>
          <div class="pay">
            <p>Subtotal {{ money(subtotal()) }} @if (profile()) { · discount cap {{ profile()!.maxDiscountPercent }}% }</p>
            <label class="field"><span>Customer search</span><input pInputText [(ngModel)]="customerSearch" (keyup.enter)="findCustomer()" /></label>
            <span>{{ customerName }}</span>
            @if (canDiscount) {
              <label class="field"><span>Sale discount</span><input pInputText type="number" [(ngModel)]="headerDiscount" min="0" step="0.01" /></label>
            }
            @for (pay of payments; track $index) {
              <div class="toolbar">
                <label class="field"><span>Method</span>
                  <p-select [options]="methods()" [(ngModel)]="pay.code" optionLabel="name" optionValue="code" [fluid]="true" appendTo="body" />
                </label>
                <label class="field"><span>Amount</span><input pInputText type="number" [(ngModel)]="pay.amount" min="0.01" step="0.01" /></label>
                @if (pay.code === 'CASH') { <label class="field"><span>Cash tendered</span><input pInputText type="number" [(ngModel)]="pay.tendered" step="0.01" /></label> }
                @if (pay.code === 'MOBILE_MONEY' || pay.code === 'BANK_CARD' || pay.code === 'OTHER') { <label class="field"><span>Reference</span><input pInputText [(ngModel)]="pay.reference" /></label> }
                <p-button label="Remove" [text]="true" severity="danger" (onClick)="payments.splice($index, 1)" />
              </div>
            }
            <p-button label="Add payment" [text]="true" (onClick)="addPayment()" />
            @if (auth.has('SALE_EXPIRED') && profile()?.allowAuthorizedExpiredUse) {
              <label class="check"><p-checkbox [(ngModel)]="authorizeExpired" [binary]="true" /> Authorize expired stock</label>
              @if (authorizeExpired) { <label class="field"><span>Reason</span><input pInputText [(ngModel)]="expiredReason" /></label> }
            }
            <div class="actions">
              <p-button styleClass="checkout" label="Complete sale" icon="pi pi-check" [disabled]="busy()" (onClick)="complete()" />
              <p-button label="Clear" [text]="true" (onClick)="clear()" />
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    input[type=number] { width: 88px; }
  `]
})
export class PosComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  private readonly dialog = inject(DialogService);
  readonly auth = inject(AuthService);
  readonly results = signal<StockRow[]>([]);
  readonly cart = signal<CartLine[]>([]);
  readonly methods = signal<PaymentMethod[]>([]);
  readonly profile = signal<DisplayProfile | null>(null);
  readonly busy = signal(false);
  q = '';
  customerSearch = '';
  customerId: number | null = null;
  customerName = '';
  headerDiscount = 0;
  authorizeExpired = false;
  expiredReason = '';
  payments: PayLine[] = [{ code: 'CASH', amount: 0, tendered: null, reference: '' }];
  currency = 'USD';
  money = (value: number) => money(value, this.currency);
  get canDiscount() { return this.auth.has('DISCOUNT_APPLY'); }

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => { this.profile.set(profile); this.currency = profile.currency; }, error: () => undefined });
    this.pharmacy.paymentMethods(true).subscribe(methods => this.methods.set(methods));
  }

  search(): void {
    if (!this.q.trim()) { return; }
    this.pharmacy.pos(this.q.trim()).subscribe({
      next: rows => {
        this.results.set(rows);
        const exact = rows.find(row => row.name.toLowerCase() === this.q.trim().toLowerCase()) ?? (rows.length === 1 ? rows[0] : null);
        if (exact && rows.length === 1) { this.add(exact); this.q = ''; this.results.set([]); }
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  add(item: StockRow): void {
    this.pharmacy.availability(item.medicineId).subscribe({
      next: batches => {
        const sellable = batches.filter(batch => this.authorizeExpired ? batch.quantityOnHand > 0 : batch.sellable);
        const used = new Map<number, number>();
        for (const line of this.cart()) { used.set(line.batchId, (used.get(line.batchId) ?? 0) + line.quantity); }
        const batch = sellable.find(candidate => candidate.quantityOnHand - (used.get(candidate.batchId) ?? 0) > 0);
        if (!batch) { this.feedback.error('No sellable stock for ' + item.name + '.'); return; }
        const existing = this.cart().find(line => line.batchId === batch.batchId);
        if (existing) { this.setQty(existing, existing.quantity + 1); }
        else {
          this.cart.update(lines => [...lines, {
            medicineId: item.medicineId, name: `${item.name} ${item.strength}`, batchId: batch.batchId,
            batchNumber: batch.batchNumber, expiryDate: batch.expiryDate, unitPrice: Number(batch.sellingPrice),
            quantity: 1, max: batch.quantityOnHand, discount: 0
          }]);
        }
        this.syncPayment();
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  setQty(line: CartLine, quantity: number): void {
    line.quantity = Math.max(1, Math.min(Number(quantity) || 1, line.max));
    this.cart.set([...this.cart()]);
    this.syncPayment();
  }

  remove(line: CartLine): void {
    this.cart.set(this.cart().filter(item => item !== line));
    this.syncPayment();
  }

  subtotal(): number { return this.round(this.cart().reduce((sum, line) => sum + line.quantity * line.unitPrice, 0)); }
  due(): number {
    const discounts = this.cart().reduce((sum, line) => sum + Number(line.discount || 0), 0) + Number(this.headerDiscount || 0);
    return this.round(Math.max(0, this.subtotal() - discounts));
  }

  addPayment(): void { this.payments.push({ code: 'CASH', amount: 0, tendered: null, reference: '' }); }
  findCustomer(): void {
    this.pharmacy.customers({ q: this.customerSearch, active: true, page: 0, size: 5 }).subscribe(result => {
      const match = result.content[0];
      if (!match) { this.feedback.error('No active customer matched that search.'); return; }
      this.customerId = match.id; this.customerName = match.fullName; this.customerSearch = match.fullName;
    });
  }

  complete(): void {
    if (this.cart().length === 0) { this.feedback.error('Add at least one medicine.'); return; }
    const due = this.due();
    if (this.payments.length === 1) { this.payments[0].amount = due; }
    this.busy.set(true);
    this.pharmacy.completeSale({
      customerId: this.customerId,
      notes: null,
      discountAmount: this.canDiscount ? Number(this.headerDiscount || 0) : 0,
      authorizeExpired: this.authorizeExpired,
      expiredReason: this.expiredReason || null,
      items: this.cart().map(line => ({ medicineId: line.medicineId, quantity: line.quantity, batchId: line.batchId, discountAmount: this.canDiscount ? Number(line.discount || 0) : 0 })),
      payments: this.payments.map(pay => ({ paymentMethodCode: pay.code, amount: Number(pay.amount), tenderedAmount: pay.code === 'CASH' ? pay.tendered : null, reference: pay.reference || null }))
    }).subscribe({
      next: sale => {
        this.busy.set(false);
        this.pharmacy.receipt(sale.id).subscribe(receipt => this.dialog.open(ReceiptDialogComponent, { header: 'Receipt ' + receipt.sale.reference, data: receipt, width: '420px', modal: true }));
        this.clear();
        this.feedback.success('Sale ' + sale.reference + ' completed.');
      },
      error: error => { this.busy.set(false); this.feedback.error(errorMessage(error)); }
    });
  }

  clear(): void {
    this.cart.set([]); this.headerDiscount = 0; this.customerId = null; this.customerName = ''; this.customerSearch = '';
    this.authorizeExpired = false; this.expiredReason = '';
    this.payments = [{ code: 'CASH', amount: 0, tendered: null, reference: '' }];
  }

  private syncPayment(): void { if (this.payments.length === 1) { this.payments[0].amount = this.due(); } }
  private round(value: number): number { return Math.round(value * 100) / 100; }
}
