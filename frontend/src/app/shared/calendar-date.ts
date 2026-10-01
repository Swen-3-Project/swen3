import { Pipe, PipeTransform } from '@angular/core';
@Pipe({ name: 'calendarDate' })
export class CalendarDate implements PipeTransform {
  transform(value: string): string {
    return value.split('-').reverse().join('.');
  }
}
