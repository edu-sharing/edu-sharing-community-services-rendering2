import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Renders the confirm button in the danger color. */
  destructive?: boolean;
  /** If set, the user must type this exact text to enable the confirm button (irreversible actions). */
  confirmToken?: string;
  /** Label for the type-to-confirm input. */
  tokenHint?: string;
}

/**
 * Reusable confirmation modal (replaces the native `confirm()`/`prompt()`). With `confirmToken`
 * set it requires the exact token to be typed before confirming — used for the irreversible bulk
 * deletes. Opened via {@link ConfirmService}; closes with `true` (confirmed) or `false`.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './confirm-dialog.html',
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ConfirmDialog, boolean>);

  protected readonly typed = signal('');
  protected readonly canConfirm = computed(
    () => !this.data.confirmToken || this.typed() === this.data.confirmToken,
  );

  protected cancel(): void {
    this.ref.close(false);
  }

  protected confirm(): void {
    if (this.canConfirm()) this.ref.close(true);
  }
}
