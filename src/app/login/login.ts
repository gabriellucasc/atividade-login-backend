import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, TimeoutError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

@Component({
  imports: [FormsModule],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  email = '';
  senha = '';
  readonly isLoading = signal(false);
  readonly error = signal('');
  readonly sessionExpired = this.route.snapshot.queryParamMap.get('expired') === 'true';

  onEntrar(form: NgForm): void {
    if (this.isLoading()) return;
    this.error.set('');
    if (form.invalid || !this.email.trim() || !this.senha.trim()) {
      form.control.markAllAsTouched();
      this.error.set('Informe um e-mail válido e sua senha.');
      return;
    }
    if (new TextEncoder().encode(this.senha).length > 72) {
      this.error.set('A senha deve ter no máximo 72 bytes (acentos podem ocupar mais de um byte).');
      return;
    }

    this.isLoading.set(true);
    this.auth.login(this.email.trim(), this.senha).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.isLoading.set(false)),
    ).subscribe({
      next: () => {
        this.senha = '';
        void this.router.navigate(['/perfil']);
      },
      error: (error: unknown) => {
        this.senha = '';
        if (error instanceof TimeoutError) {
          this.error.set('O servidor demorou para responder. Tente novamente.');
        } else if (error instanceof HttpErrorResponse && error.status === 401) {
          this.error.set('Email ou senha inválidos.');
        } else if (error instanceof HttpErrorResponse && error.status === 429) {
          this.error.set('Muitas tentativas. Aguarde um minuto e tente novamente.');
        } else if (error instanceof HttpErrorResponse && error.status === 400) {
          this.error.set('Confira o e-mail e a senha informados.');
        } else {
          this.error.set('Não foi possível conectar ao serviço. Tente novamente.');
        }
      },
    });
  }
}
