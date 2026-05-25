import api from './api';

export const SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE =
  'Share link endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isNumberOrNullish = (value: unknown): value is number | null | undefined => (
  value === null || value === undefined || isNumber(value)
);

export type SharePermission = 'VIEW' | 'COMMENT' | 'EDIT';

const isSharePermission = (value: unknown): value is SharePermission => (
  value === 'VIEW' || value === 'COMMENT' || value === 'EDIT'
);

export interface ShareLink {
  id: string;
  token: string;
  nodeId: string;
  nodeName: string;
  createdBy: string;
  createdAt: string;
  expiryDate?: string | null;
  maxAccessCount?: number | null;
  accessCount: number;
  active: boolean;
  name?: string | null;
  permissionLevel: SharePermission;
  lastAccessedAt?: string | null;
  passwordProtected: boolean;
  hasIpRestrictions: boolean;
  isValid: boolean;
}

export interface CreateShareLinkRequest {
  name?: string;
  expiryDate?: string | null;
  maxAccessCount?: number | null;
  permissionLevel?: SharePermission;
  password?: string | null;
  allowedIps?: string | null;
}

export interface UpdateShareLinkRequest extends CreateShareLinkRequest {
  active?: boolean;
}

export type BulkShareLinkStatus = 'CREATED' | 'FAILED';
export type BulkShareLinkErrorCategory =
  | 'NODE_NOT_FOUND'
  | 'NO_PERMISSION'
  | 'VALIDATION_ERROR'
  | 'INTERNAL_ERROR';

export interface BulkCreateShareLinksRequest extends CreateShareLinkRequest {
  nodeIds: string[];
}

export interface BulkCreateShareLinkResult {
  nodeId: string;
  status: BulkShareLinkStatus;
  shareLink?: ShareLink | null;
  errorCategory?: BulkShareLinkErrorCategory | null;
  message?: string | null;
}

export interface BulkCreateShareLinksResponse {
  bulkShareLinkCreateResults: {
    rows: BulkCreateShareLinkResult[];
  };
}

export interface AccessLogEntry {
  id: string;
  accessedAt: string;
  clientIp?: string | null;
  userAgent?: string | null;
  success: boolean;
  failureReason?: string | null;
}

export interface AccessStats {
  totalAccesses: number;
  successfulAccesses: number;
  failedAccesses: number;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE);
};

const isShareLink = (value: unknown): value is ShareLink => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.token === 'string'
    && typeof value.nodeId === 'string'
    && typeof value.nodeName === 'string'
    && typeof value.createdBy === 'string'
    && typeof value.createdAt === 'string'
    && isStringOrNullish(value.expiryDate)
    && isNumberOrNullish(value.maxAccessCount)
    && isNumber(value.accessCount)
    && typeof value.active === 'boolean'
    && isStringOrNullish(value.name)
    && isSharePermission(value.permissionLevel)
    && isStringOrNullish(value.lastAccessedAt)
    && typeof value.passwordProtected === 'boolean'
    && typeof value.hasIpRestrictions === 'boolean'
    && typeof value.isValid === 'boolean';
};

const BULK_STATUSES: BulkShareLinkStatus[] = ['CREATED', 'FAILED'];
const BULK_ERROR_CATEGORIES: BulkShareLinkErrorCategory[] = [
  'NODE_NOT_FOUND', 'NO_PERMISSION', 'VALIDATION_ERROR', 'INTERNAL_ERROR',
];
const isNullish = (value: unknown): boolean => value === null || value === undefined;

const isBulkCreateShareLinkResult = (value: unknown): value is BulkCreateShareLinkResult => {
  if (!isObject(value)) return false;
  if (typeof value.nodeId !== 'string') return false;
  if (typeof value.status !== 'string' || !(BULK_STATUSES as string[]).includes(value.status)) return false;
  // Status-keyed invariants (closed set): CREATED carries a valid ShareLink and no error;
  // FAILED carries a known errorCategory and no shareLink. Mirrors the bulk-declare contract lock.
  if (value.status === 'CREATED') {
    return isShareLink(value.shareLink) && isNullish(value.errorCategory) && isNullish(value.message);
  }
  return isNullish(value.shareLink)
    && typeof value.errorCategory === 'string'
    && (BULK_ERROR_CATEGORIES as string[]).includes(value.errorCategory);
};

const assertBulkCreateShareLinksResponse = (value: unknown): BulkCreateShareLinksResponse => {
  if (
    isObject(value)
    && isObject(value.bulkShareLinkCreateResults)
    && Array.isArray((value.bulkShareLinkCreateResults as Record<string, unknown>).rows)
    && ((value.bulkShareLinkCreateResults as { rows: unknown[] }).rows).every(isBulkCreateShareLinkResult)
  ) {
    return value as unknown as BulkCreateShareLinksResponse;
  }
  return assertUnexpectedResponse();
};

const assertShareLink = (value: unknown): ShareLink => (
  isShareLink(value) ? value : assertUnexpectedResponse()
);

const isShareLinkArray = (value: unknown): value is ShareLink[] => (
  Array.isArray(value) && value.every(isShareLink)
);

const assertShareLinkArray = (value: unknown): ShareLink[] => (
  isShareLinkArray(value) ? value : assertUnexpectedResponse()
);

const isAccessLogEntry = (value: unknown): value is AccessLogEntry => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.accessedAt === 'string'
    && isStringOrNullish(value.clientIp)
    && isStringOrNullish(value.userAgent)
    && typeof value.success === 'boolean'
    && isStringOrNullish(value.failureReason);
};

const isAccessLogEntryArray = (value: unknown): value is AccessLogEntry[] => (
  Array.isArray(value) && value.every(isAccessLogEntry)
);

const assertAccessLogEntryArray = (value: unknown): AccessLogEntry[] => (
  isAccessLogEntryArray(value) ? value : assertUnexpectedResponse()
);

const isAccessStats = (value: unknown): value is AccessStats => (
  isObject(value)
  && isNumber(value.totalAccesses)
  && isNumber(value.successfulAccesses)
  && isNumber(value.failedAccesses)
);

const assertAccessStats = (value: unknown): AccessStats => (
  isAccessStats(value) ? value : assertUnexpectedResponse()
);

class ShareLinkService {
  async getLinksForNode(nodeId: string): Promise<ShareLink[]> {
    const result = await api.get<unknown>(`/share/nodes/${nodeId}`);
    return assertShareLinkArray(result);
  }

  async getMyLinks(): Promise<ShareLink[]> {
    const result = await api.get<unknown>('/share/my');
    return assertShareLinkArray(result);
  }

  async createLink(nodeId: string, data: CreateShareLinkRequest): Promise<ShareLink> {
    const result = await api.post<unknown>(`/share/nodes/${nodeId}`, data);
    return assertShareLink(result);
  }

  async bulkCreateLinks(request: BulkCreateShareLinksRequest): Promise<BulkCreateShareLinksResponse> {
    const result = await api.post<unknown>('/bulk/share-links', request);
    // assertBulkCreateShareLinksResponse throws the sentinel on a Phase-5 SPA HTML fallback or any
    // non-conforming body, so the per-row contract is never parsed from HTML.
    return assertBulkCreateShareLinksResponse(result);
  }

  async updateLink(token: string, data: UpdateShareLinkRequest): Promise<ShareLink> {
    const result = await api.put<unknown>(`/share/${token}`, data);
    return assertShareLink(result);
  }

  async deactivateLink(token: string): Promise<void> {
    await api.post<void>(`/share/${token}/deactivate`);
  }

  async deleteLink(token: string): Promise<void> {
    await api.delete<void>(`/share/${token}`);
  }

  async reactivateLink(token: string): Promise<ShareLink> {
    const result = await api.post<unknown>(`/share/${token}/reactivate`);
    return assertShareLink(result);
  }

  async listAllLinks(): Promise<ShareLink[]> {
    const result = await api.get<unknown>('/share/admin/all');
    return assertShareLinkArray(result);
  }

  async getAccessLog(token: string): Promise<AccessLogEntry[]> {
    const result = await api.get<unknown>(`/share/${token}/access-log`);
    return assertAccessLogEntryArray(result);
  }

  async getAccessStats(token: string): Promise<AccessStats> {
    const result = await api.get<unknown>(`/share/${token}/access-stats`);
    return assertAccessStats(result);
  }
}

const shareLinkService = new ShareLinkService();
export default shareLinkService;
