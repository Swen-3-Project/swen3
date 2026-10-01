import { Routes } from '@angular/router';
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'Übersicht · Paperless',
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'documents',
    title: 'Dokumente · Paperless',
    loadComponent: () => import('./documents/document-list').then((m) => m.DocumentList),
  },
  {
    path: 'documents/new',
    title: 'Dokument anlegen · Paperless',
    loadComponent: () => import('./documents/document-form').then((m) => m.DocumentForm),
  },
  {
    path: 'documents/:id',
    title: 'Dokumentdetails · Paperless',
    loadComponent: () => import('./documents/document-detail').then((m) => m.DocumentDetail),
  },
  {
    path: '**',
    title: 'Seite nicht gefunden · Paperless',
    loadComponent: () => import('./shared/not-found').then((m) => m.NotFound),
  },
];
