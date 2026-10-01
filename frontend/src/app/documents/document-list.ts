import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { DocumentApi } from '../core/document-api';
import { Document } from '../core/models';
import { apiErrorMessage } from '../core/api-error';
import { FileSize } from '../shared/file-size';
import { StatusLabel } from '../shared/status-label';

@Component({
  selector: 'app-document-list',
  imports: [DatePipe, RouterLink, FileSize, StatusLabel],
  templateUrl: './document-list.html',
})
export class DocumentList {
  private readonly api = inject(DocumentApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly documents = signal<Document[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  constructor() {
    this.load();
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (documents) => {
          this.documents.set(
            [...documents].sort(
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
