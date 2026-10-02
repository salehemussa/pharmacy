import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { AuthService } from '../core/auth.service';
import { errorMessage } from '../core/format';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule],
  template: `
    <div class="login">
      <section class="intro">
        <img class="logo" src="logo.png" alt="Hangou Memorial Hospital" />
        <p>Medicines, shelves, prescriptions, and the counter in one place.</p>
        <ul>
          <li>Stock and expiry in one view</li>
          <li>Prescriptions through to dispensing</li>
          <li>Counter sales with a printed receipt</li>
        </ul>
      </section>
      <section class="card">
        <h2>Sign in</h2>
        <p>Use the account issued by your administrator.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label class="field"><span>Username</span><input pInputText formControlName="username" autocomplete="username" /></label>
          <label class="field"><span>Password</span><p-password formControlName="password" [feedback]="false" [toggleMask]="true" autocomplete="current-password" [fluid]="true" /></label>
          @if (error()) { <p class="err">{{ error() }}</p> }
          <p-button type="submit" [label]="busy() ? 'Signing in…' : 'Sign in'" [disabled]="form.invalid || busy()" styleClass="w-full" />
        </form>
      </section>
    </div>
  `,
  styles: [`
    .login { min-height: 100vh; display: grid; grid-template-columns: 1.1fr 1fr; }
    .intro { display: flex; flex-direction: column; justify-content: center; padding: 40px; color: #f4fbf8; background: radial-gradient(circle at 18% 18%, #1f8a6e, transparent 34%), linear-gradient(165deg, #143f37, #0c2421); }
    .logo { height: 72px; width: auto; max-width: min(360px, 100%); object-fit: contain; object-position: left center; }
    .intro p { max-width: 36ch; margin: 16px 0 0; font-size: 0.92rem; color: #d5ebe4; }
    .intro ul { margin: 18px 0 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 8px; color: #e7f6f1; font-size: 0.85rem; }
    .intro li::before { content: ""; display: inline-block; width: 6px; height: 6px; margin-right: 8px; border-radius: 50%; background: #3dcca6; vertical-align: middle; }
    .card { margin: auto; width: min(380px, 92%); padding: 22px; background: white; border: 1px solid #e4eeea; border-radius: 16px; box-shadow: 0 12px 32px rgba(16,36,32,.06); }
    h2 { margin: 0; letter-spacing: -0.03em; }
    .card > p { margin: 6px 0 16px; color: #667872; }
    form { display: flex; flex-direction: column; gap: 12px; }
    @media (max-width: 800px) { .login { grid-template-columns: 1fr; } .intro { min-height: 220px; padding: 28px; } .logo { height: 56px; } }
  `]
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) { return; }
    this.busy.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    this.auth.login(value.username.trim(), value.password).subscribe({
      next: response => void this.router.navigate([response.user.mustChangePassword ? '/change-password' : '/dashboard']),
      error: error => {
        this.error.set(errorMessage(error, 'Sign-in failed.'));
        this.busy.set(false);
      }
    });
  }
}
