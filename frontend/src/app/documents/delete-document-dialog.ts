import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-delete-document-dialog',
  template: `<div class="modal-header">
      <h2 class="modal-title fs-5" id="delete-document-title">Dokument löschen?</h2>
      <button
        type="button"
        class="btn-close"
        aria-label="Schließen"
        (click)="modal.dismiss()"
      ></button>
    </div>
    <div class="modal-body" id="delete-document-description">
      <p class="mb-0">
        „{{ filename }}“ und alle zugehörigen Erinnerungen werden endgültig gelöscht.
      </p>
    </div>
    <div class="modal-footer">
      <button
        type="button"
        class="btn btn-outline-secondary"
        ngbAutofocus
        (click)="modal.dismiss()"
      >
        Abbrechen
      </button>
      <button type="button" class="btn btn-danger" (click)="modal.close(true)">
        Endgültig löschen
      </button>
    </div>`,
})
export class DeleteDocumentDialog {
  readonly modal = inject(NgbActiveModal);
  filename = '';
}
