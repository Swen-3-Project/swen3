import { HttpErrorResponse } from '@angular/common/http';
export function apiErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'Die Aktion konnte nicht abgeschlossen werden. Bitte versuchen Sie es erneut.';
  switch (error.status) {
    case 0: return 'Keine Verbindung zum Server. Prüfen Sie die Verbindung und versuchen Sie es erneut.';
    case 400: return 'Die Angaben wurden vom Server abgelehnt. Bitte prüfen Sie Ihre Eingaben.';
    case 404: return 'Das angeforderte Dokument oder die Erinnerung wurde nicht gefunden.';
    case 413: return 'Die Datei ist zu groß. Bitte eine PDF mit höchstens 20 MiB auswählen.';
    default: return 'Der Server ist momentan nicht verfügbar. Bitte versuchen Sie es erneut.';
  }
}
