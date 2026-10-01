import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-not-found',
  imports: [RouterLink],
  template:
    '<section class="card card-body shadow-sm mb-4 text-center text-body-secondary py-4"><h1>Seite nicht gefunden</h1><p>Diese Adresse ist nicht verfügbar.</p><a class="btn btn-primary"  routerLink="/dashboard">Zur Übersicht</a></section>',
})
export class NotFound {}
