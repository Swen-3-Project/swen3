import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Reminder } from '../core/models';
import { ReminderPanel } from './reminder-panel';

describe('Reminder panel', () => {
  let http: HttpTestingController;
  const reminder: Reminder = {
    id: 'reminder-1',
    documentId: 'doc-1',
    title: 'Prüfen',
    description: null,
    dueDate: '2000-02-01',
    status: 'OPEN',
    createdAt: '2026-01-01T12:00:00Z',
    completedAt: null,
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function render(reminders: Reminder[] = []) {
    const fixture = TestBed.createComponent(ReminderPanel);
    fixture.componentRef.setInput('documentId', 'doc-1');
    fixture.detectChanges();
    http.expectOne('/api/documents/doc-1/reminders').flush(reminders);
    fixture.detectChanges();
    return fixture;
  }
  it('blocks missing, whitespace and impossible-date reminder values before HTTP', () => {
    const fixture = render();
    fixture.componentInstance.create();
    fixture.componentInstance.form.patchValue({ title: '   ', dueDate: '2026-01-01' });
    fixture.componentInstance.create();
    fixture.componentInstance.form.patchValue({ title: 'Prüfen', dueDate: '2026-02-30' });
    fixture.componentInstance.create();
    http.expectNone((request) => request.method === 'POST');
    expect(fixture.componentInstance.form.controls.dueDate.hasError('date')).toBe(true);
  });
  it('allows past due dates and sends only one request while creation is pending', () => {
    const fixture = render();
    fixture.componentInstance.form.patchValue({ title: ' Prüfen ', dueDate: '2000-02-01' });
    fixture.componentInstance.create();
    fixture.componentInstance.create();
    const request = http.expectOne('/api/documents/doc-1/reminders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      title: 'Prüfen',
      description: null,
      dueDate: '2000-02-01',
    });
    request.flush(reminder);
    fixture.detectChanges();
    expect(fixture.componentInstance.reminders()).toEqual([reminder]);
    expect(fixture.nativeElement.textContent).toContain('01.02.2000');
    expect(fixture.nativeElement.textContent).toContain('Offen');
  });
  it('completes once and reloads an open history with the persisted completion event', () => {
    const fixture = render([reminder]);
    const component = fixture.componentInstance;
    const created = { id: 'event-1', event: 'CREATED', occurredAt: reminder.createdAt };
    component.toggleHistory(reminder.id);
    http.expectOne('/api/documents/doc-1/reminders/reminder-1/history').flush([created]);
    component.complete(reminder);
    component.complete(reminder);
    const request = http.expectOne('/api/documents/doc-1/reminders/reminder-1/complete');
    expect(request.request.method).toBe('PATCH');
    request.flush({ ...reminder, status: 'COMPLETED', completedAt: '2026-01-02T12:00:00Z' });
    http
      .expectOne('/api/documents/doc-1/reminders/reminder-1/history')
      .flush([created, { id: 'event-2', event: 'COMPLETED', occurredAt: '2026-01-02T12:00:00Z' }]);
    expect(component.history().length).toBe(2);
    expect(component.reminders()[0].status).toBe('COMPLETED');
    component.complete(component.reminders()[0]);
    http.expectNone('/api/documents/doc-1/reminders/reminder-1/complete');
  });
  it('attaches server field errors and clears them when the value is corrected', () => {
    const fixture = render();
    const component = fixture.componentInstance;
    component.form.patchValue({ title: 'Prüfen', dueDate: '2026-01-01' });
    component.create();
    http
      .expectOne('/api/documents/doc-1/reminders')
      .flush(
        { status: 400, errors: { dueDate: 'must not be null' } },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(component.form.controls.dueDate.hasError('server')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Der Server hat diesen Wert abgelehnt');
    component.form.controls.dueDate.setValue('2026-01-02');
    expect(component.form.valid).toBe(true);
  });
});
