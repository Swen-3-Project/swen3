import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DocumentApi } from '../core/document-api';
import { Document } from '../core/models';
import { apiErrorMessage } from '../core/api-error';
import { FileSize } from '../shared/file-size';
import { StatusLabel } from '../shared/status-label';

@Component({
  selector: 'app-document-list',
  imports: [DatePipe, RouterLink, FileSize, StatusLabel, ReactiveFormsModule],
  templateUrl: './document-list.html',
})
export class DocumentList {
  private readonly api = inject(DocumentApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly documents = signal<Document[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly query = signal('');
  readonly searchControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(200)],
  });

  constructor() {
    this.load();
  }

  search() {
    this.searchControl.markAsTouched();
    if (this.searchControl.invalid) return;
    this.query.set(this.searchControl.value.trim());
    this.load();
  }

  resetSearch() {
    this.searchControl.reset('');
    this.query.set('');
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    const query = this.query();
    const request = query ? this.api.search(query) : this.api.list();
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (documents) => {
        this.documents.set(
          query
            ? documents
            : [...documents].sort(
                (a, b) => b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id),
              ),
        );
        this.loading.set(false);
      },
      error: (error) => {
        this.error.set(apiErrorMessage(error));
        this.loading.set(false);
      },
    });
  }
}
