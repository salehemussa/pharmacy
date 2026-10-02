import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Supplier } from '../core/models';
import { errorMessage } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-supplier-dialog',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, TextareaModule, CheckboxModule],
  template: `
    <form class="form-grid" [formGroup]="form">
      <label class="field"><span>Name</span><input pInputText formControlName="name" /></label>
      <label class="field"><span>Contact person</span><input pInputText formControlName="contactPerson" /></label>
      <label class="field"><span>Phone</span><input pInputText formControlName="phone" /></label>
      <label class="field"><span>Email</span><input pInputText formControlName="email" /></label>
      <label class="field span-2"><span>Address</span><textarea pTextarea rows="2" formControlName="address"></textarea></label>
      <label class="check"><p-checkbox formControlName="active" [binary]="true" /> Active</label>
    </form>
    <div class="actions">
      <p-button label="Cancel" [text]="true" (onClick)="ref.close()" />
      <p-button label="Save" [disabled]="form.invalid" (onClick)="ref.close(form.getRawValue())" />
    </div>
  `
})
export class SupplierDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as Supplier | null;
  readonly ref = inject(DynamicDialogRef);
  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.data?.name ?? '', Validators.required],
    contactPerson: [this.data?.contactPerson ?? ''],
    phone: [this.data?.phone ?? ''],
    email: [this.data?.email ?? '', Validators.email],
    address: [this.data?.address ?? ''],
    active: [this.data?.active ?? true]
  });
}

@Component({
  selector: 'app-suppliers',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Suppliers</h1><p>Contacts and purchase history.</p></div>
        @if (auth.has('SUPPLIER_MANAGE')) { <p-button label="Add supplier" icon="pi pi-plus" (onClick)="edit(null)" /> }
      </div>
      <div class="toolbar">
        <label class="field grow"><span>Search</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Name</th><th>Contact</th><th>Phone</th><th>Email</th><th>Status</th><th></th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td>{{ item.name }}<br><span>{{ item.address }}</span></td>
              <td>{{ item.contactPerson }}</td><td>{{ item.phone }}</td><td>{{ item.email }}</td>
              <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
              <td class="actions">
                @if (auth.has('SUPPLIER_MANAGE')) { <p-button label="Edit" [text]="true" (onClick)="edit(item)" /> }
                @if (auth.has('PURCHASE_VIEW')) { <a [routerLink]="['/purchases']" [queryParams]="{ supplierId: item.id }">Purchases</a> }
              </td>
            </tr>
          } @empty { <tr><td colspan="6" class="empty">No suppliers.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
    </section>
  `
})
export class SuppliersComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly dialog = inject(DialogService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<Supplier[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = '';

  constructor() { this.load(0); }

  load(page: number): void {
    this.pharmacy.suppliers({ q: this.q, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  edit(supplier: Supplier | null): void {
    this.dialog.open(SupplierDialogComponent, { header: supplier ? 'Edit supplier' : 'Add supplier', width: '720px', modal: true, data: supplier }).onClose.subscribe(body => {
      if (!body) { return; }
      this.pharmacy.saveSupplier(supplier?.id ?? null, {
        ...body,
        email: body.email || null,
        contactPerson: body.contactPerson || null,
        phone: body.phone || null,
        address: body.address || null
      }).subscribe({
        next: () => { this.feedback.success('Supplier saved.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
}
