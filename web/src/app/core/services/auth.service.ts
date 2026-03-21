import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { AuthResponse, AuthUser } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly ACCESS_KEY  = 'medoq_access';
  private readonly REFRESH_KEY = 'medoq_refresh';
  private readonly USER_KEY    = 'medoq_user';

  readonly currentUser = signal<AuthUser | null>(this._loadUser());

  constructor(private http: HttpClient, private router: Router) {}

  login(phone: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { phone, password })
      .pipe(tap(r => this._persist(r)));
  }

  logout(): void {
    this.http.post('/api/auth/logout', {}).subscribe({ error: () => {} });
    localStorage.removeItem(this.ACCESS_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  refreshToken(): Observable<AuthResponse> {
    const refresh = localStorage.getItem(this.REFRESH_KEY) ?? '';
    return this.http
      .post<AuthResponse>('/api/auth/refresh', { refreshToken: refresh })
      .pipe(tap(r => this._persist(r)));
  }

  get accessToken(): string | null {
    return localStorage.getItem(this.ACCESS_KEY);
  }

  get isLoggedIn(): boolean {
    return !!this.accessToken;
  }

  private _persist(r: AuthResponse): void {
    localStorage.setItem(this.ACCESS_KEY,  r.accessToken);
    localStorage.setItem(this.REFRESH_KEY, r.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(r.user));
    this.currentUser.set(r.user);
  }

  private _loadUser(): AuthUser | null {
    const raw = localStorage.getItem(this.USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }
}
