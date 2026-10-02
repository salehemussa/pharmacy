import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.restore().pipe(map(user => user ? true : router.createUrlTree(['/login'])));
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.restore().pipe(map(user => {
    if (!user) {
      return true;
    }
    return router.createUrlTree([user.mustChangePassword ? '/change-password' : '/dashboard']);
  }));
};

export const passwordGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.restore().pipe(map(user => {
    if (!user) {
      return router.createUrlTree(['/login']);
    }
    return user.mustChangePassword ? router.createUrlTree(['/change-password']) : true;
  }));
};

export const changePasswordGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.restore().pipe(map(user => {
    if (!user) {
      return router.createUrlTree(['/login']);
    }
    return user.mustChangePassword ? true : router.createUrlTree(['/dashboard']);
  }));
};

export const permissionGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data['anyOf'] as string[] | undefined) ?? [];
  return auth.restore().pipe(map(user => {
    if (!user) {
      return router.createUrlTree(['/login']);
    }
    if (user.mustChangePassword) {
      return router.createUrlTree(['/change-password']);
    }
    return required.length === 0 || required.some(code => user.permissions.includes(code))
      ? true
      : router.createUrlTree(['/dashboard']);
  }));
};
