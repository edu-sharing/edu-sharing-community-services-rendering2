// TypeScript models that mirror the backend DTOs of the /admin API exactly.
// (Alternatively these could be generated from the OpenAPI `administration` contract —
//  see plan; hand-maintained here so the scaffold builds without a running backend.)

export type RenderingJobStatus =
  | 'QUEUED' | 'PROCESSING' | 'FINISHED' | 'FAILED' | 'PARTIALLY_FAILED';

export type SubJobStatus = 'QUEUED' | 'PROCESSING' | 'FINISHED' | 'FAILED';

export interface RegistrationInfo {
  repoId: string;
  url: string;
  publicKey: string;
  domains: string[];
}

export interface ModuleSettingInfo {
  credentialKeys: string[];
  cspHeader: string | null;
}

export interface RepositoryDetailInfo {
  repoId: string;
  url: string;
  domains: string[];
  optionalModules: string[];
  modules: Record<string, ModuleSettingInfo>;
  quota: number;
  renderingBucket: string | null;
  tempBucket: string | null;
  allowedOrigins: string[];
  allowedOriginPatterns: string[];
  lastAllowedOriginSync: number;
  signingAlgorithm: string;
  publicKeyPreview: string;
}

export interface BucketUsageInfo {
  name: string;
  size: number;
}

export interface StorageUsageInfo {
  repoId: string;
  totalSize: number;
  quota: number | null;
  usedPercent: number | null;
  exact: boolean;
  buckets: BucketUsageInfo[];
}

export interface SubJobStats {
  queued: number;
  processing: number;
  finished: number;
  failed: number;
  total: number;
}

export interface JobStatsInfo {
  repoId: string;
  queued: number;
  processing: number;
  finished: number;
  failed: number;
  partiallyFailed: number;
  total: number;
  subJobs: SubJobStats;
}

export interface SubJobInfo {
  id: string;
  routingKey: string;
  status: SubJobStatus;
  quality: number;
  progress: number;
  errorMessage: string | null;
}

export interface JobListItem {
  id: string;
  module: string;
  status: RenderingJobStatus;
  esObjectId: string;
  esObjectType: string;
  mimeType: string;
  creationTimestamp: number;
  finishedTimestamp: number | null;
  errorMessage: string | null;
  subJobs: SubJobInfo[];
}

export interface JobPage {
  content: JobListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AssetInfo {
  nodeId: string;
  hash: string;
  type: string;
  bucket: string;
  size: number;
  lastAccessed: number;
}

export interface AssetPage {
  content: AssetInfo[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AssetTypeInfo {
  type: string;
  count: number;
  totalSize: number;
}

export interface AssetNode {
  nodeId: string;
  type: string;
  bucket: string;
  hash: string;
  size: number;
  lastAccessed: number;
  versionCount: number;
  totalSize: number;
}

export interface AssetNodePage {
  content: AssetNode[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AssetDeletionResult {
  deleted: number;
}
