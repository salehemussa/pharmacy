import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TextareaModule } from 'primeng/textarea';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

export interface ConfirmData {
  title: string;
  message: string;
  confirmLabel: string;
  reason: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  imports: [ButtonModule, TextareaModule, ReactiveFormsModule],
  template: `
    <p>{{ data.message }}</p>
    @if (data.reason) {
      <label class="field">
        <span>Reason</span>
        <textarea pTextarea rows="3" [formControl]="reason"></textarea>
      </label>
    }
    <div class="actions">
      <p-button label="Cancel" [text]="true" (onClick)="ref.close()" />
      <p-button [label]="data.confirmLabel" [disabled]="data.reason && reason.invalid" (onClick)="accept()" />
    </div>
  `
})
export class ConfirmDialogComponent {
  readonly data = inject(DynamicDialogConfig).data as ConfirmData;
  readonly ref = inject(DynamicDialogRef);
  readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(500)] });

  accept(): void {
    this.ref.close(this.data.reason ? this.reason.value.trim() : true);
  }
}
