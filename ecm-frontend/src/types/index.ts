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
