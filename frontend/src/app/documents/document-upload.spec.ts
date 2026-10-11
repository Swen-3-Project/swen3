import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { DocumentUpload } from './document-upload';

describe('PDF upload', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function select(component: DocumentUpload, file: File) {
    component.selectFile({ target: { files: [file] } } as unknown as Event);
  }

  it('blocks missing, empty, oversized and non-PDF files', () => {
    const component = TestBed.createComponent(DocumentUpload).componentInstance;
    component.save();
    expect(component.fileError()).toContain('auswählen');
    select(component, new File([], 'empty.pdf'));
    expect(component.fileError()).toContain('leer');
    select(component, new File(['text'], 'notes.txt'));
    expect(component.fileError()).toContain('PDF');
    select(component, new File([new Uint8Array(20 * 1024 * 1024 + 1)], 'big.pdf'));
    expect(component.fileError()).toContain('20 MiB');
    component.save();
    http.expectNone('/api/documents/upload');
  });

  it('sends the actual file as multipart and navigates after creation', () => {
    const component = TestBed.createComponent(DocumentUpload).componentInstance;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const file = new File(['%PDF-1.7'], 'invoice.pdf', { type: 'application/pdf' });
    select(component, file);
    component.form.patchValue({ title: ' Invoice ', description: ' Details ' });
    component.save();
    component.save();
    const req = http.expectOne('/api/documents/upload');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.get('file').name).toBe('invoice.pdf');
    expect(req.request.body.get('title')).toBe('Invoice');
    expect(req.request.headers.has('Content-Type')).toBe(false);
    req.flush({ id: 'uploaded' });
    expect(navigate).toHaveBeenCalledWith(['/documents', 'uploaded']);
    expect(component.saving()).toBe(false);
  });

  it('preserves the selected file after a rejected upload', () => {
    const component = TestBed.createComponent(DocumentUpload).componentInstance;
    const file = new File(['%PDF-'], 'file.pdf');
    select(component, file);
    component.save();
    http.expectOne('/api/documents/upload').flush({}, { status: 413, statusText: 'Too Large' });
    expect(component.error()).toContain('20 MiB');
    expect(component.file()).toBe(file);
    expect(component.saving()).toBe(false);
  });
});
