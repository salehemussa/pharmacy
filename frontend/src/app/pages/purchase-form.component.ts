import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Location, Purchase, PurchaseItem, Supplier } from '../core/models';
import { errorMessage, money, statusLabel, today } from '../core/format';

@Component({
  selector: 'app-purchase-form',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, SelectModule],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>{{ purchase()?.reference || 'New purchase' }}</h1>
          <p>{{ purchase() ? statusLabel(purchase()!.status) + ' · ' + statusLabel(purchase()!.paymentStatus) : 'Draft until you confirm receipt.' }}</p>
        </div>
        <a routerLink="/purchases" pButton [text]="true">Back</a>
      </div>
      <ol class="steps"><li class="done">Purchase</li><li [class.done]="purchase()?.status === 'CONFIRMED'">Receive</li><li [class.done]="purchase()?.status === 'CONFIRMED'">Batch and stock</li></ol>
      @if (editable()) {
        <div class="toolbar">
          <label class="field"><span>Supplier</span>
            <p-select [options]="suppliers()" [(ngModel)]="supplierId" optionLabel="name" optionValue="id" [fluid]="true" appendTo="body" />
          </label>
          <label class="field"><span>Date</span><input pInputText type="date" [(ngModel)]="purchaseDate" /></label>
          <label class="field grow"><span>Notes</span><input pInputText [(ngModel)]="notes" /></label>
        </div>
        <div class="panel">
          <table class="data">
            <tr><th>Medicine</th><th>Qty</th><th>Buy</th><th>Sell</th><th>Batch</th><th>Made</th><th>Expiry</th><th>Location</th><th></th></tr>
            @for (line of lines; track $index) {
              <tr>
                <td>
                  <input pInputText [(ngModel)]="line.search" (keyup.enter)="findMedicine(line)" aria-label="Search medicine" />
                  @if (line.medicineName) { <div>{{ line.medicineName }}</div> }
                </td>
                <td><input type="number" [(ngModel)]="line.quantity" min="1"></td>
                <td><input type="number" [(ngModel)]="line.purchasePrice" min="0" step="0.01"></td>
                <td><input type="number" [(ngModel)]="line.sellingPrice" min="0" step="0.01"></td>
                <td><input pInputText [(ngModel)]="line.batchNumber" /></td>
                <td><input pInputText type="date" [(ngModel)]="line.manufacturingDate" /></td>
                <td><input pInputText type="date" [(ngModel)]="line.expiryDate" /></td>
                <td>
                  <p-select [options]="locationOptions()" [(ngModel)]="line.locationId" optionLabel="label" optionValue="value" appendTo="body" [fluid]="true" />
                </td>
                <td><p-button label="Remove" [text]="true" severity="danger" (onClick)="lines.splice($index, 1)" /></td>
              </tr>
            }
          </table>
          <div class="pager"><p-button label="Add line" [outlined]="true" (onClick)="addLine()" /></div>
        </div>
        @if (auth.has('PURCHASE_MANAGE')) {
          <div class="actions">
            <p-button label="Save draft" [outlined]="true" (onClick)="save(false)" />
            <p-button label="Confirm receipt" (onClick)="save(true)" />
          </div>
        }
      } @else if (purchase()) {
        @if (purchase(); as current) {
        <div class="panel">
          <table class="data">
            <tr><th>Medicine</th><th>Qty</th><th>Batch</th><th>Expiry</th><th>Location</th><th>Line total</th></tr>
            @for (item of current.items; track item.id) {
              <tr>
                <td>{{ item.medicineName }}</td><td>{{ item.quantity }}</td><td>{{ item.batchNumber }}</td>
                <td>{{ item.expiryDate }}</td><td>{{ item.locationCode || 'Default' }}</td><td>{{ money(item.lineTotal) }}</td>
              </tr>
            }
          </table>
        </div>
        <p>Total {{ money(current.totalAmount) }} · Paid {{ money(current.amountPaid) }} · Created by {{ current.createdBy }}</p>
        @if (current.cancellationReason) { <p>Cancelled: {{ current.cancellationReason }}</p> }
        @if (auth.has('PURCHASE_MANAGE') && current.status === 'CONFIRMED') {
          <div class="toolbar">
            <label class="field"><span>Amount paid</span><input pInputText type="number" [(ngModel)]="amountPaid" min="0" step="0.01" /></label>
            <p-button label="Update payment" [outlined]="true" (onClick)="pay()" />
            <p-button label="Cancel purchase" severity="danger" [text]="true" (onClick)="cancel()" />
          </div>
          <p>A confirmed purchase can be cancelled only while every batch still has its full received quantity.</p>
        }
        }
      }
    </section>
  `
})
export class PurchaseFormComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly purchase = signal<Purchase | null>(null);
  readonly suppliers = signal<Supplier[]>([]);
  readonly locations = signal<Location[]>([]);
  locationOptions() {
    return [{ label: 'Medicine default', value: null as number | null }, ...this.locations().map(item => ({ label: item.path, value: item.id as number | null }))];
  }
  supplierId: number | null = null;
  purchaseDate = today();
  notes = '';
  amountPaid = 0;
  lines: (PurchaseItem & { search: string })[] = [];
  currency = 'USD';
  statusLabel = statusLabel;
  money = (value: number | undefined) => money(value, this.currency);

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.pharmacy.suppliers({ page: 0, size: 100, active: true }).subscribe(result => this.suppliers.set(result.content));
    this.pharmacy.locations(true).subscribe(items => this.locations.set(items));
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.pharmacy.purchase(Number(id)).subscribe({
        next: purchase => this.apply(purchase),
        error: error => this.feedback.error(errorMessage(error))
      });
    } else {
      this.addLine();
    }
  }

  editable(): boolean {
    const current = this.purchase();
    return !current || current.status === 'DRAFT';
  }

  addLine(): void {
    this.lines.push({ medicineId: 0, search: '', medicineName: '', quantity: 1, purchasePrice: 0, sellingPrice: 0, batchNumber: '', manufacturingDate: null, expiryDate: today(), locationId: null });
  }

  findMedicine(line: PurchaseItem & { search: string }): void {
    this.pharmacy.medicines({ q: line.search, active: true, page: 0, size: 5 }).subscribe(result => {
      const match = result.content[0];
      if (!match) { this.feedback.error('No medicine matched that search.'); return; }
      line.medicineId = match.id;
      line.medicineName = `${match.name} ${match.strength}`;
      line.purchasePrice = match.purchasePrice;
      line.sellingPrice = match.sellingPrice;
      line.locationId = match.locationId;
      line.search = match.name;
    });
  }

  save(confirm: boolean): void {
    const items = this.lines.filter(line => line.medicineId).map(line => ({
      medicineId: line.medicineId,
      quantity: Number(line.quantity),
      purchasePrice: Number(line.purchasePrice),
      sellingPrice: Number(line.sellingPrice),
      batchNumber: line.batchNumber,
      manufacturingDate: line.manufacturingDate || null,
      expiryDate: line.expiryDate,
      locationId: line.locationId
    }));
    if (!this.supplierId || items.length === 0) {
      this.feedback.error('Choose a supplier and at least one medicine.');
      return;
    }
    const body = { supplierId: this.supplierId, purchaseDate: this.purchaseDate, notes: this.notes || null, items };
    const id = this.purchase()?.id ?? null;
    this.pharmacy.savePurchase(id, body).subscribe({
      next: saved => {
        if (!confirm) {
          this.feedback.success('Draft saved.');
          void this.router.navigate(['/purchases', saved.id]);
          this.apply(saved);
          return;
        }
        this.pharmacy.confirmPurchase(saved.id).subscribe({
          next: confirmed => { this.feedback.success('Purchase confirmed and stock received.'); this.apply(confirmed); void this.router.navigate(['/purchases', confirmed.id]); },
          error: error => this.feedback.error(errorMessage(error))
        });
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  pay(): void {
    const current = this.purchase();
    if (!current) { return; }
    this.pharmacy.payPurchase(current.id, Number(this.amountPaid)).subscribe({
      next: updated => { this.feedback.success('Payment updated.'); this.apply(updated); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  cancel(): void {
    const current = this.purchase();
    if (!current) { return; }
    this.feedback.reason('Cancel purchase', 'Stock will be reversed only if the received quantity is still on hand.').subscribe(reason => {
      if (!reason) { return; }
      this.pharmacy.cancelPurchase(current.id, reason).subscribe({
        next: updated => { this.feedback.success('Purchase cancelled.'); this.apply(updated); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }

  private apply(purchase: Purchase): void {
    this.purchase.set(purchase);
    this.supplierId = purchase.supplierId;
    this.purchaseDate = purchase.purchaseDate;
    this.notes = purchase.notes ?? '';
    this.amountPaid = purchase.amountPaid;
    this.lines = purchase.items.map(item => ({ ...item, search: item.medicineName ?? '', manufacturingDate: item.manufacturingDate }));
  }
}
