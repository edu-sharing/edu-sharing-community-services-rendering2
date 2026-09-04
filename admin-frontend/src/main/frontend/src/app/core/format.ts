import { Pipe, PipeTransform } from '@angular/core';

/** Formats bytes in a human-readable way (KB/MB/GB/TB, base 1024). */
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

/** Formats a duration in milliseconds in a human-readable way (e.g. "2m 5s", "1h 12m"). */
export function formatDuration(millis: number | null | undefined): string {
  if (millis == null || millis < 0) {
    return '–';
  }
  const totalSeconds = Math.floor(millis / 1000);
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  const totalMinutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (totalMinutes < 60) {
    return `${totalMinutes}m ${seconds}s`;
  }
  const totalHours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (totalHours < 24) {
    return `${totalHours}h ${minutes}m`;
  }
  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;
  return `${days}d ${hours}h`;
}
