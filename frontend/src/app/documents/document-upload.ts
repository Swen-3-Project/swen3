import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DocumentApi } from '../core/document-api';
import { apiErrorMessage } from '../core/api-error';
import { applyServerErrors, fieldError } from '../shared/form-validation';

@Component({
  selector: 'app-document-upload',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './document-upload.html',
})
export class DocumentUpload {
  private readonly api = inject(DocumentApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly file = signal<File | null>(null);
  readonly fileError = signal('');
  readonly saving = signal(false);
  readonly error = signal('');
  readonly fieldError = fieldError;
  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', Validators.maxLength(255)],
    description: [''],
  });

  selectFile(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.file.set(null);
    this.fileError.set('');
    if (!file) return;
    if (!file.size) this.fileError.set('Die Datei ist leer.');
    else if (file.size > 20 * 1024 * 1024) this.fileError.set('Die PDF darf höchstens 20 MiB groß sein.');
    else if (!file.name.toLowerCase().endsWith('.pdf')) this.fileError.set('Bitte eine PDF-Datei auswählen.');
    else this.file.set(file);
  }

  save() {
    if (this.saving()) return;
    this.form.markAllAsTouched();
    const file = this.file();
    if (!file) {
      if (!this.fileError()) this.fileError.set('Bitte eine PDF-Datei auswählen.');
      return;
    }
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.error.set('');
    this.api.upload(file, value.title.trim(), value.description.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (document) => {
          this.saving.set(false);
          void this.router.navigate(['/documents', document.id]);
        },
        error: (error) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(error));
          applyServerErrors(this.form, error);
        },
      });
  }
}
