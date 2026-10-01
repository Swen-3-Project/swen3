import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CreateDocument, Document, UpdateDocument } from './models';
@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/documents';
  list() { return this.http.get<Document[]>(this.url); }
  get(id: string) { return this.http.get<Document>(`${this.url}/${id}`); }
  create(request: CreateDocument) { return this.http.post<Document>(this.url, request); }
  update(id: string, request: UpdateDocument) { return this.http.put<Document>(`${this.url}/${id}`, request); }
  delete(id: string) { return this.http.delete<void>(`${this.url}/${id}`); }
}
