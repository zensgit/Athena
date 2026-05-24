import api from './api';

export const LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE =
  'Legal hold endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

// Bulk-create and release each get a dedicated sentinel so Phase 5 Mocked
// HTML-fallback drift on either new request shape is debuggable from the
// thrown error text alone. See feedback_phase5_mocked_html_fallback.md.
export const LEGAL_HOLD_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE =
  'Legal hold bulk-create endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';
export const LEGAL_HOLD_RELEASE_UNEXPECTED_RESPONSE_MESSAGE =
  'Legal hold release endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type LegalHoldStatus = 'ACTIVE' | 'RELEASED';

export type HoldReleaseReason =
  | 'LITIGATION_ENDED'
  | 'SCHEDULED_DISPOSITION'
  | 'REQUEST_BY_REQUESTOR'
  | 'OTHER';

export type BulkApplyErrorCategory =
  | 'NODE_NOT_FOUND'
  | 'NODE_NOT_VISIBLE'
  | 'INTERNAL_ERROR';

export type BulkApplyResultStatus = 'ADDED' | 'SKIPPED_DUPLICATE' | 'FAILED';

export interface BulkApplyResult {
  requestedNodeId: string;
  status: BulkApplyResultStatus;
  item: LegalHoldItem | null;
  errorCategory: BulkApplyErrorCategory | null;
  errorMessage: string | null;
}

export interface BulkApplyResults {
  rows: BulkApplyResult[];
}

export interface LegalHoldSummary {
  id: string;
  name: string;
  description?: string | null;
  status: LegalHoldStatus;
  itemCount: number;
  createdBy?: string | null;
  createdDate?: string | null;
  releasedBy?: string | null;
  releasedAt?: string | null;
  // New 2026-05-24 (migration 094). Null for ACTIVE holds AND for legacy
  // RELEASED rows predating the migration. Frontend renders a "Legacy
  // release" chip when status==RELEASED && releaseReason==null.
  releaseReason?: HoldReleaseReason | null;
}

export interface LegalHoldItem {
  nodeId: string;
  nodeName?: string | null;
  nodeType?: string | null;
  nodePath?: string | null;
  addedAt?: string | null;
  addedBy?: string | null;
}

export interface LegalHoldDetail {
  id: string;
  name: string;
  description?: string | null;
  status: LegalHoldStatus;
  createdBy?: string | null;
  createdDate?: string | null;
  releasedBy?: string | null;
  releasedAt?: string | null;
  releaseComment?: string | null;
  releaseReason?: HoldReleaseReason | null;
  itemCount: number;
  items: LegalHoldItem[];
  // Non-null only when the create request supplied `nodeIds`. Null for
  // every other endpoint that returns LegalHoldDetail (getHold, addItems,
  // removeItem, releaseHold).
  bulkApplyResults?: BulkApplyResults | null;
}

export interface CreateLegalHoldRequest {
  name: string;
  description?: string;
  // Optional bulk apply since 2026-05-24. Empty / null / missing →
  // single-row create with no items added (back-compat).
  nodeIds?: string[];
}

export interface AddItemsRequest {
  nodeIds: string[];
}

export interface ReleaseHoldRequest {
  // Required since 2026-05-24 (migration 094). Submitting without a
  // releaseReason results in HTTP 400.
  releaseReason: HoldReleaseReason;
  comment?: string;
}

const LEGAL_HOLD_STATUSES: LegalHoldStatus[] = ['ACTIVE', 'RELEASED'];
const HOLD_RELEASE_REASONS: HoldReleaseReason[] = [
  'LITIGATION_ENDED',
  'SCHEDULED_DISPOSITION',
  'REQUEST_BY_REQUESTOR',
  'OTHER',
];
const BULK_APPLY_ERROR_CATEGORIES: BulkApplyErrorCategory[] = [
  'NODE_NOT_FOUND',
  'NODE_NOT_VISIBLE',
  'INTERNAL_ERROR',
];
const BULK_APPLY_RESULT_STATUSES: BulkApplyResultStatus[] = [
  'ADDED',
  'SKIPPED_DUPLICATE',
  'FAILED',
];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isLegalHoldStatus = (value: unknown): value is LegalHoldStatus => (
  typeof value === 'string' && (LEGAL_HOLD_STATUSES as string[]).includes(value)
);

const isHoldReleaseReasonOrNullish = (value: unknown): value is HoldReleaseReason | null | undefined => (
  value === null
    || value === undefined
    || (typeof value === 'string' && (HOLD_RELEASE_REASONS as string[]).includes(value))
);

const isBulkApplyErrorCategoryOrNull = (value: unknown): value is BulkApplyErrorCategory | null => (
  value === null
    || (typeof value === 'string' && (BULK_APPLY_ERROR_CATEGORIES as string[]).includes(value))
);

const isBulkApplyResultStatus = (value: unknown): value is BulkApplyResultStatus => (
  typeof value === 'string' && (BULK_APPLY_RESULT_STATUSES as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertBulkCreateUnexpectedResponse = (): never => {
  throw new Error(LEGAL_HOLD_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertReleaseUnexpectedResponse = (): never => {
  throw new Error(LEGAL_HOLD_RELEASE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isLegalHoldSummary = (value: unknown): value is LegalHoldSummary => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isLegalHoldStatus(value.status)
    && isFiniteNumber(value.itemCount)
    && isStringOrNullish(value.description)
    && isStringOrNullish(value.createdBy)
    && isStringOrNullish(value.createdDate)
    && isStringOrNullish(value.releasedBy)
    && isStringOrNullish(value.releasedAt)
    && isHoldReleaseReasonOrNullish(value.releaseReason);
};

const isLegalHoldItem = (value: unknown): value is LegalHoldItem => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.nodeId === 'string'
    && isStringOrNullish(value.nodeName)
    && isStringOrNullish(value.nodeType)
    && isStringOrNullish(value.nodePath)
    && isStringOrNullish(value.addedAt)
    && isStringOrNullish(value.addedBy);
};

const isBulkApplyResult = (value: unknown): value is BulkApplyResult => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.requestedNodeId !== 'string') {
    return false;
  }
  if (!isBulkApplyResultStatus(value.status)) {
    return false;
  }
  // ADDED rows must carry an item; SKIPPED_DUPLICATE and FAILED must have item == null.
  if (value.status === 'ADDED') {
    if (!isLegalHoldItem(value.item)) {
      return false;
    }
  } else if (value.item !== null) {
    return false;
  }
  if (!isBulkApplyErrorCategoryOrNull(value.errorCategory)) {
    return false;
  }
  if (!isStringOrNullish(value.errorMessage)) {
    return false;
  }
  // FAILED MUST carry errorCategory; ADDED/SKIPPED_DUPLICATE MUST NOT.
  if (value.status === 'FAILED' && value.errorCategory === null) {
    return false;
  }
  if (value.status !== 'FAILED' && value.errorCategory !== null) {
    return false;
  }
  return true;
};

const isBulkApplyResultsOrNull = (value: unknown): value is BulkApplyResults | null | undefined => {
  if (value === null || value === undefined) {
    return true;
  }
  if (!isObject(value)) {
    return false;
  }
  return Array.isArray(value.rows) && value.rows.every(isBulkApplyResult);
};

const isLegalHoldDetail = (value: unknown): value is LegalHoldDetail => {
  if (!isLegalHoldSummary(value)) {
    return false;
  }
  const candidate = value as unknown as Record<string, unknown>;
  if (!isStringOrNullish(candidate.releaseComment)) {
    return false;
  }
  if (!Array.isArray(candidate.items)) {
    return false;
  }
  if (!candidate.items.every(isLegalHoldItem)) {
    return false;
  }
  if (!isBulkApplyResultsOrNull(candidate.bulkApplyResults)) {
    return false;
  }
  return true;
};

const assertLegalHoldSummaryList = (value: unknown): LegalHoldSummary[] => {
  if (!Array.isArray(value) || !value.every(isLegalHoldSummary)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const assertLegalHoldDetail = (value: unknown): LegalHoldDetail => (
  isLegalHoldDetail(value) ? value : assertUnexpectedResponse()
);

const assertLegalHoldDetailBulkCreate = (value: unknown): LegalHoldDetail => (
  isLegalHoldDetail(value) ? value : assertBulkCreateUnexpectedResponse()
);

const assertLegalHoldDetailRelease = (value: unknown): LegalHoldDetail => (
  isLegalHoldDetail(value) ? value : assertReleaseUnexpectedResponse()
);

class LegalHoldService {
  async listHolds(): Promise<LegalHoldSummary[]> {
    const result = await api.get<unknown>('/legal-holds');
    return assertLegalHoldSummaryList(result);
  }

  async getHold(holdId: string): Promise<LegalHoldDetail> {
    const result = await api.get<unknown>(`/legal-holds/${holdId}`);
    return assertLegalHoldDetail(result);
  }

  async createHold(data: CreateLegalHoldRequest): Promise<LegalHoldDetail> {
    const result = await api.post<unknown>('/legal-holds', data);
    return assertLegalHoldDetailBulkCreate(result);
  }

  async addItems(holdId: string, data: AddItemsRequest): Promise<LegalHoldDetail> {
    const result = await api.post<unknown>(`/legal-holds/${holdId}/items`, data);
    return assertLegalHoldDetail(result);
  }

  async removeItem(holdId: string, nodeId: string): Promise<LegalHoldDetail> {
    const result = await api.delete<unknown>(`/legal-holds/${holdId}/items/${nodeId}`);
    return assertLegalHoldDetail(result);
  }

  async releaseHold(holdId: string, data: ReleaseHoldRequest): Promise<LegalHoldDetail> {
    const result = await api.post<unknown>(`/legal-holds/${holdId}/release`, data);
    return assertLegalHoldDetailRelease(result);
  }
}

const legalHoldService = new LegalHoldService();
export default legalHoldService;
