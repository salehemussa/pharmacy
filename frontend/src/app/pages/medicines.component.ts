import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { MenuModule } from 'primeng/menu';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Category, Location, Medicine, Named } from '../core/models';
import { errorMessage, money } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-medicine-dialog',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, SelectModule, CheckboxModule],
  template: `
    <form [formGroup]="form">
      <div class="form-section"><h3>Medicine information</h3>
        <div class="form-grid">
          <label class="field"><span>Name</span><input pInputText formControlName="name" placeholder="Amoxicillin" /></label>
          <label class="field"><span>Generic name</span><input pInputText formControlName="genericName" /></label>
          <label class="field"><span>Brand</span><input pInputText formControlName="brandName" /></label>
          <label class="field"><span>Category</span>
            <p-select [options]="categoryOptions" formControlName="categoryId" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
          <label class="field"><span>Dosage form</span>
            <p-select [options]="formOptions" formControlName="dosageForm" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
          <label class="field"><span>Strength</span><input pInputText formControlName="strength" placeholder="500 mg" /></label>
          <label class="field"><span>Unit</span>
            <p-select [options]="unitOptions" formControlName="unit" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
          <label class="field"><span>Manufacturer</span><input pInputText formControlName="manufacturer" /></label>
          <label class="field"><span>Barcode</span><input pInputText formControlName="barcode" /></label>
        </div>
      </div>
      <div class="form-section"><h3>Pricing</h3>
        <div class="form-grid">
          <label class="field"><span>Purchase price</span><input pInputText type="number" formControlName="purchasePrice" /></label>
          <label class="field"><span>Selling price</span><input pInputText type="number" formControlName="sellingPrice" /></label>
        </div>
      </div>
      <div class="form-section"><h3>Stock and storage</h3>
        <div class="form-grid">
          <label class="field"><span>Reorder level</span><input pInputText type="number" formControlName="reorderLevel" /></label>
          <label class="field"><span>Shelf location</span>
            <p-select [options]="locationOptions" formControlName="locationId" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
          </label>
          <label class="check"><p-checkbox formControlName="active" [binary]="true" /> Active and available to sell</label>
        </div>
      </div>
    </form>
    <div class="actions">
      <p-button label="Cancel" [text]="true" (onClick)="ref.close()" />
      <p-button label="Save" [disabled]="form.invalid" (onClick)="save()" />
    </div>
  `
})
export class MedicineDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as { medicine: Medicine | null; categories: Category[]; locations: Location[]; forms: Named[]; units: Named[] };
  readonly ref = inject(DynamicDialogRef);
  readonly categoryOptions = [{ label: 'None', value: null as number | null }, ...this.data.categories.map(item => ({ label: item.name, value: item.id as number | null }))];
  readonly formOptions = this.data.forms.map(item => ({ label: item.name, value: item.name }));
  readonly unitOptions = this.data.units.map(item => ({ label: item.name, value: item.name }));
  readonly locationOptions = [{ label: 'Unassigned', value: null as number | null }, ...this.data.locations.map(item => ({ label: item.path, value: item.id as number | null }))];
  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.data.medicine?.name ?? '', Validators.required],
    genericName: [this.data.medicine?.genericName ?? '', Validators.required],
    brandName: [this.data.medicine?.brandName ?? ''],
    categoryId: [this.data.medicine?.categoryId ?? null as number | null],
    dosageForm: [this.data.medicine?.dosageForm ?? '', Validators.required],
    strength: [this.data.medicine?.strength ?? '', Validators.required],
    unit: [this.data.medicine?.unit ?? '', Validators.required],
    manufacturer: [this.data.medicine?.manufacturer ?? ''],
    barcode: [this.data.medicine?.barcode ?? ''],
    reorderLevel: [this.data.medicine?.reorderLevel ?? 0, [Validators.required, Validators.min(0)]],
    purchasePrice: [this.data.medicine?.purchasePrice ?? 0, [Validators.required, Validators.min(0)]],
    sellingPrice: [this.data.medicine?.sellingPrice ?? 0, [Validators.required, Validators.min(0)]],
    locationId: [this.data.medicine?.locationId ?? null as number | null],
    active: [this.data.medicine?.active ?? true]
  });

  save(): void {
    const value = this.form.getRawValue();
    this.ref.close({
      ...value,
      brandName: value.brandName || null,
      manufacturer: value.manufacturer || null,
      barcode: value.barcode || null
    });
  }
}

@Component({
  selector: 'app-medicines',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, TabsModule, MenuModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Medicines</h1><p>Manage medicines, pricing, stock levels and shelf locations.</p></div>
        @if (auth.has('MEDICINE_MANAGE')) { <p-button label="Add medicine" icon="pi pi-plus" (onClick)="edit(null)" /> }
      </div>
      <p-tabs value="0">
        <p-tablist>
          <p-tab value="0">Medicines</p-tab>
          <p-tab value="1">Categories</p-tab>
        </p-tablist>
        <p-tabpanels>
        <p-tabpanel value="0">
          <div class="toolbar">
            <label class="field grow"><span>Search name, generic, brand or barcode</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
            <label class="field"><span>Category</span>
              <p-select [options]="categoryOptions()" [(ngModel)]="categoryId" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
            </label>
            <label class="field"><span>Location</span>
              <p-select [options]="locationOptions()" [(ngModel)]="locationId" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
            </label>
            <label class="field"><span>Status</span>
              <p-select [options]="statusOptions" [(ngModel)]="active" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
            </label>
            <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
            <p-button label="Clear filters" [text]="true" (onClick)="clearFilters()" />
          </div>
          @if (loading()) { <div class="skeleton"></div> }
          <div class="panel">
            <table class="data">
              <tr><th>Medicine</th><th>Form</th><th>Location</th><th>Reorder</th><th>Sell</th><th>Status</th><th></th></tr>
              @for (item of rows(); track item.id) {
                <tr>
                  <td><strong>{{ item.name }}</strong><br>{{ item.genericName }} {{ item.strength }} @if (item.barcode) { <br>Barcode {{ item.barcode }} }</td>
                  <td>{{ item.dosageForm }} / {{ item.unit }}</td>
                  <td>{{ item.locationPath || 'Unassigned' }}</td>
                  <td>{{ item.reorderLevel }}</td>
                  <td>{{ money(item.sellingPrice) }}</td>
                  <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
                  <td>
                    @if (auth.has('MEDICINE_MANAGE')) {
                      <p-button icon="pi pi-ellipsis-h" [text]="true" [rounded]="true" (onClick)="menu.toggle($event)" ariaLabel="Actions" />
                      <p-menu #menu [popup]="true" [model]="rowMenu(item)" appendTo="body" />
                    }
                  </td>
                </tr>
              } @empty { <tr><td colspan="7" class="empty"><strong>No medicines found</strong>Try changing your search or filters.</td></tr> }
            </table>
            <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
          </div>
        </p-tabpanel>
        <p-tabpanel value="1">
          @if (auth.has('MEDICINE_MANAGE')) {
            <div class="toolbar">
              <label class="field"><span>Category name</span><input pInputText [(ngModel)]="categoryName" /></label>
              <label class="field grow"><span>Description</span><input pInputText [(ngModel)]="categoryDescription" /></label>
              <p-button label="Add" (onClick)="saveCategory(null)" />
            </div>
          }
          <div class="panel">
            <table class="data">
              <tr><th>Name</th><th>Description</th><th>Status</th><th></th></tr>
              @for (item of categories(); track item.id) {
                <tr>
                  <td>{{ item.name }}</td><td>{{ item.description }}</td>
                  <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
                  <td>@if (auth.has('MEDICINE_MANAGE')) { <p-button [label]="item.active ? 'Deactivate' : 'Activate'" [text]="true" (onClick)="saveCategory(item)" /> }</td>
                </tr>
              }
            </table>
          </div>
        </p-tabpanel>
        </p-tabpanels>
      </p-tabs>
    </section>
  `
})
export class MedicinesComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly dialog = inject(DialogService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<Medicine[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly forms = signal<Named[]>([]);
  readonly units = signal<Named[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(true);
  q = '';
  categoryId: number | null = null;
  locationId: number | null = null;
  active: boolean | null = true;
  readonly statusOptions = [{ label: 'All', value: null }, { label: 'Active', value: true }, { label: 'Inactive', value: false }];
  categoryOptions() { return [{ label: 'All', value: null as number | null }, ...this.categories().map(item => ({ label: item.name, value: item.id as number | null }))]; }
  locationOptions() { return [{ label: 'All', value: null as number | null }, ...this.locations().map(item => ({ label: item.path, value: item.id as number | null }))]; }
  rowMenu(item: Medicine) {
    return [
      { label: 'Edit', command: () => this.edit(item) },
      { label: item.active ? 'Deactivate' : 'Activate', command: () => this.toggleActive(item) }
    ];
  }
  categoryName = '';
  categoryDescription = '';
  currency = 'USD';
  money = (value: number) => money(value, this.currency);

  constructor() {
    this.pharmacy.display().subscribe({ next: profile => this.currency = profile.currency, error: () => undefined });
    this.pharmacy.categories().subscribe(items => this.categories.set(items));
    this.pharmacy.locations(true).subscribe(items => this.locations.set(items));
    this.pharmacy.dosageForms(true).subscribe(items => this.forms.set(items));
    this.pharmacy.units(true).subscribe(items => this.units.set(items));
    this.q = inject(ActivatedRoute).snapshot.queryParamMap.get('q') ?? '';
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.pharmacy.medicines({ q: this.q, categoryId: this.categoryId, locationId: this.locationId, active: this.active, page, size: 20 }).subscribe({
      next: result => {
        this.rows.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: error => { this.feedback.error(errorMessage(error)); this.loading.set(false); }
    });
  }

  clearFilters(): void {
    this.q = '';
    this.categoryId = null;
    this.locationId = null;
    this.active = true;
    this.load(0);
  }

  toggleActive(item: Medicine): void {
    const next = !item.active;
    this.feedback.confirm(next ? 'Activate medicine' : 'Deactivate medicine', next
      ? 'This medicine can be sold and dispensed again.'
      : 'The medicine stays on record, but it cannot be sold or dispensed.').subscribe(ok => {
      if (!ok) { return; }
      this.pharmacy.saveMedicine(item.id, {
        name: item.name, genericName: item.genericName, brandName: item.brandName, categoryId: item.categoryId,
        dosageForm: item.dosageForm, strength: item.strength, unit: item.unit, manufacturer: item.manufacturer,
        barcode: item.barcode, reorderLevel: item.reorderLevel, purchasePrice: item.purchasePrice,
        sellingPrice: item.sellingPrice, locationId: item.locationId, active: next
      }).subscribe({
        next: () => { this.feedback.success(next ? 'Medicine activated.' : 'Medicine deactivated.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }

  edit(medicine: Medicine | null): void {
    this.dialog.open(MedicineDialogComponent, {
      header: medicine ? 'Edit medicine' : 'Add medicine',
      width: '860px',
      modal: true,
      data: { medicine, categories: this.categories(), locations: this.locations(), forms: this.forms(), units: this.units() }
    }).onClose.subscribe(body => {
      if (!body) { return; }
      this.pharmacy.saveMedicine(medicine?.id ?? null, body).subscribe({
        next: () => { this.feedback.success('Medicine saved.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }

  saveCategory(existing: Category | null): void {
    const body = existing
      ? { name: existing.name, description: existing.description, active: !existing.active }
      : { name: this.categoryName.trim(), description: this.categoryDescription.trim(), active: true };
    if (!existing && !this.categoryName.trim()) { return; }
    this.pharmacy.saveCategory(existing?.id ?? null, body).subscribe({
      next: () => {
        this.categoryName = '';
        this.categoryDescription = '';
        this.pharmacy.categories().subscribe(items => this.categories.set(items));
        this.feedback.success('Category saved.');
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
