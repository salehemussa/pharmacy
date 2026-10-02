import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Customer, HistoryItem } from '../core/models';
import { errorMessage, statusClass, statusLabel, when } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-customer-dialog',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, TextareaModule, CheckboxModule],
  template: `
    <form class="form-grid" [formGroup]="form">
      <label class="field"><span>Full name</span><input pInputText formControlName="fullName" /></label>
      <label class="field"><span>Phone</span><input pInputText formControlName="phone" /></label>
      <label class="field"><span>Email</span><input pInputText formControlName="email" /></label>
      <label class="field"><span>Date of birth</span><input pInputText type="date" formControlName="dateOfBirth" /></label>
      <label class="field span-2"><span>Address</span><input pInputText formControlName="address" /></label>
      <label class="field span-2"><span>Notes</span><textarea pTextarea rows="2" formControlName="notes"></textarea></label>
      <label class="check"><p-checkbox formControlName="active" [binary]="true" /> Active</label>
    </form>
    <div class="actions">
      <p-button label="Cancel" [text]="true" (onClick)="ref.close()" />
      <p-button label="Save" [disabled]="form.invalid" (onClick)="ref.close(form.getRawValue())" />
    </div>
  `
})
export class CustomerDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as Customer | null;
  readonly ref = inject(DynamicDialogRef);
  readonly form = inject(FormBuilder).nonNullable.group({
    fullName: [this.data?.fullName ?? '', Validators.required],
    phone: [this.data?.phone ?? ''],
    email: [this.data?.email ?? ''],
    dateOfBirth: [this.data?.dateOfBirth ?? ''],
    address: [this.data?.address ?? ''],
    notes: [this.data?.notes ?? ''],
    active: [this.data?.active ?? true]
  });
}

@Component({
  selector: 'app-customers',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Customers and patients</h1><p>Basic contact details and transaction history.</p></div>
        @if (auth.has('CUSTOMER_MANAGE')) { <p-button label="Add customer" icon="pi pi-plus" (onClick)="edit(null)" /> }
      </div>
      <div class="toolbar">
        <label class="field grow"><span>Search name, phone or reference</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Reference</th><th>Name</th><th>Phone</th><th>Status</th><th></th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td>{{ item.reference }}</td><td>{{ item.fullName }}</td><td>{{ item.phone }}</td>
              <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="actions">
                @if (auth.has('CUSTOMER_MANAGE')) { <p-button label="Edit" [text]="true" (onClick)="edit(item)" /> }
                <a [routerLink]="['/customers', item.id]">History</a>
              </td>
            </tr>
          } @empty { <tr><td colspan="5" class="empty">No customers.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
    </section>
  `
})
export class CustomersComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly dialog = inject(DialogService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<Customer[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = '';
  constructor() {
    this.q = inject(ActivatedRoute).snapshot.queryParamMap.get('q') ?? '';
    this.load(0);
  }
  load(page: number): void {
    this.pharmacy.customers({ q: this.q, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
  edit(customer: Customer | null): void {
    this.dialog.open(CustomerDialogComponent, { header: customer ? 'Edit customer' : 'Add customer', width: '720px', modal: true, data: customer }).onClose.subscribe(body => {
      if (!body) { return; }
      this.pharmacy.saveCustomer(customer?.id ?? null, { ...body, email: body.email || null, dateOfBirth: body.dateOfBirth || null }).subscribe({
        next: () => { this.feedback.success('Customer saved.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
}

@Component({
  selector: 'app-customer-history',
  imports: [RouterLink, ButtonModule],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>{{ customer()?.fullName }}</h1><p>{{ customer()?.reference }} · {{ customer()?.phone }}</p></div>
        <a routerLink="/customers" pButton [text]="true">Back</a>
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Type</th><th>Reference</th><th>When</th><th>Status</th></tr>
          @for (item of items(); track item.type + item.id) {
            <tr><td>{{ statusLabel(item.type) }}</td><td>{{ item.reference }}</td><td>{{ when(item.occurredAt) }}</td><td><span class="badge" [class]="statusClass(item.status)">{{ statusLabel(item.status) }}</span></td></tr>
          } @empty { <tr><td colspan="4" class="empty">No history yet.</td></tr> }
        </table>
      </div>
    </section>
  `
})
export class CustomerHistoryComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly route = inject(ActivatedRoute);
  private readonly feedback = inject(FeedbackService);
  readonly customer = signal<Customer | null>(null);
  readonly items = signal<HistoryItem[]>([]);
  when = when; statusClass = statusClass; statusLabel = statusLabel;
  constructor() {
    this.pharmacy.customerHistory(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: history => { this.customer.set(history.customer); this.items.set(history.items); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
