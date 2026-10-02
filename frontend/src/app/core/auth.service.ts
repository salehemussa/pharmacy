import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, map, of, shareReplay, tap, catchError } from 'rxjs';
import { AuthResponse, UserProfile } from './models';

const REFRESH_KEY = 'pharmacy.refresh';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly access = signal<string | null>(null);
  private refreshInFlight: Observable<AuthResponse> | null = null;
  readonly user = signal<UserProfile | null>(null);

  accessToken(): string | null {
    return this.access();
  }

  refreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_KEY);
  }

  has(...codes: string[]): boolean {
    const permissions = this.user()?.permissions ?? [];
    return codes.some(code => permissions.includes(code));
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/login', { username, password }).pipe(tap(response => this.store(response)));
  }

  refresh(): Observable<AuthResponse> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken: this.refreshToken() }).pipe(
        tap(response => this.store(response)),
        finalize(() => this.refreshInFlight = null),
        shareReplay(1)
      );
    }
    return this.refreshInFlight;
  }

  restore(): Observable<UserProfile | null> {
    if (this.user()) {
      return of(this.user());
    }
    if (!this.refreshToken()) {
      return of(null);
    }
    return this.refresh().pipe(map(response => response.user), catchError(() => {
      this.clear();
      return of(null);
    }));
  }

  updateProfile(body: { fullName: string; email: string; phone: string | null }): Observable<UserProfile> {
    return this.http.put<UserProfile>('/api/v1/auth/profile', body).pipe(tap(user => this.user.set(user)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/change-password', { currentPassword, newPassword }).pipe(tap(response => this.store(response)));
  }

  logout(): Observable<unknown> {
    const refreshToken = this.refreshToken();
    const finish = () => this.clear();
    if (!refreshToken) {
      finish();
      return of(null);
    }
    return this.http.post('/api/v1/auth/logout', { refreshToken }).pipe(catchError(() => of(null)), tap(finish));
  }

  clear(): void {
    this.access.set(null);
    this.user.set(null);
    sessionStorage.removeItem(REFRESH_KEY);
  }

  private store(response: AuthResponse): void {
    this.access.set(response.accessToken);
    sessionStorage.setItem(REFRESH_KEY, response.refreshToken);
    this.user.set(response.user);
  }
}
