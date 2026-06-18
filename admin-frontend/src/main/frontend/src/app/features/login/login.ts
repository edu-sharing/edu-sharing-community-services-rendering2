import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AdminApiService } from '../../core/admin-api.service';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly api = inject(AdminApiService);
  private readonly router = inject(Router);

  protected username = 'admin';
  protected password = '';
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  submit(): void {
    this.error.set(null);
    this.loading.set(true);
    this.auth.login(this.username, this.password);
    this.api.listRepos().subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.auth.logout();
        this.error.set(
          err?.status === 401
            ? 'Anmeldung fehlgeschlagen – Passwort prüfen.'
            : 'Verbindung zum Service fehlgeschlagen.',
        );
      },
    });
  }
}