import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { RepositoryService } from '../api/services';

const STORAGE_KEY = 'rs2-admin-credentials';

/**
 * Holds the HTTP-Basic credentials of the single admin user. The header is attached to every
 * request by the AuthInterceptor. Credentials live in sessionStorage (survive reloads, but not
 * closing the tab).
 *
 * Note the distinction between *having credentials* and being *verified*: `login()` stores the
 * credentials so the interceptor can authenticate, but `isAuthenticated()` only flips to true
 * once they have actually been confirmed against the backend (`verify()` / `markVerified()`).
 * This prevents the app chrome from briefly appearing on a failed/stale login.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(RepositoryService);

  private readonly _credentials = signal<string | null>(sessionStorage.getItem(STORAGE_KEY));
  private readonly _verified = signal(false);

  /** True only after the stored credentials were confirmed against the backend. Gates routes + chrome. */
  readonly isAuthenticated = computed(() => this._verified());

  /** Credentials are present (for the interceptor + the verification bootstrap) — not the same as verified. */
  get hasCredentials(): boolean {
    return this._credentials() !== null;
  }

  get basicHeader(): string | null {
    const c = this._credentials();
    return c ? `Basic ${c}` : null;
  }

  login(username: string, password: string): void {
    const encoded = btoa(`${username}:${password}`);
    sessionStorage.setItem(STORAGE_KEY, encoded);
    this._credentials.set(encoded);
    this._verified.set(false);
  }

  markVerified(): void {
    this._verified.set(true);
  }

  /**
   * Confirms the stored credentials once via a lightweight admin call. Caches the result for the
   * session. Used by the route guard (e.g. on reload with a stored session) so the dashboard is
   * never activated before the session is known to be valid.
   */
  verify(): Observable<boolean> {
    if (this._verified()) return of(true);
    if (!this.hasCredentials) return of(false);
    return this.api.registeredRepos().pipe(
      map(() => {
        this.markVerified();
        return true;
      }),
      catchError(() => {
        this.logout();
        return of(false);
      }),
    );
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this._credentials.set(null);
    this._verified.set(false);
  }
}
