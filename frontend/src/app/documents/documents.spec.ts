import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject, Subject } from 'rxjs';
import { DocumentForm } from './document-form';
import { DocumentDetail } from './document-detail';
import { DocumentList } from './document-list';

describe('Document screens', () => {
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const document = {
    id: 'doc-1',
    filename: 'vertrag.pdf',
    mimeType: 'application/pdf',
    fileSize: 2048,
    status: 'CREATED',
    createdAt: '2026-01-01T12:00:00Z',
    title: 'Alt',
    description: 'Beschreibung bleibt',
  };
  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({ id: 'doc-1' }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function confirmation() {
    return {
      componentInstance: { filename: '' },
      closed: new Subject<boolean>(),
      hidden: new Subject<void>(),
      dismiss: vi.fn(),
    };
  }

  it('opens one confirmation and dismisses it when the screen is destroyed', () => {
    const fixture = TestBed.createComponent(DocumentDetail);
    http.expectOne('/api/documents/doc-1').flush(document);
    const modal = confirmation();
    const open = vi
      .spyOn(TestBed.inject(NgbModal), 'open')
      .mockReturnValue(modal as unknown as NgbModalRef);
    fixture.componentInstance.confirmDelete();
    fixture.componentInstance.confirmDelete();
    expect(open).toHaveBeenCalledTimes(1);
    expect(modal.componentInstance.filename).toBe(document.filename);
    fixture.destroy();
    expect(modal.dismiss).toHaveBeenCalledTimes(1);
    modal.closed.next(true);
    http.expectNone((request) => request.method === 'DELETE');
  });

  it('deletes the document only after explicit confirmation', () => {
    const fixture = TestBed.createComponent(DocumentDetail);
    http.expectOne('/api/documents/doc-1').flush(document);
    const cancelled = confirmation();
    const accepted = confirmation();
    vi.spyOn(TestBed.inject(NgbModal), 'open')
      .mockReturnValueOnce(cancelled as unknown as NgbModalRef)
      .mockReturnValueOnce(accepted as unknown as NgbModalRef);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.confirmDelete();
    cancelled.closed.next(false);
    http.expectNone((request) => request.method === 'DELETE');
    cancelled.hidden.next();
    fixture.componentInstance.confirmDelete();
    accepted.closed.next(true);
    const request = http.expectOne('/api/documents/doc-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    expect(navigate).toHaveBeenCalledWith(['/documents']);
  });

  it('dismisses a stale confirmation when the route changes', () => {
    const fixture = TestBed.createComponent(DocumentDetail);
    http.expectOne('/api/documents/doc-1').flush(document);
    const modal = confirmation();
    vi.spyOn(TestBed.inject(NgbModal), 'open').mockReturnValue(modal as unknown as NgbModalRef);
    fixture.componentInstance.confirmDelete();
    params.next(convertToParamMap({ id: 'doc-2' }));
    expect(modal.dismiss).toHaveBeenCalledTimes(1);
    http.expectOne('/api/documents/doc-2').flush({ ...document, id: 'doc-2' });
    modal.closed.next(true);
    http.expectNone((request) => request.method === 'DELETE');
  });

  it('blocks missing, whitespace, negative and fractional create values before HTTP', () => {
    const fixture = TestBed.createComponent(DocumentForm);
    fixture.componentInstance.save();
    fixture.componentInstance.form.patchValue({ filename: '   ', fileSize: 1 });
    fixture.componentInstance.save();
    fixture.componentInstance.form.patchValue({ filename: 'vertrag.pdf', fileSize: -1 });
    fixture.componentInstance.save();
    fixture.componentInstance.form.patchValue({ fileSize: 1.5 });
    fixture.componentInstance.save();
    http.expectNone('/api/documents');
    expect(fixture.componentInstance.form.controls.fileSize.hasError('integer')).toBe(true);
  });
  it('preserves description when changing only the title', () => {
    const fixture = TestBed.createComponent(DocumentDetail);
    http.expectOne('/api/documents/doc-1').flush(document);
    fixture.componentInstance.startEdit();
    fixture.componentInstance.form.controls.title.setValue('Neu');
    fixture.componentInstance.save();
    const request = http.expectOne('/api/documents/doc-1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ title: 'Neu', description: 'Beschreibung bleibt' });
    request.flush({ ...document, title: 'Neu' });
    expect(fixture.componentInstance.document()?.title).toBe('Neu');
    expect(fixture.componentInstance.editing()).toBe(false);
  });
  it('shows a useful missing-document message', () => {
    const fixture = TestBed.createComponent(DocumentDetail);
    http.expectOne('/api/documents/doc-1').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'nicht gefunden',
    );
    expect(fixture.componentInstance.document()).toBeNull();
  });
  it('renders every required list field and links to the detail screen', () => {
    const fixture = TestBed.createComponent(DocumentList);
    http.expectOne('/api/documents').flush([document]);
    fixture.detectChanges();
    const table = fixture.nativeElement.querySelector('table');
    expect(table.textContent).toContain('vertrag.pdf');
    expect(table.textContent).toContain('Angelegt');
    expect(table.textContent).toContain('2 KiB');
    expect(table.textContent).toContain('01.01.2026');
    expect(table.querySelector('a').getAttribute('href')).toBe('/documents/doc-1');
  });
});
