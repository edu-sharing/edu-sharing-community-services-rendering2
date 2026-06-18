import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'rs2-admin-credentials';

/**
 * Holds the HTTP-Basic credentials of the single admin user (see plan: auth via the existing
 * Basic-Auth admin). The header is attached to every request by the AuthInterceptor.
 * Credentials live in sessionStorage (survive reloads, but not closing the tab).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _credentials = signal<string | null>(sessionStorage.getItem(STORAGE_KEY));

  readonly isAuthenticated = computed(() => this._credentials() !== null);

  get basicHeader(): string | null {
    const c = this._credentials();
    return c ? `Basic ${c}` : null;
  }

  login(username: string, password: string): void {
    const encoded = btoa(`${username}:${password}`);
    sessionStorage.setItem(STORAGE_KEY, encoded);
    this._credentials.set(encoded);
  }

  logout(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this._credentials.set(null);
  }
}
