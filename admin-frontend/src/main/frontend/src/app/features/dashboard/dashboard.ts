import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Subject, combineLatest, of } from 'rxjs';
import { catchError, map, switchMap, withLatestFrom } from 'rxjs/operators';
import { JobsService, StorageService } from '../../api/services';
import { BucketUsageInfo } from '../../api/models';
import { BytesPipe, formatBytes } from '../../core/format';
import { ConfirmService } from '../../core/confirm.service';
import { NotificationService } from '../../core/notification.service';
import { PollingService } from '../../core/polling.service';
import { RepoContextService } from '../../core/repo-context.service';
import { Column, DataTable } from '../../shared/data-table';

@Component({
  selector: 'app-dashboard',
  imports: [DataTable, BytesPipe, MatButtonModule, MatIconModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly storageApi = inject(StorageService);
  private readonly jobsApi = inject(JobsService);
  private readonly repoCtx = inject(RepoContextService);
  private readonly poll = inject(PollingService);
  private readonly confirm = inject(ConfirmService);
  private readonly notify = inject(NotificationService);
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  /** Explains the tracked vs. exact storage measurement modes — shown via the info icon. */
  protected readonly storageInfoText =
    'Sizes are normally read from a cheap running total ("tracked") kept up to date as files are ' +
    'written or deleted. Click the refresh icon on a bucket row to recompute that one bucket\'s ' +
    'exact size live from S3 — slower, but authoritative, and the only way to see the temp ' +
    "bucket's size, which isn't tracked otherwise. That row then stays pinned to the measured " +
    'snapshot across automatic refreshes until you reset it. Note: an exact recount is only ' +
    'possible when a bucket is dedicated to this repository (bucket mode "byCustomer" or ' +
    '"externalBucket"); with buckets shared across repositories ("byType"), tracked values are ' +
    'used regardless. The "H5P library cache" row is not a bucket but a volume inside lumi, so it has no ' +
    'exact measurement; it is shown with the size and quota lumi reports, and the cache cleaner frees it ' +
    'like a bucket (it is not part of the repository total).';

  /** Always requests tracked (non-exact) totals — refreshed purely by the automatic poll. */
  protected readonly storage = toSignal(
    combineLatest([this.repoId$, this.poll.ticks$]).pipe(
      switchMap(([repoId]) =>
        repoId
          ? this.storageApi.getStorageUsage({ repoId, exact: false }).pipe(catchError(() => of(null)))
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  /**
   * Bucket name -> exact snapshot, keyed per bucket rather than a single global mode: clicking
   * "measure exact" on one row only freezes that row, other rows keep following the live tracked
   * poll. A pinned row is excluded from automatic updates until [resetExact] is called — otherwise
   * the very next poll tick would immediately overwrite the exact value with the tracked one.
   */
  protected readonly pinnedExact = signal<Record<string, BucketUsageInfo>>({});
  private readonly exactClick$ = new Subject<string>();

  constructor() {
    this.exactClick$
      .pipe(
        withLatestFrom(this.repoId$),
        switchMap(([bucketName, repoId]) =>
          repoId
            ? this.storageApi.getStorageUsage({ repoId, exact: true, bucket: bucketName }).pipe(
                map((res) => ({ bucketName, bucket: res.buckets.find((b) => b.name === bucketName) ?? null })),
                catchError(() => of({ bucketName, bucket: null })),
              )
            : of(null),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        if (result?.bucket) {
          this.pinnedExact.update((m) => ({ ...m, [result.bucketName]: result.bucket! }));
        }
      });
  }

  protected readonly stats = toSignal(
    combineLatest([this.repoId$, this.poll.ticks$]).pipe(
      switchMap(([repoId]) =>
        repoId ? this.jobsApi.getJobStats({ repoId }).pipe(catchError(() => of(null))) : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly queueCount = computed(() => {
    const s = this.stats();
    return s ? s.queued + s.processing : 0;
  });

  protected readonly failedCount = computed(() => {
    const s = this.stats();
    return s ? s.failed + s.partiallyFailed : 0;
  });

  /** Live tracked buckets, with any pinned-exact rows (see [pinnedExact]) substituted in by name. */
  protected readonly buckets = computed<BucketUsageInfo[]>(() => {
    const live = this.storage()?.buckets ?? [];
    const pinned = this.pinnedExact();
    return live.map((b) => pinned[b.name] ?? b);
  });

  protected readonly bucketColumns: Column[] = [
    { key: 'name', label: 'Bucket / volume', sortable: true, cssClass: 'mono' },
    { key: 'size', label: 'Size', sortable: true, align: 'right', kind: 'bytes' },
    { key: 'quota', label: 'Quota', sortable: true, align: 'right', kind: 'bytes' },
    {
      key: 'usedPercent',
      label: 'Used',
      sortable: true,
      align: 'right',
      kind: 'percent',
      barColor: (row: BucketUsageInfo) => this.barColor(row.usedPercent ?? null),
    },
    {
      key: 'status',
      label: 'Status',
      value: (row: BucketUsageInfo) => this.bucketStatus(row),
      cssClass: 'muted',
    },
  ];

  /** Requests a fresh exact snapshot for one bucket and pins it (see [pinnedExact]) once it arrives. */
  measureExact(bucketName: string): void {
    this.exactClick$.next(bucketName);
  }

  protected readonly cleaning = signal(false);

  /** Runs the cache cleaner for the active repo after confirmation; it only touches scopes over their upper threshold. */
  runCleanup(): void {
    const repoId = this.repoCtx.activeRepoId();
    if (!repoId || this.cleaning()) return;
    this.confirm
      .confirm({
        title: 'Run cache cleaner',
        message:
          `Run the cache cleaner for repo ${repoId} now? Cached renderings are evicted only for ` +
          'buckets (and lumi\'s H5P library cache) whose usage is above the configured upper threshold; ' +
          'evicted renderings are re-created on demand.',
        confirmLabel: 'Run cleaner',
        destructive: true,
      })
      .subscribe((ok) => {
        if (!ok) return;
        this.cleaning.set(true);
        this.storageApi.triggerCleanup({ repoId }).subscribe({
          next: (r) => {
            this.cleaning.set(false);
            this.notify.success(
              r.deletedEntries > 0
                ? `Cache cleaner removed ${r.deletedEntries} objects (${formatBytes(r.freedBytes)}).`
                : `Cache cleaner finished: nothing to clean (${r.scopesChecked} scope(s) checked).`,
            );
          },
          error: () => {
            this.cleaning.set(false);
            this.notify.error('Cache cleaner failed.');
          },
        });
      });
  }

  /** Un-pins a bucket row so the next poll tick resumes showing its live tracked value. */
  resetExact(bucketName: string): void {
    this.pinnedExact.update((m) => {
      const { [bucketName]: _removed, ...rest } = m;
      return rest;
    });
  }

  protected isPinned(row: BucketUsageInfo): boolean {
    return this.pinnedExact()[row.name] !== undefined;
  }

  barColor(percent: number | null): string {
    if (percent == null) return 'var(--es-info)';
    if (percent >= 90) return 'var(--es-danger)';
    if (percent >= 70) return 'var(--es-warning)';
    return 'var(--es-success)';
  }

  /** lumi's library cache is a volume, not an S3 bucket: it can neither be recounted from S3 nor pinned. */
  protected isLibraryCache(row: BucketUsageInfo): boolean {
    return row.kind === 'LIBRARY_CACHE';
  }

  private bucketStatus(row: BucketUsageInfo): string {
    if (this.isLibraryCache(row)) {
      return (row.usedPercent ?? 0) > 100
        ? 'volume of lumi — over its quota, freed by the cache cleaner'
        : 'volume of lumi — freed by the cache cleaner';
    }
    if (this.isPinned(row)) return 'exact snapshot — click reset to resume live updates';
    if (!row.measured) return 'not measured — click the refresh icon to measure exact';
    if (!row.enforced) return 'not enforced';
    return '';
  }
}
