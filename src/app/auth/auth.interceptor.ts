import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  // Comparar origem e caminho evita enviar o token a outros servidores.
  const url = new URL(request.url, window.location.origin);
  const api = new URL(API_BASE_URL);
  const isProtectedApi = url.origin === api.origin && url.pathname.startsWith('/api/');
  if (!isProtectedApi) return next(request);

  const auth = inject(AuthService);
  const token = auth.getToken();
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401
          && auth.getToken() === token) {
        auth.expireSession();
      }
      return throwError(() => error);
    }),
  );
};
