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

export interface AdvancedSearchPivotStatsRequest {
  query?: string;
  filters?: Record<string, any>;
  rowField: string;
  columnField: string;
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
  status?: string | null;
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
// Introduced with the relations/renditions sub-slice; the sentinel and the
// generic helpers below are intentionally service-wide so later nodeService
// sub-slices reuse them rather than minting new sentinels/styles. This round
// only converts the relations/renditions methods (lines ~1500-1688); no other
// nodeService method is touched.
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
    return api.post<BatchDownloadAsyncTask>('/nodes/download/batch-async', {
      nodeIds,
      name,
    });
  }

  async preflightBatchDownloadAsync(nodeIds: string[], name = 'archive'): Promise<BatchDownloadPreflightResponse> {
    return api.post<BatchDownloadPreflightResponse>('/nodes/download/batch-async/preflight', {
      nodeIds,
      name,
    });
  }

  async listBatchDownloadAsyncTasks(
    limit = 10,
    status?: string,
    skipCount = 0,
    query?: string,
    owner?: string
  ): Promise<BatchDownloadAsyncTaskListResponse> {
    return api.get<BatchDownloadAsyncTaskListResponse>('/nodes/download/batch-async', {
      params: {
        maxItems: limit,
        skipCount,
        status: status || undefined,
        q: query || undefined,
        owner: owner || undefined,
      },
    });
  }

  async getBatchDownloadAsyncTask(taskId: string): Promise<BatchDownloadAsyncTask> {
    return api.get<BatchDownloadAsyncTask>(`/nodes/download/batch-async/${encodeURIComponent(taskId)}`);
  }

  async getBatchDownloadAsyncSummary(): Promise<BatchDownloadAsyncTaskSummaryResponse> {
    return api.get<BatchDownloadAsyncTaskSummaryResponse>('/nodes/download/batch-async/summary');
  }

  async cancelBatchDownloadAsyncTask(taskId: string): Promise<BatchDownloadAsyncTask> {
    return api.post<BatchDownloadAsyncTask>(`/nodes/download/batch-async/${encodeURIComponent(taskId)}/cancel`);
  }

  async cleanupBatchDownloadAsyncTasks(status?: string): Promise<BatchDownloadAsyncTaskCleanupResponse> {
    return api.post<BatchDownloadAsyncTaskCleanupResponse>('/nodes/download/batch-async/cleanup', undefined, {
      params: {
        status: status || undefined,
      },
    });
  }

  async cancelActiveBatchDownloadAsyncTasks(status?: string): Promise<BatchDownloadAsyncTaskCancelActiveResponse> {
    return api.post<BatchDownloadAsyncTaskCancelActiveResponse>('/nodes/download/batch-async/cancel-active', undefined, {
      params: {
        status: status || undefined,
      },
    });
  }

  async cleanupBatchDownloadAsyncTask(taskId: string): Promise<BatchDownloadAsyncTaskCleanupResponse> {
    return api.post<BatchDownloadAsyncTaskCleanupResponse>(`/nodes/download/batch-async/${encodeURIComponent(taskId)}/cleanup`);
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
      const response = await api.get<SearchPagePayload>('/search', {
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

      const nodes = (response.content || []).map((item) => this.mapSearchItemToNode(item));
      return { nodes, total: response.totalElements ?? nodes.length };
    }

    const response = await api.post<SearchQueryEnvelopeResponse>('/search/query', {
          query,
          filters,
          sortBy: criteria.sortBy,
          sortDirection: criteria.sortDirection,
          pageable: { page, size },
          include: ['results', 'facets', ...(query ? ['suggestions'] : [])],
        });

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

    const response = await api.post<SearchQueryEnvelopeResponse>('/search/query', payload);
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

  async getAdvancedSearchStats(criteria: SearchCriteria): Promise<AdvancedSearchStats> {
    const query = (criteria.name || '').trim();
    const filters = this.buildSearchFilters(criteria);
    const response = await api.post<SearchQueryEnvelopeResponse>('/search/query', {
      query,
      filters,
      include: ['stats'],
      facets: ['previewStatus', 'mimeType', 'createdBy', 'fileSizeRange', 'createdDateRange'],
    });
    return response.stats || {
      query: query || null,
      normalizedQuery: query || null,
      hasFilters: false,
      totalHits: 0,
      facetFieldCount: 0,
      previewStatusStats: [],
      mimeTypeStats: [],
      createdByStats: [],
      fileSizeRangeStats: [],
      createdDateRangeStats: [],
      generatedAt: null,
    };
  }

  async getAdvancedSearchPivotStats(criteria: SearchCriteria): Promise<AdvancedSearchPivotStats> {
    const query = (criteria.name || '').trim();
    const filters = this.buildSearchFilters(criteria);
    const payload: AdvancedSearchPivotStatsRequest = {
      query,
      filters,
      rowField: 'previewStatus',
      columnField: 'mimeType',
    };
    const envelope = await api.post<SearchQueryEnvelopeResponse>('/search/query', {
      query,
      filters,
      include: ['pivot'],
    });
    const response = envelope.pivot || null;
    const matrixCells = ((response?.matrix) || []).flatMap((row) => {
      const rowValue = (row.previewStatus || '').trim();
      return (row.mimeTypeCounts || []).map((cell) => ({
        rowValue,
        columnValue: ((cell.mimeType || '') as string).trim(),
        count: Number(cell.count || 0),
      }));
    });
    const fallbackCells = ((response?.cells) || []).map((cell) => ({
      rowValue: (cell.rowValue || '').trim(),
      columnValue: (cell.columnValue || '').trim(),
      count: Number(cell.count || 0),
    }));
    return {
      query: response?.query ?? (query || null),
      normalizedQuery: response?.normalizedQuery ?? (query || null),
      hasFilters: Boolean(response?.hasFilters),
      totalHits: Number(response?.totalHits || 0),
      rowField: ((response?.rowField) || payload.rowField).trim(),
      columnField: ((response?.columnField) || payload.columnField).trim(),
      cells: matrixCells.length > 0 ? matrixCells : fallbackCells,
      generatedAt: response?.generatedAt ?? null,
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
    const results = await api.get<any[]>(`/search/similar/${documentId}`, {
      params: { maxResults },
    });
    return (results || []).map((item) => this.mapSearchItemToNode(item));
  }

  async getSearchFacets(query = ''): Promise<Record<string, { value: string; count: number }[]>> {
    return api.get<Record<string, { value: string; count: number }[]>>('/search/facets', {
      params: { q: query },
    });
  }

  async getSuggestedFilters(
    query = ''
  ): Promise<Array<{ field: string; label: string; value: string; count?: number }>> {
    return api.get<Array<{ field: string; label: string; value: string; count?: number }>>(
      '/search/filters/suggested',
      { params: { q: query } }
    );
  }

  async getSpellcheckSuggestions(query = '', limit = 5): Promise<string[]> {
    return api.get<string[]>('/search/spellcheck', { params: { q: query, limit } });
  }

  async getSearchSuggestions(prefix: string, limit = 10): Promise<string[]> {
    return api.get<string[]>('/search/suggestions', { params: { prefix, limit } });
  }

  async getSearchDiagnostics(): Promise<SearchDiagnostics> {
    return api.get<SearchDiagnostics>('/search/diagnostics');
  }

  async getSearchIndexStats(): Promise<SearchIndexStats> {
    return api.get<SearchIndexStats>('/search/index/stats');
  }

  async getSearchRebuildStatus(): Promise<SearchRebuildStatus> {
    return api.get<SearchRebuildStatus>('/search/index/rebuild/status');
  }

  async queuePreview(nodeId: string, force = false): Promise<PreviewQueueStatus> {
    return api.post<PreviewQueueStatus>(`/documents/${nodeId}/preview/queue`, null, {
      params: { force },
    });
  }

  async cancelQueuedPreview(nodeId: string): Promise<PreviewQueueCancelStatus> {
    return api.post<PreviewQueueCancelStatus>(`/documents/${nodeId}/preview/queue/cancel`);
  }

  async repairPreview(
    nodeId: string,
    options?: {
      forceInvalidate?: boolean;
      requeue?: boolean;
      forceQueue?: boolean;
    }
  ): Promise<PreviewRepairStatus> {
    return api.post<PreviewRepairStatus>(`/documents/${nodeId}/preview/repair`, null, {
      params: {
        forceInvalidate: options?.forceInvalidate ?? true,
        requeue: options?.requeue ?? true,
        forceQueue: options?.forceQueue ?? true,
      },
    });
  }

  async queueOcr(nodeId: string, force = false): Promise<OcrQueueStatus> {
    return api.post<OcrQueueStatus>(`/documents/${nodeId}/ocr/queue`, null, {
      params: { force },
    });
  }

  async getPreviewQueueBySearchCapabilities(): Promise<PreviewQueueSearchCapabilities> {
    return api.get<PreviewQueueSearchCapabilities>('/search/preview/queue-failed/capabilities');
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
    return api.post<PreviewQueueSearchBatchResult>('/search/preview/queue-failed', payload);
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
    return api.post<PreviewQueueSearchDryRunResult>('/search/preview/queue-failed/dry-run', payload);
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
    return api.post<PreviewQueueSearchDryRunExportAsyncTask>('/search/preview/queue-failed/dry-run/export-async', payload);
  }

  async getDryRunFailedPreviewsCsvExportAsyncBySearchTask(taskId: string): Promise<PreviewQueueSearchDryRunExportAsyncTaskStatus> {
    return api.get<PreviewQueueSearchDryRunExportAsyncTaskStatus>(
      `/search/preview/queue-failed/dry-run/export-async/${encodeURIComponent(taskId)}`
    );
  }

  async listDryRunFailedPreviewsCsvExportAsyncBySearchTasks(
    limit = 20,
    status?: string
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskList> {
    return api.get<PreviewQueueSearchDryRunExportAsyncTaskList>(
      '/search/preview/queue-failed/dry-run/export-async',
      {
        params: {
          limit,
          ...(status ? { status } : {}),
        },
      }
    );
  }

  async getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary(
    status?: string
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskSummary> {
    return api.get<PreviewQueueSearchDryRunExportAsyncTaskSummary>(
      '/search/preview/queue-failed/dry-run/export-async/summary',
      {
        params: status ? { status } : undefined,
      }
    );
  }

  async cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks(
    status?: string
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskCleanupResult> {
    return api.post<PreviewQueueSearchDryRunExportAsyncTaskCleanupResult>(
      '/search/preview/queue-failed/dry-run/export-async/cleanup',
      null,
      {
        params: status ? { status } : undefined,
      }
    );
  }

  async cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks(
    status?: PreviewQueueSearchDryRunExportAsyncTaskActiveStatusFilter
  ): Promise<PreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult> {
    return api.post<PreviewQueueSearchDryRunExportAsyncTaskCancelActiveResult>(
      '/search/preview/queue-failed/dry-run/export-async/cancel-active',
      {},
      {
        params: status ? { status } : undefined,
      }
    );
  }

  async cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask(taskId: string): Promise<PreviewQueueSearchDryRunExportAsyncTaskStatus> {
    return api.post<PreviewQueueSearchDryRunExportAsyncTaskStatus>(
      `/search/preview/queue-failed/dry-run/export-async/${encodeURIComponent(taskId)}/cancel`
    );
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
