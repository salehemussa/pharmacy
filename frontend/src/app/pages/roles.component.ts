import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { PharmacyService } from '../core/pharmacy.service';
import { FeedbackService } from '../core/feedback.service';
import { Permission, Role } from '../core/models';
import { errorMessage } from '../core/format';

@Component({
  selector: 'app-roles',
  imports: [FormsModule, ButtonModule, CheckboxModule],
  template: `
    <section class="page">
      <div class="page-head"><div><h1>Roles and permissions</h1><p>The administrator role stays fully granted so the system cannot be locked out.</p></div></div>
      @for (role of roles(); track role.id) {
        <div class="panel role-card">
          <h2>{{ role.name }}</h2>
          <p>{{ role.description }}</p>
          @for (module of modules(); track module) {
            <h3>{{ module }}</h3>
            <div class="checks">
              @for (permission of byModule(module); track permission.id) {
                <label class="check"><p-checkbox [binary]="true" [disabled]="role.name === 'ADMINISTRATOR'" [ngModel]="selected(role, permission.code)" (onChange)="toggle(role, permission.code, $event.checked)" /> {{ permission.description }}</label>
              }
            </div>
          }
          @if (role.name !== 'ADMINISTRATOR') { <p-button [label]="'Save ' + role.name" (onClick)="save(role)" /> }
        </div>
      }
    </section>
  `,
  styles: [`
    h2 { margin: 0; font-size: 1.05rem; }
    h3 { margin: 12px 0 6px; font-size: 0.85rem; color: var(--muted); }
    .checks { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 4px 12px; }
  `]
})
export class RolesComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly feedback = inject(FeedbackService);
  readonly roles = signal<Role[]>([]);
  readonly permissions = signal<Permission[]>([]);
  private readonly draft = new Map<number, Set<string>>();

  constructor() { this.reload(); }

  modules(): string[] {
    return [...new Set(this.permissions().map(item => item.module))];
  }

  byModule(module: string): Permission[] {
    return this.permissions().filter(item => item.module === module);
  }

  selected(role: Role, code: string): boolean {
    return this.draft.get(role.id)?.has(code) ?? false;
  }

  toggle(role: Role, code: string, checked: boolean): void {
    const set = this.draft.get(role.id) ?? new Set<string>();
    if (checked) { set.add(code); } else { set.delete(code); }
    this.draft.set(role.id, set);
  }

  save(role: Role): void {
    const codes = [...(this.draft.get(role.id) ?? [])];
    if (codes.length === 0) { this.feedback.error('A role needs at least one permission.'); return; }
    this.pharmacy.saveRolePermissions(role.id, codes).subscribe({
      next: () => { this.feedback.success('Permissions saved.'); this.reload(); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }

  private reload(): void {
    this.pharmacy.roles().subscribe(roles => {
      this.roles.set(roles);
      this.draft.clear();
      for (const role of roles) { this.draft.set(role.id, new Set(role.permissions)); }
    });
    this.pharmacy.permissions().subscribe(items => this.permissions.set(items));
  }
}
