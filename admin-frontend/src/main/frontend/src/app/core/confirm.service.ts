import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';

/**
 * Opens the reusable {@link ConfirmDialog} as a Material modal and emits the user's decision.
 * Replaces the native `confirm()`/`prompt()` dialogs.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(MatDialog);

  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open(ConfirmDialog, {
        data,
        width: '440px',
        autoFocus: data.confirmToken ? 'input' : 'button',
        restoreFocus: true,
      })
      .afterClosed()
      .pipe(map((result) => result === true));
  }
}
