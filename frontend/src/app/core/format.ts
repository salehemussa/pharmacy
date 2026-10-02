import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

export function errorMessage(error: unknown, fallback = 'The request could not be completed.'): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ApiError | undefined;
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0];
      if (first) {
        return first;
      }
    }
    if (body?.message) {
      return body.message;
    }
    if (error.status === 0) {
      return 'The pharmacy service is not reachable.';
    }
  }
  return fallback;
}

export function money(value: number | string | null | undefined, currency = 'USD'): string {
  const amount = Number(value ?? 0);
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
  } catch {
    return amount.toFixed(2);
  }
}

export function when(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function statusLabel(status: string | null | undefined): string {
  const text = (status ?? '').toLowerCase().replaceAll('_', ' ');
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : '';
}

export function statusClass(status: string | null | undefined): string {
  const value = (status ?? '').toUpperCase();
  if (['CANCELLED', 'REJECTED', 'EXPIRED', 'OUT'].includes(value) || value.includes('EXPIRED')) {
    return 'bad';
  }
  if (['PENDING', 'PARTIAL', 'PARTIALLY_DISPENSED', 'REVIEWED', 'UNPAID', 'DRAFT'].includes(value)) {
    return 'warn';
  }
  if (['COMPLETED', 'CONFIRMED', 'APPROVED', 'FULLY_DISPENSED', 'PAID', 'ACTIVE'].includes(value)) {
    return 'ok';
  }
  return '';
}
