import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PharmacyService } from '../core/pharmacy.service';
import { FeedbackService } from '../core/feedback.service';
import { AuditLog } from '../core/models';
import { errorMessage, when } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-audit',
  imports: [FormsModule, ButtonModule, InputTextModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Activity history</h1><p>Important actions, who performed them, and the recorded values.</p></div></div>
      <div class="toolbar">
        <label class="field grow"><span>Search</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <label class="field"><span>Action</span><input pInputText [(ngModel)]="action" /></label>
        <label class="field"><span>Username</span><input pInputText [(ngModel)]="username" /></label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>When</th><th>User</th><th>Action</th><th>Record</th><th>Details</th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td>{{ when(item.createdAt) }}</td>
              <td>{{ item.username || 'System' }}</td>
              <td>{{ item.action }}</td>
              <td>{{ item.entityType }} {{ item.entityId }}</td>
              <td><pre class="audit">{{ details(item) }}</pre></td>
            </tr>
          } @empty { <tr><td colspan="5" class="empty">No activity.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
    </section>
  `,
  styles: [`pre { margin: 0; white-space: pre-wrap; font-size: 0.78rem; max-width: 420px; }`]
})
export class AuditComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly rows = signal<AuditLog[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = ''; action = ''; username = '';
  when = when;
  constructor() { this.load(0); }
  load(page: number): void {
    this.pharmacy.audit({ q: this.q, action: this.action, username: this.username, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
  details(item: AuditLog): string {
    return item.details ? JSON.stringify(item.details) : '';
  }
}
