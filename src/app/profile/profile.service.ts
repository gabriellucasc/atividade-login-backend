import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { timeout } from 'rxjs';
import { API_BASE_URL, LOGIN_TIMEOUT_MS } from '../auth/api.config';

export interface ProfileData { email: string; role: string; }

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  load() {
    return this.http.get<ProfileData>(`${API_BASE_URL}/api/profile`).pipe(timeout(LOGIN_TIMEOUT_MS));
  }
}
