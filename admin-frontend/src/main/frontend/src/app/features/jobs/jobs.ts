import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { combineLatest, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { JobsService } from '../../api/services';
import { JobListItem } from '../../api/models';
import { ConfirmService } from '../../core/confirm.service';
import { EpochPipe, formatDuration } from '../../core/format';
import { NotificationService } from '../../core/notification.service';
import { PollingService } from '../../core/polling.service';
import { RepoContextService } from '../../core/repo-context.service';
import { Column, DataTable, SortConfig } from '../../shared/data-table';
import { DateRange, DateRangeFilter } from '../../shared/date-range-filter';

/** Time a job/sub-job spent waiting, i.e. between creation and the start of processing. */
function queuedTime(createdAt: number, processingStartedAt: number | undefined, finishedAt: number | undefined): string {
  return formatDuration((processingStartedAt ?? finishedAt ?? Date.now()) - createdAt);
}

/** Time a job/sub-job spent actually processing, i.e. between processing start and end. */
function processingTime(processingStartedAt: number | undefined, finishedAt: number | undefined): string {
  return processingStartedAt == null ? '–' : formatDuration((finishedAt ?? Date.now()) - processingStartedAt);
}

/** Job status union, derived from the generated contract (the enum is inlined in the spec). */
type RenderingJobStatus = NonNullable<JobListItem['status']>;

@Component({
  selector: 'app-jobs',
  imports: [DataTable, DateRangeFilter, EpochPipe, MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule],
  templateUrl: './jobs.html',
  styleUrl: './jobs.scss',
})
export class Jobs {
  private readonly api = inject(JobsService);
  private readonly repoCtx = inject(RepoContextService);
  private readonly poll = inject(PollingService);
  private readonly notify = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  protected readonly statuses: RenderingJobStatus[] =
    ['QUEUED', 'PROCESSING', 'FINISHED', 'FAILED', 'PARTIALLY_FAILED'];

  protected readonly selectedStatuses = signal<RenderingJobStatus[]>([]);
  protected readonly page = signal(0);
  protected readonly size = 50;
  // Server-side sort + search (defaults mirror the table's initialSort / backend default).
  private readonly sort = signal('creationTimestamp');
  private readonly dir = signal<'asc' | 'desc'>('desc');
  private readonly search = signal('');
  // Server-side date range (epoch-ms) on creationTimestamp; null ⇒ no constraint.
  private readonly createdFrom = signal<number | null>(null);
  private readonly createdTo = signal<number | null>(null);
  private readonly refreshTick = signal(0);

  protected readonly jobs = toSignal(
    combineLatest([
      this.repoId$,
      toObservable(this.selectedStatuses),
      toObservable(this.sort),
      toObservable(this.dir),
      toObservable(this.search),
      toObservable(this.createdFrom),
      toObservable(this.createdTo),
      toObservable(this.page),
      toObservable(this.refreshTick),
      this.poll.ticks$,
    ]).pipe(
      switchMap(([repoId, statuses, sort, dir, search, createdFrom, createdTo, page]) =>
        repoId
          ? this.api
              .listJobs({
                repoId,
                statuses: statuses.length ? statuses : undefined,
                sort,
                dir,
                search: search || undefined,
                createdFrom: createdFrom ?? undefined,
                createdTo: createdTo ?? undefined,
                page,
                size: this.size,
              })
              .pipe(catchError(() => of(null)))
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly rows = computed<JobListItem[]>(() => this.jobs()?.content ?? []);

  protected readonly columns: Column[] = [
    { key: 'id', label: 'Job ID', cssClass: 'mono' },
    { key: 'module', label: 'Module', sortable: true },
    {
      key: 'status', label: 'Status', sortable: true, kind: 'badge',
      badgeClass: (r) => `badge-${r.status.toLowerCase()}`,
    },
    { key: 'esObjectId', label: 'Node', sortable: true, cssClass: 'mono' },
    { key: 'creationTimestamp', label: 'Created', sortable: true, kind: 'date' },
    {
      key: 'queuedTime', label: 'Queued',
      value: (r: JobListItem) => queuedTime(r.creationTimestamp, r.processingStartedTimestamp, r.finishedTimestamp),
    },
    {
      key: 'processingTime', label: 'Processing',
      value: (r: JobListItem) => processingTime(r.processingStartedTimestamp, r.finishedTimestamp),
    },
    { key: 'finishedTimestamp', label: 'Ended', kind: 'date' },
    { key: 'errorMessage', label: 'Error', cssClass: 'err' },
  ];

  protected readonly hasSubJobs = (job: JobListItem): boolean => job.subJobs.length > 0;
  protected readonly queuedTime = queuedTime;
  protected readonly processingTime = processingTime;

  setStatuses(values: RenderingJobStatus[]): void {
    this.selectedStatuses.set(values);
    this.page.set(0);
  }

  onSort(s: SortConfig): void {
    this.sort.set(s.key);
    this.dir.set(s.dir);
    this.page.set(0);
  }

  onSearch(q: string): void {
    this.search.set(q);
    this.page.set(0);
  }

  onCreatedRange(r: DateRange): void {
    this.createdFrom.set(r.from);
    this.createdTo.set(r.to);
    this.page.set(0);
  }

  prevPage(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  nextPage(): void {
    const total = this.jobs()?.totalPages ?? 0;
    this.page.update((p) => (p + 1 < total ? p + 1 : p));
  }

  deleteJob(id: string): void {
    this.confirm
      .confirm({
        title: 'Delete job',
        message: `Really delete job ${id}? The job and its sub-jobs will be removed.`,
        confirmLabel: 'Delete',
        destructive: true,
      })
      .subscribe((ok) => {
        if (!ok) return;
        this.api.deleteJob({ id }).subscribe({
          next: () => {
            this.refreshTick.update((v) => v + 1);
            this.notify.success('Job deleted.');
          },
          error: () => this.notify.error('Deletion failed.'),
        });
      });
  }
}
