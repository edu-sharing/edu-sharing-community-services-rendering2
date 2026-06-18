import { Component, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AdminApiService } from '../../core/admin-api.service';
import { BytesPipe, EpochPipe } from '../../core/format';
import { RepoContextService } from '../../core/repo-context.service';
import { ModuleSettingInfo } from '../../core/models';

@Component({
  selector: 'app-repo',
  imports: [BytesPipe, EpochPipe],
  templateUrl: './repo.html',
  styleUrl: './repo.scss',
})
export class Repo {
  private readonly api = inject(AdminApiService);
  private readonly repoCtx = inject(RepoContextService);
  private readonly repoId$ = toObservable(this.repoCtx.activeRepoId);

  protected readonly detail = toSignal(
    this.repoId$.pipe(
      switchMap((id) =>
        id ? this.api.repoDetails(id).pipe(catchError(() => of(null))) : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected readonly moduleEntries = computed<[string, ModuleSettingInfo][]>(() =>
    Object.entries(this.detail()?.modules ?? {}),
  );
}
