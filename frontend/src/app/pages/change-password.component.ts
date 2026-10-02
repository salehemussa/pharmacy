import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/format';

@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule, ButtonModule, PasswordModule],
  template: `
    <div class="gate">
      <section class="card">
        <h1>Choose a new password</h1>
        <p>Use at least 8 characters, with a letter and a digit. You can continue once this is saved.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label class="field"><span>Current password</span><p-password formControlName="currentPassword" [feedback]="false" [toggleMask]="true" autocomplete="current-password" [fluid]="true" /></label>
          <label class="field"><span>New password</span><p-password formControlName="newPassword" [feedback]="false" [toggleMask]="true" autocomplete="new-password" [fluid]="true" /></label>
          <label class="field"><span>Confirm new password</span><p-password formControlName="confirm" [feedback]="false" [toggleMask]="true" autocomplete="new-password" [fluid]="true" /></label>
          @if (error()) { <p class="err">{{ error() }}</p> }
          <p-button type="submit" [label]="busy() ? 'Saving…' : 'Save password'" [disabled]="form.invalid || busy()" styleClass="w-full" />
        </form>
      </section>
    </div>
  `,
  styles: [`
    .gate { min-height: 100vh; display: grid; place-items: center; background: radial-gradient(circle at top, #e7f5f0, #f4f7f6 42%); }
    .card { width: min(400px, 92vw); padding: 20px; background: white; border-radius: 16px; box-shadow: 0 16px 40px rgba(16,36,32,.08); }
    h1 { margin: 0; letter-spacing: -0.03em; }
    .card > p { margin: 8px 0 16px; color: #667872; }
    form { display: flex; flex-direction: column; gap: 12px; }
  `]
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', Validators.required]
  });

  submit(): void {
    const value = this.form.getRawValue();
    if (value.newPassword !== value.confirm) {
      this.error.set('The new passwords do not match.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.auth.changePassword(value.currentPassword, value.newPassword).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: error => {
        this.error.set(errorMessage(error));
        this.busy.set(false);
      }
    });
  }
}
