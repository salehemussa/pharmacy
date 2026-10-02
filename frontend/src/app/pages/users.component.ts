import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { MenuModule } from 'primeng/menu';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PharmacyService } from '../core/pharmacy.service';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { Role, UserAccount } from '../core/models';
import { errorMessage } from '../core/format';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-user-dialog',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, MultiSelectModule, CheckboxModule],
  template: `
    <form class="form-grid" [formGroup]="form">
      @if (!data.user) { <label class="field"><span>Username</span><input pInputText formControlName="username" /></label> }
      <label class="field"><span>Full name</span><input pInputText formControlName="fullName" /></label>
      <label class="field"><span>Email</span><input pInputText formControlName="email" /></label>
      <label class="field"><span>Phone</span><input pInputText formControlName="phone" /></label>
      @if (!data.user) { <label class="field"><span>Password</span><input pInputText type="password" formControlName="password" /></label> }
      <label class="field"><span>Roles</span>
        <p-multiselect [options]="data.roles" formControlName="roles" optionLabel="name" optionValue="name" [fluid]="true" appendTo="body" placeholder="Roles" />
      </label>
      @if (!data.user) { <label class="check"><p-checkbox formControlName="active" [binary]="true" /> Active</label> }
    </form>
    <div class="actions">
      <p-button label="Cancel" [text]="true" (onClick)="ref.close()" />
      <p-button label="Save" [disabled]="form.invalid" (onClick)="ref.close(form.getRawValue())" />
    </div>
  `
})
export class UserDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as { user: UserAccount | null; roles: Role[] };
  readonly ref = inject(DynamicDialogRef);
  readonly form = inject(FormBuilder).nonNullable.group({
    username: [this.data.user?.username ?? '', this.data.user ? [] : [Validators.required]],
    fullName: [this.data.user?.fullName ?? '', Validators.required],
    email: [this.data.user?.email ?? '', [Validators.required, Validators.email]],
    phone: [this.data.user?.phone ?? ''],
    password: ['', this.data.user ? [] : [Validators.required, Validators.minLength(8)]],
    roles: [this.data.user?.roles ?? [] as string[], Validators.required],
    active: [this.data.user?.active ?? true]
  });
}

@Component({
  selector: 'app-users',
  imports: [FormsModule, ButtonModule, InputTextModule, SelectModule, MenuModule, PagerComponent],
  template: `
    <section class="page">
      <div class="page-head">
        <div><h1>Users</h1><p>Accounts, roles and password resets. A temporary password is shown once.</p></div>
        @if (auth.has('USER_MANAGE')) { <p-button label="Create user" icon="pi pi-plus" (onClick)="edit(null)" /> }
      </div>
      <div class="toolbar">
        <label class="field grow"><span>Search</span><input pInputText [(ngModel)]="q" (keyup.enter)="load(0)" /></label>
        <label class="field"><span>Role</span>
          <p-select [options]="roleOptions()" [(ngModel)]="role" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
        </label>
        <label class="field"><span>Status</span>
          <p-select [options]="activeOptions" [(ngModel)]="active" optionLabel="label" optionValue="value" (ngModelChange)="load(0)" [fluid]="true" appendTo="body" />
        </label>
        <p-button label="Search" [outlined]="true" (onClick)="load(0)" />
      </div>
      <div class="panel">
        <table class="data">
          <tr><th>Username</th><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th></th></tr>
          @for (item of rows(); track item.id) {
            <tr>
              <td>{{ item.username }}</td><td>{{ item.fullName }}</td><td>{{ item.email }}</td><td>{{ item.roles.join(', ') }}</td>
              <td><span class="badge" [class.bad]="!item.active">{{ item.active ? 'Active' : 'Inactive' }}</span> @if (item.mustChangePassword) { <span class="badge warn">Password change</span> }</td>
              <td>
                @if (auth.has('USER_MANAGE')) {
                  <p-button icon="pi pi-ellipsis-h" [text]="true" [rounded]="true" (onClick)="openMenu($event, item, menu)" ariaLabel="Actions" />
                  <p-menu #menu [popup]="true" [model]="menuItems(item)" appendTo="body" />
                }
              </td>
            </tr>
          } @empty { <tr><td colspan="6" class="empty">No users.</td></tr> }
        </table>
        <app-pager [page]="page()" [totalPages]="totalPages()" [total]="total()" (pageChange)="load($event)" />
      </div>
    </section>
  `
})
export class UsersComponent {
  private readonly pharmacy = inject(PharmacyService);
  private readonly dialog = inject(DialogService);
  private readonly feedback = inject(FeedbackService);
  readonly auth = inject(AuthService);
  readonly rows = signal<UserAccount[]>([]);
  readonly roles = signal<Role[]>([]);
  readonly page = signal(0); readonly totalPages = signal(0); readonly total = signal(0);
  q = ''; role = ''; active: boolean | null = null;
  readonly activeOptions = [{ label: 'All', value: null }, { label: 'Active', value: true }, { label: 'Inactive', value: false }];
  roleOptions() { return [{ label: 'All', value: '' }, ...this.roles().map(item => ({ label: item.name, value: item.name }))]; }
  menuItems(item: UserAccount) {
    return [
      { label: 'Edit', command: () => this.edit(item) },
      { label: item.active ? 'Deactivate' : 'Activate', command: () => this.toggle(item) },
      { label: 'Reset password', command: () => this.reset(item) }
    ];
  }
  openMenu(event: Event, _item: UserAccount, menu: { toggle: (event: Event) => void }): void { menu.toggle(event); }
  constructor() {
    this.pharmacy.roles().subscribe(roles => this.roles.set(roles));
    this.load(0);
  }
  load(page: number): void {
    this.pharmacy.users({ q: this.q, role: this.role, active: this.active, page, size: 20 }).subscribe({
      next: result => { this.rows.set(result.content); this.page.set(result.page); this.totalPages.set(result.totalPages); this.total.set(result.totalElements); },
      error: error => this.feedback.error(errorMessage(error))
    });
  }
  edit(user: UserAccount | null): void {
    this.dialog.open(UserDialogComponent, { header: user ? 'Edit user' : 'Create user', width: '720px', modal: true, data: { user, roles: this.roles() } }).onClose.subscribe(body => {
      if (!body) { return; }
      const request = user
        ? this.pharmacy.updateUser(user.id, { email: body.email, fullName: body.fullName, phone: body.phone || null, roles: body.roles })
        : this.pharmacy.saveUser({ ...body, phone: body.phone || null });
      request.subscribe({
        next: () => { this.feedback.success('User saved.'); this.load(this.page()); },
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
  toggle(user: UserAccount): void {
    this.feedback.confirm(user.active ? 'Deactivate user' : 'Activate user', user.fullName).subscribe(ok => {
      if (!ok) { return; }
      this.pharmacy.activateUser(user.id, !user.active).subscribe({
        next: () => this.load(this.page()),
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
  reset(user: UserAccount): void {
    this.feedback.confirm('Reset password', 'Existing sessions for this user will be revoked. Copy the temporary password immediately.').subscribe(ok => {
      if (!ok) { return; }
      this.pharmacy.resetPassword(user.id).subscribe({
        next: result => this.feedback.success('Temporary password: ' + result.temporaryPassword),
        error: error => this.feedback.error(errorMessage(error))
      });
    });
  }
}
