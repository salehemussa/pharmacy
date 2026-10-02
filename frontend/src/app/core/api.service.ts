import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

type Query = Record<string, string | number | boolean | null | undefined>;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  get<T>(path: string, query?: Query): Observable<T> {
    return this.http.get<T>(`/api/v1${path}`, { params: this.params(query) });
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(`/api/v1${path}`, body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(`/api/v1${path}`, body);
  }

  blob(path: string, query?: Query): Observable<Blob> {
    return this.http.get(`/api/v1${path}`, { params: this.params(query), responseType: 'blob' });
  }

  private params(query?: Query): HttpParams {
    let params = new HttpParams();
    if (!query) {
      return params;
    }
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }
}
