import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateReminder, Reminder, ReminderHistory } from './models';
@Injectable({ providedIn: 'root' })
export class ReminderApi {
  private readonly http = inject(HttpClient);
  private url(documentId: string) { return `/api/documents/${documentId}/reminders`; }
  list(documentId: string) { return this.http.get<Reminder[]>(this.url(documentId)); }
  create(documentId: string, request: CreateReminder) { return this.http.post<Reminder>(this.url(documentId), request); }
  complete(documentId: string, reminderId: string) { return this.http.patch<Reminder>(`${this.url(documentId)}/${reminderId}/complete`, {}); }
  history(documentId: string, reminderId: string) { return this.http.get<ReminderHistory[]>(`${this.url(documentId)}/${reminderId}/history`); }
}
