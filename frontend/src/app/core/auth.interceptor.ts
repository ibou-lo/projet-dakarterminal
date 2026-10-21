import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { apiBase } from './api-base';
import { AuthService } from './auth.service';

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

function withApiBase(req: HttpRequest<unknown>): HttpRequest<unknown> {
  if (!apiBase || !req.url.startsWith('/api')) {
    return req;
  }
  return req.clone({ url: apiBase + req.url });
}

/**
 * Ajoute le JWT à chaque appel /api. Sur un 401, tente une fois le
 * rafraîchissement de session, sinon renvoie vers la connexion.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  // Connexion et rafraîchissement : sans jeton. Les autres routes /api/auth (me, password, logout) en ont besoin.
  const isAuthCall = /^\/api\/(auth\/(login|refresh)|client\/login)$/.test(req.url);
  const request = withApiBase(isAuthCall ? req : withToken(req, auth.accessToken));

  return next(request).pipe(
    catchError((err: unknown) => {
      // Mot de passe provisoire : le serveur n'ouvre que « Mon compte »
      if (err instanceof HttpErrorResponse && err.status === 403 && err.error?.error === 'password_change_required') {
        auth.requirePasswordChange();
        return throwError(() => err);
      }
      if (err instanceof HttpErrorResponse && err.status === 401 && !isAuthCall && req.url !== '/api/auth/logout' && auth.isLoggedIn()) {
        return auth.refresh().pipe(
          switchMap((t) => next(withApiBase(withToken(req, t.accessToken)))),
          catchError((e) => {
            auth.logout();
            return throwError(() => e);
          }),
        );
      }
      return throwError(() => err);
    }),
  );
};
