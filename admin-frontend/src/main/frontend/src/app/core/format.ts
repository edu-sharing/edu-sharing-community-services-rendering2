import { Pipe, PipeTransform } from '@angular/core';

/** Formatiert Bytes menschenlesbar (KB/MB/GB/TB, Basis 1024). */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) {
    return '–';
  }
  if (bytes === 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, i);
  return `${value.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

@Pipe({ name: 'bytes' })
export class BytesPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatBytes(value);
  }
}

@Pipe({ name: 'epoch' })
export class EpochPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return '–';
    }
    return new Date(value).toLocaleString();
  }
}
