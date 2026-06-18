import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'rs2-admin-credentials';

/**
 * Hält die HTTP-Basic-Credentials des einzelnen Admin-Users (siehe Plan: Auth über den
 * bestehenden Basic-Auth-Admin). Der Header wird vom AuthInterceptor an jede Anfrage gehängt.
 * Credentials liegen in sessionStorage (überleben Reloads, nicht aber das Schließen des Tabs).
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
