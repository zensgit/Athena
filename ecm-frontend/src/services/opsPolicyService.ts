import api from './api';

export const OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE =
  'Ops policy endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type OpsPolicyProfile = {
  key: string;
  label: string;
  maxAttempts: number;
  retryDelayMs: number;
  backoffMultiplier: number;
  quietPeriodMs: number;
  builtIn: boolean;
};

export type OpsPolicyDomainState = {
  domain: string;
  currentVersion: number;
  updatedAt: string | null;
  actor: string | null;
  reason: string | null;
  policies: OpsPolicyProfile[];
};

export type OpsPolicyUpdateRequest = {
  profileKey: string;
  maxAttempts?: number;
  retryDelayMs?: number;
  backoffMultiplier?: number;
  quietPeriodMs?: number;
  reason?: string;
};

export type OpsPolicyUpdateResponse = {
  domain: string;
  currentVersion: number;
  updatedAt: string | null;
  actor: string | null;
  reason: string | null;
  updatedPolicy: OpsPolicyProfile | null;
  policies: OpsPolicyProfile[];
  error?: string | null;
};

export type OpsPolicyRollbackRequest = {
  targetVersion?: number;
  reason?: string;
};

export type OpsPolicyRollbackResponse = {
  domain: string;
  previousVersion: number;
  rolledBackToVersion: number;
  currentVersion: number;
  updatedAt: string | null;
  actor: string | null;
  reason: string | null;
  policies: OpsPolicyProfile[];
  error?: string | null;
};

export type OpsPolicyHistoryEntry = {
  version: number;
  updatedAt: string | null;
  actor: string | null;
  reason: string | null;
};

export type OpsPolicyHistoryResponse = {
  domain: string;
  currentVersion: number;
  history: OpsPolicyHistoryEntry[];
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const isNullableString = (value: unknown): value is string | null =>
  value === null || typeof value === 'string';

const isOptionalNullableString = (value: unknown): value is string | null | undefined =>
  value === undefined || isNullableString(value);

function assertOpsPolicyResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertOpsPolicyProfile = (value: unknown): OpsPolicyProfile => {
  assertOpsPolicyResponse(isRecord(value));
  assertOpsPolicyResponse(typeof value.key === 'string');
  assertOpsPolicyResponse(typeof value.label === 'string');
  assertOpsPolicyResponse(isFiniteNumber(value.maxAttempts));
  assertOpsPolicyResponse(isFiniteNumber(value.retryDelayMs));
  assertOpsPolicyResponse(isFiniteNumber(value.backoffMultiplier));
  assertOpsPolicyResponse(isFiniteNumber(value.quietPeriodMs));
  assertOpsPolicyResponse(typeof value.builtIn === 'boolean');

  return value as unknown as OpsPolicyProfile;
};

const assertOpsPolicyProfiles = (value: unknown): OpsPolicyProfile[] => {
  assertOpsPolicyResponse(Array.isArray(value));
  return value.map(assertOpsPolicyProfile);
};

const assertOpsPolicyDomainState = (value: unknown): OpsPolicyDomainState => {
  assertOpsPolicyResponse(isRecord(value));
  assertOpsPolicyResponse(typeof value.domain === 'string');
  assertOpsPolicyResponse(isFiniteNumber(value.currentVersion));
  assertOpsPolicyResponse(isNullableString(value.updatedAt));
  assertOpsPolicyResponse(isNullableString(value.actor));
  assertOpsPolicyResponse(isNullableString(value.reason));
  const policies = assertOpsPolicyProfiles(value.policies);

  return {
    ...value,
    policies,
  } as OpsPolicyDomainState;
};

const assertOpsPolicyUpdateResponse = (value: unknown): OpsPolicyUpdateResponse => {
  assertOpsPolicyResponse(isRecord(value));
  assertOpsPolicyResponse(typeof value.domain === 'string');
  assertOpsPolicyResponse(isFiniteNumber(value.currentVersion));
  assertOpsPolicyResponse(isNullableString(value.updatedAt));
  assertOpsPolicyResponse(isNullableString(value.actor));
  assertOpsPolicyResponse(isNullableString(value.reason));
  assertOpsPolicyResponse(isOptionalNullableString(value.error));
  const updatedPolicy = value.updatedPolicy === null
    ? null
    : assertOpsPolicyProfile(value.updatedPolicy);
  const policies = assertOpsPolicyProfiles(value.policies);

  return {
    ...value,
    updatedPolicy,
    policies,
  } as OpsPolicyUpdateResponse;
};

const assertOpsPolicyRollbackResponse = (value: unknown): OpsPolicyRollbackResponse => {
  assertOpsPolicyResponse(isRecord(value));
  assertOpsPolicyResponse(typeof value.domain === 'string');
  assertOpsPolicyResponse(isFiniteNumber(value.previousVersion));
  assertOpsPolicyResponse(isFiniteNumber(value.rolledBackToVersion));
  assertOpsPolicyResponse(isFiniteNumber(value.currentVersion));
  assertOpsPolicyResponse(isNullableString(value.updatedAt));
  assertOpsPolicyResponse(isNullableString(value.actor));
  assertOpsPolicyResponse(isNullableString(value.reason));
  assertOpsPolicyResponse(isOptionalNullableString(value.error));
  const policies = assertOpsPolicyProfiles(value.policies);

  return {
    ...value,
    policies,
  } as OpsPolicyRollbackResponse;
};

const assertOpsPolicyHistoryEntry = (value: unknown): OpsPolicyHistoryEntry => {
  assertOpsPolicyResponse(isRecord(value));
  assertOpsPolicyResponse(isFiniteNumber(value.version));
  assertOpsPolicyResponse(isNullableString(value.updatedAt));
  assertOpsPolicyResponse(isNullableString(value.actor));
  assertOpsPolicyResponse(isNullableString(value.reason));

  return value as unknown as OpsPolicyHistoryEntry;
};

const assertOpsPolicyHistoryResponse = (value: unknown): OpsPolicyHistoryResponse => {
  assertOpsPolicyResponse(isRecord(value));
  assertOpsPolicyResponse(typeof value.domain === 'string');
  assertOpsPolicyResponse(isFiniteNumber(value.currentVersion));
  assertOpsPolicyResponse(Array.isArray(value.history));
  const history = value.history.map(assertOpsPolicyHistoryEntry);

  return {
    ...value,
    history,
  } as OpsPolicyHistoryResponse;
};

class OpsPolicyService {
  async getDomain(domain = 'PREVIEW'): Promise<OpsPolicyDomainState> {
    const result = await api.get<unknown>('/ops/policies', { params: { domain } });
    return assertOpsPolicyDomainState(result);
  }

  async updatePolicy(domain: string, payload: OpsPolicyUpdateRequest): Promise<OpsPolicyUpdateResponse> {
    const result = await api.put<unknown>(`/ops/policies/${encodeURIComponent(domain)}`, payload);
    return assertOpsPolicyUpdateResponse(result);
  }

  async rollback(domain: string, payload?: OpsPolicyRollbackRequest): Promise<OpsPolicyRollbackResponse> {
    const result = await api.post<unknown>(
      `/ops/policies/${encodeURIComponent(domain)}/rollback`,
      payload ?? {}
    );
    return assertOpsPolicyRollbackResponse(result);
  }

  async getHistory(domain = 'PREVIEW', limit = 20): Promise<OpsPolicyHistoryResponse> {
    const result = await api.get<unknown>(
      `/ops/policies/${encodeURIComponent(domain)}/history`,
      { params: { limit } }
    );
    return assertOpsPolicyHistoryResponse(result);
  }
}

const opsPolicyService = new OpsPolicyService();
export default opsPolicyService;
