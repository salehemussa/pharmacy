import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { DialogService } from 'primeng/dynamicdialog';
import { Observable } from 'rxjs';
import { ConfirmDialogComponent } from '../shared/confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private readonly messages = inject(MessageService);
  private readonly dialog = inject(DialogService);

  success(message: string): void {
    this.messages.add({ severity: 'success', summary: 'Done', detail: message, life: 3500 });
  }

  error(message: string): void {
    this.messages.add({ severity: 'error', summary: 'Unable to complete', detail: message, life: 6000 });
  }

  confirm(title: string, message: string, confirmLabel = 'Confirm'): Observable<boolean | undefined> {
    return this.dialog.open(ConfirmDialogComponent, {
      header: title,
      width: '440px',
      modal: true,
      closable: true,
      data: { title, message, confirmLabel, reason: false }
    }).onClose;
  }

  reason(title: string, message: string, confirmLabel = 'Continue'): Observable<string | undefined> {
    return this.dialog.open(ConfirmDialogComponent, {
      header: title,
      width: '460px',
      modal: true,
      closable: true,
      data: { title, message, confirmLabel, reason: true }
    }).onClose;
  }
}
