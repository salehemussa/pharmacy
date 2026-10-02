import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../core/auth.service';
import { FeedbackService } from '../core/feedback.service';
import { errorMessage } from '../core/format';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h1>Profile</h1>
          <p>Update your name and contact details. An administrator assigns your roles.</p>
        </div>
      </div>
      <div class="panel profile-card">
        <h2>Account</h2>
        <form class="form-grid" [formGroup]="details" (ngSubmit)="saveDetails()">
          <label class="field"><span>Username</span><input pInputText [value]="auth.user()?.username" readonly /></label>
          <label class="field"><span>Roles</span><input pInputText [value]="roles()" readonly /></label>
          <label class="field"><span>Full name</span><input pInputText formControlName="fullName" /></label>
          <label class="field"><span>Email</span><input pInputText type="email" formControlName="email" /></label>
          <label class="field"><span>Phone</span><input pInputText formControlName="phone" /></label>
          <div class="actions span-2">
            <p-button type="submit" label="Save profile" [disabled]="details.invalid || savingDetails()" />
          </div>
        </form>
      </div>
      <div class="panel profile-card">
        <h2>Password</h2>
        <form class="form-grid" [formGroup]="password" (ngSubmit)="savePassword()">
          <label class="field"><span>Current password</span><p-password formControlName="currentPassword" [feedback]="false" [toggleMask]="true" autocomplete="current-password" [fluid]="true" /></label>
          <label class="field"><span>New password</span><p-password formControlName="newPassword" [feedback]="false" [toggleMask]="true" autocomplete="new-password" [fluid]="true" /></label>
          <label class="field"><span>Confirm new password</span><p-password formControlName="confirm" [feedback]="false" [toggleMask]="true" autocomplete="new-password" [fluid]="true" /></label>
          <div class="actions span-2">
            <p-button type="submit" label="Change password" [disabled]="password.invalid || savingPassword()" />
          </div>
        </form>
      </div>
    </section>
  `,
  styles: [`
    .profile-card { padding-bottom: 8px; }
    form { padding: 12px; }
  `]
})
export class ProfileComponent {
  readonly auth = inject(AuthService);
  private readonly feedback = inject(FeedbackService);
  readonly savingDetails = signal(false);
  readonly savingPassword = signal(false);
  readonly details = inject(FormBuilder).nonNullable.group({
    fullName: [this.auth.user()?.fullName ?? '', [Validators.required, Validators.maxLength(150)]],
    email: [this.auth.user()?.email ?? '', [Validators.required, Validators.email, Validators.maxLength(150)]],
    phone: [this.auth.user()?.phone ?? '', Validators.maxLength(30)]
  });
  readonly password = inject(FormBuilder).nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', Validators.required]
  });

  roles(): string {
    return (this.auth.user()?.roles ?? []).map(role => role.toLowerCase().replaceAll('_', ' ')).join(', ');
  }

  saveDetails(): void {
    if (this.details.invalid) { return; }
    const value = this.details.getRawValue();
    this.savingDetails.set(true);
    this.auth.updateProfile({
      fullName: value.fullName.trim(),
      email: value.email.trim(),
      phone: value.phone.trim() || null
    }).subscribe({
      next: () => {
        this.savingDetails.set(false);
        this.feedback.success('Profile saved.');
      },
      error: error => {
        this.savingDetails.set(false);
        this.feedback.error(errorMessage(error));
      }
    });
  }

  savePassword(): void {
    const value = this.password.getRawValue();
    if (value.newPassword !== value.confirm) {
      this.feedback.error('The new passwords do not match.');
      return;
    }
    this.savingPassword.set(true);
    this.auth.changePassword(value.currentPassword, value.newPassword).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.password.reset();
        this.feedback.success('Password changed.');
      },
      error: error => {
        this.savingPassword.set(false);
        this.feedback.error(errorMessage(error));
      }
    });
  }
}
