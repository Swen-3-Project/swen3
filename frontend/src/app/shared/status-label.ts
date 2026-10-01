import { Pipe, PipeTransform } from '@angular/core';
@Pipe({ name: 'statusLabel' })
export class StatusLabel implements PipeTransform {
  transform(status: string): string { return ({ CREATED: 'Angelegt', OPEN: 'Offen', COMPLETED: 'Erledigt' } as Record<string, string>)[status] ?? status; }
}
