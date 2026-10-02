import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Location } from '../core/models';
import { errorMessage, statusLabel } from '../core/format';

@Component({
  selector: 'app-location-dialog',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, SelectModule, CheckboxModule],
  template: `
    <form class="form-grid" [formGroup]="form">
      <label class="field"><span>Type</span>
        <p-select [options]="types" formControlName="locationType" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
      </label>
      <label class="field"><span>Parent</span>
        <p-select [options]="parents" formControlName="parentId" optionLabel="label" optionValue="value" [fluid]="true" appendTo="body" />
      </label>
      <label class="field"><span>Code</span><input pInputText formControlName="code" /></label>
      <label class="field"><span>Name</span><input pInputText formControlName="name" /></label>
      <label class="check"><p-checkbox formControlName="active" [binary]="true" inputId="loc-active" /> Active</label>
    </form>
    <p>Hierarchy: Store, then Shelf, then Rack, then Position. Example: STORE-A / A1 / A1-R01 / P01.</p>
    <div class="actions">
      <p-button label="Cancel" [text]="true" (onClick)="ref.close()" />
      <p-button label="Save" [disabled]="form.invalid" (onClick)="ref.close(form.getRawValue())" />
    </div>
  `
})
export class LocationDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as { location: Location | null; locations: Location[] };
  readonly ref = inject(DynamicDialogRef);
  readonly types = [
    { label: 'Store / area', value: 'STORE' }, { label: 'Shelf', value: 'SHELF' },
    { label: 'Rack', value: 'RACK' }, { label: 'Position / bin', value: 'POSITION' }
  ];
  get parents() { return [{ label: 'None (store)', value: null }, ...this.data.locations.map(item => ({ label: item.path, value: item.id }))]; }
  readonly form = inject(FormBuilder).nonNullable.group({
    locationType: [this.data.location?.locationType ?? 'STORE', Validators.required],
    parentId: [this.data.location?.parentId ?? null as number | null],
    code: [this.data.location?.code ?? '', Validators.required],
    name: [this.data.location?.name ?? '', Validators.required],
    active: [this.data.location?.active ?? true]
  });
}

@Component({
  selector: 'app-locations',
  imports: [FormsModule, ButtonModule, InputTextModule],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Shelves and racks</h1><p>Physical store, shelf, rack and bin locations.</p></div>
        @if (auth.has('LOCATION_MANAGE')) { <p-button label="Add location" icon="pi pi-plus" (onClick)="edit(null)" /> }
      </div>
      <div class="toolbar"><label class="field grow"><span>Search code, name or path</span><input pInputText [(ngModel)]="q" /></label></div>
      <div class="panel">
        <table class="data">
          <tr><th>Path</th><th>Type</th><th>Code</th><th>Name</th><th>Status</th><th></th></tr>
          @for (item of filtered(); track item.id) {
            <tr>
              <td>{{ item.path }}</td><td>{{ statusLabel(item.locationType) }}</td><td>{{ item.code }}</td><td>{{ item.name }}</td>
              <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
              <td>@if (auth.has('LOCATION_MANAGE')) { <p-button label="Edit" [text]="true" (onClick)="edit(item)" /> }</td>
            </tr>
          } @empty { <tr><td colspan="6" class="empty">No locations.</td></tr> }
        </table>
      </div>
    </section>
  `
})
export class LocationsComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly dialog = inject(DialogService);
  statusLabel = statusLabel;
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<Location[]>([]);
  q = '';

  constructor() { this.load(); }

  filtered(): Location[] {
    const needle = this.q.trim().toLowerCase();
    return this.rows().filter(item => !needle || `${item.path} ${item.code} ${item.name}`.toLowerCase().includes(needle));
  }

  load(): void {
    this.pharmacy.locations().subscribe({
      next: rows => this.rows.set(rows),
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  edit(location: Location | null): void {
    this.dialog.open(LocationDialogComponent, { header: location ? 'Edit location' : 'Add location', width: '720px', modal: true, data: { location, locations: this.rows().filter(item => item.id !== location?.id) } })
      .onClose.subscribe(body => {
        if (!body) { return; }
        this.pharmacy.saveLocation(location?.id ?? null, body).subscribe({
          next: () => { this.feedback.success('Location saved.'); this.load(); },
          error: error => this.feedback.error(errorMessage(error))
        });
      });
  }
}
