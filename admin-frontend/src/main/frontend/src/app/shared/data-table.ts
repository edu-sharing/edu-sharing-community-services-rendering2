import { NgTemplateOutlet } from '@angular/common';
import { Component, TemplateRef, computed, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { BytesPipe, EpochPipe } from '../core/format';

export type ColumnKind = 'text' | 'number' | 'bytes' | 'date' | 'badge';

/**
 * Column definition. Deliberately loosely typed (`any` accessors) so the non-generic table is
 * bindable in the template with arbitrary row types (Angular cannot infer a component's generic
 * type arguments from inputs in the template).
 */
export interface Column {
  key: string;
  label: string;
  sortable?: boolean;
  align?: 'left' | 'right' | 'center';
  kind?: ColumnKind;
  /** Accessor for display/sorting/search; defaults to row[key]. */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  value?: (row: any) => unknown;
  /** CSS class for the badge rendering (kind = 'badge'). */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  badgeClass?: (row: any) => string;
  /** Additional CSS class for the cell (e.g. 'mono'). */
  cssClass?: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Row = any;

export interface SortConfig {
  key: string;
  dir: 'asc' | 'desc';
}

/**
 * Lightweight, reusable table with full-text search and column sorting (client-side over the
 * passed rows). Optionally expandable rows (expansion) and an action column — both via
 * projected `ng-template`s with `$implicit = row`.
 */
@Component({
  selector: 'app-data-table',
  imports: [
    FormsModule, NgTemplateOutlet, BytesPipe, EpochPipe,
    MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
  ],
  templateUrl: './data-table.html',
  styleUrl: './data-table.scss',
})
export class DataTable {
  readonly columns = input.required<Column[]>();
  readonly rows = input.required<Row[]>();
  readonly searchable = input(true);
  readonly searchPlaceholder = input('Search…');
  readonly trackKey = input('id');
  /**
   * Max height of the scrollable table area (any CSS length). The toolbar and the sticky header
   * stay visible while the body scrolls within this height; short tables don't scroll. Default
   * caps the table to most of the viewport so the header stays in view.
   */
  readonly maxHeight = input('60vh');
  readonly initialSort = input<SortConfig | null>(null);
  readonly expansion = input<TemplateRef<{ $implicit: Row }> | null>(null);
  readonly rowActions = input<TemplateRef<{ $implicit: Row }> | null>(null);
  /** Determines whether a row is expandable (default: all, provided expansion is set). */
  readonly canExpand = input<(row: Row) => boolean>(() => true);
  /**
   * Server-side mode: the table does NOT filter/sort the rows itself (they are shown as passed,
   * already searched/sorted by the backend). Instead it emits `sortChange`/`queryChange` so the
   * host can re-query. Default `false` keeps the client-side behaviour (small, fully-loaded lists).
   */
  readonly serverSide = input(false);
  /** Emitted when a row is NEWLY expanded (e.g. to lazily load detail data). */
  readonly expanded = output<Row>();
  /** (server-side) Emitted when the user changes the sort column/direction. */
  readonly sortChange = output<SortConfig>();
  /** (server-side) Emitted (debounced) when the search text changes. */
  readonly queryChange = output<string>();

  private readonly querySubject = new Subject<string>();

  constructor() {
    this.querySubject
      .pipe(debounceTime(300), takeUntilDestroyed())
      .subscribe((q) => this.queryChange.emit(q));
  }

  protected readonly query = signal('');
  private readonly sortKey = signal<string | null>(null);
  private readonly sortDir = signal<'asc' | 'desc'>('asc');
  private readonly expandedKeys = signal<Set<unknown>>(new Set());

  protected readonly activeSortKey = computed(() => this.sortKey() ?? this.initialSort()?.key ?? null);
  protected readonly activeSortDir = computed(() =>
    this.sortKey() ? this.sortDir() : (this.initialSort()?.dir ?? 'asc'),
  );

  protected readonly view = computed<Row[]>(() => {
    const data = this.rows() ?? [];
    // Server-side: rows are already searched + sorted by the backend — show them verbatim.
    if (this.serverSide()) return data;
    return this.clientView(data);
  });

  private clientView(rows: Row[]): Row[] {
    const cols = this.columns();
    let data = rows;

    const q = this.query().trim().toLowerCase();
    if (q) {
      data = data.filter((row) =>
        cols.some((c) => String(this.cell(row, c) ?? '').toLowerCase().includes(q)),
      );
    }

    const key = this.activeSortKey();
    const col = key ? cols.find((c) => c.key === key) : undefined;
    if (col) {
      const dir = this.activeSortDir() === 'asc' ? 1 : -1;
      data = [...data].sort((a, b) => {
        const va = this.cell(a, col);
        const vb = this.cell(b, col);
        if (typeof va === 'number' && typeof vb === 'number') {
          return (va - vb) * dir;
        }
        return String(va ?? '').localeCompare(String(vb ?? '')) * dir;
      });
    }
    return data;
  }

  protected readonly colspan = computed(
    () => this.columns().length + (this.expansion() ? 1 : 0) + (this.rowActions() ? 1 : 0),
  );

  protected cell(row: Row, col: Column): Row {
    return col.value ? col.value(row) : (row as Record<string, unknown>)[col.key];
  }

  protected cellClass(col: Column): string {
    const classes: string[] = [];
    if (col.align === 'right') classes.push('right');
    if (col.align === 'center') classes.push('center');
    if (col.cssClass) classes.push(col.cssClass);
    return classes.join(' ');
  }

  protected toggleSort(col: Column): void {
    if (!col.sortable) return;
    const dir: 'asc' | 'desc' =
      this.activeSortKey() === col.key && this.activeSortDir() === 'asc' ? 'desc' : 'asc';
    this.sortKey.set(col.key);
    this.sortDir.set(dir);
    if (this.serverSide()) this.sortChange.emit({ key: col.key, dir });
  }

  /** Search input handler — drives the client filter and (server-side) the debounced re-query. */
  protected onQueryInput(value: string): void {
    this.query.set(value);
    if (this.serverSide()) this.querySubject.next(value);
  }

  protected trackVal(row: Row): unknown {
    return (row as Record<string, unknown>)[this.trackKey()];
  }

  protected isExpanded(row: Row): boolean {
    return this.expandedKeys().has(this.trackVal(row));
  }

  protected toggle(row: Row): void {
    const key = this.trackVal(row);
    const next = new Set(this.expandedKeys());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
      this.expanded.emit(row);
    }
    this.expandedKeys.set(next);
  }
}
