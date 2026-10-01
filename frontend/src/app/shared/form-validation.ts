import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ApiProblem } from '../core/models';

export const notBlank: ValidatorFn = (control) =>
  String(control.value ?? '').trim() ? null : { blank: true };
export const safeInteger: ValidatorFn = (control) =>
  control.value === null || Number.isSafeInteger(control.value) ? null : { integer: true };
export const calendarDate: ValidatorFn = (control) => {
  if (!control.value) return null;
  const value = String(control.value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return { date: true };
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
    ? null
    : { date: true };
};

export function applyServerErrors(form: FormGroup, error: unknown): void {
  if (!(error instanceof HttpErrorResponse) || error.status !== 400) return;
  const problem = error.error as ApiProblem | null;
  if (!problem?.errors || typeof problem.errors !== 'object') return;
  for (const [field, message] of Object.entries(problem.errors)) {
    const control = form.get(field);
    if (control && typeof message === 'string') {
      control.setErrors({
        ...control.errors,
        server: 'Der Server hat diesen Wert abgelehnt. Bitte prüfen Sie die Angabe.',
      });
      control.markAsTouched();
    }
  }
}

export function fieldError(control: AbstractControl): string {
  const errors: ValidationErrors | null = control.errors;
  if (!errors) return '';
  if (errors['server']) return String(errors['server']);
  if (errors['required'] || errors['blank'] || errors['pattern'])
    return 'Bitte dieses Feld ausfüllen.';
  if (errors['maxlength'])
    return `Bitte höchstens ${errors['maxlength'].requiredLength} Zeichen eingeben.`;
  if (errors['min'] || errors['integer']) return 'Bitte eine ganze Zahl ab 0 angeben.';
  if (errors['date']) return 'Bitte ein gültiges Datum angeben.';
  return 'Bitte die Angabe prüfen.';
}
