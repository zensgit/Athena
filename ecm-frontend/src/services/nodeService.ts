import api from './api';
import {
  Node,
  CheckoutInfo,
  LockInfo,
  LockType,
  SearchCriteria,
  Version,
  Permission,
  PermissionType,
  PdfAnnotation,
  PdfAnnotationState,
} from 'types';

interface CreateFolderRequest {
  name: string;
  description?: string;
  isSmart?: boolean;
  queryCriteria?: Record<string, any>;
}

interface FolderResponse {
  id: string;
  name: string;
  description?: string;
  path: string;
  parentId?: string;
  folderType: string;
  inheritPermissions?: boolean;
  smart?: boolean;
  queryCriteria?: Record<string, any>;
  createdBy: string;
  createdDate: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
}

// API response node structure (different from frontend Node type)
interface ApiNodeResponse {
  id: string;
  name: string;
  description?: string;
  path: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  size?: number;
  createdBy: string;
  createdDate: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
  contentType?: string;
  correspondentId?: string;
  correspondentName?: string;
  locked?: boolean;
  lockedBy?: string;
  checkedOut?: boolean;
  checkoutUser?: string;
  checkoutDate?: string;
  currentVersionLabel?: string;
  properties?: Record<string, any>;
  metadata?: Record<string, any>;
  aspects?: string[];
  tags?: string[];
  categories?: string[];
  inheritPermissions?: boolean;
  previewStatus?: string;
  previewFailureReason?: string;
  previewFailureCategory?: string;
}

interface ApiNodeDetailsResponse {
  id: string;
  name: string;
  description?: string;
  path: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  size?: number;
  contentType?: string;
  currentVersionLabel?: string;
  correspondentId?: string;
  correspondentName?: string;
  properties?: Record<string, any>;
  metadata?: Record<string, any>;
  aspects?: string[];
  tags?: string[];
  categories?: string[];
  inheritPermissions?: boolean;
  locked?: boolean;
  lockedBy?: string;
  checkedOut?: boolean;
  checkoutUser?: string;
  checkoutDate?: string;
  previewStatus?: string;
  previewFailureReason?: string;
  previewFailureCategory?: string;
  createdBy: string;
  createdDate: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
}

interface ApiVersionResponse {
  id: string;
  documentId?: string;
  versionLabel: string;
  comment?: string;
  createdDate: string;
  creator: string;
  size: number;
  major: boolean;
  mimeType?: string;
  contentHash?: string;
  contentId?: string;
  status?: string;
  checkoutBaseline?: boolean;
  checkoutCurrent?: boolean;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface UploadResponse {
  success: boolean;
  documentId?: string;
  contentId?: string;
  filename?: string;
  processingTimeMs?: number;
  errors?: Record<string, string>;
}

interface SearchPagePayload {
  content: any[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
}

interface SearchQueryEnvelopeResponse {
  results?: SearchPagePayload | null;
  facets?: Record<string, { value: string; count: number }[]> | null;
  suggestions?: string[] | null;
  stats?: AdvancedSearchStats | null;
  pivot?: AdvancedSearchPivotStatsApiResponse | null;
}

export interface AdvancedSearchFacetStat {
  value: string;
  count: number;
}

export interface AdvancedSearchStats {
  query: string | null;
  normalizedQuery: string | null;
  hasFilters: boolean;
  totalHits: number;
  facetFieldCount: number;
  previewStatusStats: AdvancedSearchFacetStat[];
  mimeTypeStats: AdvancedSearchFacetStat[];
  createdByStats: AdvancedSearchFacetStat[];
  fileSizeRangeStats: AdvancedSearchFacetStat[];
  createdDateRangeStats: AdvancedSearchFacetStat[];
  generatedAt?: string | null;
}

export interface AdvancedSearchPivotStatsCell {
  rowValue: string;
  columnValue: string;
  count: number;
}

interface AdvancedSearchPivotStatsMatrixCellApi {
  mimeType?: string | null;
  count?: number | null;
}

interface AdvancedSearchPivotStatsMatrixRowApi {
  previewStatus?: string | null;
  mimeTypeCounts?: AdvancedSearchPivotStatsMatrixCellApi[] | null;
}

export interface AdvancedSearchPivotStats {
  query: string | null;
  normalizedQuery: string | null;
  hasFilters: boolean;
  totalHits: number;
  rowField: string;
  columnField: string;
  cells: AdvancedSearchPivotStatsCell[];
  generatedAt?: string | null;
}

export interface UnifiedSearchEnvelopeResult {
  nodes: Node[];
  total: number;
  facets?: Record<string, { value: string; count: number }[]>;
  suggestions?: string[];
  stats?: AdvancedSearchStats | null;
  pivot?: AdvancedSearchPivotStats | null;
}

export interface NodeRelationsSummary {
  nodeId: string;
  nodeType: string;
  parentCount: number;
  childCount: number;
  sourceRelationCount: number;
  targetRelationCount: number;
  versionCount: number;
  previewStatus: string | null;
  renditionAvailable: boolean;
  checkedOut: boolean;
  checkoutUser?: string | null;
  checkoutDate?: string | null;
}

export interface NodeRelationNodeRef {
  id: string;
  name: string;
  path: string;
  nodeType: string;
  parentId?: string | null;
}

export interface NodeRelationEdge {
  relationId: string;
  relationType: string;
  source: NodeRelationNodeRef;
  target: NodeRelationNodeRef;
  createdDate?: string | null;
}

export interface NodeRenditionRelationSummary {
  nodeId: string;
  document: boolean;
  previewStatus: string | null;
  renditionAvailable: boolean;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  currentVersionLabel?: string | null;
}

export interface NodeRenditionRelation {
  nodeId: string;
  renditionId: string;
  label: string;
  status: string;
  available: boolean;
  mimeType: string;
  url: string;
  downloadable: boolean;
  failureReason?: string | null;
  failureCategory?: string | null;
  previewLastUpdated?: string | null;
  currentVersionLabel?: string | null;
}

export interface NodeRenditionDefinitionStatus {
  nodeId: string;
  renditionKey: string;
  label: string;
  targetMimeType: string;
  generationMode?: string | null;
  downloadable: boolean;
  sortOrder: number;
  dependencyRenditionKey?: string | null;
  registered: boolean;
  applicable: boolean;
  applicabilityReason?: string | null;
  currentState?: string | null;
  available: boolean;
  contentUrl?: string | null;
  canRequeue: boolean;
  canInvalidate: boolean;
  mutationBlockedReason?: string | null;
}

export interface NodeRenditionMutationResponse {
  renditionKey: string;
  action: string;
  invalidated: boolean;
  previewLinked: boolean;
  message?: string | null;
  queueStatus?: {
    documentId: string;
    previewStatus?: string | null;
    queued: boolean;
    attempts: number;
    nextAttemptAt?: string | null;
    message?: string | null;
  } | null;
  previewSummary?: NodeRenditionRelationSummary | null;
  resource: {
    id: string;
    documentId: string;
    renditionKey: string;
    label: string;
    mimeType: string;
    state: string;
    available: boolean;
    downloadable: boolean;
    applicable: boolean;
    applicabilityReason?: string | null;
    generationMode?: string | null;
    dependencyRenditionKey?: string | null;
    contentUrl?: string | null;
    errorReason?: string | null;
    errorCategory?: string | null;
    sourceStatus?: string | null;
    versionLabel?: string | null;
    sourceUpdatedAt?: string | null;
    lastSyncedAt?: string | null;
    sortOrder: number;
  };
}

export interface NodeCheckoutRelation {
  nodeId: string;
  document: boolean;
  checkedOut: boolean;
  checkoutUser?: string | null;
  checkoutDate?: string | null;
  checkoutBaselineVersionId?: string | null;
  checkoutBaselineVersionLabel?: string | null;
  currentVersionLabel?: string | null;
  canCheckout: boolean;
  canCheckIn: boolean;
  canCancelCheckout: boolean;
  canKeepCheckedOut: boolean;
  requiresNewVersionFile: boolean;
  blockingReason?: string | null;
}

export interface NodeCheckoutGraphNode {
  id: string;
  kind: string;
  label: string;
  focus: boolean;
  virtualNode: boolean;
  available: boolean;
}

export interface NodeCheckoutGraphEdge {
  relationType: string;
  sourceId: string;
  targetId: string;
  label: string;
}

export interface NodeCheckoutGraph {
  nodeId: string;
  document: boolean;
  checkedOut: boolean;
  checkoutUser?: string | null;
  checkoutDate?: string | null;
  documentNode?: NodeCheckoutGraphNode | null;
  workingCopyNode?: NodeCheckoutGraphNode | null;
  destinationNode?: NodeCheckoutGraphNode | null;
  baselineVersion?: Version | null;
  currentVersion?: Version | null;
  nodes: NodeCheckoutGraphNode[];
  edges: NodeCheckoutGraphEdge[];
  canCheckIn: boolean;
  canCancelCheckout: boolean;
  canKeepCheckedOut: boolean;
  blockingReason?: string | null;
}

export interface CheckoutLineage {
  documentId: string;
  checkout: CheckoutInfo;
  baselineVersion?: Version | null;
  currentVersion?: Version | null;
}

export type BatchDownloadAsyncStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'FAILED';

export interface BatchDownloadAsyncTask {
  taskId: string;
  name: string;
  createdBy?: string | null;
  filename: string;
  status: BatchDownloadAsyncStatus | string;
  nodeIds: string[];
  totalFiles: number;
  filesAdded: number;
  totalBytes: number;
  bytesAdded: number;
  createdAt: string;
  startedAt?: string | null;
  completedAt?: string | null;
  errorMessage?: string | null;
  downloadUrl?: string | null;
  cleanupUrl?: string | null;
  archiveSizeBytes?: number | null;
  retentionExpiresAt?: string | null;
  cleanupEligible: boolean;
  artifactPresent: boolean;
  cancellable: boolean;
  downloadReady: boolean;
}

export interface BatchDownloadAsyncTaskListResponse {
  items: BatchDownloadAsyncTask[];
  totalCount: number;
  activeCount: number;
  paging?: {
    maxItems: number;
    skipCount: number;
    totalItems: number;
    hasMoreItems: boolean;
  };
}

export interface BatchDownloadAsyncTaskSummaryResponse {
  totalCount: number;
  activeCount: number;
  terminalCount: number;
  queuedCount: number;
  runningCount: number;
  cancelRequestedCount: number;
  cancelledCount: number;
  completedCount: number;
  failedCount: number;
}

export interface BatchDownloadAsyncTaskCleanupResponse {
  taskId?: string;
  deletedCount: number;
  remainingCount: number;
  statusFilter?: string | null;
  message: string;
}

export interface BatchDownloadAsyncTaskCancelActiveResponse {
  cancelledCount: number;
  remainingActiveCount: number;
  statusFilter?: string | null;
  message: string;
}

export type BatchDownloadPreflightOutcome =
  | 'INCLUDED'
  | 'MISSING'
  | 'DELETED'
  | 'FORBIDDEN'
  | 'EMPTY_FOLDER';

export type BatchDownloadPreflightDecision = 'READY' | 'PARTIAL' | 'BLOCKED';
export type BatchDownloadPreflightPrimaryReason =
  | 'NONE'
  | 'DUPLICATE_REFERENCES'
  | 'MISSING_NODES'
  | 'DELETED_NODES'
  | 'FORBIDDEN_NODES'
  | 'EMPTY_FOLDERS'
  | 'NO_READABLE_FILES';

export interface BatchDownloadPreflightItem {
  nodeId?: string | null;
  nodeName?: string | null;
  nodeType?: string | null;
  outcome: BatchDownloadPreflightOutcome | string;
  includedFiles: number;
  includedBytes: number;
  message: string;
}

export interface BatchDownloadPreflightResponse {
  requestedCount: number;
  distinctCount: number;
  duplicateCount: number;
  includedNodeIds: string[];
  includedNodeCount: number;
  includedFileCount: number;
  includedBytes: number;
  missingCount: number;
  deletedCount: number;
  forbiddenCount: number;
  emptyFolderCount: number;
  skippedCount: number;
  executable: boolean;
  decision: BatchDownloadPreflightDecision | string;
  primaryReason: BatchDownloadPreflightPrimaryReason | string;
  message: string;
  warnings: string[];
  items: BatchDownloadPreflightItem[];
}

interface AdvancedSearchPivotStatsApiResponse {
  query?: string | null;
  normalizedQuery?: string | null;
  hasFilters?: boolean;
  totalHits?: number;
  rowField?: string | null;
  columnField?: string | null;
  cells?: AdvancedSearchPivotStatsCell[] | null;
  matrix?: AdvancedSearchPivotStatsMatrixRowApi[] | null;
  generatedAt?: string | null;
}

export interface PreviewQueueSearchBatchItem {
  documentId: string | null;
  outcome: string;
  message: string | null;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  queueState?: string | null;
  attempts: number;
  nextAttemptAt: string | null;
}

export interface PreviewQueueSearchBatchResult {
  query: string | null;
  reason: string | null;
  maxDocuments: number;
  workerCount?: number;
  totalCandidates: number;
  scanned: number;
  matched: number;
  scanSkipped?: number;
  truncated: boolean;
  reasonBreakdown: PreviewQueueSearchReasonCount[];
  skipBreakdown?: PreviewQueueSearchSkipCount[];
  requested: number;
  deduplicated: number;
  queued: number;
  skipped: number;
  failed: number;
  results: PreviewQueueSearchBatchItem[];
}

export interface PreviewQueueSearchCapabilities {
  defaultMaxDocuments: number;
  maxMaxDocuments: number;
  scanPageSize: number;
  scanLimit: number;
  defaultWorkerCount: number;
  maxWorkerCount: number;
}

export interface PreviewQueueSearchDryRunItem {
  documentId: string | null;
  name: string | null;
  previewStatus: string | null;
  previewFailureReason: string | null;
  previewFailureCategory: string | null;
  preflightStatus?: string | null;
  preflightSkipReason?: string | null;
  preflightRoute?: string | null;
  preflightPolicyProfile?: string | null;
  preflightPipeline?: string | null;
}

export interface PreviewQueueSearchReasonCount {
  reason: string;
  count: number;
}

export interface PreviewQueueSearchSkipCount {
  reason: string;
  count: number;
}

export interface PreviewQueueSearchDryRunResult {
  query: string | null;
  reason: string | null;
  maxDocuments: number;
  totalCandidates: number;
  scanned: number;
  matched: number;
  scanSkipped?: number;
  truncated: boolean;
  reasonBreakdown: PreviewQueueSearchReasonCount[];
  skipBreakdown?: PreviewQueueSearchSkipCount[];
  workerCount?: number;
  sampleCount: number;
  samples: PreviewQueueSearchDryRunItem[];
}

export interface PreviewQueueSearchDryRunExportAsyncTask {
  taskId: string;
  status?: string | null;
  error?: string | null;
  message?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
}

export interface PreviewQueueSearchDryRunExportAsyncTaskStatus {
  taskId: string;
  status?: string | null;
  error?: string | null;
  message?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
}

export interface PreviewQueueSearchDryRunExportAsyncTaskList {
  count: number;
  items: PreviewQueueSearchDryRunExportAsyncTaskStatus[];
}

export interface PreviewQueueSearchDryRunExportAsyncTaskSummary {
  total: number;
  queued: number;
  running: number;
  completed: number;
  cancelled: number;
  failed: number;
  terminal: number;
  active: number;
}

export interface PreviewQueueSearchDryRunExportAsyncTaskCleanupResult {
  deletedCount: number;
  remainingCount: number;
  status?: string | null;
  message?: string | null;
}

export type PreviewQueueSearchDryRunExportAsyncTaskActiveStatusFilter = 'QUEUED' | 'RUNNING';

export interface PreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult {
  cancelledCount: number;
  remainingActiveCount: number;
  // Backend SearchController record uses `statusFilter` (not `status`) for
  // this endpoint only; the parallel cleanup result uses `status`.
  statusFilter?: string | null;
  message?: string | null;
}

export interface SearchDiagnostics {
  username: string | null;
  admin: boolean;
  readFilterApplied: boolean;
  authorityCount: number;
  authoritySample: string[];
  note?: string | null;
  generatedAt?: string | null;
}

export interface SearchIndexStats {
  indexName: string;
  documentCount?: number;
  searchEnabled: boolean;
  error?: string;
}

export interface SearchRebuildStatus {
  inProgress: boolean;
  documentsIndexed: number;
}

export interface PreviewQueueStatus {
  documentId: string;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  queued: boolean;
  attempts?: number;
  nextAttemptAt?: string;
  message?: string | null;
}

export interface PreviewRepairStatus {
  documentId: string;
  readinessState: string | null;
  readinessReason: string | null;
  invalidated: boolean;
  invalidationReason: string | null;
  queued: boolean;
  queueMessage: string | null;
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
}

export interface PreviewQueueCancelStatus {
  documentId: string;
  queueState: string;
  cancelled: boolean;
  hadActiveTask: boolean;
  running: boolean;
  message?: string | null;
}

export interface OcrQueueStatus {
  documentId: string;
  ocrStatus: string | null;
  queued: boolean;
  attempts?: number;
  nextAttemptAt?: string | null;
  message?: string | null;
}

export interface PermissionDecision {
  nodeId: string | null;
  username: string | null;
  permission: PermissionType;
  allowed: boolean;
  reason: string;
  dynamicAuthority?: string | null;
  allowedAuthorities: string[];
  deniedAuthorities: string[];
}

export interface PermissionSetMetadata {
  name: string;
  label: string;
  description?: string | null;
  order?: number | null;
  permissions: PermissionType[];
}

export interface LockNodeTypedRequest {
  lockType?: LockType;
  lifetime?: 'PERSISTENT' | 'EPHEMERAL' | string;
  durationSeconds?: number;
  deep?: boolean;
  additionalInfo?: string;
}

// --- response-shape guards (shared nodeService bundle) ---
// Introduced incrementally across nodeService sub-slices; the sentinel and the
// generic helpers below are intentionally service-wide so later nodeService
// sub-slices reuse them rather than minting new sentinels/styles.
export const NODE_UNEXPECTED_RESPONSE_MESSAGE =
  'Node endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isBooleanOrNullish = (value: unknown): value is boolean | null | undefined => (
  value === null || value === undefined || typeof value === 'boolean'
);

const assertUnexpectedResponse = (): never => {
  throw new Error(NODE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isNullishOr = <T>(
  value: unknown,
  guard: (input: unknown) => input is T
): value is T | null | undefined => (
  value === undefined || value === null || guard(value)
);

const isNodeRelationNodeRef = (value: unknown): value is NodeRelationNodeRef => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.name === 'string'
  && typeof value.path === 'string'
  && typeof value.nodeType === 'string'
  && isStringOrNullish(value.parentId)
);

const isNodeRelationsSummary = (value: unknown): value is NodeRelationsSummary => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.nodeType === 'string'
  && isFiniteNumber(value.parentCount)
  && isFiniteNumber(value.childCount)
  && isFiniteNumber(value.sourceRelationCount)
  && isFiniteNumber(value.targetRelationCount)
  && isFiniteNumber(value.versionCount)
  && isStringOrNullish(value.previewStatus)
  && typeof value.renditionAvailable === 'boolean'
  && typeof value.checkedOut === 'boolean'
  && isStringOrNullish(value.checkoutUser)
  && isStringOrNullish(value.checkoutDate)
);

const isNodeRelationEdge = (value: unknown): value is NodeRelationEdge => (
  isObject(value)
  && typeof value.relationId === 'string'
  && typeof value.relationType === 'string'
  && isNodeRelationNodeRef(value.source)
  && isNodeRelationNodeRef(value.target)
  && isStringOrNullish(value.createdDate)
);

const isApiVersionResponse = (value: unknown): value is ApiVersionResponse => (
  isObject(value)
  && typeof value.id === 'string'
  && isStringOrNullish(value.documentId)
  && typeof value.versionLabel === 'string'
  && isStringOrNullish(value.comment)
  && typeof value.createdDate === 'string'
  && typeof value.creator === 'string'
  && isFiniteNumber(value.size)
  && typeof value.major === 'boolean'
  && isStringOrNullish(value.mimeType)
  && isStringOrNullish(value.contentHash)
  && isStringOrNullish(value.contentId)
  && isStringOrNullish(value.status)
  && isBooleanOrNullish(value.checkoutBaseline)
  && isBooleanOrNullish(value.checkoutCurrent)
);

const isNodeCheckoutRelation = (value: unknown): value is NodeCheckoutRelation => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.document === 'boolean'
  && typeof value.checkedOut === 'boolean'
  && isStringOrNullish(value.checkoutUser)
  && isStringOrNullish(value.checkoutDate)
  && isStringOrNullish(value.checkoutBaselineVersionId)
  && isStringOrNullish(value.checkoutBaselineVersionLabel)
  && isStringOrNullish(value.currentVersionLabel)
  && typeof value.canCheckout === 'boolean'
  && typeof value.canCheckIn === 'boolean'
  && typeof value.canCancelCheckout === 'boolean'
  && typeof value.canKeepCheckedOut === 'boolean'
  && typeof value.requiresNewVersionFile === 'boolean'
  && isStringOrNullish(value.blockingReason)
);

const isNodeCheckoutGraphNode = (value: unknown): value is NodeCheckoutGraphNode => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.kind === 'string'
  && typeof value.label === 'string'
  && typeof value.focus === 'boolean'
  && typeof value.virtualNode === 'boolean'
  && typeof value.available === 'boolean'
);

const isNodeCheckoutGraphEdge = (value: unknown): value is NodeCheckoutGraphEdge => (
  isObject(value)
  && typeof value.relationType === 'string'
  && typeof value.sourceId === 'string'
  && typeof value.targetId === 'string'
  && typeof value.label === 'string'
);

// Raw wire shape of /relations/checkout-graph (the method maps it to
// NodeCheckoutGraph; nodes/edges undefined|null are mapped to [] downstream,
// so they are optional here but, when present, every element is deep-checked).
type NodeCheckoutGraphRaw = {
  nodeId: string;
  document: boolean;
  checkedOut: boolean;
  checkoutUser?: string | null;
  checkoutDate?: string | null;
  documentNode?: NodeCheckoutGraphNode | null;
  workingCopyNode?: NodeCheckoutGraphNode | null;
  destinationNode?: NodeCheckoutGraphNode | null;
  baselineVersion?: ApiVersionResponse | null;
  currentVersion?: ApiVersionResponse | null;
  nodes?: NodeCheckoutGraphNode[] | null;
  edges?: NodeCheckoutGraphEdge[] | null;
  canCheckIn: boolean;
  canCancelCheckout: boolean;
  canKeepCheckedOut: boolean;
  blockingReason?: string | null;
};

const isOptionalNodeArray = <T>(
  value: unknown,
  guard: (input: unknown) => input is T
): boolean => (
  value === undefined
  || value === null
  || (Array.isArray(value) && value.every(guard))
);

const isNodeCheckoutGraphRaw = (value: unknown): value is NodeCheckoutGraphRaw => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.document === 'boolean'
  && typeof value.checkedOut === 'boolean'
  && isStringOrNullish(value.checkoutUser)
  && isStringOrNullish(value.checkoutDate)
  && isNullishOr(value.documentNode, isNodeCheckoutGraphNode)
  && isNullishOr(value.workingCopyNode, isNodeCheckoutGraphNode)
  && isNullishOr(value.destinationNode, isNodeCheckoutGraphNode)
  && isNullishOr(value.baselineVersion, isApiVersionResponse)
  && isNullishOr(value.currentVersion, isApiVersionResponse)
  && isOptionalNodeArray(value.nodes, isNodeCheckoutGraphNode)
  && isOptionalNodeArray(value.edges, isNodeCheckoutGraphEdge)
  && typeof value.canCheckIn === 'boolean'
  && typeof value.canCancelCheckout === 'boolean'
  && typeof value.canKeepCheckedOut === 'boolean'
  && isStringOrNullish(value.blockingReason)
);

const isNodeRenditionRelationSummary = (
  value: unknown
): value is NodeRenditionRelationSummary => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.document === 'boolean'
  && isStringOrNullish(value.previewStatus)
  && typeof value.renditionAvailable === 'boolean'
  && isStringOrNullish(value.previewFailureReason)
  && isStringOrNullish(value.previewFailureCategory)
  && isStringOrNullish(value.previewLastUpdated)
  && isStringOrNullish(value.currentVersionLabel)
);

const isNodeRenditionRelation = (value: unknown): value is NodeRenditionRelation => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.renditionId === 'string'
  && typeof value.label === 'string'
  && typeof value.status === 'string'
  && typeof value.available === 'boolean'
  && typeof value.mimeType === 'string'
  && typeof value.url === 'string'
  && typeof value.downloadable === 'boolean'
  && isStringOrNullish(value.failureReason)
  && isStringOrNullish(value.failureCategory)
  && isStringOrNullish(value.previewLastUpdated)
  && isStringOrNullish(value.currentVersionLabel)
);

const isNodeRenditionDefinitionStatus = (
  value: unknown
): value is NodeRenditionDefinitionStatus => (
  isObject(value)
  && typeof value.nodeId === 'string'
  && typeof value.renditionKey === 'string'
  && typeof value.label === 'string'
  && typeof value.targetMimeType === 'string'
  && isStringOrNullish(value.generationMode)
  && typeof value.downloadable === 'boolean'
  && isFiniteNumber(value.sortOrder)
  && isStringOrNullish(value.dependencyRenditionKey)
  && typeof value.registered === 'boolean'
  && typeof value.applicable === 'boolean'
  && isStringOrNullish(value.applicabilityReason)
  && isStringOrNullish(value.currentState)
  && typeof value.available === 'boolean'
  && isStringOrNullish(value.contentUrl)
  && typeof value.canRequeue === 'boolean'
  && typeof value.canInvalidate === 'boolean'
  && isStringOrNullish(value.mutationBlockedReason)
);

const isNodeRenditionMutationQueueStatus = (value: unknown): boolean => (
  isObject(value)
  && typeof value.documentId === 'string'
  && isStringOrNullish(value.previewStatus)
  && typeof value.queued === 'boolean'
  && isFiniteNumber(value.attempts)
  && isStringOrNullish(value.nextAttemptAt)
  && isStringOrNullish(value.message)
);

const isNodeRenditionMutationResource = (value: unknown): boolean => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.documentId === 'string'
  && typeof value.renditionKey === 'string'
  && typeof value.label === 'string'
  && typeof value.mimeType === 'string'
  && typeof value.state === 'string'
  && typeof value.available === 'boolean'
  && typeof value.downloadable === 'boolean'
  && typeof value.applicable === 'boolean'
  && isStringOrNullish(value.applicabilityReason)
  && isStringOrNullish(value.generationMode)
  && isStringOrNullish(value.dependencyRenditionKey)
  && isStringOrNullish(value.contentUrl)
  && isStringOrNullish(value.errorReason)
  && isStringOrNullish(value.errorCategory)
  && isStringOrNullish(value.sourceStatus)
  && isStringOrNullish(value.versionLabel)
  && isStringOrNullish(value.sourceUpdatedAt)
  && isStringOrNullish(value.lastSyncedAt)
  && isFiniteNumber(value.sortOrder)
);

const isNodeRenditionMutationResponse = (
  value: unknown
): value is NodeRenditionMutationResponse => (
  isObject(value)
  && typeof value.renditionKey === 'string'
  && typeof value.action === 'string'
  && typeof value.invalidated === 'boolean'
  && typeof value.previewLinked === 'boolean'
  && isStringOrNullish(value.message)
  && (value.queueStatus === undefined
    || value.queueStatus === null
    || isNodeRenditionMutationQueueStatus(value.queueStatus))
  && isNullishOr(value.previewSummary, isNodeRenditionRelationSummary)
  && isNodeRenditionMutationResource(value.resource)
);

const assertResponse = <T>(
  value: unknown,
  guard: (input: unknown) => input is T
): T => (guard(value) ? value : assertUnexpectedResponse());

const assertResponseArray = <T>(
  value: unknown,
  guard: (input: unknown) => input is T
): T[] => {
  if (!Array.isArray(value) || !value.every(guard)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const assertPageResponse = <T>(
  value: unknown,
  itemGuard: (input: unknown) => input is T
): PageResponse<T> => {
  if (
    !isObject(value)
    || !Array.isArray(value.content)
    || !value.content.every(itemGuard)
    || !isFiniteNumber(value.totalElements)
    || !isFiniteNumber(value.totalPages)
    || !isFiniteNumber(value.number)
    || !isFiniteNumber(value.size)
  ) {
    return assertUnexpectedResponse();
  }
  return value as unknown as PageResponse<T>;
};

// --- batch-download async predicates (sub-slice 2, reuses the bundle above) ---
// Union-typed wire fields (status / decision / primaryReason / outcome) are
// validated as strings only, not against the enum (gate G3). UI-read nesting
// is deep-checked (gate G4). The list-response uses a dedicated predicate,
// not assertPageResponse (the shape is items/totalCount/activeCount/paging,
// not the PageResponse content/totalElements/... shape).

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

const isBatchDownloadAsyncTask = (value: unknown): value is BatchDownloadAsyncTask => (
  isObject(value)
  && typeof value.taskId === 'string'
  && typeof value.name === 'string'
  && isStringOrNullish(value.createdBy)
  && typeof value.filename === 'string'
  && typeof value.status === 'string'
  && isStringArray(value.nodeIds)
  && isFiniteNumber(value.totalFiles)
  && isFiniteNumber(value.filesAdded)
  && isFiniteNumber(value.totalBytes)
  && isFiniteNumber(value.bytesAdded)
  && typeof value.createdAt === 'string'
  && isStringOrNullish(value.startedAt)
  && isStringOrNullish(value.completedAt)
  && isStringOrNullish(value.errorMessage)
  && isStringOrNullish(value.downloadUrl)
  && isStringOrNullish(value.cleanupUrl)
  && isNullishOr(value.archiveSizeBytes, isFiniteNumber)
  && isStringOrNullish(value.retentionExpiresAt)
  && typeof value.cleanupEligible === 'boolean'
  && typeof value.artifactPresent === 'boolean'
  && typeof value.cancellable === 'boolean'
  && typeof value.downloadReady === 'boolean'
);

const isBatchDownloadPreflightItem = (
  value: unknown
): value is BatchDownloadPreflightItem => (
  isObject(value)
  && isStringOrNullish(value.nodeId)
  && isStringOrNullish(value.nodeName)
  && isStringOrNullish(value.nodeType)
  && typeof value.outcome === 'string'
  && isFiniteNumber(value.includedFiles)
  && isFiniteNumber(value.includedBytes)
  && typeof value.message === 'string'
);

const isBatchDownloadPreflightResponse = (
  value: unknown
): value is BatchDownloadPreflightResponse => (
  isObject(value)
  && isFiniteNumber(value.requestedCount)
  && isFiniteNumber(value.distinctCount)
  && isFiniteNumber(value.duplicateCount)
  && isStringArray(value.includedNodeIds)
  && isFiniteNumber(value.includedNodeCount)
  && isFiniteNumber(value.includedFileCount)
  && isFiniteNumber(value.includedBytes)
  && isFiniteNumber(value.missingCount)
  && isFiniteNumber(value.deletedCount)
  && isFiniteNumber(value.forbiddenCount)
  && isFiniteNumber(value.emptyFolderCount)
  && isFiniteNumber(value.skippedCount)
  && typeof value.executable === 'boolean'
  && typeof value.decision === 'string'
  && typeof value.primaryReason === 'string'
  && typeof value.message === 'string'
  && isStringArray(value.warnings)
  && Array.isArray(value.items)
  && value.items.every(isBatchDownloadPreflightItem)
);

const isBatchDownloadAsyncTaskListPaging = (value: unknown): boolean => (
  isObject(value)
  && isFiniteNumber(value.maxItems)
  && isFiniteNumber(value.skipCount)
  && isFiniteNumber(value.totalItems)
  && typeof value.hasMoreItems === 'boolean'
);

const isBatchDownloadAsyncTaskListResponse = (
  value: unknown
): value is BatchDownloadAsyncTaskListResponse => (
  isObject(value)
  && Array.isArray(value.items)
  && value.items.every(isBatchDownloadAsyncTask)
  && isFiniteNumber(value.totalCount)
  && isFiniteNumber(value.activeCount)
  && (value.paging === undefined || isBatchDownloadAsyncTaskListPaging(value.paging))
);

const isBatchDownloadAsyncTaskSummaryResponse = (
  value: unknown
): value is BatchDownloadAsyncTaskSummaryResponse => (
  isObject(value)
  && isFiniteNumber(value.totalCount)
  && isFiniteNumber(value.activeCount)
  && isFiniteNumber(value.terminalCount)
  && isFiniteNumber(value.queuedCount)
  && isFiniteNumber(value.runningCount)
  && isFiniteNumber(value.cancelRequestedCount)
  && isFiniteNumber(value.cancelledCount)
  && isFiniteNumber(value.completedCount)
  && isFiniteNumber(value.failedCount)
);

// Single guard tolerant of both shapes (gate correction): bulk cleanup carries
// statusFilter and omits taskId; single-task cleanup carries taskId and omits
// statusFilter. Tests cover both separately.
const isBatchDownloadAsyncTaskCleanupResponse = (
  value: unknown
): value is BatchDownloadAsyncTaskCleanupResponse => (
  isObject(value)
  && isStringOrNullish(value.taskId)
  && isFiniteNumber(value.deletedCount)
  && isFiniteNumber(value.remainingCount)
  && isStringOrNullish(value.statusFilter)
  && typeof value.message === 'string'
);

const isBatchDownloadAsyncTaskCancelActiveResponse = (
  value: unknown
): value is BatchDownloadAsyncTaskCancelActiveResponse => (
  isObject(value)
  && isFiniteNumber(value.cancelledCount)
  && isFiniteNumber(value.remainingActiveCount)
  && isStringOrNullish(value.statusFilter)
  && typeof value.message === 'string'
);

// --- preview-side predicates (sub-slice 3, reuses the bundle above) ---
// Reason/Skip-count and Task/TaskStatus shapes are structurally identical
// pairs; each DTO keeps its own named entry point (per-DTO discipline) while
// sharing an internal shape helper.

// Optional but NOT nullable finite number (`?: number`, no `| null`).
// isNullishOr(_, isFiniteNumber) is too permissive here because it accepts
// null; the preview-side DTOs declare these as `?: number` only.
const isOptionalFiniteNumber = (value: unknown): boolean => (
  value === undefined || isFiniteNumber(value)
);

const isReasonOrSkipCountShape = (value: unknown): boolean => (
  isObject(value)
  && typeof value.reason === 'string'
  && isFiniteNumber(value.count)
);

const isPreviewQueueSearchReasonCount = (
  value: unknown
): value is PreviewQueueSearchReasonCount => isReasonOrSkipCountShape(value);

const isPreviewQueueSearchSkipCount = (
  value: unknown
): value is PreviewQueueSearchSkipCount => isReasonOrSkipCountShape(value);

const isPreviewQueueSearchBatchItem = (
  value: unknown
): value is PreviewQueueSearchBatchItem => (
  isObject(value)
  && isStringOrNullish(value.documentId)
  && typeof value.outcome === 'string'
  && isStringOrNullish(value.message)
  && isStringOrNullish(value.previewStatus)
  && isStringOrNullish(value.previewFailureReason)
  && isStringOrNullish(value.previewFailureCategory)
  && isStringOrNullish(value.previewLastUpdated)
  && isStringOrNullish(value.queueState)
  && isFiniteNumber(value.attempts)
  && isStringOrNullish(value.nextAttemptAt)
);

const isPreviewQueueSearchDryRunItem = (
  value: unknown
): value is PreviewQueueSearchDryRunItem => (
  isObject(value)
  && isStringOrNullish(value.documentId)
  && isStringOrNullish(value.name)
  && isStringOrNullish(value.previewStatus)
  && isStringOrNullish(value.previewFailureReason)
  && isStringOrNullish(value.previewFailureCategory)
  && isStringOrNullish(value.preflightStatus)
  && isStringOrNullish(value.preflightSkipReason)
  && isStringOrNullish(value.preflightRoute)
  && isStringOrNullish(value.preflightPolicyProfile)
  && isStringOrNullish(value.preflightPipeline)
);

// Shared inner shape: Task (method 8) and TaskStatus (methods 9/14) carry the
// same wire fields. Optional fields stay optional — the backend omits
// error/message/finishedAt/filename in QUEUED/RUNNING states (gate H2).
const isExportAsyncTaskShape = (value: unknown): boolean => (
  isObject(value)
  && typeof value.taskId === 'string'
  && isStringOrNullish(value.status)
  && isStringOrNullish(value.error)
  && isStringOrNullish(value.message)
  && isStringOrNullish(value.createdAt)
  && isStringOrNullish(value.finishedAt)
  && isStringOrNullish(value.filename)
);

const isPreviewQueueSearchDryRunExportAsyncTask = (
  value: unknown
): value is PreviewQueueSearchDryRunExportAsyncTask => isExportAsyncTaskShape(value);

const isPreviewQueueSearchDryRunExportAsyncTaskStatus = (
  value: unknown
): value is PreviewQueueSearchDryRunExportAsyncTaskStatus => isExportAsyncTaskShape(value);

const isPreviewQueueStatus = (value: unknown): value is PreviewQueueStatus => (
  isObject(value)
  && typeof value.documentId === 'string'
  && isStringOrNullish(value.previewStatus)
  && isStringOrNullish(value.previewFailureReason)
  && isStringOrNullish(value.previewFailureCategory)
  && isStringOrNullish(value.previewLastUpdated)
  && typeof value.queued === 'boolean'
  && isOptionalFiniteNumber(value.attempts)
  && isStringOrNullish(value.nextAttemptAt)
  && isStringOrNullish(value.message)
);

const isPreviewQueueCancelStatus = (
  value: unknown
): value is PreviewQueueCancelStatus => (
  isObject(value)
  && typeof value.documentId === 'string'
  && typeof value.queueState === 'string'
  && typeof value.cancelled === 'boolean'
  && typeof value.hadActiveTask === 'boolean'
  && typeof value.running === 'boolean'
  && isStringOrNullish(value.message)
);

const isPreviewRepairStatus = (
  value: unknown
): value is PreviewRepairStatus => (
  isObject(value)
  && typeof value.documentId === 'string'
  && isStringOrNullish(value.readinessState)
  && isStringOrNullish(value.readinessReason)
  && typeof value.invalidated === 'boolean'
  && isStringOrNullish(value.invalidationReason)
  && typeof value.queued === 'boolean'
  && isStringOrNullish(value.queueMessage)
  && isStringOrNullish(value.previewStatus)
  && isStringOrNullish(value.previewFailureReason)
  && isStringOrNullish(value.previewFailureCategory)
  && isStringOrNullish(value.previewLastUpdated)
);

const isOcrQueueStatus = (value: unknown): value is OcrQueueStatus => (
  isObject(value)
  && typeof value.documentId === 'string'
  && isStringOrNullish(value.ocrStatus)
  && typeof value.queued === 'boolean'
  && isOptionalFiniteNumber(value.attempts)
  && isStringOrNullish(value.nextAttemptAt)
  && isStringOrNullish(value.message)
);

const isPreviewQueueSearchCapabilities = (
  value: unknown
): value is PreviewQueueSearchCapabilities => (
  isObject(value)
  && isFiniteNumber(value.defaultMaxDocuments)
  && isFiniteNumber(value.maxMaxDocuments)
  && isFiniteNumber(value.scanPageSize)
  && isFiniteNumber(value.scanLimit)
  && isFiniteNumber(value.defaultWorkerCount)
  && isFiniteNumber(value.maxWorkerCount)
);

const isPreviewQueueSearchBatchResult = (
  value: unknown
): value is PreviewQueueSearchBatchResult => (
  isObject(value)
  && isStringOrNullish(value.query)
  && isStringOrNullish(value.reason)
  && isFiniteNumber(value.maxDocuments)
  && isOptionalFiniteNumber(value.workerCount)
  && isFiniteNumber(value.totalCandidates)
  && isFiniteNumber(value.scanned)
  && isFiniteNumber(value.matched)
  && isOptionalFiniteNumber(value.scanSkipped)
  && typeof value.truncated === 'boolean'
  && Array.isArray(value.reasonBreakdown)
  && value.reasonBreakdown.every(isPreviewQueueSearchReasonCount)
  && (value.skipBreakdown === undefined
    || (Array.isArray(value.skipBreakdown)
      && value.skipBreakdown.every(isPreviewQueueSearchSkipCount)))
  && isFiniteNumber(value.requested)
  && isFiniteNumber(value.deduplicated)
  && isFiniteNumber(value.queued)
  && isFiniteNumber(value.skipped)
  && isFiniteNumber(value.failed)
  && Array.isArray(value.results)
  && value.results.every(isPreviewQueueSearchBatchItem)
);

const isPreviewQueueSearchDryRunResult = (
  value: unknown
): value is PreviewQueueSearchDryRunResult => (
  isObject(value)
  && isStringOrNullish(value.query)
  && isStringOrNullish(value.reason)
  && isFiniteNumber(value.maxDocuments)
  && isFiniteNumber(value.totalCandidates)
  && isFiniteNumber(value.scanned)
  && isFiniteNumber(value.matched)
  && isOptionalFiniteNumber(value.scanSkipped)
  && typeof value.truncated === 'boolean'
  && Array.isArray(value.reasonBreakdown)
  && value.reasonBreakdown.every(isPreviewQueueSearchReasonCount)
  && (value.skipBreakdown === undefined
    || (Array.isArray(value.skipBreakdown)
      && value.skipBreakdown.every(isPreviewQueueSearchSkipCount)))
  && isOptionalFiniteNumber(value.workerCount)
  && isFiniteNumber(value.sampleCount)
  && Array.isArray(value.samples)
  && value.samples.every(isPreviewQueueSearchDryRunItem)
);

const isPreviewQueueSearchDryRunExportAsyncTaskList = (
  value: unknown
): value is PreviewQueueSearchDryRunExportAsyncTaskList => (
  isObject(value)
  && isFiniteNumber(value.count)
  && Array.isArray(value.items)
  && value.items.every(isPreviewQueueSearchDryRunExportAsyncTaskStatus)
);

const isPreviewQueueSearchDryRunExportAsyncTaskSummary = (
  value: unknown
): value is PreviewQueueSearchDryRunExportAsyncTaskSummary => (
  isObject(value)
  && isFiniteNumber(value.total)
  && isFiniteNumber(value.queued)
  && isFiniteNumber(value.running)
  && isFiniteNumber(value.completed)
  && isFiniteNumber(value.cancelled)
  && isFiniteNumber(value.failed)
  && isFiniteNumber(value.terminal)
  && isFiniteNumber(value.active)
);

// Backend `status` here (cleanup endpoint) is intentional and differs from
// the cancel-active record's `statusFilter` — see SearchController:849-861.
const isPreviewQueueSearchDryRunExportAsyncTaskCleanupResult = (
  value: unknown
): value is PreviewQueueSearchDryRunExportAsyncTaskCleanupResult => (
  isObject(value)
  && isFiniteNumber(value.deletedCount)
  && isFiniteNumber(value.remainingCount)
  && isStringOrNullish(value.status)
  && isStringOrNullish(value.message)
);

const isPreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult = (
  value: unknown
): value is PreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult => (
  isObject(value)
  && isFiniteNumber(value.cancelledCount)
  && isFiniteNumber(value.remainingActiveCount)
  && isStringOrNullish(value.statusFilter)
  && isStringOrNullish(value.message)
);

// --- search proper (Group A) predicates ---
// Per H2: a single shared envelope predicate validates SearchQueryEnvelopeResponse
// for all callers (searchNodes POST path, searchNodesEnvelope); all five sub-
// fields are optional+nullable and validated only when present.
// Per H3: search-result items only require id/name/path; other mapper-read
// fields are tolerated as optional/nullish so partial/historical responses
// still pass.

const isFacetValueCount = (
  value: unknown
): value is { value: string; count: number } => (
  isObject(value)
  && typeof value.value === 'string'
  && isFiniteNumber(value.count)
);

const isRecordOfFacetValueCounts = (
  value: unknown
): value is Record<string, { value: string; count: number }[]> => {
  if (!isObject(value)) return false;
  return Object.values(value).every(
    (entry) => Array.isArray(entry) && entry.every(isFacetValueCount)
  );
};

const isSearchResultItem = (value: unknown): value is Record<string, unknown> => {
  if (!isObject(value)) return false;
  if (typeof value.id !== 'string') return false;
  if (typeof value.name !== 'string') return false;
  if (typeof value.path !== 'string') return false;
  // mapper-read optional fields (gate H3 — tolerate missing/nullish)
  if (!isStringOrNullish(value.createdDate)) return false;
  if (!isStringOrNullish(value.createdBy)) return false;
  if (!isStringOrNullish(value.lastModifiedDate)) return false;
  if (!isStringOrNullish(value.lastModifiedBy)) return false;
  if (!isStringOrNullish(value.parentId)) return false;
  if (!isStringOrNullish(value.description)) return false;
  if (!isStringOrNullish(value.nodeType)) return false;
  if (!isStringOrNullish(value.mimeType)) return false;
  if (!isStringOrNullish(value.currentVersionLabel)) return false;
  if (!isStringOrNullish(value.previewStatus)) return false;
  if (!isStringOrNullish(value.previewFailureReason)) return false;
  if (!isStringOrNullish(value.previewFailureCategory)) return false;
  if (!isStringOrNullish(value.correspondent)) return false;
  if (!isStringOrNullish(value.highlightSummary)) return false;
  if (!isStringOrNullish(value.declaredBy)) return false;
  if (!isStringOrNullish(value.declaredAt)) return false;
  if (!isStringOrNullish(value.declaredVersionLabel)) return false;
  if (!isStringOrNullish(value.declarationComment)) return false;
  if (!isStringOrNullish(value.recordCategoryId)) return false;
  if (!isStringOrNullish(value.recordCategoryName)) return false;
  if (!isStringOrNullish(value.recordCategoryPath)) return false;
  if (!isOptionalFiniteNumber(value.fileSize)) return false;
  if (!isOptionalFiniteNumber(value.score)) return false;
  if (!(value.record === undefined || value.record === null || typeof value.record === 'boolean')) return false;
  if (!(value.tags === undefined || value.tags === null || isStringArray(value.tags))) return false;
  if (!(value.categories === undefined || value.categories === null || isStringArray(value.categories))) return false;
  if (!(value.matchFields === undefined || value.matchFields === null || isStringArray(value.matchFields))) return false;
  if (value.highlights !== undefined && value.highlights !== null) {
    if (!isObject(value.highlights)) return false;
    if (!Object.values(value.highlights).every(isStringArray)) return false;
  }
  return true;
};

const isSearchPagePayload = (value: unknown): value is SearchPagePayload => (
  isObject(value)
  && Array.isArray(value.content)
  && value.content.every(isSearchResultItem)
  && isOptionalFiniteNumber(value.totalElements)
  && isOptionalFiniteNumber(value.totalPages)
  && isOptionalFiniteNumber(value.number)
  && isOptionalFiniteNumber(value.size)
);

const isAdvancedSearchFacetStat = (
  value: unknown
): value is AdvancedSearchFacetStat => (
  isObject(value)
  && typeof value.value === 'string'
  && isFiniteNumber(value.count)
);

const isAdvancedSearchStats = (
  value: unknown
): value is AdvancedSearchStats => (
  isObject(value)
  && isStringOrNullish(value.query)
  && isStringOrNullish(value.normalizedQuery)
  && typeof value.hasFilters === 'boolean'
  && isFiniteNumber(value.totalHits)
  && isFiniteNumber(value.facetFieldCount)
  && Array.isArray(value.previewStatusStats)
  && value.previewStatusStats.every(isAdvancedSearchFacetStat)
  && Array.isArray(value.mimeTypeStats)
  && value.mimeTypeStats.every(isAdvancedSearchFacetStat)
  && Array.isArray(value.createdByStats)
  && value.createdByStats.every(isAdvancedSearchFacetStat)
  && Array.isArray(value.fileSizeRangeStats)
  && value.fileSizeRangeStats.every(isAdvancedSearchFacetStat)
  && Array.isArray(value.createdDateRangeStats)
  && value.createdDateRangeStats.every(isAdvancedSearchFacetStat)
  && isStringOrNullish(value.generatedAt)
);

const isAdvancedSearchPivotStatsMatrixCellApi = (value: unknown): boolean => (
  isObject(value)
  && isStringOrNullish(value.mimeType)
  && (value.count === undefined || value.count === null || isFiniteNumber(value.count))
);

const isAdvancedSearchPivotStatsMatrixRowApi = (value: unknown): boolean => {
  if (!isObject(value)) return false;
  if (!isStringOrNullish(value.previewStatus)) return false;
  if (value.mimeTypeCounts !== undefined && value.mimeTypeCounts !== null) {
    if (!Array.isArray(value.mimeTypeCounts)) return false;
    if (!value.mimeTypeCounts.every(isAdvancedSearchPivotStatsMatrixCellApi)) return false;
  }
  return true;
};

const isAdvancedSearchPivotStatsCell = (
  value: unknown
): value is AdvancedSearchPivotStatsCell => (
  isObject(value)
  && typeof value.rowValue === 'string'
  && typeof value.columnValue === 'string'
  && isFiniteNumber(value.count)
);

const isAdvancedSearchPivotStatsApiResponse = (value: unknown): boolean => {
  if (!isObject(value)) return false;
  if (!isStringOrNullish(value.query)) return false;
  if (!isStringOrNullish(value.normalizedQuery)) return false;
  if (!(value.hasFilters === undefined || typeof value.hasFilters === 'boolean')) return false;
  if (!isOptionalFiniteNumber(value.totalHits)) return false;
  if (!isStringOrNullish(value.rowField)) return false;
  if (!isStringOrNullish(value.columnField)) return false;
  if (value.cells !== undefined && value.cells !== null) {
    if (!Array.isArray(value.cells)) return false;
    if (!value.cells.every(isAdvancedSearchPivotStatsCell)) return false;
  }
  if (value.matrix !== undefined && value.matrix !== null) {
    if (!Array.isArray(value.matrix)) return false;
    if (!value.matrix.every(isAdvancedSearchPivotStatsMatrixRowApi)) return false;
  }
  if (!isStringOrNullish(value.generatedAt)) return false;
  return true;
};

const isSearchQueryEnvelopeResponse = (
  value: unknown
): value is SearchQueryEnvelopeResponse => {
  if (!isObject(value)) return false;
  if (value.results !== undefined && value.results !== null && !isSearchPagePayload(value.results)) return false;
  if (value.facets !== undefined && value.facets !== null && !isRecordOfFacetValueCounts(value.facets)) return false;
  if (value.suggestions !== undefined && value.suggestions !== null && !isStringArray(value.suggestions)) return false;
  if (value.stats !== undefined && value.stats !== null && !isAdvancedSearchStats(value.stats)) return false;
  if (value.pivot !== undefined && value.pivot !== null && !isAdvancedSearchPivotStatsApiResponse(value.pivot)) return false;
  return true;
};

const isSearchDiagnostics = (value: unknown): value is SearchDiagnostics => (
  isObject(value)
  && isStringOrNullish(value.username)
  && typeof value.admin === 'boolean'
  && typeof value.readFilterApplied === 'boolean'
  && isFiniteNumber(value.authorityCount)
  && isStringArray(value.authoritySample)
  && isStringOrNullish(value.note)
  && isStringOrNullish(value.generatedAt)
);

const isSearchIndexStats = (value: unknown): value is SearchIndexStats => (
  isObject(value)
  && typeof value.indexName === 'string'
  && isOptionalFiniteNumber(value.documentCount)
  && typeof value.searchEnabled === 'boolean'
  && (value.error === undefined || typeof value.error === 'string')
);

const isSearchRebuildStatus = (value: unknown): value is SearchRebuildStatus => (
  isObject(value)
  && typeof value.inProgress === 'boolean'
  && isFiniteNumber(value.documentsIndexed)
);

const isSuggestedFilterItem = (
  value: unknown
): value is { field: string; label: string; value: string; count?: number } => (
  isObject(value)
  && typeof value.field === 'string'
  && typeof value.label === 'string'
  && typeof value.value === 'string'
  && isOptionalFiniteNumber(value.count)
);

class NodeService {
  private buildSearchFilters(criteria: SearchCriteria): Record<string, any> {
    const filters: Record<string, any> = {};

    if (criteria.mimeTypes?.length) {
      filters.mimeTypes = criteria.mimeTypes;
    } else if (criteria.contentType) {
      filters.mimeTypes = [criteria.contentType];
    }
    if (criteria.locked !== undefined) {
      filters.locked = criteria.locked;
    }
    if (criteria.lockedBy?.trim()) {
      filters.lockedBy = criteria.lockedBy.trim();
    }
    if (criteria.checkedOut !== undefined) {
      filters.checkedOut = criteria.checkedOut;
    }
    if (criteria.checkoutUser?.trim()) {
      filters.checkoutUser = criteria.checkoutUser.trim();
    }
    if (criteria.recordOnly !== undefined) {
      filters.recordOnly = criteria.recordOnly;
    }
    if (criteria.recordCategoryPaths?.length) {
      filters.recordCategoryPaths = criteria.recordCategoryPaths;
    }
    if (criteria.createdByList?.length) {
      filters.createdByList = criteria.createdByList;
    } else if (criteria.createdBy) {
      filters.createdBy = criteria.createdBy;
    }
    if (criteria.tags?.length) {
      filters.tags = criteria.tags;
    }
    if (criteria.aspects?.length) {
      filters.aspects = criteria.aspects;
    }
    if (criteria.properties && Object.keys(criteria.properties).length > 0) {
      filters.properties = criteria.properties;
    }
    if (criteria.categories?.length) {
      filters.categories = criteria.categories;
    }
    if (criteria.correspondents?.length) {
      filters.correspondents = criteria.correspondents;
    }
    if (criteria.minSize !== undefined) {
      filters.minSize = criteria.minSize;
    }
    if (criteria.maxSize !== undefined) {
      filters.maxSize = criteria.maxSize;
    }
    if (criteria.createdFrom) {
      filters.dateFrom = criteria.createdFrom;
    }
    if (criteria.createdTo) {
      filters.dateTo = criteria.createdTo;
    }
    if (criteria.modifiedFrom) {
      filters.modifiedFrom = criteria.modifiedFrom;
    }
    if (criteria.modifiedTo) {
      filters.modifiedTo = criteria.modifiedTo;
    }
    if (criteria.path) {
      filters.path = criteria.path;
    }
    if (criteria.folderId) {
      filters.folderId = criteria.folderId;
      filters.includeChildren = criteria.includeChildren ?? true;
    }
    if (criteria.previewStatuses?.length) {
      filters.previewStatuses = criteria.previewStatuses;
    }

    return filters;
  }

  private pickPrimaryRoot(roots: FolderResponse[]): FolderResponse {
    if (!roots || roots.length === 0) {
      throw new Error('No root folder found');
    }

    const isSystemRoot = (folder: FolderResponse) => folder.folderType?.toUpperCase() === 'SYSTEM';
    const isRootPath = (folder: FolderResponse) => folder.path === '/Root' || folder.path === '/root';
    const isRootName = (folder: FolderResponse) => folder.name === 'Root' || folder.name === 'root';
    const createdDateAsc = (a: FolderResponse, b: FolderResponse) => a.createdDate.localeCompare(b.createdDate);

    const preferred = roots.filter(isSystemRoot).filter(isRootPath).filter(isRootName);
    if (preferred.length > 0) {
      return [...preferred].sort(createdDateAsc)[0];
    }

    const systemByPath = roots.filter(isSystemRoot).filter(isRootPath);
    if (systemByPath.length > 0) {
      return [...systemByPath].sort(createdDateAsc)[0];
    }

    const system = roots.filter(isSystemRoot);
    if (system.length > 0) {
      return [...system].sort(createdDateAsc)[0];
    }

    return [...roots].sort(createdDateAsc)[0];
  }

  private async getRootFolder(): Promise<FolderResponse> {
    const roots = await api.get<FolderResponse[]>('/folders/roots');
    return this.pickPrimaryRoot(roots);
  }

  private folderToNode(folder: FolderResponse): Node {
    return {
      id: folder.id,
      name: folder.name,
      path: folder.path,
      nodeType: 'FOLDER',
      parentId: folder.parentId,
      properties: folder.description ? { description: folder.description } : {},
      aspects: [],
      created: folder.createdDate,
      modified: folder.lastModifiedDate || folder.createdDate,
      creator: folder.createdBy,
      modifier: folder.lastModifiedBy || folder.createdBy,
      description: folder.description,
      inheritPermissions: folder.inheritPermissions,
      smart: folder.smart,
      queryCriteria: folder.queryCriteria,
    };
  }

  private apiNodeToNode(apiNode: ApiNodeResponse): Node {
    const properties = apiNode.properties || { description: apiNode.description };
    return {
      id: apiNode.id,
      name: apiNode.name,
      path: apiNode.path,
      nodeType: apiNode.nodeType,
      parentId: apiNode.parentId,
      properties,
      metadata: apiNode.metadata || {},
      aspects: apiNode.aspects || [],
      created: apiNode.createdDate,
      modified: apiNode.lastModifiedDate || apiNode.createdDate,
      creator: apiNode.createdBy,
      modifier: apiNode.lastModifiedBy || apiNode.createdBy,
      size: apiNode.size,
      contentType: apiNode.contentType,
      currentVersionLabel: apiNode.currentVersionLabel,
      description: apiNode.description,
      tags: apiNode.tags,
      categories: apiNode.categories,
      inheritPermissions: apiNode.inheritPermissions,
      correspondentId: apiNode.correspondentId,
      correspondent: apiNode.correspondentName,
      locked: apiNode.locked,
      lockedBy: apiNode.lockedBy,
      checkedOut: apiNode.checkedOut,
      checkoutUser: apiNode.checkoutUser,
      checkoutDate: apiNode.checkoutDate,
      previewStatus: apiNode.previewStatus,
      previewFailureReason: apiNode.previewFailureReason,
      previewFailureCategory: apiNode.previewFailureCategory,
      record: Boolean(
        apiNode.aspects?.includes('rm:record')
          || properties['rm:declaredAt']
          || properties['rm:declaredBy']
      ),
      declaredBy: properties['rm:declaredBy'],
      declaredAt: properties['rm:declaredAt'],
      declaredVersionLabel: properties['rm:declaredVersionLabel'],
      declarationComment: properties['rm:declarationComment'],
      recordCategoryId: properties['rm:recordCategoryId'],
      recordCategoryName: properties['rm:recordCategoryName'],
      recordCategoryPath: properties['rm:recordCategoryPath'],
    };
  }

  private apiNodeDetailsToNode(apiNode: ApiNodeDetailsResponse): Node {
    const createdBy = apiNode.createdBy || '';
    const createdDate = apiNode.createdDate || new Date().toISOString();
    return {
      id: apiNode.id,
      name: apiNode.name,
      path: apiNode.path,
      nodeType: apiNode.nodeType,
      parentId: apiNode.parentId,
      properties: apiNode.properties || { description: apiNode.description },
      metadata: apiNode.metadata || {},
      aspects: apiNode.aspects || [],
      created: createdDate,
      modified: apiNode.lastModifiedDate || createdDate,
      creator: createdBy,
      modifier: apiNode.lastModifiedBy || createdBy,
      size: apiNode.size,
      contentType: apiNode.contentType,
      currentVersionLabel: apiNode.currentVersionLabel,
      description: apiNode.description,
      tags: apiNode.tags,
      categories: apiNode.categories,
      correspondentId: apiNode.correspondentId,
      correspondent: apiNode.correspondentName,
      inheritPermissions: apiNode.inheritPermissions,
      locked: apiNode.locked,
      lockedBy: apiNode.lockedBy,
      checkedOut: apiNode.checkedOut,
      checkoutUser: apiNode.checkoutUser,
      checkoutDate: apiNode.checkoutDate,
      previewStatus: apiNode.previewStatus,
      previewFailureReason: apiNode.previewFailureReason,
      previewFailureCategory: apiNode.previewFailureCategory,
      record: Boolean(
        apiNode.aspects?.includes('rm:record')
          || apiNode.properties?.['rm:declaredAt']
          || apiNode.properties?.['rm:declaredBy']
      ),
      declaredBy: apiNode.properties?.['rm:declaredBy'],
      declaredAt: apiNode.properties?.['rm:declaredAt'],
      declaredVersionLabel: apiNode.properties?.['rm:declaredVersionLabel'],
      declarationComment: apiNode.properties?.['rm:declarationComment'],
      recordCategoryId: apiNode.properties?.['rm:recordCategoryId'],
      recordCategoryName: apiNode.properties?.['rm:recordCategoryName'],
      recordCategoryPath: apiNode.properties?.['rm:recordCategoryPath'],
    };
  }

  async getFolderByPath(path: string): Promise<Node> {
    const folder = await api.get<FolderResponse>('/folders/path', { params: { path } });
    return this.folderToNode(folder);
  }

  async getNode(nodeId: string): Promise<Node> {
    // Handle special "root" case by fetching first root folder
    if (nodeId === 'root') {
      const root = await this.getRootFolder();
      return this.folderToNode(root);
    }
    // /nodes/{id} works for both folders and documents; avoid noisy 404s from probing /folders/{id}.
    const node = await api.get<ApiNodeDetailsResponse>(`/nodes/${nodeId}`);
    return this.apiNodeDetailsToNode(node);
  }

  async getLockInfo(nodeId: string): Promise<LockInfo> {
    return await api.get<LockInfo>(`/nodes/${nodeId}/lock-info`);
  }

  async lockNodeTyped(nodeId: string, request: LockNodeTypedRequest): Promise<LockInfo> {
    return api.post<LockInfo>(`/nodes/${nodeId}/lock-typed`, null, {
      params: {
        lockType: request.lockType ?? 'WRITE_LOCK',
        lifetime: request.lifetime ?? 'PERSISTENT',
        durationSeconds: request.durationSeconds,
        deep: request.deep ?? false,
        additionalInfo: request.additionalInfo?.trim() || undefined,
      },
    });
  }

  async unlockNode(nodeId: string): Promise<void> {
    await api.post<void>(`/nodes/${nodeId}/unlock`);
  }

  async unlockNodeDeep(nodeId: string, unlockChildren = true): Promise<void> {
    await api.post<void>(`/nodes/${nodeId}/unlock-deep`, null, {
      params: { unlockChildren },
    });
  }

  async getCheckoutInfo(nodeId: string): Promise<CheckoutInfo> {
    return await api.get<CheckoutInfo>(`/documents/${nodeId}/checkout-info`);
  }

  async getCheckoutLineage(nodeId: string): Promise<CheckoutLineage> {
    const response = await api.get<{
      documentId: string;
      checkout: CheckoutInfo;
      baselineVersion?: ApiVersionResponse | null;
      currentVersion?: ApiVersionResponse | null;
    }>(`/documents/${nodeId}/checkout-lineage`);

    const mapVersion = (version?: ApiVersionResponse | null): Version | null => {
      if (!version) {
        return null;
      }
      return {
        id: version.id,
        documentId: version.documentId || nodeId,
        versionLabel: version.versionLabel,
        comment: version.comment,
        created: version.createdDate,
        creator: version.creator,
        size: version.size,
        isMajor: version.major,
        mimeType: version.mimeType,
        contentHash: version.contentHash,
        contentId: version.contentId,
        status: version.status,
        checkoutBaseline: version.checkoutBaseline,
        checkoutCurrent: version.checkoutCurrent,
      };
    };

    return {
      documentId: response.documentId,
      checkout: response.checkout,
      baselineVersion: mapVersion(response.baselineVersion),
      currentVersion: mapVersion(response.currentVersion),
    };
  }

  async checkoutDocument(nodeId: string): Promise<Node> {
    const node = await api.post<ApiNodeDetailsResponse>(`/documents/${nodeId}/checkout`);
    return this.apiNodeDetailsToNode(node);
  }

  async cancelCheckoutDocument(nodeId: string): Promise<Node> {
    const node = await api.post<ApiNodeDetailsResponse>(`/documents/${nodeId}/cancel-checkout`);
    return this.apiNodeDetailsToNode(node);
  }

  async checkinDocument(
    nodeId: string,
    options?: {
      file?: File | null;
      comment?: string;
      majorVersion?: boolean;
      keepCheckedOut?: boolean;
    },
  ): Promise<Node> {
    const formData = new FormData();
    if (options?.file) {
      formData.append('file', options.file);
    }
    if (options?.comment?.trim()) {
      formData.append('comment', options.comment.trim());
    }
    formData.append('majorVersion', Boolean(options?.majorVersion).toString());
    formData.append('keepCheckedOut', Boolean(options?.keepCheckedOut).toString());

    const node = await api.post<ApiNodeDetailsResponse>(`/documents/${nodeId}/checkin`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return this.apiNodeDetailsToNode(node);
  }

  async getChildren(nodeId: string, sortBy = 'name', ascending = true): Promise<Node[]> {
    // Handle special "root" case
    if (nodeId === 'root') {
      const root = await this.getRootFolder();
      nodeId = root.id;
    }
    // Use folder contents endpoint (paginated in backend; request a large page size for tree/list views)
    try {
      const sortDirection = ascending ? 'asc' : 'desc';
      const response = await api.get<{ content: ApiNodeResponse[] }>(`/folders/${nodeId}/contents`, {
        params: {
          page: 0,
          size: 1000,
          sort: `${sortBy},${sortDirection}`,
        },
      });
      const apiNodes = response.content || [];
      return apiNodes.map(node => this.apiNodeToNode(node));
    } catch {
      // Fall back to node children endpoint
      const response = await api.get<{ content: ApiNodeDetailsResponse[] }>(`/nodes/${nodeId}/children`, {
        params: { sortBy, ascending },
      });
      const apiNodes = response.content || [];
      return apiNodes.map((node) => this.apiNodeDetailsToNode(node));
    }
  }

  async getChildrenPage(
    nodeId: string,
    sortBy = 'name',
    ascending = true,
    page = 0,
    size = 50
  ): Promise<{ nodes: Node[]; total: number }> {
    if (nodeId === 'root') {
      const root = await this.getRootFolder();
      nodeId = root.id;
    }
    try {
      const sortDirection = ascending ? 'asc' : 'desc';
      const response = await api.get<{
        content: ApiNodeResponse[];
        totalElements?: number;
        number?: number;
        size?: number;
      }>(`/folders/${nodeId}/contents`, {
        params: {
          page,
          size,
          sort: `${sortBy},${sortDirection}`,
        },
      });
      const apiNodes = response.content || [];
      const total = response.totalElements ?? apiNodes.length;
      return { nodes: apiNodes.map((node) => this.apiNodeToNode(node)), total };
    } catch {
      const response = await api.get<{ content: ApiNodeDetailsResponse[] }>(`/nodes/${nodeId}/children`, {
        params: { sortBy, ascending },
      });
      const apiNodes = response.content || [];
      return { nodes: apiNodes.map((node) => this.apiNodeDetailsToNode(node)), total: apiNodes.length };
    }
  }

  async createFolder(parentId: string, request: CreateFolderRequest): Promise<Node> {
    // Handle special "root" case
    if (parentId === 'root') {
      const root = await this.getRootFolder();
      parentId = root.id;
    }
    const folder = await api.post<FolderResponse>('/folders', {
      name: request.name,
      description: request.description,
      parentId: parentId,
      folderType: 'GENERAL',
      inheritPermissions: true,
      isSmart: request.isSmart === true ? true : undefined,
      queryCriteria: request.isSmart === true ? request.queryCriteria : undefined,
    });
    return this.folderToNode(folder);
  }

  async uploadDocument(parentId: string, file: File, properties?: Record<string, any>, onProgress?: (progress: number) => void): Promise<Node> {
    // Handle special "root" case
    if (parentId === 'root') {
      const root = await this.getRootFolder();
      parentId = root.id;
    }

    const url = `/documents/upload?folderId=${encodeURIComponent(parentId)}`;
    const response = (await api.uploadFile(url, file, onProgress)) as UploadResponse;
    if (!response?.success || !response.documentId) {
      throw new Error('Upload failed');
    }
    return this.getNode(response.documentId);
  }

  async downloadDocument(nodeId: string): Promise<void> {
    const node = await this.getNode(nodeId);
    return api.downloadFile(`/nodes/${nodeId}/content`, node.name);
  }

  async downloadNodesAsZip(nodeIds: string[], name = 'archive'): Promise<void> {
    if (!nodeIds.length) {
      return;
    }

    const idsParam = nodeIds.map((id) => encodeURIComponent(id)).join(',');
    const safeName = name || 'archive';
    const url = `/nodes/download/batch?ids=${idsParam}&name=${encodeURIComponent(safeName)}`;
    const filename = `${safeName}.zip`;
    return api.downloadFile(url, filename);
  }

  async startBatchDownloadAsync(nodeIds: string[], name = 'archive'): Promise<BatchDownloadAsyncTask> {
    const result = await api.post<unknown>('/nodes/download/batch-async', {
      nodeIds,
      name,
    });
    return assertResponse(result, isBatchDownloadAsyncTask);
  }

  async preflightBatchDownloadAsync(nodeIds: string[], name = 'archive'): Promise<BatchDownloadPreflightResponse> {
    const result = await api.post<unknown>('/nodes/download/batch-async/preflight', {
      nodeIds,
      name,
    });
    return assertResponse(result, isBatchDownloadPreflightResponse);
  }

  async listBatchDownloadAsyncTasks(
    limit = 10,
    status?: string,
    skipCount = 0,
    query?: string,
    owner?: string
  ): Promise<BatchDownloadAsyncTaskListResponse> {
    const result = await api.get<unknown>('/nodes/download/batch-async', {
      params: {
        maxItems: limit,
        skipCount,
        status: status || undefined,
        q: query || undefined,
        owner: owner || undefined,
      },
    });
    return assertResponse(result, isBatchDownloadAsyncTaskListResponse);
  }

  async getBatchDownloadAsyncTask(taskId: string): Promise<BatchDownloadAsyncTask> {
    const result = await api.get<unknown>(`/nodes/download/batch-async/${encodeURIComponent(taskId)}`);
    return assertResponse(result, isBatchDownloadAsyncTask);
  }

  async getBatchDownloadAsyncSummary(): Promise<BatchDownloadAsyncTaskSummaryResponse> {
    const result = await api.get<unknown>('/nodes/download/batch-async/summary');
    return assertResponse(result, isBatchDownloadAsyncTaskSummaryResponse);
  }

  async cancelBatchDownloadAsyncTask(taskId: string): Promise<BatchDownloadAsyncTask> {
    const result = await api.post<unknown>(`/nodes/download/batch-async/${encodeURIComponent(taskId)}/cancel`);
    return assertResponse(result, isBatchDownloadAsyncTask);
  }

  async cleanupBatchDownloadAsyncTasks(status?: string): Promise<BatchDownloadAsyncTaskCleanupResponse> {
    const result = await api.post<unknown>('/nodes/download/batch-async/cleanup', undefined, {
      params: {
        status: status || undefined,
      },
    });
    return assertResponse(result, isBatchDownloadAsyncTaskCleanupResponse);
  }

  async cancelActiveBatchDownloadAsyncTasks(status?: string): Promise<BatchDownloadAsyncTaskCancelActiveResponse> {
    const result = await api.post<unknown>('/nodes/download/batch-async/cancel-active', undefined, {
      params: {
        status: status || undefined,
      },
    });
    return assertResponse(result, isBatchDownloadAsyncTaskCancelActiveResponse);
  }

  async cleanupBatchDownloadAsyncTask(taskId: string): Promise<BatchDownloadAsyncTaskCleanupResponse> {
    const result = await api.post<unknown>(`/nodes/download/batch-async/${encodeURIComponent(taskId)}/cleanup`);
    return assertResponse(result, isBatchDownloadAsyncTaskCleanupResponse);
  }

  async downloadBatchDownloadAsyncTask(taskId: string, filename?: string): Promise<void> {
    const effectiveFilename = filename || `download-${taskId}.zip`;
    return api.downloadFile(
      `/nodes/download/batch-async/${encodeURIComponent(taskId)}/download`,
      effectiveFilename
    );
  }

  async updateNode(nodeId: string, updates: Record<string, any>): Promise<Node> {
    const updated = await api.patch<ApiNodeDetailsResponse>(`/nodes/${nodeId}`, updates);
    return this.apiNodeDetailsToNode(updated);
  }

  async moveNode(nodeId: string, targetParentId: string): Promise<Node> {
    const moved = await api.post<ApiNodeResponse>(`/folders/${targetParentId}/move`, {
      nodeId,
    });
    return this.apiNodeToNode(moved);
  }

  async copyNode(nodeId: string, targetParentId: string, deepCopy = true, newName?: string): Promise<Node> {
    const copied = await api.post<ApiNodeResponse>(`/folders/${targetParentId}/copy`, {
      nodeId,
      newName: newName || null,
      deep: deepCopy,
    });
    return this.apiNodeToNode(copied);
  }

  async deleteNode(nodeId: string): Promise<void> {
    return api.delete(`/nodes/${nodeId}`);
  }

  async searchNodes(criteria: SearchCriteria): Promise<{
    nodes: Node[];
    total: number;
    facets?: Record<string, { value: string; count: number }[]>;
    suggestions?: string[];
  }> {
    const query = (criteria.name || '').trim();
    const filters = this.buildSearchFilters(criteria);
    const page = criteria.page ?? 0;
    const size = criteria.size ?? 50;

    const hasNonScopeFilters = Object.keys(filters).some(
      (key) => key !== 'folderId' && key !== 'includeChildren' && key !== 'previewStatuses'
    );
    const canUseFullTextEndpoint = Boolean(query) && !hasNonScopeFilters;

    // Fast path: for simple name-only searches, use the dedicated full-text endpoint.
    // It handles punctuation (e.g. hyphens) more reliably than the Criteria-based advanced endpoint.
    if (canUseFullTextEndpoint) {
      const rawResponse = await api.get<unknown>('/search', {
          params: {
            q: query,
            page,
            size,
            sortBy: criteria.sortBy,
            sortDirection: criteria.sortDirection,
            folderId: criteria.folderId,
            includeChildren: criteria.includeChildren ?? true,
            previewStatus: criteria.previewStatuses?.length ? criteria.previewStatuses.join(',') : undefined,
          },
        });
      const response = assertResponse(rawResponse, isSearchPagePayload);

      const nodes = (response.content || []).map((item) => this.mapSearchItemToNode(item));
      return { nodes, total: response.totalElements ?? nodes.length };
    }

    const rawResponse = await api.post<unknown>('/search/query', {
          query,
          filters,
          sortBy: criteria.sortBy,
          sortDirection: criteria.sortDirection,
          pageable: { page, size },
          include: ['results', 'facets', ...(query ? ['suggestions'] : [])],
        });
    const response = assertResponse(rawResponse, isSearchQueryEnvelopeResponse);

    const resultPage = response.results || { content: [] };
    const nodes = (resultPage.content || []).map((item) => this.mapSearchItemToNode(item));
    const total = resultPage.totalElements ?? nodes.length;
    return {
      nodes,
      total,
      facets: response.facets || undefined,
      suggestions: response.suggestions || undefined,
    };
  }

  async searchNodesEnvelope(
    criteria: SearchCriteria,
    options?: {
      includeFacets?: boolean;
      includeSuggestions?: boolean;
      includeStats?: boolean;
      includePivot?: boolean;
    }
  ): Promise<UnifiedSearchEnvelopeResult> {
    const query = (criteria.name || '').trim();
    const filters = this.buildSearchFilters(criteria);
    const page = criteria.page ?? 0;
    const size = criteria.size ?? 50;
    const include = [
      'results',
      ...(options?.includeFacets ? ['facets'] : []),
      ...(options?.includeSuggestions && query ? ['suggestions'] : []),
      ...(options?.includeStats ? ['stats'] : []),
      ...(options?.includePivot ? ['pivot'] : []),
    ];

    const payload: Record<string, unknown> = {
      query,
      filters,
      sortBy: criteria.sortBy,
      sortDirection: criteria.sortDirection,
      pageable: { page, size },
      include,
    };

    if (options?.includeFacets || options?.includeStats) {
      payload.facets = ['previewStatus', 'mimeType', 'createdBy', 'fileSizeRange', 'createdDateRange'];
    }

    const response = assertResponse(
      await api.post<unknown>('/search/query', payload),
      isSearchQueryEnvelopeResponse
    );
    const resultPage = response.results || { content: [] };
    const nodes = (resultPage.content || []).map((item) => this.mapSearchItemToNode(item));
    const total = resultPage.totalElements ?? nodes.length;
    const pivotResponse = response.pivot || null;
    const pivotCells = ((pivotResponse?.matrix) || []).flatMap((row) => {
      const rowValue = (row.previewStatus || '').trim();
      return (row.mimeTypeCounts || []).map((cell) => ({
        rowValue,
        columnValue: ((cell.mimeType || '') as string).trim(),
        count: Number(cell.count || 0),
      }));
    });
    const pivotFallbackCells = ((pivotResponse?.cells) || []).map((cell) => ({
      rowValue: (cell.rowValue || '').trim(),
      columnValue: (cell.columnValue || '').trim(),
      count: Number(cell.count || 0),
    }));

    return {
      nodes,
      total,
      facets: response.facets || undefined,
      suggestions: response.suggestions || undefined,
      stats: response.stats || null,
      pivot: pivotResponse
        ? {
            query: pivotResponse.query ?? (query || null),
            normalizedQuery: pivotResponse.normalizedQuery ?? (query || null),
            hasFilters: Boolean(pivotResponse.hasFilters),
            totalHits: Number(pivotResponse.totalHits || 0),
            rowField: ((pivotResponse.rowField) || 'previewStatus').trim(),
            columnField: ((pivotResponse.columnField) || 'mimeType').trim(),
            cells: pivotCells.length > 0 ? pivotCells : pivotFallbackCells,
            generatedAt: pivotResponse.generatedAt ?? null,
          }
        : null,
    };
  }

  async getNodeRelationsSummary(nodeId: string): Promise<NodeRelationsSummary> {
    const result = await api.get<unknown>(`/nodes/${encodeURIComponent(nodeId)}/relations/summary`);
    return assertResponse(result, isNodeRelationsSummary);
  }

  async getNodeRelationParents(nodeId: string, maxDepth = 20): Promise<NodeRelationNodeRef[]> {
    const result = await api.get<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/relations/parents`,
      { params: { maxDepth } }
    );
    return assertResponseArray(result, isNodeRelationNodeRef);
  }

  async getNodeRelationSources(
    nodeId: string,
    page = 0,
    size = 5,
    relationType?: string
  ): Promise<PageResponse<NodeRelationEdge>> {
    const result = await api.get<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/relations/sources`,
      { params: { page, size, relationType: relationType || undefined } }
    );
    return assertPageResponse(result, isNodeRelationEdge);
  }

  async getNodeRelationTargets(
    nodeId: string,
    page = 0,
    size = 5,
    relationType?: string
  ): Promise<PageResponse<NodeRelationEdge>> {
    const result = await api.get<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/relations/targets`,
      { params: { page, size, relationType: relationType || undefined } }
    );
    return assertPageResponse(result, isNodeRelationEdge);
  }

  async getNodeRelationVersions(
    nodeId: string,
    page = 0,
    size = 5,
    majorOnly = false
  ): Promise<Version[]> {
    const response = assertPageResponse(
      await api.get<unknown>(
        `/nodes/${encodeURIComponent(nodeId)}/relations/versions`,
        { params: { page, size, majorOnly } }
      ),
      isApiVersionResponse
    );
    return (response.content || []).map((version) => ({
      id: version.id,
      documentId: version.documentId || nodeId,
      versionLabel: version.versionLabel,
      comment: version.comment,
      created: version.createdDate,
      creator: version.creator,
      size: version.size,
      isMajor: version.major,
      mimeType: version.mimeType,
      contentHash: version.contentHash,
      contentId: version.contentId,
      status: version.status,
      checkoutBaseline: version.checkoutBaseline,
      checkoutCurrent: version.checkoutCurrent,
    }));
  }

  async getNodeRelationCheckout(nodeId: string): Promise<NodeCheckoutRelation> {
    const result = await api.get<unknown>(`/nodes/${encodeURIComponent(nodeId)}/relations/checkout`);
    return assertResponse(result, isNodeCheckoutRelation);
  }

  async getNodeRelationCheckoutGraph(nodeId: string): Promise<NodeCheckoutGraph> {
    const graph = assertResponse(
      await api.get<unknown>(`/nodes/${encodeURIComponent(nodeId)}/relations/checkout-graph`),
      isNodeCheckoutGraphRaw
    );
    const mapVersion = (version?: ApiVersionResponse | null): Version | null => (version ? ({
      id: version.id,
      documentId: version.documentId || nodeId,
      versionLabel: version.versionLabel,
      comment: version.comment,
      created: version.createdDate,
      creator: version.creator,
      size: version.size,
      isMajor: version.major,
      mimeType: version.mimeType,
      contentHash: version.contentHash,
      contentId: version.contentId,
      status: version.status,
      checkoutBaseline: version.checkoutBaseline,
      checkoutCurrent: version.checkoutCurrent,
    }) : null);

    return {
      nodeId: graph.nodeId,
      document: graph.document,
      checkedOut: graph.checkedOut,
      checkoutUser: graph.checkoutUser,
      checkoutDate: graph.checkoutDate,
      documentNode: graph.documentNode ?? null,
      workingCopyNode: graph.workingCopyNode ?? null,
      destinationNode: graph.destinationNode ?? null,
      baselineVersion: mapVersion(graph.baselineVersion),
      currentVersion: mapVersion(graph.currentVersion),
      nodes: graph.nodes || [],
      edges: graph.edges || [],
      canCheckIn: graph.canCheckIn,
      canCancelCheckout: graph.canCancelCheckout,
      canKeepCheckedOut: graph.canKeepCheckedOut,
      blockingReason: graph.blockingReason,
    };
  }

  async getNodeRelationRenditions(
    nodeId: string,
    page = 0,
    size = 5
  ): Promise<NodeRenditionRelation[]> {
    const response = assertPageResponse(
      await api.get<unknown>(
        `/nodes/${encodeURIComponent(nodeId)}/relations/renditions`,
        { params: { page, size } }
      ),
      isNodeRenditionRelation
    );
    return response.content || [];
  }

  async getNodeRelationRendition(
    nodeId: string,
    renditionId: string
  ): Promise<NodeRenditionRelation> {
    const result = await api.get<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/relations/renditions/${encodeURIComponent(renditionId)}`
    );
    return assertResponse(result, isNodeRenditionRelation);
  }

  async getNodeRenditionRelationSummary(nodeId: string): Promise<NodeRenditionRelationSummary> {
    const result = await api.get<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/relations/renditions/summary`
    );
    return assertResponse(result, isNodeRenditionRelationSummary);
  }

  async getNodeRenditionDefinitions(nodeId: string): Promise<NodeRenditionDefinitionStatus[]> {
    const result = await api.get<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/renditions/definitions`
    );
    return assertResponseArray(result, isNodeRenditionDefinitionStatus);
  }

  async requeueNodeRendition(
    nodeId: string,
    renditionKey: string,
    force = false
  ): Promise<NodeRenditionMutationResponse> {
    const result = await api.post<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/renditions/${encodeURIComponent(renditionKey)}/requeue`,
      null,
      { params: { force } }
    );
    return assertResponse(result, isNodeRenditionMutationResponse);
  }

  async invalidateNodeRendition(
    nodeId: string,
    renditionKey: string,
    options?: {
      reason?: string;
      requeue?: boolean;
      forceQueue?: boolean;
    }
  ): Promise<NodeRenditionMutationResponse> {
    const result = await api.post<unknown>(
      `/nodes/${encodeURIComponent(nodeId)}/renditions/${encodeURIComponent(renditionKey)}/invalidate`,
      null,
      {
        params: {
          reason: options?.reason,
          requeue: options?.requeue ?? false,
          forceQueue: options?.forceQueue ?? true,
        },
      }
    );
    return assertResponse(result, isNodeRenditionMutationResponse);
  }

  async findSimilar(documentId: string, maxResults = 5): Promise<Node[]> {
    const rawResults = await api.get<unknown>(`/search/similar/${documentId}`, {
      params: { maxResults },
    });
    const results = assertResponseArray(rawResults, isSearchResultItem);
    return (results || []).map((item) => this.mapSearchItemToNode(item));
  }

  async getSearchFacets(query = ''): Promise<Record<string, { value: string; count: number }[]>> {
    const result = await api.get<unknown>('/search/facets', {
      params: { q: query },
    });
    return assertResponse(result, isRecordOfFacetValueCounts);
  }

  async getSuggestedFilters(
    query = ''
  ): Promise<Array<{ field: string; label: string; value: string; count?: number }>> {
    const result = await api.get<unknown>(
      '/search/filters/suggested',
      { params: { q: query } }
    );
    return assertResponseArray(result, isSuggestedFilterItem);
  }

  async getSpellcheckSuggestions(query = '', limit = 5): Promise<string[]> {
    const result = await api.get<unknown>('/search/spellcheck', { params: { q: query, limit } });
    return assertResponse(result, isStringArray);
  }

  async getSearchSuggestions(prefix: string, limit = 10): Promise<string[]> {
    const result = await api.get<unknown>('/search/suggestions', { params: { prefix, limit } });
    return assertResponse(result, isStringArray);
  }

  async getSearchDiagnostics(): Promise<SearchDiagnostics> {
    const result = await api.get<unknown>('/search/diagnostics');
    return assertResponse(result, isSearchDiagnostics);
  }

  async getSearchIndexStats(): Promise<SearchIndexStats> {
    const result = await api.get<unknown>('/search/index/stats');
    return assertResponse(result, isSearchIndexStats);
  }

  async getSearchRebuildStatus(): Promise<SearchRebuildStatus> {
    const result = await api.get<unknown>('/search/index/rebuild/status');
    return assertResponse(result, isSearchRebuildStatus);
  }

  async queuePreview(nodeId: string, force = false): Promise<PreviewQueueStatus> {
    const result = await api.post<unknown>(`/documents/${nodeId}/preview/queue`, null, {
      params: { force },
    });
    return assertResponse(result, isPreviewQueueStatus);
  }

  async cancelQueuedPreview(nodeId: string): Promise<PreviewQueueCancelStatus> {
    const result = await api.post<unknown>(`/documents/${nodeId}/preview/queue/cancel`);
    return assertResponse(result, isPreviewQueueCancelStatus);
  }

  async repairPreview(
    nodeId: string,
    options?: {
      forceInvalidate?: boolean;
      requeue?: boolean;
      forceQueue?: boolean;
    }
  ): Promise<PreviewRepairStatus> {
    const result = await api.post<unknown>(`/documents/${nodeId}/preview/repair`, null, {
      params: {
        forceInvalidate: options?.forceInvalidate ?? true,
        requeue: options?.requeue ?? true,
        forceQueue: options?.forceQueue ?? true,
      },
    });
    return assertResponse(result, isPreviewRepairStatus);
  }

  async queueOcr(nodeId: string, force = false): Promise<OcrQueueStatus> {
    const result = await api.post<unknown>(`/documents/${nodeId}/ocr/queue`, null, {
      params: { force },
    });
    return assertResponse(result, isOcrQueueStatus);
  }

  async getPreviewQueueBySearchCapabilities(): Promise<PreviewQueueSearchCapabilities> {
    const result = await api.get<unknown>('/search/preview/queue-failed/capabilities');
    return assertResponse(result, isPreviewQueueSearchCapabilities);
  }

  async queueFailedPreviewsBySearch(payload: {
    query?: string;
    filters?: SearchCriteria;
    sortBy?: string;
    sortDirection?: 'asc' | 'desc';
    reason?: string;
    maxDocuments?: number;
    force?: boolean;
    workerCount?: number;
  }): Promise<PreviewQueueSearchBatchResult> {
    const result = await api.post<unknown>('/search/preview/queue-failed', payload);
    return assertResponse(result, isPreviewQueueSearchBatchResult);
  }

  async dryRunFailedPreviewsBySearch(payload: {
    query?: string;
    filters?: SearchCriteria;
    sortBy?: string;
    sortDirection?: 'asc' | 'desc';
    reason?: string;
    maxDocuments?: number;
    force?: boolean;
    workerCount?: number;
  }): Promise<PreviewQueueSearchDryRunResult> {
    const result = await api.post<unknown>('/search/preview/queue-failed/dry-run', payload);
    return assertResponse(result, isPreviewQueueSearchDryRunResult);
  }

  async exportDryRunFailedPreviewsCsvBySearch(payload: {
    query?: string;
    filters?: SearchCriteria;
    sortBy?: string;
    sortDirection?: 'asc' | 'desc';
    reason?: string;
    maxDocuments?: number;
    workerCount?: number;
  }): Promise<Blob> {
    return api.post<Blob>('/search/preview/queue-failed/dry-run/export', payload, {
      responseType: 'blob',
    });
  }

  async startDryRunFailedPreviewsCsvExportAsyncBySearch(payload: {
    query?: string;
    filters?: SearchCriteria;
    sortBy?: string;
    sortDirection?: 'asc' | 'desc';
    reason?: string;
    maxDocuments?: number;
    workerCount?: number;
  }): Promise<PreviewQueueSearchDryRunExportAsyncTask> {
    const result = await api.post<unknown>('/search/preview/queue-failed/dry-run/export-async', payload);
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTask);
  }

  async getDryRunFailedPreviewsCsvExportAsyncBySearchTask(taskId: string): Promise<PreviewQueueSearchDryRunExportAsyncTaskStatus> {
    const result = await api.get<unknown>(
      `/search/preview/queue-failed/dry-run/export-async/${encodeURIComponent(taskId)}`
    );
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTaskStatus);
  }

  async listDryRunFailedPreviewsCsvExportAsyncBySearchTasks(
    limit = 20,
    status?: string
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskList> {
    const result = await api.get<unknown>(
      '/search/preview/queue-failed/dry-run/export-async',
      {
        params: {
          limit,
          ...(status ? { status } : {}),
        },
      }
    );
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTaskList);
  }

  async getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary(
    status?: string
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskSummary> {
    const result = await api.get<unknown>(
      '/search/preview/queue-failed/dry-run/export-async/summary',
      {
        params: status ? { status } : undefined,
      }
    );
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTaskSummary);
  }

  async cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks(
    status?: string
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskCleanupResult> {
    const result = await api.post<unknown>(
      '/search/preview/queue-failed/dry-run/export-async/cleanup',
      null,
      {
        params: status ? { status } : undefined,
      }
    );
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTaskCleanupResult);
  }

  async cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks(
    status?: PreviewQueueSearchDryRunExportAsyncTaskActiveStatusFilter
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult> {
    const result = await api.post<unknown>(
      '/search/preview/queue-failed/dry-run/export-async/cancel-active',
      {},
      {
        params: status ? { status } : undefined,
      }
    );
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult);
  }

  async cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask(taskId: string): Promise<PreviewQueueSearchDryRunExportAsyncTaskStatus> {
    const result = await api.post<unknown>(
      `/search/preview/queue-failed/dry-run/export-async/${encodeURIComponent(taskId)}/cancel`
    );
    return assertResponse(result, isPreviewQueueSearchDryRunExportAsyncTaskStatus);
  }

  async downloadDryRunFailedPreviewsCsvExportAsyncBySearch(taskId: string): Promise<Blob> {
    return api.getBlob(`/search/preview/queue-failed/dry-run/export-async/${encodeURIComponent(taskId)}/download`);
  }

  private mapSearchItemToNode(item: any): Node {
    const inferredNodeType = item.mimeType || item.fileSize
      ? 'DOCUMENT'
      : (item.nodeType === 'FOLDER' || item.nodeType === 'DOCUMENT' ? item.nodeType : 'FOLDER');
    const properties: Record<string, any> = { description: item.description };
    if (item.declaredBy) properties['rm:declaredBy'] = item.declaredBy;
    if (item.declaredAt) properties['rm:declaredAt'] = item.declaredAt;
    if (item.declaredVersionLabel) properties['rm:declaredVersionLabel'] = item.declaredVersionLabel;
    if (item.declarationComment) properties['rm:declarationComment'] = item.declarationComment;
    if (item.recordCategoryId) properties['rm:recordCategoryId'] = item.recordCategoryId;
    if (item.recordCategoryName) properties['rm:recordCategoryName'] = item.recordCategoryName;
    if (item.recordCategoryPath) properties['rm:recordCategoryPath'] = item.recordCategoryPath;
    const isRecord = Boolean(
      item.record
        || item.declaredBy
        || item.declaredAt
        || item.declaredVersionLabel
        || item.recordCategoryId
        || item.recordCategoryName
        || item.recordCategoryPath
    );

    return ({
      id: item.id,
      name: item.name,
      path: item.path,
      nodeType: inferredNodeType,
      parentId: item.parentId,
      properties,
      aspects: isRecord ? ['rm:record'] : [],
      created: item.createdDate,
      modified: item.lastModifiedDate || item.createdDate,
      creator: item.createdBy,
      modifier: item.lastModifiedBy || item.createdBy,
      size: item.fileSize,
      contentType: item.mimeType,
      currentVersionLabel: item.currentVersionLabel,
      description: item.description,
      highlights: item.highlights,
      matchFields: item.matchFields,
      highlightSummary: item.highlightSummary,
      tags: item.tags,
      categories: item.categories,
      correspondent: item.correspondent,
      previewStatus: item.previewStatus,
      previewFailureReason: item.previewFailureReason,
      previewFailureCategory: item.previewFailureCategory,
      score: item.score,
      record: isRecord,
      declaredBy: item.declaredBy,
      declaredAt: item.declaredAt,
      declaredVersionLabel: item.declaredVersionLabel,
      declarationComment: item.declarationComment,
      recordCategoryId: item.recordCategoryId,
      recordCategoryName: item.recordCategoryName,
      recordCategoryPath: item.recordCategoryPath,
    } as Node);
  }

  async addAspect(nodeId: string, aspect: string, properties?: Record<string, any>): Promise<Node> {
    return api.post<Node>(`/nodes/${nodeId}/aspects/${aspect}`, properties);
  }

  async removeAspect(nodeId: string, aspect: string): Promise<Node> {
    return api.delete<Node>(`/nodes/${nodeId}/aspects/${aspect}`);
  }

  // ---- peer / secondary-child associations --------------------------------

  async getTargetAssociations(nodeId: string, assocType?: string): Promise<NodeRelationEdge[]> {
    const params = assocType ? { assocType } : {};
    return api.get<NodeRelationEdge[]>(`/nodes/${nodeId}/targets`, { params });
  }

  async createTargetAssociation(nodeId: string, targetId: string, assocType = 'cm:references'): Promise<NodeRelationEdge> {
    return api.post<NodeRelationEdge>(`/nodes/${nodeId}/targets`, null, {
      params: { targetId, assocType },
    });
  }

  async removeTargetAssociation(nodeId: string, targetId: string): Promise<void> {
    return api.delete(`/nodes/${nodeId}/targets/${targetId}`);
  }

  async getSourceAssociations(nodeId: string, assocType?: string): Promise<NodeRelationEdge[]> {
    const params = assocType ? { assocType } : {};
    return api.get<NodeRelationEdge[]>(`/nodes/${nodeId}/sources`, { params });
  }

  async addSecondaryChild(parentId: string, childId: string): Promise<NodeRelationEdge> {
    return api.post<NodeRelationEdge>(`/nodes/${parentId}/secondary-children`, null, {
      params: { childId },
    });
  }

  async removeSecondaryChild(parentId: string, childId: string): Promise<void> {
    return api.delete(`/nodes/${parentId}/secondary-children/${childId}`);
  }

  async getSecondaryChildren(nodeId: string): Promise<NodeRelationEdge[]> {
    return api.get<NodeRelationEdge[]>(`/nodes/${nodeId}/secondary-children`);
  }

  async getSecondaryParents(nodeId: string): Promise<NodeRelationEdge[]> {
    return api.get<NodeRelationEdge[]>(`/nodes/${nodeId}/secondary-parents`);
  }

  async getVersionHistory(nodeId: string): Promise<Version[]> {
    const versions = await api.get<ApiVersionResponse[]>(`/documents/${nodeId}/versions`);
    return versions.map((version) => ({
      id: version.id,
      documentId: version.documentId || nodeId,
      versionLabel: version.versionLabel,
      comment: version.comment,
      created: version.createdDate,
      creator: version.creator,
      size: version.size,
      isMajor: version.major,
      mimeType: version.mimeType,
      contentHash: version.contentHash,
      contentId: version.contentId,
      status: version.status,
      checkoutBaseline: version.checkoutBaseline,
      checkoutCurrent: version.checkoutCurrent,
    }));
  }

  async getVersionHistoryPage(
    nodeId: string,
    page = 0,
    size = 20,
    majorOnly = false
  ): Promise<{
    versions: Version[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  }> {
    const response = await api.get<PageResponse<ApiVersionResponse>>(
      `/documents/${nodeId}/versions/paged`,
      { params: { page, size, majorOnly } }
    );
    const versions = response.content.map((version) => ({
      id: version.id,
      documentId: version.documentId || nodeId,
      versionLabel: version.versionLabel,
      comment: version.comment,
      created: version.createdDate,
      creator: version.creator,
      size: version.size,
      isMajor: version.major,
      mimeType: version.mimeType,
      contentHash: version.contentHash,
      contentId: version.contentId,
      status: version.status,
      checkoutBaseline: version.checkoutBaseline,
      checkoutCurrent: version.checkoutCurrent,
    }));
    return {
      versions,
      page: response.number,
      size: response.size,
      totalElements: response.totalElements,
      totalPages: response.totalPages,
    };
  }

  async createVersion(
    nodeId: string,
    file: File,
    comment?: string,
    major = false,
    keepCheckedOut = false,
  ): Promise<Version> {
    const formData = new FormData();
    formData.append('file', file);
    if (comment) formData.append('comment', comment);
    formData.append('majorVersion', major.toString());
    formData.append('keepCheckedOut', keepCheckedOut.toString());

    // Reuse check-in endpoint to create a new version (backend persists via VersionService).
    await api.post(`/documents/${nodeId}/checkin`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    const history = await this.getVersionHistory(nodeId);
    if (history.length === 0) {
      throw new Error('Version creation succeeded but version history is empty');
    }
    return history[0];
  }

  async downloadVersion(nodeId: string, versionId: string): Promise<void> {
    const node = await this.getNode(nodeId);
    const versions = await this.getVersionHistory(nodeId);
    const version = versions.find((v) => v.id === versionId);
    const suffix = version?.versionLabel ? `_v${version.versionLabel}` : `_v${versionId}`;
    return api.downloadFile(`/documents/${nodeId}/versions/${versionId}/download`, `${node.name}${suffix}`);
  }

  async getVersionTextDiff(
    nodeId: string,
    fromVersionId: string,
    toVersionId: string,
    maxBytes = 200000,
    maxLines = 2000
  ): Promise<{ available: boolean; truncated: boolean; reason?: string | null; diff?: string | null }> {
    const response = await api.get<{
      textDiff?: { available: boolean; truncated: boolean; reason?: string | null; diff?: string | null } | null;
    }>(`/documents/${nodeId}/versions/compare`, {
      params: {
        fromVersionId,
        toVersionId,
        includeTextDiff: true,
        maxBytes,
        maxLines,
      },
    });

    return response.textDiff ?? { available: false, truncated: false, reason: 'No diff available', diff: null };
  }

  async revertToVersion(nodeId: string, versionId: string): Promise<Node> {
    const node = await api.post<ApiNodeDetailsResponse>(`/documents/${nodeId}/versions/${versionId}/revert`);
    return this.apiNodeDetailsToNode(node);
  }

  async getPdfAnnotations(nodeId: string): Promise<PdfAnnotationState> {
    return api.get<PdfAnnotationState>(`/documents/${nodeId}/annotations`);
  }

  async savePdfAnnotations(nodeId: string, annotations: PdfAnnotation[]): Promise<PdfAnnotationState> {
    return api.post<PdfAnnotationState>(`/documents/${nodeId}/annotations`, { annotations });
  }

  async getPermissions(nodeId: string): Promise<Record<string, Permission[]>> {
    const perms = await api.get<Permission[]>(`/security/nodes/${nodeId}/permissions`);
    return perms.reduce<Record<string, Permission[]>>((acc, perm) => {
      const key = perm.authorityType === 'GROUP' ? `GROUP_${perm.authority}` : perm.authority;
      acc[key] = acc[key] || [];
      acc[key].push(perm);
      return acc;
    }, {});
  }

  async getPermissionDiagnostics(
    nodeId: string,
    permissionType: PermissionType,
    username?: string
  ): Promise<PermissionDecision> {
    return api.get<PermissionDecision>(`/security/nodes/${nodeId}/permission-diagnostics`, {
      params: { permissionType, username: username || undefined },
    });
  }

  async getPermissionSets(): Promise<Record<string, PermissionType[]>> {
    return api.get<Record<string, PermissionType[]>>('/security/permission-sets');
  }

  async getPermissionSetMetadata(): Promise<PermissionSetMetadata[]> {
    return api.get<PermissionSetMetadata[]>('/security/permission-sets/metadata');
  }

  async setPermission(
    nodeId: string,
    authority: string,
    authorityType: Permission['authorityType'],
    permissionType: PermissionType,
    allowed: boolean
  ): Promise<void> {
    return api.post(`/security/nodes/${nodeId}/permissions`, null, {
      params: { authority, authorityType, permissionType, allowed },
    });
  }

  async applyPermissionSet(
    nodeId: string,
    authority: string,
    authorityType: Permission['authorityType'],
    permissionSet: string,
    replace = false
  ): Promise<void> {
    return api.post(`/security/nodes/${nodeId}/permission-sets`, null, {
      params: { authority, authorityType, permissionSet, replace },
    });
  }

  async setInheritPermissions(nodeId: string, inherit: boolean): Promise<void> {
    return api.post(`/security/nodes/${nodeId}/inherit-permissions`, null, {
      params: { inherit },
    });
  }

  async removePermission(nodeId: string, authority: string, permissionType: PermissionType): Promise<void> {
    return api.delete(`/security/nodes/${nodeId}/permissions`, {
      params: { authority, permissionType },
    });
  }
}

const nodeService = new NodeService();
export default nodeService;
