import api from './api';

export const LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE =
  'Legal hold endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type LegalHoldStatus = 'ACTIVE' | 'RELEASED';

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
  itemCount: number;
  items: LegalHoldItem[];
}

export interface CreateLegalHoldRequest {
  name: string;
  description?: string;
}

export interface AddItemsRequest {
  nodeIds: string[];
}

export interface ReleaseHoldRequest {
  comment?: string;
}

const LEGAL_HOLD_STATUSES: LegalHoldStatus[] = ['ACTIVE', 'RELEASED'];

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

const assertUnexpectedResponse = (): never => {
  throw new Error(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE);
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
    && isStringOrNullish(value.releasedAt);
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
  return candidate.items.every(isLegalHoldItem);
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
    return assertLegalHoldDetail(result);
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
    return assertLegalHoldDetail(result);
  }
}

const legalHoldService = new LegalHoldService();
export default legalHoldService;
