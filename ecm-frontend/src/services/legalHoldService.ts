import api from './api';

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

class LegalHoldService {
  async listHolds(): Promise<LegalHoldSummary[]> {
    return api.get<LegalHoldSummary[]>('/legal-holds');
  }

  async getHold(holdId: string): Promise<LegalHoldDetail> {
    return api.get<LegalHoldDetail>(`/legal-holds/${holdId}`);
  }

  async createHold(data: CreateLegalHoldRequest): Promise<LegalHoldDetail> {
    return api.post<LegalHoldDetail>('/legal-holds', data);
  }

  async addItems(holdId: string, data: AddItemsRequest): Promise<LegalHoldDetail> {
    return api.post<LegalHoldDetail>(`/legal-holds/${holdId}/items`, data);
  }

  async removeItem(holdId: string, nodeId: string): Promise<LegalHoldDetail> {
    return api.delete<LegalHoldDetail>(`/legal-holds/${holdId}/items/${nodeId}`);
  }

  async releaseHold(holdId: string, data: ReleaseHoldRequest): Promise<LegalHoldDetail> {
    return api.post<LegalHoldDetail>(`/legal-holds/${holdId}/release`, data);
  }
}

const legalHoldService = new LegalHoldService();
export default legalHoldService;
