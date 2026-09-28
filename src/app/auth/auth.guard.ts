import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// Apenas navegação/UX. A autorização real é executada no Spring Security.
export const authGuard: CanActivateFn = () =>
  inject(AuthService).getToken() !== null || inject(Router).createUrlTree(['/login']);
