import { Routes } from '@angular/router';
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', title: 'Übersicht · Paperless', loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard) },
  { path: '**', title: 'Seite nicht gefunden · Paperless', loadComponent: () => import('./shared/not-found').then(m => m.NotFound) },
];
