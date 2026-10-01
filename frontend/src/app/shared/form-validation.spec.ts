import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { DocumentForm } from '../documents/document-form';
import { calendarDate } from './form-validation';
import { CalendarDate } from './calendar-date';

describe('Shared form validation', () => {
  it('accepts leap days correctly and keeps calendar dates independent of timezones', () => {
    expect(calendarDate(new FormControl('2024-02-29'))).toBeNull();
    expect(calendarDate(new FormControl('2025-02-29'))).toEqual({ date: true });
    expect(calendarDate(new FormControl('2026-13-01'))).toEqual({ date: true });
    expect(new CalendarDate().transform('2026-01-01')).toBe('01.01.2026');
  });
  it('shows document server errors inline and keeps the entered values', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DocumentForm);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({ filename: 'vertrag.pdf', fileSize: 0 });
    component.save();
    const request = http.expectOne('/api/documents');
    expect(request.request.body.fileSize).toBe(0);
    request.flush(
      { status: 400, errors: { filename: 'invalid' } },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();
    expect(component.form.controls.filename.hasError('server')).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Der Server hat diesen Wert abgelehnt');
    expect(component.form.controls.filename.value).toBe('vertrag.pdf');
    expect(component.saving()).toBe(false);
    component.form.controls.filename.setValue('vertrag-neu.pdf');
    expect(component.form.valid).toBe(true);
    http.verify();
  });
});
