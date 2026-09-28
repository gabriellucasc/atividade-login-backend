import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ProfileData, ProfileService } from './profile.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.html',
  styleUrl: '../login/login.css',
})
export class Profile {
  readonly auth = inject(AuthService);
  private readonly service = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  readonly profile = signal<ProfileData | null>(null);
  readonly error = signal('');
  readonly isLoading = signal(false);

  constructor() { this.load(); }

  load(): void {
    if (this.isLoading()) return;
    this.error.set('');
    this.isLoading.set(true);
    this.service.load().pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.isLoading.set(false)),
    ).subscribe({
      next: data => this.profile.set(data),
      error: () => this.error.set('Não foi possível carregar seu perfil. Tente novamente.'),
    });
  }
}
