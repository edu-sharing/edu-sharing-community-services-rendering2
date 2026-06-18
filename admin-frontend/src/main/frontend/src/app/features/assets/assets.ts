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
import { NotificationService } from '../../core/notification.service';
import { PollingService } from '../../core/polling.service';
import { RepoContextService } from '../../core/repo-context.service';
import { Column, DataTable } from '../../shared/data-table';

@Component({
  selector: 'app-assets',
  imports: [
    DataTable, BytesPipe, EpochPipe,
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
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  protected readonly filterType = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly size = 50;
  private readonly refreshTick = signal(0);

  /** Loaded versions per nodeId (lazy, on expand). */
  protected readonly versionsByNode = signal<Record<string, AssetInfo[]>>({});

  protected readonly nodes = toSignal(
    combineLatest([
      this.repoId$,
      toObservable(this.filterType),
      toObservable(this.page),
      toObservable(this.refreshTick),
      this.poll.ticks$,
    ]).pipe(
      switchMap(([repoId, type, page]) =>
        repoId
          ? this.api
              .listAssetNodes({ repoId, type: type ?? undefined, page, size: this.size })
              .pipe(catchError(() => of(null)))
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly types = toSignal(
    combineLatest([this.repoId$, toObservable(this.refreshTick), this.poll.ticks$]).pipe(
      switchMap(([repoId]) =>
        repoId ? this.api.listAssetTypes({ repoId }).pipe(catchError(() => of([]))) : of([]),
      ),
    ),
    { initialValue: [] as AssetTypeInfo[] },
  );

  protected readonly nodeRows = computed<AssetNode[]>(() => this.nodes()?.content ?? []);

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

  setFilterType(value: string): void {
    this.filterType.set(value || null);
    this.page.set(0);
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
    if (!confirm(`Delete version (hash ${version.hash}) of node ${node.nodeId}?`)) return;
    this.api.deleteAsset({ repoId, nodeId: node.nodeId, hash: version.hash }).subscribe({
      next: () => {
        this.loadVersions(node.nodeId);
        this.refreshTick.update((v) => v + 1);
      },
      error: () => this.notify.error('Deletion failed.'),
    });
  }

  deleteNode(node: AssetNode): void {
    const repoId = this.repoId;
    if (!repoId) return;
    if (!confirm(`Delete all ${node.versionCount} version(s) of node ${node.nodeId}?`)) return;
    this.api.deleteAsset({ repoId, nodeId: node.nodeId }).subscribe({
      next: () => this.bumpRefresh(),
      error: () => this.notify.error('Deletion failed.'),
    });
  }

  deleteByType(type: string): void {
    const repoId = this.repoId;
    if (!repoId) return;
    const answer = prompt(
      `Delete all assets of type "${type}" in repo ${repoId}.\nTo confirm, type the type name exactly:`,
    );
    if (answer !== type) {
      if (answer !== null) this.notify.error('Input does not match — aborted.');
      return;
    }
    this.api.deleteAssetsByType({ repoId, type }).subscribe({
      next: (r) => {
        this.bumpRefresh();
        this.notify.success(`${r.deleted} assets deleted.`);
      },
      error: () => this.notify.error('Deletion failed.'),
    });
  }

  deleteAll(): void {
    const repoId = this.repoId;
    if (!repoId) return;
    const answer = prompt(
      `Irreversibly delete ALL assets of repo ${repoId}.\nTo confirm, type the repoId exactly:`,
    );
    if (answer !== repoId) {
      if (answer !== null) this.notify.error('Input does not match — aborted.');
      return;
    }
    this.api.deleteAllAssets({ repoId }).subscribe({
      next: (r) => {
        this.bumpRefresh();
        this.notify.success(`${r.deleted} assets deleted.`);
      },
      error: () => this.notify.error('Deletion failed.'),
    });
  }
}
