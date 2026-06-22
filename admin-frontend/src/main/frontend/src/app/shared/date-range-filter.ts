import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTimepickerModule } from '@angular/material/timepicker';

/** Inclusive epoch-ms range; `null` on a bound means "no constraint". */
export interface DateRange {
  from: number | null;
  to: number | null;
}

/**
 * Reusable from–to date+time range filter, projected into the data-table toolbar via `dt-filters`.
 * The dates use a single Material range picker (one calendar popup for start+end); the time of each
 * bound is set via a separate MatTimepicker (Material has no combined date+time range picker). The
 * emitted value is epoch-ms: when only the date is set, `from` defaults to the start of day
 * (00:00:00.000) and `to` to the end of day (23:59:59.999), so a date-only range covers the whole
 * day inclusively.
 */
@Component({
  selector: 'app-date-range-filter',
  imports: [
    FormsModule, MatFormFieldModule, MatInputModule, MatDatepickerModule,
    MatTimepickerModule, MatIconModule, MatButtonModule,
  ],
  templateUrl: './date-range-filter.html',
  styleUrl: './date-range-filter.scss',
})
export class DateRangeFilter {
  /** Group label shown before the inputs, e.g. "Created" / "Last accessed". */
  readonly label = input('');

  readonly rangeChange = output<DateRange>();

  protected readonly fromDate = signal<Date | null>(null);
  protected readonly fromTime = signal<Date | null>(null);
  protected readonly toDate = signal<Date | null>(null);
  protected readonly toTime = signal<Date | null>(null);

  /** True when at least one bound is set (controls the clear button visibility). */
  protected readonly hasValue = (): boolean =>
    !!(this.fromDate() || this.fromTime() || this.toDate() || this.toTime());

  protected emit(): void {
    this.rangeChange.emit({
      from: this.combine(this.fromDate(), this.fromTime(), 'start'),
      to: this.combine(this.toDate(), this.toTime(), 'end'),
    });
  }

  clear(): void {
    this.fromDate.set(null);
    this.fromTime.set(null);
    this.toDate.set(null);
    this.toTime.set(null);
    this.rangeChange.emit({ from: null, to: null });
  }

  /** Combines a date with an optional time into epoch-ms; null date ⇒ no bound. */
  private combine(date: Date | null, time: Date | null, bound: 'start' | 'end'): number | null {
    if (!date) return null;
    const d = new Date(date);
    if (time) {
      d.setHours(time.getHours(), time.getMinutes(), time.getSeconds(), 0);
    } else if (bound === 'start') {
      d.setHours(0, 0, 0, 0);
    } else {
      d.setHours(23, 59, 59, 999);
    }
    return d.getTime();
  }
}
