import { Component, effect, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { PollingService } from './core/polling.service';
import { RepoContextService } from './core/repo-context.service';
import { ContrastMode, SettingsService } from './core/settings.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
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

  constructor() {
    // Load the repo list as soon as the admin is authenticated.
    effect(() => {
      if (this.auth.isAuthenticated() && !this.repoCtx.loaded()) {
        this.repoCtx.loadRepos();
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
