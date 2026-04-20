export interface User {
  id: string;
  username: string;
  email: string;
  roles: string[];
  firstName?: string;
  lastName?: string;
  enabled?: boolean;
  locked?: boolean;
}

export interface Node {
  id: string;
  name: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  typeQName?: string;
  parentId?: string;
  properties: Record<string, any>;
  metadata?: Record<string, any>;
  aspects: string[];
  created: string;
  modified: string;
  creator: string;
  modifier: string;
  path: string;
  contentType?: string;
  size?: number;
  currentVersionLabel?: string;
  description?: string;
  highlights?: Record<string, string[]>;
  matchFields?: string[];
  highlightSummary?: string;
  tags?: string[];
  categories?: string[];
  correspondent?: string;
  correspondentId?: string;
  score?: number;
  inheritPermissions?: boolean;
  locked?: boolean;
  lockedBy?: string;
  lockedDate?: string;
  lockLifetime?: 'PERSISTENT' | 'EPHEMERAL' | string;
  lockExpiresAt?: string;
  previewStatus?: string;
  previewFailureReason?: string;
  previewFailureCategory?: 'UNSUPPORTED' | 'TEMPORARY' | 'PERMANENT' | string;
  previewLastUpdated?: string;
  checkedOut?: boolean;
  checkoutUser?: string;
  checkoutDate?: string;
  workingCopyOf?: string;
  isWorkingCopy?: boolean;
  smart?: boolean;
  queryCriteria?: Record<string, any>;
  record?: boolean;
  declaredBy?: string;
  declaredAt?: string;
  declaredVersionLabel?: string;
  declarationComment?: string;
  recordCategoryId?: string;
  recordCategoryName?: string;
  recordCategoryPath?: string;
}

export interface DeclareRecordRequest {
  comment?: string;
  categoryId?: string;
}

export interface UndeclareRecordRequest {
  reason: string;
}

export interface RecordDeclaration {
  nodeId: string;
  name: string;
  path: string;
  currentVersionLabel?: string;
  declaredVersionLabel?: string;
  declaredBy?: string;
  declaredAt?: string;
  declarationComment?: string;
  recordCategoryId?: string;
  recordCategoryName?: string;
  recordCategoryPath?: string;
}

export interface FilePlan {
  folderId: string;
  name: string;
  description?: string;
  path: string;
  parentId?: string | null;
}

export interface CreateFilePlanRequest {
  name: string;
  description?: string;
  parentId?: string | null;
}

export interface UpdateFilePlanRequest {
  description?: string;
}

export interface RenameFilePlanRequest {
  name: string;
}

export interface MoveFilePlanRequest {
  targetParentId: string;
}

export interface RecordCategory {
  categoryId: string;
  name: string;
  description?: string;
  path: string;
  level?: number | null;
  parentId?: string | null;
}

export interface CreateRecordCategoryRequest {
  name: string;
  description?: string;
  parentId?: string | null;
}

export interface UpdateRecordCategoryRequest {
  description?: string;
}

export interface RenameRecordCategoryRequest {
  name: string;
}

export interface MoveRecordCategoryRequest {
  targetParentId: string;
}

export interface RecordCategoryAssignmentRequest {
  categoryId: string;
}

export interface SummaryBucket {
  key: string;
  count: number;
}

export interface RecordsSummary {
  declaredRecordCount: number;
  filePlanCount: number;
  recordCategoryCount: number;
  uncategorizedRecordCount: number;
  outsideFilePlanRecordCount: number;
  categoryBreakdown: SummaryBucket[];
  filePlanBreakdown: SummaryBucket[];
}

export type RmReportPresetKind =
  | 'ACTIVITY_FAMILY_REPORT'
  | 'ACTIVITY_FAMILY_HIGHLIGHTS'
  | 'ACTIVITY_FAMILY_MIX'
  | 'ACTIVITY_EVENT_TYPE_REPORT'
  | 'ACTIVITY_CONTRIBUTOR_REPORT'
  | 'ACTIVITY_CONTRIBUTOR_FAMILY_REPORT'
  | 'ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT';

export interface RmReportPreset {
  id: string;
  owner: string;
  name: string;
  description?: string | null;
  kind: RmReportPresetKind;
  params: Record<string, unknown>;
  createdDate?: string | null;
  lastModifiedDate?: string | null;
}

export interface RecordAuditEntry {
  auditLogId: string;
  eventType: string;
  nodeId?: string | null;
  nodeName?: string | null;
  username?: string | null;
  eventTime?: string | null;
  details?: string | null;
}

export interface GovernedImportJob {
  jobId: string;
  targetFolderId?: string | null;
  targetFolderName?: string | null;
  targetFolderPath?: string | null;
  status?: string | null;
  conflictPolicy?: string | null;
  totalFiles: number;
  importedFiles: number;
  skippedFiles: number;
  failedFiles: number;
  lastMessage?: string | null;
  governanceReasons: string[];
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string | null;
}

export interface GovernedTransferJob {
  jobId: string;
  definitionId?: string | null;
  sourceNodeId?: string | null;
  sourceNodeName?: string | null;
  sourceNodePath?: string | null;
  targetFolderId?: string | null;
  targetFolderName?: string | null;
  targetFolderPath?: string | null;
  status?: string | null;
  transportStatus?: string | null;
  lastMessage?: string | null;
  transportMessage?: string | null;
  governanceReasons: string[];
  startedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string | null;
}

export interface RecordsOperationsTelemetry {
  governedImportJobCount: number;
  activeGovernedImportJobCount: number;
  failedGovernedImportJobCount: number;
  governedTransferJobCount: number;
  activeGovernedTransferJobCount: number;
  failedGovernedTransferJobCount: number;
  importStatusBreakdown: SummaryBucket[];
  transferStatusBreakdown: SummaryBucket[];
  importGovernanceReasonBreakdown: SummaryBucket[];
  transferGovernanceReasonBreakdown: SummaryBucket[];
  recentImportJobs: GovernedImportJob[];
  recentTransferJobs: GovernedTransferJob[];
}

export interface RecordsActivityPoint {
  day: string;
  declaredCount: number;
  undeclaredCount: number;
  categoryAssignedCount: number;
  governanceChangeCount: number;
  totalCount: number;
}

export interface RecordsActivityTimeline {
  days: number;
  points: RecordsActivityPoint[];
}

export interface RecordsActivityWindow {
  fromDay: string | null;
  toDay: string | null;
  activeDayCount: number;
  declaredCount: number;
  undeclaredCount: number;
  categoryAssignedCount: number;
  governanceChangeCount: number;
  totalCount: number;
}

export interface RecordsActivityPeak {
  day: string;
  totalCount: number;
}

export interface RecordsActivityHighlights {
  windowDays: number;
  currentWindow: RecordsActivityWindow;
  previousWindow: RecordsActivityWindow;
  busiestDay: RecordsActivityPeak | null;
}

export interface RecordsActivityBucket {
  label: string;
  fromDay: string;
  toDay: string;
  activeDayCount: number;
  declaredCount: number;
  undeclaredCount: number;
  categoryAssignedCount: number;
  governanceChangeCount: number;
  totalCount: number;
}

export interface RecordsActivityBreakdown {
  days: number;
  bucketDays: number;
  buckets: RecordsActivityBucket[];
}

export interface RecordsActivityContributor {
  username?: string | null;
  label: string;
  declaredCount: number;
  undeclaredCount: number;
  categoryAssignedCount: number;
  governanceChangeCount: number;
  totalCount: number;
  lastEventTime?: string | null;
}

export interface RecordsActivityContributors {
  days: number;
  limit: number;
  contributors: RecordsActivityContributor[];
}

export interface RecordsActivityContributorEventTypeTrendCount {
  eventType: string;
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  count: number;
}

export interface RecordsActivityContributorEventTypeTrendContributor {
  username?: string | null;
  label: string;
  count: number;
  eventTypes: RecordsActivityContributorEventTypeTrendCount[];
}

export interface RecordsActivityContributorEventTypeTrendBucket {
  label: string;
  fromDay: string;
  toDay: string;
  activeDayCount: number;
  totalCount: number;
  otherCount: number;
  contributorCounts: RecordsActivityContributorEventTypeTrendContributor[];
}

export interface RecordsActivityContributorEventTypeTrend {
  days: number;
  bucketDays: number;
  limit: number;
  eventTypeLimit: number;
  trackedContributors: Array<{
    username?: string | null;
    label: string;
    count: number;
    lastEventTime?: string | null;
  }>;
  buckets: RecordsActivityContributorEventTypeTrendBucket[];
}

export interface RecordsActivityContributorFamilyTrendCount {
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  count: number;
}

export interface RecordsActivityContributorFamilyTrendContributor {
  username?: string | null;
  label: string;
  count: number;
  families: RecordsActivityContributorFamilyTrendCount[];
}

export interface RecordsActivityContributorFamilyTrendBucket {
  label: string;
  fromDay: string;
  toDay: string;
  activeDayCount: number;
  totalCount: number;
  otherCount: number;
  contributorCounts: RecordsActivityContributorFamilyTrendContributor[];
}

export interface RecordsActivityContributorFamilyTrend {
  days: number;
  bucketDays: number;
  limit: number;
  trackedContributors: Array<{
    username?: string | null;
    label: string;
    count: number;
    lastEventTime?: string | null;
  }>;
  buckets: RecordsActivityContributorFamilyTrendBucket[];
}

export interface RecordsActivityContributorEventTypeHighlightEventType {
  eventType: string;
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  currentCount: number;
  previousCount: number;
  delta: number;
  lastEventTime?: string | null;
}

export interface RecordsActivityContributorEventTypeHighlightContributor {
  username?: string | null;
  label: string;
  currentCount: number;
  previousCount: number;
  delta: number;
  lastEventTime?: string | null;
  eventTypes: RecordsActivityContributorEventTypeHighlightEventType[];
}

export interface RecordsActivityContributorEventTypeHighlights {
  windowDays: number;
  limit: number;
  eventTypeLimit: number;
  currentWindow: {
    fromDay: string;
    toDay: string;
  };
  previousWindow: {
    fromDay: string;
    toDay: string;
  };
  contributors: RecordsActivityContributorEventTypeHighlightContributor[];
}

export interface RecordsActivityContributorFamilyHighlightFamily {
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  currentCount: number;
  previousCount: number;
  delta: number;
  lastEventTime?: string | null;
}

export interface RecordsActivityContributorFamilyHighlightContributor {
  username?: string | null;
  label: string;
  currentCount: number;
  previousCount: number;
  delta: number;
  lastEventTime?: string | null;
  families: RecordsActivityContributorFamilyHighlightFamily[];
}

export interface RecordsActivityContributorFamilyHighlights {
  windowDays: number;
  limit: number;
  currentWindow: {
    fromDay: string;
    toDay: string;
  };
  previousWindow: {
    fromDay: string;
    toDay: string;
  };
  contributors: RecordsActivityContributorFamilyHighlightContributor[];
}

export interface RecordsActivityEventType {
  eventType: string;
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  count: number;
  lastEventTime?: string | null;
}

export interface RecordsActivityEventTypes {
  days: number;
  limit: number;
  eventTypes: RecordsActivityEventType[];
}

export interface RecordsActivityFamily {
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  count: number;
  lastEventTime?: string | null;
}

export interface RecordsActivityFamilies {
  days: number;
  totalCount: number;
  families: RecordsActivityFamily[];
}

export interface RecordsActivityFamilyHighlight {
  family: 'DECLARED' | 'UNDECLARED' | 'CATEGORY_ASSIGNED' | 'GOVERNANCE_CHANGE' | 'OTHER' | string;
  currentCount: number;
  previousCount: number;
  delta: number;
  lastEventTime?: string | null;
}

export interface RecordsActivityFamilyHighlights {
  windowDays: number;
  currentWindow: {
    fromDay: string;
    toDay: string;
  };
  previousWindow: {
    fromDay: string;
    toDay: string;
  };
  families: RecordsActivityFamilyHighlight[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export type LockStatus = 'NO_LOCK' | 'LOCK_OWNER' | 'LOCKED_BY_OTHER' | 'LOCK_EXPIRED';
export type LockType = 'WRITE_LOCK' | 'READ_ONLY_LOCK' | 'NODE_LOCK';

export interface LockInfo {
  status: LockStatus;
  lockedBy?: string;
  lockedDate?: string;
  lockLifetime?: 'PERSISTENT' | 'EPHEMERAL' | string;
  lockExpiresAt?: string;
  lockType?: LockType;
  additionalInfo?: string;
  lockDeep?: boolean;
  remainingSeconds?: number | null;
  lockAgeSeconds?: number | null;
  canUnlock: boolean;
}

export type CheckoutStatus = 'AVAILABLE' | 'CHECKED_OUT_BY_YOU' | 'CHECKED_OUT_BY_OTHER';

export interface CheckoutInfo {
  status: CheckoutStatus;
  checkoutUser?: string;
  checkoutDate?: string;
  checkoutAgeSeconds?: number | null;
  canCheckout: boolean;
  canCheckIn: boolean;
  canCancelCheckout: boolean;
  canKeepCheckedOut: boolean;
  requiresNewVersionFile: boolean;
  blockingReason?: string | null;
}

export interface PdfAnnotation {
  id?: string;
  page: number;
  x: number;
  y: number;
  text: string;
  color?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface PdfAnnotationState {
  annotations: PdfAnnotation[];
  updatedBy?: string | null;
  updatedAt?: string | null;
}

export interface Version {
  id: string;
  documentId: string;
  versionLabel: string;
  comment?: string;
  created: string;
  creator: string;
  size: number;
  isMajor: boolean;
  mimeType?: string;
  contentHash?: string;
  contentId?: string;
  status?: string;
  checkoutBaseline?: boolean;
  checkoutCurrent?: boolean;
}

export interface Permission {
  authority: string;
  authorityType: 'USER' | 'GROUP' | 'ROLE' | 'EVERYONE';
  permission: PermissionType;
  allowed: boolean;
  inherited?: boolean;
  expiryDate?: string;
  notes?: string;
}

export type PermissionType = 
  | 'READ'
  | 'WRITE'
  | 'DELETE'
  | 'CREATE_CHILDREN'
  | 'DELETE_CHILDREN'
  | 'EXECUTE'
  | 'CHANGE_PERMISSIONS'
  | 'TAKE_OWNERSHIP'
  | 'CHECKOUT'
  | 'CHECKIN'
  | 'CANCEL_CHECKOUT'
  | 'APPROVE'
  | 'REJECT';

export interface SearchCriteria {
  name?: string;
  properties?: Record<string, any>;
  aspects?: string[];
  contentType?: string;
  mimeTypes?: string[];
  previewStatuses?: string[];
  locked?: boolean;
  lockedBy?: string;
  checkedOut?: boolean;
  checkoutUser?: string;
  recordOnly?: boolean;
  recordCategoryPaths?: string[];
  createdBy?: string;
  createdByList?: string[];
  createdFrom?: string;
  createdTo?: string;
  modifiedFrom?: string;
  modifiedTo?: string;
  tags?: string[];
  categories?: string[];
  correspondents?: string[];
  minSize?: number;
  maxSize?: number;
  path?: string;
  folderId?: string;
  includeChildren?: boolean;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export interface CreateNodeRequest {
  name: string;
  nodeType: 'FOLDER' | 'DOCUMENT';
  typeQName?: string;
  description?: string;
  mimeType?: string;
  properties?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  aspects?: string[];
}

export interface UpdateNodeRequest {
  name?: string;
  description?: string;
  properties?: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  correspondentId?: string;
}

export interface AddAspectRequest {
  aspectName: string;
  properties?: Record<string, unknown>;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}

export interface NodeState {
  currentNode: Node | null;
  nodes: Node[];
  nodesTotal: number;
  searchFacets?: Record<string, { value: string; count: number }[]>;
  searchFacetsCache?: Record<string, Record<string, { value: string; count: number }[]>>;
  lastSearchCriteria?: SearchCriteria;
  loading: boolean;
  error: string | null;
  selectedNodes: string[];
}
