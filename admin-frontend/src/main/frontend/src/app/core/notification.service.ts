import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Thin wrapper around MatSnackBar for success/error messages. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    // Polite: success confirmations should not interrupt the screen reader's current output.
    this.snackBar.open(message, 'OK', {
      duration: 3500,
      panelClass: 'snack-success',
      politeness: 'polite',
    });
  }

  error(message: string): void {
    // Assertive: errors are announced immediately; keep them on screen long enough to read
    // (≥6 s, scaled by message length so long messages stay readable).
    this.snackBar.open(message, 'OK', {
      duration: Math.max(6000, message.length * 50),
      panelClass: 'snack-error',
      politeness: 'assertive',
    });
  }
}