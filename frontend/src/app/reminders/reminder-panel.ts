import { DatePipe } from '@angular/common';
import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroupDirective, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ReminderApi } from '../core/reminder-api';
import { Reminder, ReminderHistory } from '../core/models';
import { apiErrorMessage } from '../core/api-error';
import { applyServerErrors, calendarDate, fieldError, notBlank } from '../shared/form-validation';
import { StatusLabel } from '../shared/status-label';
import { CalendarDate } from '../shared/calendar-date';

@Component({
  selector: 'app-reminder-panel',
  imports: [DatePipe, ReactiveFormsModule, StatusLabel, CalendarDate],
  templateUrl: './reminder-panel.html',
  styleUrl: './reminder-panel.css',
})
export class ReminderPanel {
  private readonly api = inject(ReminderApi);
  private readonly destroyRef = inject(DestroyRef);
  private historyRequest?: Subscription;
  readonly documentId = input.required<string>();
  readonly reminders = signal<Reminder[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly actionError = signal('');
  readonly creating = signal(false);
  readonly completing = signal<string | null>(null);
  readonly notice = signal('');
  readonly openHistory = signal<string | null>(null);
  readonly history = signal<ReminderHistory[]>([]);
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  readonly fieldError = fieldError;
  readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, notBlank, Validators.maxLength(255)]],
    description: [''],
    dueDate: ['', [Validators.required, calendarDate]],
  });

  constructor() {
    effect(() => this.load());
  }
  load() {
    this.loading.set(true);
    this.loadError.set('');
    this.api
      .list(this.documentId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reminders) => {
          this.reminders.set(reminders);
          this.loading.set(false);
        },
        error: (error) => {
          this.loadError.set(apiErrorMessage(error));
          this.loading.set(false);
        },
      });
  }
  create(directive?: FormGroupDirective) {
    if (this.creating()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    this.creating.set(true);
    this.actionError.set('');
    this.notice.set('');
    this.api
      .create(this.documentId(), {
        title: value.title.trim(),
        description: value.description.trim() || null,
        dueDate: value.dueDate,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reminder) => {
          this.reminders.update((reminders) => [...reminders, reminder]);
          if (directive) directive.resetForm();
          else this.form.reset();
          this.creating.set(false);
          this.notice.set('Erinnerung angelegt.');
        },
        error: (error) => {
          this.creating.set(false);
          this.actionError.set(apiErrorMessage(error));
          applyServerErrors(this.form, error);
        },
      });
  }
  complete(reminder: Reminder) {
    if (this.completing() || reminder.status === 'COMPLETED') return;
    this.completing.set(reminder.id);
    this.actionError.set('');
    this.notice.set('');
    this.api
      .complete(this.documentId(), reminder.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (completed) => {
          this.reminders.update((reminders) =>
            reminders.map((item) => (item.id === completed.id ? completed : item)),
          );
          this.completing.set(null);
          this.notice.set('Erinnerung erledigt.');
          if (this.openHistory() === reminder.id) this.loadHistory(reminder.id);
        },
        error: (error) => {
          this.completing.set(null);
          this.actionError.set(apiErrorMessage(error));
        },
      });
  }
  toggleHistory(id: string) {
    this.historyRequest?.unsubscribe();
    if (this.openHistory() === id) {
      this.openHistory.set(null);
      return;
    }
    this.openHistory.set(id);
    this.loadHistory(id);
  }
  loadHistory(id: string) {
    this.historyRequest?.unsubscribe();
    this.history.set([]);
    this.historyLoading.set(true);
    this.historyError.set('');
    this.historyRequest = this.api
      .history(this.documentId(), id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (events) => {
          this.history.set(events);
          this.historyLoading.set(false);
        },
        error: (error) => {
          this.historyError.set(apiErrorMessage(error));
          this.historyLoading.set(false);
        },
      });
  }
}
