import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap, timeout } from 'rxjs';
import { API_BASE_URL, LOGIN_TIMEOUT_MS } from './api.config';

interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly accessToken = signal<string | null>(null);
  private expiresAt = 0;
  private expirationTimer?: ReturnType<typeof setTimeout>;
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  login(email: string, password: string) {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { email, password }).pipe(
      timeout(LOGIN_TIMEOUT_MS),
      tap(response => {
        this.clearSession();
        this.expiresAt = Date.now() + response.expiresIn * 1000;
        this.accessToken.set(response.accessToken);
        this.expirationTimer = setTimeout(() => this.expireSession(), response.expiresIn * 1000);
      }),
    );
  }

  getToken(): string | null {
    if (this.accessToken() && Date.now() >= this.expiresAt) {
      this.clearSession();
    }
    return this.accessToken();
  }

  logout(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  expireSession(): void {
    this.clearSession();
    void this.router.navigate(['/login'], { queryParams: { expired: 'true' } });
  }

  private clearSession(): void {
    clearTimeout(this.expirationTimer);
    this.accessToken.set(null);
    this.expiresAt = 0;
  }
}
