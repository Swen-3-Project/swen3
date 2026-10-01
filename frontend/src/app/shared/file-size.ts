import { Pipe, PipeTransform } from '@angular/core';
@Pipe({ name: 'fileSize' })
export class FileSize implements PipeTransform {
  transform(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    const units = ['KiB', 'MiB', 'GiB', 'TiB'];
    let value = bytes / 1024;
    let index = 0;
    while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
    return `${new Intl.NumberFormat('de-AT', { maximumFractionDigits: 1 }).format(value)} ${units[index]}`;
  }
}
