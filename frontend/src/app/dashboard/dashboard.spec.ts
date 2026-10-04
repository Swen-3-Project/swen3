import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Dashboard } from './dashboard';
import { Document } from '../core/models';

describe('Dashboard', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('derives totals, status counts and recent documents from REST data', () => {
    const fixture = TestBed.createComponent(Dashboard);
    const documents: Document[] = [
      { id: 'old', filename: 'Alt.pdf', mimeType: 'application/pdf', fileSize: 100, status: 'CREATED', createdAt: '2026-01-01T12:00:00Z', title: null, description: null },
      { id: 'new', filename: 'Neu.pdf', mimeType: 'application/pdf', fileSize: 200, status: 'CREATED', createdAt: '2026-02-01T12:00:00Z', title: null, description: null },
    ];
    http.expectOne('/api/documents').flush(documents);
    fixture.detectChanges();
    expect(fixture.componentInstance.totalSize()).toBe(300);
    expect(fixture.componentInstance.statusCounts()).toEqual([{ status: 'CREATED', count: 2 }]);
    expect(fixture.componentInstance.recent()[0].id).toBe('new');
    expect(fixture.nativeElement.querySelector('[data-testid="document-count"]').textContent.trim()).toBe('2');
  });
  it('shows a retryable server error instead of displaying a false zero', () => {
    const fixture = TestBed.createComponent(Dashboard);
    http.expectOne('/api/documents').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('nicht verfügbar');
    expect(fixture.nativeElement.querySelector('[data-testid="document-count"]')).toBeNull();
    fixture.componentInstance.load();
    http.expectOne('/api/documents').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Erstes Dokument anlegen');
  });
});
