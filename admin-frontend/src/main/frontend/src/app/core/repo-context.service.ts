import { Injectable, computed, inject, signal } from '@angular/core';
import { RepositoryService } from '../api/services';
import { RegistrationInfo } from '../api/models';

const STORAGE_KEY = 'rs2-admin-active-repo';

/**
 * The overarching scope: holds the active repoId that ALL views are scoped to. At most one
 * repoId is ever active. The list of selectable repos is loaded once (topbar selector); the
 * selection is persisted in localStorage.
 */
@Injectable({ providedIn: 'root' })
export class RepoContextService {
  private readonly api = inject(RepositoryService);

  private readonly _repos = signal<RegistrationInfo[]>([]);
  private readonly _activeRepoId = signal<string | null>(localStorage.getItem(STORAGE_KEY));
  private readonly _loaded = signal(false);

  readonly repos = this._repos.asReadonly();
  readonly activeRepoId = this._activeRepoId.asReadonly();
  readonly loaded = this._loaded.asReadonly();
  readonly hasRepos = computed(() => this._repos().length > 0);

  /** Loads the repo list and ensures a valid active repoId is set. */
  loadRepos(): void {
    this.api.registeredRepos().subscribe({
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
