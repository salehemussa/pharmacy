import { Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Receipt } from '../core/models';
import { money, when } from '../core/format';

@Component({
  selector: 'app-receipt-dialog',
  imports: [ButtonModule],
  template: `
    <div class="receipt" id="receipt">
      <img src="logo-dark.png" alt="Hangou Memorial Hospital" />
      <h2>{{ data.pharmacyName }}</h2>
      @if (data.address) { <p>{{ data.address }}</p> }
      @if (data.phone) { <p>{{ data.phone }}</p> }
      <p>{{ data.sale.reference }} · {{ when(data.sale.createdAt) }}</p>
      <p>Cashier {{ data.sale.cashier }}</p>
      <table>
        @for (item of data.sale.items; track item.id) {
          <tr><td>{{ item.medicineName }} x{{ item.quantity }}</td><td>{{ money(item.lineTotal) }}</td></tr>
          <tr><td colspan="2">{{ item.batchNumber }} exp {{ item.expiryDate }}</td></tr>
        }
      </table>
      <p>Total {{ money(data.sale.totalAmount) }}</p>
      @for (pay of data.sale.payments; track pay.id) {
        <p>{{ pay.methodName }} {{ money(pay.amount) }} @if (pay.changeAmount) { · change {{ money(pay.changeAmount) }} }</p>
      }
      <p>{{ data.footer }}</p>
    </div>
    <div class="actions">
      <p-button label="Close" [text]="true" (onClick)="ref.close()" />
      <p-button label="Print" (onClick)="print()" />
    </div>
  `
})
export class ReceiptDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as Receipt;
  readonly ref = inject(DynamicDialogRef);
  money = (value: number | null) => money(value, this.data.currency);
  when = when;
  print(): void { window.print(); }
}
