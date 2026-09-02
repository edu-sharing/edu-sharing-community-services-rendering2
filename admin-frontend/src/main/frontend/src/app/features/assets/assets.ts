import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { combineLatest, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AssetsService } from '../../api/services';
import { AssetInfo, AssetNode, AssetTypeInfo } from '../../api/models';
import { BytesPipe, EpochPipe } from '../../core/format';
import { ConfirmService } from '../../core/confirm.service';
import { NotificationService } from '../../core/notification.service';
import { PollingService } from '../../core/polling.service';
import { RepoContextService } from '../../core/repo-context.service';
import { Column, DataTable, SortConfig } from '../../shared/data-table';
import { DateRange, DateRangeFilter } from '../../shared/date-range-filter';

@Component({
  selector: 'app-assets',
  imports: [
    DataTable, DateRangeFilter, BytesPipe, EpochPipe,
    MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule,
  ],
  templateUrl: './assets.html',
  styleUrl: './assets.scss',
})
export class Assets {
  private readonly api = inject(AssetsService);
  private readonly repoCtx = inject(RepoContextService);
  private readonly poll = inject(PollingService);
  private readonly notify = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  protected readonly selectedTypes = signal<string[]>([]);
  protected readonly page = signal(0);
  protected readonly size = 50;
  private readonly refreshTick = signal(0);

  // Server-side sort + search per table (defaults mirror each table's initialSort).
  private readonly nodeSort = signal('lastAccessed');
  private readonly nodeDir = signal<'asc' | 'desc'>('desc');
  private readonly nodeSearch = signal('');
  // Server-side date range (epoch-ms) on lastAccessed; null ⇒ no constraint.
  private readonly accessedFrom = signal<number | null>(null);
  private readonly accessedTo = signal<number | null>(null);
  private readonly typeSort = signal('totalSize');
  private readonly typeDir = signal<'asc' | 'desc'>('desc');
  private readonly typeSearch = signal('');

  /** Loaded versions per nodeId (lazy, on expand). */
  protected readonly versionsByNode = signal<Record<string, AssetInfo[]>>({});

  protected readonly nodes = toSignal(
    combineLatest([
      this.repoId$,
      toObservable(this.selectedTypes),
      toObservable(this.nodeSort),
      toObservable(this.nodeDir),
      toObservable(this.nodeSearch),
      toObservable(this.accessedFrom),
      toObservable(this.accessedTo),
      toObservable(this.page),
      toObservable(this.refreshTick),
      this.poll.ticks$,
    ]).pipe(
      switchMap(([repoId, types, sort, dir, search, accessedFrom, accessedTo, page]) =>
        repoId
          ? this.api
              .listAssetNodes({
                repoId,
                types: types.length ? types : undefined,
                sort,
                dir,
                search: search || undefined,
                accessedFrom: accessedFrom ?? undefined,
                accessedTo: accessedTo ?? undefined,
                page,
                size: this.size,
              })
              .pipe(catchError(() => of(null)))
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly types = toSignal(
    combineLatest([
      this.repoId$,
      toObservable(this.typeSort),
      toObservable(this.typeDir),
      toObservable(this.typeSearch),
      toObservable(this.refreshTick),
      this.poll.ticks$,
    ]).pipe(
      switchMap(([repoId, sort, dir, search]) =>
        repoId
          ? this.api
              .listAssetTypes({ repoId, sort, dir, search: search || undefined })
              .pipe(catchError(() => of([])))
          : of([]),
      ),
    ),
    { initialValue: [] as AssetTypeInfo[] },
  );

  protected readonly nodeRows = computed<AssetNode[]>(() => this.nodes()?.content ?? []);

  /** Type names for the node filter dropdown — always stable, sorted by name (so it doesn't
   *  reorder while polling and regardless of how the Types table itself is sorted). */
  protected readonly filterTypes = computed<AssetTypeInfo[]>(() =>
    [...this.types()].sort((a, b) => a.type.localeCompare(b.type)),
  );

  protected readonly nodeColumns: Column[] = [
    { key: 'nodeId', label: 'Node', sortable: true, cssClass: 'mono' },
    { key: 'type', label: 'Type', sortable: true },
    { key: 'versionCount', label: 'Versions', sortable: true, align: 'right', kind: 'number' },
    { key: 'size', label: 'Latest', sortable: true, align: 'right', kind: 'bytes' },
    { key: 'totalSize', label: 'Total', sortable: true, align: 'right', kind: 'bytes' },
    { key: 'lastAccessed', label: 'Last accessed', sortable: true, kind: 'date' },
  ];

  protected readonly typeColumns: Column[] = [
    { key: 'type', label: 'Type', sortable: true, cssClass: 'mono' },
    { key: 'count', label: 'Count', sortable: true, align: 'right', kind: 'number' },
    { key: 'totalSize', label: 'Size', sortable: true, align: 'right', kind: 'bytes' },
  ];

  private get repoId(): string | null {
    return this.repoCtx.activeRepoId();
  }

  setFilterTypes(values: string[]): void {
    this.selectedTypes.set(values);
    this.page.set(0);
  }

  onNodeSort(s: SortConfig): void {
    this.nodeSort.set(s.key);
    this.nodeDir.set(s.dir);
    this.page.set(0);
  }

  onNodeSearch(q: string): void {
    this.nodeSearch.set(q);
    this.page.set(0);
  }

  onAccessedRange(r: DateRange): void {
    this.accessedFrom.set(r.from);
    this.accessedTo.set(r.to);
    this.page.set(0);
  }

  onTypeSort(s: SortConfig): void {
    this.typeSort.set(s.key);
    this.typeDir.set(s.dir);
  }

  onTypeSearch(q: string): void {
    this.typeSearch.set(q);
  }

  prevPage(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    const total = this.nodes()?.totalPages ?? 0;
    this.page.update((p) => (p + 1 < total ? p + 1 : p));
  }

  /** Lazily loads the versions of a nodeId on expand. */
  onExpandNode(node: AssetNode): void {
    this.loadVersions(node.nodeId);
  }

  private loadVersions(nodeId: string): void {
    const repoId = this.repoId;
    if (!repoId) return;
    this.api.listAssetVersions({ repoId, nodeId }).subscribe({
      next: (versions) => this.versionsByNode.update((m) => ({ ...m, [nodeId]: versions })),
      error: () => this.versionsByNode.update((m) => ({ ...m, [nodeId]: [] })),
    });
  }

  private bumpRefresh(): void {
    this.versionsByNode.set({});
    this.refreshTick.update((v) => v + 1);
  }

  deleteVersion(node: AssetNode, version: AssetInfo): void {
    const repoId = this.repoId;
    if (!repoId) return;
    this.confirm
      .confirm({
        title: 'Delete version',
        message: `Delete version (hash ${version.hash}) of node ${node.nodeId}?`,
        confirmLabel: 'Delete',
        destructive: true,
      })
      .subscribe((ok) => {
        if (!ok) return;
        this.api.deleteAsset({ repoId, nodeId: node.nodeId, hash: version.hash }).subscribe({
          next: () => {
            this.loadVersions(node.nodeId);
            this.refreshTick.update((v) => v + 1);
          },
          error: () => this.notify.error('Deletion failed.'),
        });
      });
  }

  deleteNode(node: AssetNode): void {
    const repoId = this.repoId;
    if (!repoId) return;
    this.confirm
      .confirm({
        title: 'Delete node',
        message: `Delete all ${node.versionCount} version(s) of node ${node.nodeId}?`,
        confirmLabel: 'Delete',
        destructive: true,
      })
      .subscribe((ok) => {
        if (!ok) return;
        this.api.deleteAsset({ repoId, nodeId: node.nodeId }).subscribe({
          next: () => this.bumpRefresh(),
          error: () => this.notify.error('Deletion failed.'),
        });
      });
  }

  deleteByType(type: string): void {
    const repoId = this.repoId;
    if (!repoId) return;
    this.confirm
      .confirm({
        title: 'Delete all assets of a type',
        message: `Delete all assets of type "${type}" in repo ${repoId}. This cannot be undone.`,
        confirmLabel: 'Delete type',
        destructive: true,
        confirmToken: type,
        tokenHint: 'Type the type name to confirm',
      })
      .subscribe((ok) => {
        if (!ok) return;
        this.api.deleteAssetsByType({ repoId, type }).subscribe({
          next: (r) => {
            this.bumpRefresh();
            this.notify.success(`${r.deleted} assets deleted.`);
          },
          error: () => this.notify.error('Deletion failed.'),
        });
      });
  }

  deleteAll(): void {
    const repoId = this.repoId;
    if (!repoId) return;
    this.confirm
      .confirm({
        title: 'Delete ALL assets',
        message: `Irreversibly delete ALL assets of repo ${repoId}. This cannot be undone.`,
        confirmLabel: 'Delete all',
        destructive: true,
        confirmToken: repoId,
        tokenHint: 'Type the repoId to confirm',
      })
      .subscribe((ok) => {
        if (!ok) return;
        this.api.deleteAllAssets({ repoId }).subscribe({
          next: (r) => {
            this.bumpRefresh();
            this.notify.success(`${r.deleted} assets deleted.`);
          },
          error: () => this.notify.error('Deletion failed.'),
        });
      });
  }
}
