import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TabsModule } from 'primeng/tabs';
import { PharmacyService } from '../core/pharmacy.service';
import { FeedbackService } from '../core/feedback.service';
import { Named, PaymentMethod, Setting } from '../core/models';
import { errorMessage } from '../core/format';

@Component({
  selector: 'app-named-list',
  imports: [FormsModule, ButtonModule, InputTextModule],
  template: `
    <div class="toolbar">
      <label class="field"><span>Name</span><input pInputText [(ngModel)]="name" /></label>
      <p-button label="Add" (onClick)="add()" />
    </div>
    <div class="panel">
      <table class="data">
        <tr><th>Name</th><th>Status</th><th></th></tr>
        @for (item of rows(); track item.id) {
          <tr>
            <td>{{ item.name }}</td>            <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
            <td><p-button [label]="item.active ? 'Deactivate' : 'Activate'" [text]="true" (onClick)="toggle(item)" /></td>
          </tr>
        }
      </table>
    </div>
  `
})
export class NamedListComponent implements OnInit {
  readonly kind = input<'form' | 'unit'>('form');
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly rows = signal<Named[]>([]);
  name = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    const request = this.kind() === 'unit' ? this.pharmacy.units() : this.pharmacy.dosageForms();
    request.subscribe(items => this.rows.set(items));
  }

  add(): void {
    if (!this.name.trim()) { return; }
    const body = { name: this.name.trim(), active: true };
    const save = this.kind() === 'unit' ? this.pharmacy.saveUnit(null, body) : this.pharmacy.saveDosageForm(null, body);
    save.subscribe({ next: () => { this.name = ''; this.load(); }, error: error => this.feedback.error(errorMessage(error)) });
  }

  toggle(item: Named): void {
    const body = { name: item.name, active: !item.active };
    const save = this.kind() === 'unit' ? this.pharmacy.saveUnit(item.id, body) : this.pharmacy.saveDosageForm(item.id, body);
    save.subscribe({ next: () => this.load(), error: error => this.feedback.error(errorMessage(error)) });
  }
}

@Component({
  selector: 'app-settings',
  imports: [FormsModule, ButtonModule, InputTextModule, TabsModule, NamedListComponent],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Settings</h1><p>Pharmacy identity, stock rules, discount limit and reference lists.</p></div></div>
      <p-tabs value="0">
        <p-tablist>
          <p-tab value="0">System</p-tab>
          <p-tab value="1">Dosage forms</p-tab>
          <p-tab value="2">Units</p-tab>
          <p-tab value="3">Payment methods</p-tab>
        </p-tablist>
        <p-tabpanels>
        <p-tabpanel value="0">
          <div class="form-grid">
            @for (item of settings(); track item.key) {
              <label class="field">
                <span [title]="item.key">{{ settingLabel(item.key) }}</span>
                <input pInputText [(ngModel)]="item.value" />
                <small>{{ item.description }}</small>
              </label>
            }
          </div>
          <p-button label="Save settings" (onClick)="saveSettings()" />
        </p-tabpanel>
        <p-tabpanel value="1"><app-named-list kind="form" /></p-tabpanel>
        <p-tabpanel value="2"><app-named-list kind="unit" /></p-tabpanel>
        <p-tabpanel value="3">
          <div class="toolbar">
            <label class="field"><span>Code</span><input pInputText [(ngModel)]="methodCode" /></label>
            <label class="field"><span>Name</span><input pInputText [(ngModel)]="methodName" /></label>
            <p-button label="Add" (onClick)="saveMethod(null)" />
          </div>
          <div class="panel">
            <table class="data">
              <tr><th>Code</th><th>Name</th><th>Status</th><th></th></tr>
              @for (item of methods(); track item.id) {
                <tr>
                  <td>{{ item.code }}</td><td>{{ item.name }}</td>                  <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span></td>
                  <td><p-button [label]="item.active ? 'Deactivate' : 'Activate'" [text]="true" (onClick)="saveMethod(item)" /></td>
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
export class SettingsComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly settings = signal<Setting[]>([]);
  readonly methods = signal<PaymentMethod[]>([]);
  methodCode = '';
  methodName = '';

  constructor() { this.reload(); }

  settingLabel(key: string): string {
    const name = key.includes('.') ? key.slice(key.lastIndexOf('.') + 1) : key;
    return name.replaceAll('_', ' ');
  }

  reload(): void {
    this.pharmacy.settings().subscribe({ next: items => this.settings.set(items), error: error => this.feedback.error(errorMessage(error)) });
    this.pharmacy.paymentMethods().subscribe(items => this.methods.set(items));
  }

  saveSettings(): void {
    const values: Record<string, string> = {};
    for (const item of this.settings()) { values[item.key] = item.value; }
    this.pharmacy.saveSettings(values).subscribe({
      next: items => { this.settings.set(items); this.feedback.success('Settings saved.'); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  saveMethod(existing: PaymentMethod | null): void {
    const body = existing
      ? { code: existing.code, name: existing.name, active: !existing.active }
      : { code: this.methodCode, name: this.methodName, active: true };
    this.pharmacy.savePaymentMethod(existing?.id ?? null, body).subscribe({
      next: () => {
        this.methodCode = '';
        this.methodName = '';
        this.pharmacy.paymentMethods().subscribe(items => this.methods.set(items));
        this.feedback.success('Payment method saved.');
      },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
}
