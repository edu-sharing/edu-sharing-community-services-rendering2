import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { VersionResponse } from './api/models';
import { SystemService } from './api/services';
import { AuthService } from './core/auth.service';
import { PollingService } from './core/polling.service';
import { RepoContextService } from './core/repo-context.service';
import { ContrastMode, SettingsService } from './core/settings.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, DatePipe,
    MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule, MatToolbarModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly repoCtx = inject(RepoContextService);
  protected readonly settings = inject(SettingsService);
  private readonly poll = inject(PollingService);
  private readonly system = inject(SystemService);

  /** Build info of the rendering service, shown at the bottom of the side navigation. */
  protected readonly version = signal<VersionResponse | null>(null);

  constructor() {
    // Load the repo list as soon as the admin is authenticated.
    effect(() => {
      if (this.auth.isAuthenticated() && !this.repoCtx.loaded()) {
        this.repoCtx.loadRepos();
      }
    });

    // Load the version once per login; it does not change while the service runs.
    effect(() => {
      if (!this.auth.isAuthenticated()) {
        this.version.set(null);
      } else if (!this.version()) {
        this.system.getVersion().subscribe({
          next: (v) => this.version.set(v),
          // not essential: the navigation simply shows no version
          error: () => this.version.set(null),
        });
      }
    });

    // Apply the high-contrast (WCAG-AA) theme overrides on <html> when active. The semantic /
    // keyboard a11y fixes are always on; only the color overrides are gated behind this class.
    effect(() => {
      document.documentElement.classList.toggle('contrast-high', this.settings.highContrastActive());
    });
  }

  onContrastChange(mode: ContrastMode): void {
    this.settings.setContrastMode(mode);
  }

  onRepoChange(repoId: string): void {
    this.repoCtx.setActiveRepo(repoId);
  }

  onIntervalChange(value: number): void {
    this.settings.setPollInterval(value);
  }

  onRefresh(): void {
    this.poll.refresh();
  }

  logout(): void {
    this.auth.logout();
  }
}
