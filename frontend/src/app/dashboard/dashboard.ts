import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { DocumentApi } from '../core/document-api';
import { apiErrorMessage } from '../core/api-error';
import { Document } from '../core/models';
import { StatusLabel } from '../shared/status-label';
import { FileSize } from '../shared/file-size';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, RouterLink, StatusLabel, FileSize],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly api = inject(DocumentApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly documents = signal<Document[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly totalSize = computed(() =>
    this.documents().reduce((total, document) => total + document.fileSize, 0),
  );
  readonly statusCounts = computed(() => {
    const counts = new Map<string, number>();
    for (const document of this.documents())
      counts.set(document.status, (counts.get(document.status) ?? 0) + 1);
    return Array.from(counts, ([status, count]) => ({ status, count }));
  });
  readonly recent = computed(() =>
    [...this.documents()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || a.id.localeCompare(b.id))
      .slice(0, 5),
  );

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
          this.documents.set(documents);
          this.loading.set(false);
        },
        error: (error) => {
          this.error.set(apiErrorMessage(error));
          this.loading.set(false);
        },
      });
  }
}
