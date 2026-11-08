import { Pipe, PipeTransform } from '@angular/core';
@Pipe({ name: 'statusLabel' })
export class StatusLabel implements PipeTransform {
  transform(status: string): string {
    return (
      (
        {
          CREATED: 'Angelegt',
          OCR_COMPLETED: 'Text erkannt',
          INDEXED: 'Durchsuchbar',
          FAILED: 'Verarbeitung fehlgeschlagen',
          OPEN: 'Offen',
          COMPLETED: 'Erledigt',
        } as Record<string, string>
      )[status] ?? status
    );
  }
}
