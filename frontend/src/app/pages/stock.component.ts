import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Batch, Movement, StockRow } from '../core/models';
import { errorMessage, money, when } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-stock',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, TabsModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Stock</h1><p>See what is on hand, where it sits, and which batches need attention.</p></div></div>
      <div class="cards">
        <button class="metric clickable" type="button" (click)="filter('ALL')"><span>Current stock</span><strong>{{ counts().all }}</strong><small>Medicines with a stock record</small></button>
        <button class="metric clickable" [class.attention]="counts().low > 0" type="button" (click)="filter('LOW')"><span>Low stock</span><strong>{{ counts().low }}</strong><small>At or below reorder level</small></button>
        <button class="metric clickable" [class.danger]="counts().out > 0" type="button" (click)="filter('OUT')"><span>Out of stock</span><strong>{{ counts().out }}</strong><small>Nothing sellable</small></button>
        <button class="metric clickable" [class.attention]="counts().near > 0" type="button" (click)="filter('NEAR')"><span>Near expiry</span><strong>{{ counts().near }}</strong><small>Inside the warning window</small></button>
        <button class="metric clickable" [class.danger]="counts().expired > 0" type="button" (click)="filter('EXPIRED')"><span>Expired</span><strong>{{ counts().expired }}</strong><small>Expired units still on hand</small></button>
      </div>
      <p-tabs value="0">
        <p-tablist>
          <p-tab value="0">Current stock</p-tab>
          <p-tab value="1">Batches</p-tab>
          <p-tab value="2">Movements</p-tab>
          @if (auth.has('STOCK_ADJUST')) { <p-tab value="3">Adjustment</p-tab> }
        </p-tablist>
        <p-tabpanels>
        <p-tabpanel value="0">
          <div class="toolbar">
            <label class="field grow"><span>Search</span><input pInputText [(ngModel)]="q" (keyup.enter)="loadStock(0)" /></label>
            <label class="field"><span>Status</span>
              <p-select [options]="stockStatuses" [(ngModel)]="status" optionLabel="label" optionValue="value" (ngModelChange)="loadStock(0)" [fluid]="true" appendTo="body" />
            </label>
            <p-button label="Search" [outlined]="true" (onClick)="loadStock(0)" />
          </div>
          <div class="panel">
            <table class="data">
              <tr><th>Medicine</th><th>Location</th><th>Sellable</th><th>On hand</th><th>Expired</th><th>Value</th><th></th></tr>
              @for (row of stock(); track row.medicineId) {
                <tr>
                  <td><strong>{{ row.name }}</strong> {{ row.strength }}<br>{{ row.dosageForm }}</td>
                  <td>{{ row.locationPath || 'Unassigned' }}</td>
                  <td>{{ row.sellableQuantity }} {{ row.unit }}</td>
                  <td>{{ row.onHandQuantity }}</td>
                  <td>{{ row.expiredQuantity }}</td>
                  <td>{{ money(row.sellableValue) }}</td>
                  <td>
                    @if (row.sellableQuantity === 0) { <span class="badge bad">Out</span> }
                    @else if (row.sellableQuantity <= row.reorderLevel) { <span class="badge warn">Low</span> }
                    @if (row.nearExpiry) { <span class="badge warn">Near expiry</span> }
                  </td>
                </tr>
              } @empty { <tr><td colspan="7" class="empty"><strong>No stock matches this view</strong>Try another status or clear the search.</td></tr> }
            </table>
            <app-pager [page]="stockPage()" [totalPages]="stockPages()" [total]="stockTotal()" (pageChange)="loadStock($event)" />
          </div>
        </p-tabpanel>
        <p-tabpanel value="1">
          <div class="toolbar">
            <label class="field"><span>Expiry</span>
              <p-select [options]="expiryOptions" [(ngModel)]="expiry" optionLabel="label" optionValue="value" (ngModelChange)="loadBatches(0)" [fluid]="true" appendTo="body" />
            </label>
          </div>
          <div class="panel">
            <table class="data">
              <tr><th>Medicine</th><th>Batch</th><th>Quantity</th><th>Location</th><th>Expiry</th><th>Sell</th><th>Supplier</th></tr>
              @for (row of batches(); track row.id) {
                <tr>
                  <td><strong>{{ row.medicineName }}</strong></td>
                  <td>{{ row.batchNumber }}</td>
                  <td>{{ row.quantityOnHand }} of {{ row.quantityReceived }}</td>
                  <td>{{ row.locationPath || 'Unassigned' }}</td>
                  <td>{{ row.expiryDate }}</td>
                  <td>{{ money(row.sellingPrice) }}</td>
                  <td>{{ row.supplierName || '—' }}</td>
                </tr>
              } @empty { <tr><td colspan="7" class="empty"><strong>No batches found</strong></td></tr> }
            </table>
            <app-pager [page]="batchPage()" [totalPages]="batchPages()" [total]="batchTotal()" (pageChange)="loadBatches($event)" />
          </div>
        </p-tabpanel>
        <p-tabpanel value="2">
          <div class="toolbar">
            <label class="field"><span>Type</span>
              <p-select [options]="movementOptions" [(ngModel)]="movementType" optionLabel="label" optionValue="value" (ngModelChange)="loadMovements(0)" [fluid]="true" appendTo="body" />
            </label>
          </div>
          <div class="panel">
            <table class="data">
              <tr><th>When</th><th>Medicine</th><th>Batch</th><th>Type</th><th>Qty</th><th>Balance</th><th>By</th><th>Reason</th></tr>
              @for (row of movements(); track row.id) {
                <tr>
                  <td>{{ when(row.createdAt) }}</td><td>{{ row.medicineName }}</td><td>{{ row.batchNumber }}</td>
                  <td>{{ row.movementType }} {{ row.direction }}</td><td>{{ row.quantity }}</td><td>{{ row.balanceAfter }}</td>
                  <td>{{ row.performedBy }}</td><td>{{ row.reason }}</td>
                </tr>
              } @empty { <tr><td colspan="8" class="empty">No movements.</td></tr> }
            </table>
            <app-pager [page]="movePage()" [totalPages]="movePages()" [total]="moveTotal()" (pageChange)="loadMovements($event)" />
          </div>
        </p-tabpanel>
        @if (auth.has('STOCK_ADJUST')) {
          <p-tabpanel value="3">
            <div class="toolbar">
              <label class="field"><span>Batch id</span><input pInputText type="number" [(ngModel)]="adjustBatch" /></label>
              <label class="field"><span>Type</span>
                <p-select [options]="adjustTypes" [(ngModel)]="adjustType" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
              </label>
              <label class="field"><span>Direction</span>
                <p-select [options]="directions" [(ngModel)]="adjustDirection" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
              </label>
              <label class="field"><span>Quantity</span><input pInputText type="number" [(ngModel)]="adjustQty" /></label>
              <label class="field grow"><span>Reason</span><input pInputText [(ngModel)]="adjustReason" /></label>
              <p-button label="Record" (onClick)="submitAdjust()" />
            </div>
            <p>Damaged and expired stock can only be written off (Out). Opening stock uses an Adjustment In with a reason. Use the batch id from the Batches tab.</p>
          </p-tabpanel>
        }
        </p-tabpanels>
      </p-tabs>
    </section>
  `
})
export class StockComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly types = ['RECEIVED', 'SOLD', 'DISPENSED', 'RETURNED', 'ADJUSTED', 'DAMAGED', 'EXPIRED', 'SALE_REVERSAL', 'PURCHASE_REVERSAL'];
  readonly stockStatuses = [{ label: 'All', value: 'ALL' }, { label: 'Low', value: 'LOW' }, { label: 'Out of stock', value: 'OUT' }, { label: 'Expired', value: 'EXPIRED' }, { label: 'Near expiry', value: 'NEAR' }];
  readonly expiryOptions = [{ label: 'All', value: '' }, { label: 'Expired', value: 'EXPIRED' }, { label: 'Near expiry', value: 'NEAR' }];
  readonly movementOptions = [{ label: 'All', value: '' }, ...['RECEIVED', 'SOLD', 'DISPENSED', 'RETURNED', 'ADJUSTED', 'DAMAGED', 'EXPIRED', 'SALE_REVERSAL', 'PURCHASE_REVERSAL'].map(value => ({ label: value, value }))];
  readonly adjustTypes = [{ label: 'Adjustment', value: 'ADJUSTED' }, { label: 'Damaged', value: 'DAMAGED' }, { label: 'Expired write-off', value: 'EXPIRED' }];
  readonly directions = [{ label: 'In', value: 'IN' }, { label: 'Out', value: 'OUT' }];
  readonly stock = signal<StockRow[]>([]);
  readonly batches = signal<Batch[]>([]);
  readonly movements = signal<Movement[]>([]);
  readonly stockPage = signal(0); readonly stockPages = signal(0); readonly stockTotal = signal(0);
  readonly batchPage = signal(0); readonly batchPages = signal(0); readonly batchTotal = signal(0);
  readonly movePage = signal(0); readonly movePages = signal(0); readonly moveTotal = signal(0);
  readonly counts = signal({ all: 0, low: 0, out: 0, near: 0, expired: 0 });
  q = ''; status = 'ALL'; expiry = ''; movementType = '';
  adjustBatch: number | null = null; adjustType = 'ADJUSTED'; adjustDirection = 'OUT'; adjustQty = 1; adjustReason = '';
  currency = 'USD';
  money = (value: number) => money(value, this.currency);
  when = when;

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.loadCounts();
    this.loadStock(0); this.loadBatches(0); this.loadMovements(0);
  }

  filter(status: string): void {
    this.status = status;
    this.loadStock(0);
  }

  private loadCounts(): void {
    const next = { all: 0, low: 0, out: 0, near: 0, expired: 0 };
    for (const status of ['ALL', 'LOW', 'OUT', 'NEAR', 'EXPIRED'] as const) {
      this.pharmacy.stock({ status, page: 0, size: 1 }).subscribe(result => {
        const key = status === 'ALL' ? 'all' : status === 'LOW' ? 'low' : status === 'OUT' ? 'out' : status === 'NEAR' ? 'near' : 'expired';
        next[key] = result.totalElements;
        this.counts.set({ ...next });
      });
    }
  }

  loadStock(page: number): void {
    this.pharmacy.stock({ q: this.q, status: this.status, page, size: 20 }).subscribe({
      next: result => { this.stock.set(result.content); this.stockPage.set(result.page); this.stockPages.set(result.totalPages); this.stockTotal.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  loadBatches(page: number): void {
    this.pharmacy.batches({ expiryStatus: this.expiry, page, size: 20 }).subscribe({
      next: result => { this.batches.set(result.content); this.batchPage.set(result.page); this.batchPages.set(result.totalPages); this.batchTotal.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  loadMovements(page: number): void {
    this.pharmacy.movements({ type: this.movementType, page, size: 20 }).subscribe({
      next: result => { this.movements.set(result.content); this.movePage.set(result.page); this.movePages.set(result.totalPages); this.moveTotal.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  submitAdjust(): void {
    if (!this.adjustBatch || this.adjustReason.trim().length < 5) {
      this.feedback.error('Enter a batch id and a reason of at least 5 characters.');
      return;
    }
    const direction = this.adjustType === 'ADJUSTED' ? this.adjustDirection : 'OUT';
    this.pharmacy.adjust({ batchId: this.adjustBatch, movementType: this.adjustType, direction, quantity: this.adjustQty, reason: this.adjustReason.trim() }).subscribe({
      next: () => { this.feedback.success('Stock adjustment recorded.'); this.adjustReason = ''; this.loadCounts(); this.loadStock(0); this.loadBatches(0); this.loadMovements(0); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
