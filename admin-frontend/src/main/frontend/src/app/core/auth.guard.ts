import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Allows activation only for a verified session. If credentials are present but not yet verified
 * (e.g. after a reload), the guard awaits the async verification before activating — so the
 * protected route never renders before the session is known to be valid (no dashboard flash).
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) return true;
  if (!auth.hasCredentials) return router.parseUrl('/login');
  return auth.verify().pipe(map((ok) => (ok ? true : router.parseUrl('/login'))));
};
