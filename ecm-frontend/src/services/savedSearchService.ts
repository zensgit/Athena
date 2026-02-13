import api from './api';

export interface SavedSearch {
  id: string;
  userId: string;
  name: string;
  queryParams: Record<string, any>;
  pinned?: boolean;
  createdAt: string;
}

export interface FacetValue {
  value: string;
  count: number;
}

export interface SearchResultItem {
  id: string;
  name: string;
  description?: string;
  path: string;
  nodeType?: string;
  parentId?: string;
  mimeType?: string;
  fileSize?: number;
  createdBy?: string;
  createdDate?: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
  score?: number;
  highlights?: Record<string, string[]>;
  matchFields?: string[];
  highlightSummary?: string;
  tags?: string[];
  categories?: string[];
  correspondent?: string;
}

export interface FacetedSearchResponse {
  results?: { content?: SearchResultItem[] };
  facets?: Record<string, FacetValue[]>;
  totalHits?: number;
  queryTime?: number;
}

class SavedSearchService {
  async save(name: string, queryParams: Record<string, any>): Promise<SavedSearch> {
    return api.post<SavedSearch>('/search/saved', { name, queryParams });
  }

  async list(): Promise<SavedSearch[]> {
    return api.get<SavedSearch[]>('/search/saved');
  }

  async get(id: string): Promise<SavedSearch> {
    return api.get<SavedSearch>(`/search/saved/${id}`);
  }

  async update(
    id: string,
    input: { name?: string; queryParams?: Record<string, any> },
  ): Promise<SavedSearch> {
    return api.patch<SavedSearch>(`/search/saved/${id}`, input);
  }

  async delete(id: string): Promise<void> {
    await api.delete<void>(`/search/saved/${id}`);
  }

  async setPinned(id: string, pinned: boolean): Promise<SavedSearch> {
    return api.patch<SavedSearch>(`/search/saved/${id}/pin`, { pinned });
  }

  async execute(id: string): Promise<FacetedSearchResponse> {
    return api.get<FacetedSearchResponse>(`/search/saved/${id}/execute`);
  }
}

const savedSearchService = new SavedSearchService();
export default savedSearchService;
