import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ADMIN_API_BASE } from './api.config';
import {
  AssetDeletionResult, AssetInfo, AssetNodePage, AssetPage, AssetTypeInfo,
  JobPage, JobStatsInfo, RegistrationInfo, RenderingJobStatus,
  RepositoryDetailInfo, StorageUsageInfo,
} from './models';

/**
 * Typed-Client für die /admin-Endpoints des Rendering-Service. Alle fachlichen Endpoints
 * sind auf eine repoId gescopt (siehe Plan – repoId als "große Klammer").
 */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(ADMIN_API_BASE) + '/admin';

  // --- Repos ---
  listRepos(): Observable<RegistrationInfo[]> {
    return this.http.get<RegistrationInfo[]>(`${this.base}/repository/register`);
  }

  repoDetails(repoId: string): Observable<RepositoryDetailInfo> {
    return this.http.get<RepositoryDetailInfo>(`${this.base}/repository/details`, {
      params: new HttpParams().set('repoId', repoId),
    });
  }

  // --- Storage ---
  storageUsage(repoId: string, exact = false): Observable<StorageUsageInfo> {
    return this.http.get<StorageUsageInfo>(`${this.base}/storage/usage`, {
      params: new HttpParams().set('repoId', repoId).set('exact', exact),
    });
  }

  // --- Jobs ---
  jobStats(repoId: string): Observable<JobStatsInfo> {
    return this.http.get<JobStatsInfo>(`${this.base}/jobs/stats`, {
      params: new HttpParams().set('repoId', repoId),
    });
  }

  listJobs(repoId: string, status: RenderingJobStatus | null, page: number, size: number): Observable<JobPage> {
    let params = new HttpParams().set('repoId', repoId).set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<JobPage>(`${this.base}/jobs`, { params });
  }

  deleteJob(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/jobs/${id}`);
  }

  // --- Assets ---
  listAssets(repoId: string, type: string | null, page: number, size: number): Observable<AssetPage> {
    let params = new HttpParams().set('repoId', repoId).set('page', page).set('size', size);
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<AssetPage>(`${this.base}/assets`, { params });
  }

  /** Nach nodeId gebündelte Asset-Liste (neueste Version je Node). */
  listAssetNodes(repoId: string, type: string | null, page: number, size: number): Observable<AssetNodePage> {
    let params = new HttpParams().set('repoId', repoId).set('page', page).set('size', size);
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<AssetNodePage>(`${this.base}/assets/nodes`, { params });
  }

  /** Alle Versionen einer nodeId (für die aufklappbare Detailsicht). */
  listAssetVersions(repoId: string, nodeId: string): Observable<AssetInfo[]> {
    return this.http.get<AssetInfo[]>(`${this.base}/assets/versions`, {
      params: new HttpParams().set('repoId', repoId).set('nodeId', nodeId),
    });
  }

  assetTypes(repoId: string): Observable<AssetTypeInfo[]> {
    return this.http.get<AssetTypeInfo[]>(`${this.base}/assets/types`, {
      params: new HttpParams().set('repoId', repoId),
    });
  }

  /** Einzelne Version (mit hash) löschen. */
  deleteAssetVersion(repoId: string, nodeId: string, hash: string): Observable<AssetDeletionResult> {
    return this.http.delete<AssetDeletionResult>(`${this.base}/assets`, {
      params: new HttpParams().set('repoId', repoId).set('nodeId', nodeId).set('hash', hash),
    });
  }

  /** Alle Versionen einer nodeId löschen (hash weggelassen). */
  deleteAssetNode(repoId: string, nodeId: string): Observable<AssetDeletionResult> {
    return this.http.delete<AssetDeletionResult>(`${this.base}/assets`, {
      params: new HttpParams().set('repoId', repoId).set('nodeId', nodeId),
    });
  }

  deleteAssetsByType(repoId: string, type: string): Observable<AssetDeletionResult> {
    return this.http.delete<AssetDeletionResult>(`${this.base}/assets/by-type`, {
      params: new HttpParams().set('repoId', repoId).set('type', type),
    });
  }

  deleteAllAssets(repoId: string): Observable<AssetDeletionResult> {
    return this.http.delete<AssetDeletionResult>(`${this.base}/assets/all`, {
      params: new HttpParams().set('repoId', repoId),
    });
  }
}
