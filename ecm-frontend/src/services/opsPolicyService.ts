import api from './api';

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

class OpsPolicyService {
  async getDomain(domain = 'PREVIEW'): Promise<OpsPolicyDomainState> {
    return api.get<OpsPolicyDomainState>('/ops/policies', { params: { domain } });
  }

  async updatePolicy(domain: string, payload: OpsPolicyUpdateRequest): Promise<OpsPolicyUpdateResponse> {
    return api.put<OpsPolicyUpdateResponse>(`/ops/policies/${encodeURIComponent(domain)}`, payload);
  }

  async rollback(domain: string, payload?: OpsPolicyRollbackRequest): Promise<OpsPolicyRollbackResponse> {
    return api.post<OpsPolicyRollbackResponse>(
      `/ops/policies/${encodeURIComponent(domain)}/rollback`,
      payload ?? {}
    );
  }

  async getHistory(domain = 'PREVIEW', limit = 20): Promise<OpsPolicyHistoryResponse> {
    return api.get<OpsPolicyHistoryResponse>(
      `/ops/policies/${encodeURIComponent(domain)}/history`,
      { params: { limit } }
    );
  }
}

const opsPolicyService = new OpsPolicyService();
export default opsPolicyService;
