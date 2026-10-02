import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const skip = request.url.includes('/auth/login') || request.url.includes('/auth/refresh');
  const token = auth.accessToken();
  const outgoing = !skip && token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(outgoing).pipe(catchError((error: HttpErrorResponse) => {
    const body = error.error as { code?: string } | null;
    if (error.status === 403 && body?.code === 'PASSWORD_CHANGE_REQUIRED') {
      void router.navigate(['/change-password']);
    }
    if (error.status !== 401 || skip || !auth.refreshToken() || request.headers.has('X-Retry')) {
      if (error.status === 401 && !skip) {
        auth.clear();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }
    return auth.refresh().pipe(
      switchMap(response => next(request.clone({ setHeaders: { Authorization: `Bearer ${response.accessToken}`, 'X-Retry': '1' } }))),
      catchError(refreshError => {
        auth.clear();
        void router.navigate(['/login']);
        return throwError(() => refreshError);
      })
    );
  }));
};
