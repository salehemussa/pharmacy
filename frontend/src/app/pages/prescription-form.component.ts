import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Availability, Prescription, PrescriptionItem } from '../core/models';
import { errorMessage, statusLabel, today } from '../core/format';

interface Line extends PrescriptionItem { search: string; dispenseQty: number; batchId: number | null; batches: Availability[]; stockChecked: boolean; }

@Component({
  selector: 'app-prescription-form',
  imports: [FormsModule, RouterLink, ButtonModule, CheckboxModule, InputTextModule, SelectModule],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>{{ rx()?.reference || 'New prescription' }}</h1>
          <p>{{ rx() ? statusLabel(rx()!.status) : 'Pending until reviewed.' }} @if (rx()?.customerName) { · {{ rx()?.customerName }} }</p>
        </div>
        <a routerLink="/prescriptions" pButton [text]="true">Back</a>
      </div>
      <ol class="steps">
        <li class="on">Patient</li>
        <li [class.on]="!!rx()">Medicines</li>
        <li [class.on]="reviewed()">Reviewed</li>
        <li [class.on]="dispensed()">Dispensed</li>
      </ol>
      @if (!rx() || rx()!.status === 'PENDING') {
        <div class="toolbar">
          <label class="field"><span>Patient search</span><input pInputText [(ngModel)]="customerSearch" (keyup.enter)="findCustomer()" /></label>
          <span>{{ customerName }}</span>
          <label class="field"><span>Date</span><input pInputText type="date" [(ngModel)]="prescriptionDate" /></label>
          <label class="field"><span>Prescriber</span><input pInputText [(ngModel)]="prescriberName" /></label>
          <label class="field"><span>License</span><input pInputText [(ngModel)]="prescriberLicense" /></label>
          <label class="field grow"><span>Notes</span><input pInputText [(ngModel)]="notes" /></label>
        </div>
        <div class="panel">
          <table class="data">
            <tr><th>Medicine</th><th>Dosage</th><th>Frequency</th><th>Duration</th><th>Qty</th><th>Instructions</th><th></th></tr>
            @for (line of lines; track $index) {
              <tr>
                <td><input pInputText [(ngModel)]="line.search" (keyup.enter)="findMedicine(line)" placeholder="Search" /><div>{{ line.medicineName }}</div></td>
                <td><input [(ngModel)]="line.dosage"></td>
                <td><input [(ngModel)]="line.frequency"></td>
                <td><input [(ngModel)]="line.duration"></td>
                <td><input type="number" [(ngModel)]="line.quantity" min="1"></td>
                <td><input [(ngModel)]="line.instructions"></td>
                <td><p-button label="Remove" [text]="true" severity="danger" (onClick)="lines.splice($index, 1)" /></td>
              </tr>
            }
          </table>
          <div class="pager"><p-button label="Add medicine" [outlined]="true" (onClick)="addLine()" /></div>
        </div>
        @if (auth.has('PRESCRIPTION_MANAGE')) {
          <div class="actions">
            <p-button label="Save" (onClick)="save()" />
            @if (rx()) { <p-button label="Mark reviewed" [outlined]="true" (onClick)="review()" /> }
            @if (rx()) { <p-button label="Cancel" severity="danger" [text]="true" (onClick)="cancel()" /> }
          </div>
        }
      } @else if (rx()) {
        @if (rx(); as current) {
        <p>Prescriber {{ current.prescriberName || 'Not recorded' }} · Reviewed by {{ current.reviewedBy || 'Not reviewed' }}</p>
        @if (current.cancellationReason) { <p>Cancelled: {{ current.cancellationReason }}</p> }
        <div class="panel">
          <table class="data">
            <tr><th>Medicine</th><th>Directions</th><th>Prescribed</th><th>Dispensed</th><th>Availability</th><th>This issue</th><th>Batch</th></tr>
            @for (line of lines; track line.id) {
              <tr>
                <td>{{ line.medicineName }}</td>
                <td>{{ line.dosage }}, {{ line.frequency }}, {{ line.duration }}<br>{{ line.instructions }}</td>
                <td>{{ line.quantity }}</td>
                <td>{{ line.quantityDispensed }}</td>
                <td><span class="badge" [class]="availabilityClass(line)">{{ availabilityLabel(line) }}</span></td>
                <td>@if (canDispense(line)) { <input type="number" [(ngModel)]="line.dispenseQty" [max]="remaining(line)" min="0"> }</td>
                <td>
                  @if (canDispense(line)) {
                    <p-select [options]="batchOptions(line)" [(ngModel)]="line.batchId" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
                  }
                </td>
              </tr>
            }
          </table>
        </div>
        @if (auth.has('DISPENSE') && (current.status === 'REVIEWED' || current.status === 'PARTIALLY_DISPENSED')) {
          <label class="field"><span>Dispensing notes</span><input pInputText [(ngModel)]="dispenseNotes" /></label>
          @if (auth.has('DISPENSE_EXPIRED')) { <label class="check"><p-checkbox [(ngModel)]="authorizeExpired" [binary]="true" /> Authorize expired stock</label> }
          @if (authorizeExpired) { <label class="field"><span>Expired stock reason</span><input pInputText [(ngModel)]="expiredReason" /></label> }
          <div class="actions"><p-button label="Dispense" (onClick)="dispense()" /></div>
        }
        @if (auth.has('PRESCRIPTION_REVIEW') && current.status === 'PENDING') {
          <p-button label="Mark reviewed" [outlined]="true" (onClick)="review()" />
        }
        }
      }
    </section>
  `
})
export class PrescriptionFormComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly statusLabel = statusLabel;
  readonly rx = signal<Prescription | null>(null);
  customerId: number | null = null;
  customerName = '';
  customerSearch = '';
  prescriptionDate = today();
  prescriberName = '';
  prescriberLicense = '';
  notes = '';
  dispenseNotes = '';
  authorizeExpired = false;
  expiredReason = '';
  lines: Line[] = [];

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.pharmacy.prescription(Number(id)).subscribe({
        next: rx => this.apply(rx),
        error: error => this.feedback.error(errorMessage(error))
      });
    } else {
      this.addLine();
    }
  }

  addLine(): void {
    this.lines.push({ medicineId: 0, search: '', medicineName: '', dosage: '', frequency: '', duration: '', quantity: 1, instructions: '', dispenseQty: 0, batchId: null, batches: [], stockChecked: false });
  }

  remaining(line: Line): number { return line.quantity - (line.quantityDispensed ?? 0); }
  reviewed(): boolean {
    const status = this.rx()?.status;
    return status === 'REVIEWED' || status === 'PARTIALLY_DISPENSED' || status === 'DISPENSED';
  }
  dispensed(): boolean {
    const status = this.rx()?.status;
    return status === 'PARTIALLY_DISPENSED' || status === 'DISPENSED';
  }
  availabilityLabel(line: Line): string {
    if ((line.quantityDispensed ?? 0) >= line.quantity) { return 'Dispensed'; }
    const waiting = this.rx()?.status === 'REVIEWED' || this.rx()?.status === 'PARTIALLY_DISPENSED';
    if (waiting && !line.stockChecked) { return 'Checking'; }
    const sellable = line.batches.filter(batch => batch.sellable).reduce((sum, batch) => sum + batch.quantityOnHand, 0);
    if (sellable <= 0) { return 'Unavailable'; }
    return sellable >= this.remaining(line) ? 'Available' : 'Partially available';
  }
  availabilityClass(line: Line): string {
    const label = this.availabilityLabel(line);
    if (label === 'Available' || label === 'Dispensed') { return 'ok'; }
    if (label === 'Unavailable') { return 'bad'; }
    if (label === 'Partially available') { return 'warn'; }
    return 'info';
  }
  batchOptions(line: Line) {
    return [
      { label: 'Earliest expiry (FEFO)', value: null as number | null },
      ...line.batches.map(batch => ({
        label: `${batch.batchNumber} exp ${batch.expiryDate} qty ${batch.quantityOnHand} ${batch.locationPath}`,
        value: batch.batchId as number | null
      }))
    ];
  }
  canDispense(line: Line): boolean {
    const status = this.rx()?.status;
    return (status === 'REVIEWED' || status === 'PARTIALLY_DISPENSED') && this.remaining(line) > 0;
  }

  findCustomer(): void {
    this.pharmacy.customers({ q: this.customerSearch, active: true, page: 0, size: 5 }).subscribe(result => {
      const match = result.content[0];
      if (!match) { this.feedback.error('No active customer matched that search.'); return; }
      this.customerId = match.id;
      this.customerName = match.fullName;
      this.customerSearch = match.fullName;
    });
  }

  findMedicine(line: Line): void {
    this.pharmacy.medicines({ q: line.search, active: true, page: 0, size: 5 }).subscribe(result => {
      const match = result.content[0];
      if (!match) { this.feedback.error('No medicine matched that search.'); return; }
      line.medicineId = match.id;
      line.medicineName = `${match.name} ${match.strength}`;
      line.search = match.name;
    });
  }

  save(): void {
    const items = this.lines.filter(line => line.medicineId).map(line => ({
      medicineId: line.medicineId, dosage: line.dosage, frequency: line.frequency, duration: line.duration,
      quantity: Number(line.quantity), instructions: line.instructions || null
    }));
    if (!this.customerId || items.length === 0) { this.feedback.error('Choose a patient and at least one medicine.'); return; }
    this.pharmacy.savePrescription(this.rx()?.id ?? null, {
      customerId: this.customerId, prescriptionDate: this.prescriptionDate, prescriberName: this.prescriberName || null,
      prescriberLicense: this.prescriberLicense || null, notes: this.notes || null, items
    }).subscribe({
      next: saved => { this.feedback.success('Prescription saved.'); this.apply(saved); void this.router.navigate(['/prescriptions', saved.id]); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  review(): void {
    const current = this.rx();
    if (!current) { return; }
    this.feedback.confirm('Review prescription', 'After review, the prescription can be dispensed and pending edits are locked.').subscribe(ok => {
      if (!ok) { return; }
      this.pharmacy.reviewPrescription(current.id).subscribe({
        next: saved => { this.feedback.success('Prescription reviewed.'); this.apply(saved); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }

  cancel(): void {
    const current = this.rx();
    if (!current) { return; }
    this.feedback.reason('Cancel prescription', 'This prescription will be cancelled and cannot be dispensed.').subscribe(reason => {
      if (!reason) { return; }
      this.pharmacy.cancelPrescription(current.id, reason).subscribe({
        next: saved => { this.feedback.success('Prescription cancelled.'); this.apply(saved); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }

  dispense(): void {
    const current = this.rx();
    if (!current) { return; }
    const items = this.lines.filter(line => line.dispenseQty > 0 && line.id).map(line => ({
      prescriptionItemId: line.id, quantity: Number(line.dispenseQty), batchId: line.batchId
    }));
    if (items.length === 0) { this.feedback.error('Enter a quantity for at least one medicine.'); return; }
    this.pharmacy.dispense(current.id, { notes: this.dispenseNotes || null, authorizeExpired: this.authorizeExpired, expiredReason: this.expiredReason || null, items }).subscribe({
      next: () => {
        this.feedback.success('Medicines dispensed and stock updated.');
        this.pharmacy.prescription(current.id).subscribe(saved => this.apply(saved));
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  private apply(rx: Prescription): void {
    this.rx.set(rx);
    this.customerId = rx.customerId;
    this.customerName = rx.customerName;
    this.customerSearch = rx.customerName;
    this.prescriptionDate = rx.prescriptionDate;
    this.prescriberName = rx.prescriberName ?? '';
    this.prescriberLicense = rx.prescriberLicense ?? '';
    this.notes = rx.notes ?? '';
    this.lines = rx.items.map(item => ({ ...item, search: item.medicineName ?? '', instructions: item.instructions, dispenseQty: 0, batchId: null, batches: [], stockChecked: false }));
    if (rx.status === 'REVIEWED' || rx.status === 'PARTIALLY_DISPENSED') {
      for (const line of this.lines) {
        this.pharmacy.availability(line.medicineId).subscribe(batches => {
          line.batches = batches.filter(batch => batch.quantityOnHand > 0);
          line.stockChecked = true;
        });
      }
    }
  }
}
