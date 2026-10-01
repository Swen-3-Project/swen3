import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DocumentApi } from '../core/document-api';
import { apiErrorMessage } from '../core/api-error';

@Component({
  selector: 'app-document-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './document-form.html',
})
export class DocumentForm {
  private readonly api = inject(DocumentApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly form = inject(FormBuilder).nonNullable.group({
    filename: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    mimeType: [
      'application/pdf',
      [Validators.required, Validators.pattern(/\S/), Validators.maxLength(127)],
    ],
    fileSize: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0),
      (control) =>
        control.value === null || Number.isSafeInteger(control.value) ? null : { integer: true },
    ]),
    title: ['', Validators.maxLength(255)],
    description: [''],
  });

  save() {
    if (this.saving()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.error.set('');
    this.api
      .create({
        filename: value.filename.trim(),
        mimeType: value.mimeType.trim(),
        fileSize: value.fileSize!,
        title: value.title.trim() || null,
        description: value.description.trim() || null,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (document) => {
          this.saving.set(false);
          void this.router.navigate(['/documents', document.id]);
        },
        error: (error) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(error));
        },
      });
  }
}
