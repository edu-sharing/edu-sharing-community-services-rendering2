import { Component, effect, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { RepoContextService } from './core/repo-context.service';
import { SettingsService } from './core/settings.service';

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

  constructor() {
    // Load the repo list as soon as the admin is authenticated.
    effect(() => {
      if (this.auth.isAuthenticated() && !this.repoCtx.loaded()) {
        this.repoCtx.loadRepos();
      }
    });
  }

  onRepoChange(repoId: string): void {
    this.repoCtx.setActiveRepo(repoId);
  }

  onIntervalChange(value: number): void {
    this.settings.setPollInterval(value);
  }

  logout(): void {
    this.auth.logout();
  }
}
