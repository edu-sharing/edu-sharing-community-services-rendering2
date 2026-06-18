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
import { NotificationService } from '../../core/notification.service';
import { PollingService } from '../../core/polling.service';
import { RepoContextService } from '../../core/repo-context.service';
import { Column, DataTable } from '../../shared/data-table';

/** Job status union, derived from the generated contract (the enum is inlined in the spec). */
type RenderingJobStatus = NonNullable<JobListItem['status']>;

@Component({
  selector: 'app-jobs',
  imports: [DataTable, MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule],
  templateUrl: './jobs.html',
  styleUrl: './jobs.scss',
})
export class Jobs {
  private readonly api = inject(JobsService);
  private readonly repoCtx = inject(RepoContextService);
  private readonly poll = inject(PollingService);
  private readonly notify = inject(NotificationService);
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  protected readonly statuses: RenderingJobStatus[] =
    ['QUEUED', 'PROCESSING', 'FINISHED', 'FAILED', 'PARTIALLY_FAILED'];

  protected readonly status = signal<RenderingJobStatus | null>(null);
  protected readonly page = signal(0);
  protected readonly size = 50;
  private readonly refreshTick = signal(0);

  protected readonly jobs = toSignal(
    combineLatest([
      this.repoId$,
      toObservable(this.status),
      toObservable(this.page),
      toObservable(this.refreshTick),
      this.poll.ticks$,
    ]).pipe(
      switchMap(([repoId, status, page]) =>
        repoId
          ? this.api
              .listJobs({ repoId, status: status ?? undefined, page, size: this.size })
              .pipe(catchError(() => of(null)))
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly rows = computed<JobListItem[]>(() => this.jobs()?.content ?? []);

  protected readonly columns: Column[] = [
    { key: 'module', label: 'Module', sortable: true },
    {
      key: 'status', label: 'Status', sortable: true, kind: 'badge',
      badgeClass: (r) => `badge-${r.status.toLowerCase()}`,
    },
    { key: 'esObjectId', label: 'Node', sortable: true, cssClass: 'mono' },
    { key: 'creationTimestamp', label: 'Created', sortable: true, kind: 'date' },
    { key: 'errorMessage', label: 'Error', cssClass: 'err' },
  ];

  protected readonly hasSubJobs = (job: JobListItem): boolean => job.subJobs.length > 0;

  setStatus(value: string): void {
    this.status.set(value ? (value as RenderingJobStatus) : null);
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
    if (!confirm(`Really delete job ${id}? The job and its sub-jobs will be removed.`)) {
      return;
    }
    this.api.deleteJob({ id }).subscribe({
      next: () => {
        this.refreshTick.update((v) => v + 1);
        this.notify.success('Job deleted.');
      },
      error: () => this.notify.error('Deletion failed.'),
    });
  }
}
