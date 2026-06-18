import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'assets',
    canActivate: [authGuard],
    loadComponent: () => import('./features/assets/assets').then((m) => m.Assets),
  },
  {
    path: 'repo',
    canActivate: [authGuard],
    loadComponent: () => import('./features/repo/repo').then((m) => m.Repo),
  },
  {
    path: 'jobs',
    canActivate: [authGuard],
    loadComponent: () => import('./features/jobs/jobs').then((m) => m.Jobs),
  },
  { path: '**', redirectTo: 'dashboard' },
];
