import { Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AdminApiService } from '../../core/admin-api.service';
import { BytesPipe } from '../../core/format';
import { BucketUsageInfo } from '../../core/models';
import { PollingService } from '../../core/polling.service';
import { RepoContextService } from '../../core/repo-context.service';
import { Column, DataTable } from '../../shared/data-table';

@Component({
  selector: 'app-dashboard',
  imports: [DataTable, BytesPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(AdminApiService);
  private readonly repoCtx = inject(RepoContextService);
  private readonly poll = inject(PollingService);
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  protected readonly storage = toSignal(
    combineLatest([this.repoId$, this.poll.ticks$]).pipe(
      switchMap(([repoId]) =>
        repoId ? this.api.storageUsage(repoId).pipe(catchError(() => of(null))) : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly stats = toSignal(
    combineLatest([this.repoId$, this.poll.ticks$]).pipe(
      switchMap(([repoId]) =>
        repoId ? this.api.jobStats(repoId).pipe(catchError(() => of(null))) : of(null),
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

  protected readonly buckets = computed<BucketUsageInfo[]>(() => this.storage()?.buckets ?? []);

  protected readonly bucketColumns: Column[] = [
    { key: 'name', label: 'Bucket', sortable: true, cssClass: 'mono' },
    { key: 'size', label: 'Größe', sortable: true, align: 'right', kind: 'bytes' },
  ];

  barColor(percent: number | null): string {
    if (percent == null) return 'var(--es-info)';
    if (percent >= 90) return 'var(--es-danger)';
    if (percent >= 70) return 'var(--es-warning)';
    return 'var(--es-success)';
  }
}
