import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Subject, catchError, combineLatest, of, startWith, switchMap } from 'rxjs';
import { DocumentApi } from '../core/document-api';
import { Document } from '../core/models';
import { apiErrorMessage } from '../core/api-error';
import { FileSize } from '../shared/file-size';
import { StatusLabel } from '../shared/status-label';
import { DeleteDocumentDialog } from './delete-document-dialog';

@Component({
  selector: 'app-document-detail',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, FileSize, StatusLabel],
  templateUrl: './document-detail.html',
  styleUrl: './document-detail.css',
})
export class DocumentDetail {
  private readonly api = inject(DocumentApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly modal = inject(NgbModal);
  private deleteModal?: NgbModalRef;
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  readonly document = signal<Document | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly actionError = signal('');
  readonly notice = signal('');
  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', Validators.maxLength(255)],
    description: [''],
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.deleteModal?.dismiss());
    combineLatest([this.route.paramMap, this.reload.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) => {
          this.deleteModal?.dismiss();
          this.deleteModal = undefined;
          this.document.set(null);
          this.loading.set(true);
          this.loadError.set('');
          this.actionError.set('');
          this.notice.set('');
          this.editing.set(false);
          return this.api.get(params.get('id') ?? '').pipe(
            catchError((error) => {
              this.loadError.set(apiErrorMessage(error));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((document) => {
        this.document.set(document);
        this.loading.set(false);
      });
  }

  retry() {
    this.reload.next();
  }
  startEdit() {
    const document = this.document();
    if (!document) return;
    this.form.reset({ title: document.title ?? '', description: document.description ?? '' });
    this.actionError.set('');
    this.notice.set('');
    this.editing.set(true);
  }
  cancelEdit() {
    this.editing.set(false);
    this.actionError.set('');
  }
  save() {
    const document = this.document();
    if (!document || this.saving()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.actionError.set('');
    this.api
      .update(document.id, {
        title: value.title.trim() || null,
        description: value.description.trim() || null,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          if (this.document()?.id !== document.id) return;
          this.document.set(updated);
          this.editing.set(false);
          this.notice.set('Metadaten gespeichert.');
        },
        error: (error) => {
          this.saving.set(false);
          this.actionError.set(apiErrorMessage(error));
        },
      });
  }
  confirmDelete() {
    const document = this.document();
    if (!document || this.deleting() || this.saving() || this.deleteModal) return;
    const modal = this.modal.open(DeleteDocumentDialog, {
      centered: true,
      ariaLabelledBy: 'delete-document-title',
      ariaDescribedBy: 'delete-document-description',
    });
    modal.componentInstance.filename = document.filename;
    this.deleteModal = modal;
    modal.hidden.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.deleteModal === modal) this.deleteModal = undefined;
    });
    modal.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed: unknown) => {
      if (
        confirmed !== true ||
        this.document()?.id !== document.id ||
        this.deleting() ||
        this.saving()
      )
        return;
      this.deleting.set(true);
      this.actionError.set('');
      this.api
        .delete(document.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.deleting.set(false);
            void this.router.navigate(['/documents']);
          },
          error: (error) => {
            this.deleting.set(false);
            this.actionError.set(apiErrorMessage(error));
          },
        });
    });
  }
}
