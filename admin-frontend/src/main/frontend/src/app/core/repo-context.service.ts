import { Injectable, computed, inject, signal } from '@angular/core';
import { AdminApiService } from './admin-api.service';
import { RegistrationInfo } from './models';

const STORAGE_KEY = 'rs2-admin-active-repo';

/**
 * Die "große Klammer": hält die aktive repoId, auf die ALLE Ansichten gescopt sind.
 * Es ist immer höchstens eine repoId aktiv. Die Liste der wählbaren Repos wird einmalig
 * geladen (Topbar-Selektor); die Auswahl wird in localStorage persistiert.
 */
@Injectable({ providedIn: 'root' })
export class RepoContextService {
  private readonly api = inject(AdminApiService);

  private readonly _repos = signal<RegistrationInfo[]>([]);
  private readonly _activeRepoId = signal<string | null>(localStorage.getItem(STORAGE_KEY));
  private readonly _loaded = signal(false);

  readonly repos = this._repos.asReadonly();
  readonly activeRepoId = this._activeRepoId.asReadonly();
  readonly loaded = this._loaded.asReadonly();
  readonly hasRepos = computed(() => this._repos().length > 0);

  /** Lädt die Repo-Liste und stellt sicher, dass eine gültige aktive repoId gesetzt ist. */
  loadRepos(): void {
    this.api.listRepos().subscribe({
      next: (repos) => {
        this._repos.set(repos);
        const current = this._activeRepoId();
        const stillValid = current && repos.some((r) => r.repoId === current);
        if (!stillValid) {
          this.setActiveRepo(repos.length > 0 ? repos[0].repoId : null);
        }
        this._loaded.set(true);
      },
      error: () => this._loaded.set(true),
    });
  }

  setActiveRepo(repoId: string | null): void {
    this._activeRepoId.set(repoId);
    if (repoId) {
      localStorage.setItem(STORAGE_KEY, repoId);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }
}
