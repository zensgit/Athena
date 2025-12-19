import api from './api';

export type MatchAlgorithm = 'AUTO' | 'ANY' | 'ALL' | 'EXACT' | 'REGEX' | 'FUZZY';

export interface Correspondent {
  id: string;
  name: string;
  matchAlgorithm: MatchAlgorithm;
  matchPattern?: string | null;
  insensitive: boolean;
  email?: string | null;
  phone?: string | null;
  createdDate?: string;
  createdBy?: string;
  lastModifiedDate?: string;
  lastModifiedBy?: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateCorrespondentRequest {
  name: string;
  matchAlgorithm: MatchAlgorithm;
  matchPattern?: string | null;
  insensitive: boolean;
  email?: string | null;
  phone?: string | null;
}

export type UpdateCorrespondentRequest = Partial<CreateCorrespondentRequest>;

class CorrespondentService {
  async list(page = 0, size = 200): Promise<Correspondent[]> {
    const response = await api.get<PageResponse<Correspondent>>('/correspondents', {
      params: { page, size, sort: 'name,asc' },
    });
    return response.content || [];
  }

  async create(payload: CreateCorrespondentRequest): Promise<Correspondent> {
    return api.post<Correspondent>('/correspondents', payload);
  }

  async update(id: string, payload: UpdateCorrespondentRequest): Promise<Correspondent> {
    return api.put<Correspondent>(`/correspondents/${id}`, payload);
  }
}

const correspondentService = new CorrespondentService();
export default correspondentService;
