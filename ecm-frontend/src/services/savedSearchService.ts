import api from './api';

export const SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE =
  'Saved search endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isBooleanOrNullish = (value: unknown): value is boolean | null | undefined => (
  value === null || value === undefined || typeof value === 'boolean'
);

const isNumberOrNullish = (value: unknown): value is number | null | undefined => (
  value === null || value === undefined || isNumber(value)
);

const isStringArrayOrNullish = (value: unknown): value is string[] | null | undefined => (
  value === null || value === undefined || (Array.isArray(value) && value.every((item) => typeof item === 'string'))
);

const isStringRecordArrayOrNullish = (
  value: unknown
): value is Record<string, string[]> | null | undefined => {
  if (value === null || value === undefined) {
    return true;
  }
  return isObject(value)
    && Object.values(value).every((item) => (
      Array.isArray(item) && item.every((entry) => typeof entry === 'string')
    ));
};

export interface SavedSearch {
  id: string;
  userId: string;
  name: string;
  queryParams: Record<string, any>;
  pinned?: boolean;
  createdAt: string;
}

export interface SavedSearchTemplate {
  id: string;
  name: string;
  description?: string | null;
  queryParams: Record<string, any>;
  tags: string[];
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
  currentVersionLabel?: string;
  locked?: boolean;
  lockedBy?: string;
  checkedOut?: boolean;
  checkoutUser?: string;
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
  record?: boolean;
  declaredBy?: string;
  declaredAt?: string;
  declaredVersionLabel?: string;
  declarationComment?: string;
  recordCategoryId?: string;
  recordCategoryName?: string;
  recordCategoryPath?: string;
  previewStatus?: string;
  previewFailureReason?: string;
  previewFailureCategory?: string;
}

export interface FacetedSearchResponse {
  results?: { content?: SearchResultItem[] };
  facets?: Record<string, FacetValue[]>;
  totalHits?: number;
  queryTime?: number;
}

export interface SmartFolderPayload {
  name?: string;
  description?: string;
  parentId?: string;
}

export interface SmartFolderResponse {
  id: string;
  name: string;
  path: string;
  parentId?: string | null;
  smart?: boolean;
  queryCriteria?: Record<string, any> | null;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE);
};

const isSavedSearch = (value: unknown): value is SavedSearch => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.userId === 'string'
    && typeof value.name === 'string'
    && isObject(value.queryParams)
    && isBooleanOrNullish(value.pinned)
    && typeof value.createdAt === 'string';
};

const assertSavedSearch = (value: unknown): SavedSearch => (
  isSavedSearch(value) ? value : assertUnexpectedResponse()
);

const isSavedSearchTemplate = (value: unknown): value is SavedSearchTemplate => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && isObject(value.queryParams)
    && Array.isArray(value.tags)
    && value.tags.every((tag) => typeof tag === 'string');
};

const assertSavedSearchTemplate = (value: unknown): SavedSearchTemplate => (
  isSavedSearchTemplate(value) ? value : assertUnexpectedResponse()
);

const isFacetValue = (value: unknown): value is FacetValue => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.value === 'string' && isNumber(value.count);
};

const areFacets = (value: unknown): value is Record<string, FacetValue[]> => (
  isObject(value)
  && Object.values(value).every((entries) => (
    Array.isArray(entries) && entries.every(isFacetValue)
  ))
);

const isSearchResultItem = (value: unknown): value is SearchResultItem => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && typeof value.path === 'string'
    && isStringOrNullish(value.description)
    && isStringOrNullish(value.nodeType)
    && isStringOrNullish(value.parentId)
    && isStringOrNullish(value.mimeType)
    && isNumberOrNullish(value.fileSize)
    && isStringOrNullish(value.currentVersionLabel)
    && isBooleanOrNullish(value.locked)
    && isStringOrNullish(value.lockedBy)
    && isBooleanOrNullish(value.checkedOut)
    && isStringOrNullish(value.checkoutUser)
    && isStringOrNullish(value.createdBy)
    && isStringOrNullish(value.createdDate)
    && isStringOrNullish(value.lastModifiedBy)
    && isStringOrNullish(value.lastModifiedDate)
    && isNumberOrNullish(value.score)
    && isStringRecordArrayOrNullish(value.highlights)
    && isStringArrayOrNullish(value.matchFields)
    && isStringOrNullish(value.highlightSummary)
    && isStringArrayOrNullish(value.tags)
    && isStringArrayOrNullish(value.categories)
    && isStringOrNullish(value.correspondent)
    && isBooleanOrNullish(value.record)
    && isStringOrNullish(value.declaredBy)
    && isStringOrNullish(value.declaredAt)
    && isStringOrNullish(value.declaredVersionLabel)
    && isStringOrNullish(value.declarationComment)
    && isStringOrNullish(value.recordCategoryId)
    && isStringOrNullish(value.recordCategoryName)
    && isStringOrNullish(value.recordCategoryPath)
    && isStringOrNullish(value.previewStatus)
    && isStringOrNullish(value.previewFailureReason)
    && isStringOrNullish(value.previewFailureCategory);
};

const isFacetedSearchResponse = (value: unknown): value is FacetedSearchResponse => {
  if (!isObject(value)) {
    return false;
  }
  if (value.results !== undefined && value.results !== null) {
    if (!isObject(value.results)) {
      return false;
    }
    if (
      value.results.content !== undefined
      && (!Array.isArray(value.results.content) || !value.results.content.every(isSearchResultItem))
    ) {
      return false;
    }
  }
  return (value.facets === undefined || value.facets === null || areFacets(value.facets))
    && isNumberOrNullish(value.totalHits)
    && isNumberOrNullish(value.queryTime);
};

const assertFacetedSearchResponse = (value: unknown): FacetedSearchResponse => (
  isFacetedSearchResponse(value) ? value : assertUnexpectedResponse()
);

const isSmartFolderResponse = (value: unknown): value is SmartFolderResponse => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && typeof value.path === 'string'
    && isStringOrNullish(value.parentId)
    && isBooleanOrNullish(value.smart)
    && (value.queryCriteria === undefined || value.queryCriteria === null || isObject(value.queryCriteria));
};

const assertSmartFolderResponse = (value: unknown): SmartFolderResponse => (
  isSmartFolderResponse(value) ? value : assertUnexpectedResponse()
);

class SavedSearchService {
  async save(name: string, queryParams: Record<string, any>): Promise<SavedSearch> {
    const result = await api.post<unknown>('/search/saved', { name, queryParams });
    return assertSavedSearch(result);
  }

  async list(): Promise<SavedSearch[]> {
    const result = await api.get<unknown>('/search/saved');
    if (!Array.isArray(result)) {
      throw new Error(SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result.map(assertSavedSearch);
  }

  async listTemplates(tag?: string): Promise<SavedSearchTemplate[]> {
    const result = await api.get<unknown>('/search/saved/templates', {
      params: tag ? { tag } : undefined,
    });
    if (!Array.isArray(result)) {
      throw new Error(SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result.map(assertSavedSearchTemplate);
  }

  async get(id: string): Promise<SavedSearch> {
    const result = await api.get<unknown>(`/search/saved/${id}`);
    return assertSavedSearch(result);
  }

  async update(
    id: string,
    input: { name?: string; queryParams?: Record<string, any> },
  ): Promise<SavedSearch> {
    const result = await api.patch<unknown>(`/search/saved/${id}`, input);
    return assertSavedSearch(result);
  }

  async delete(id: string): Promise<void> {
    await api.delete<void>(`/search/saved/${id}`);
  }

  async setPinned(id: string, pinned: boolean): Promise<SavedSearch> {
    const result = await api.patch<unknown>(`/search/saved/${id}/pin`, { pinned });
    return assertSavedSearch(result);
  }

  async execute(id: string): Promise<FacetedSearchResponse> {
    const result = await api.get<unknown>(`/search/saved/${id}/execute`);
    return assertFacetedSearchResponse(result);
  }

  async createSmartFolder(id: string, input: SmartFolderPayload): Promise<SmartFolderResponse> {
    const result = await api.post<unknown>(`/search/saved/${id}/smart-folder`, input);
    return assertSmartFolderResponse(result);
  }

  // Downloads the saved search results as a CSV attachment (one-shot; backend caps the row count).
  // The backend sets a sanitized Content-Disposition filename; the client filename below is the
  // saved-as default.
  async exportResultsCsv(id: string, name?: string): Promise<void> {
    const base = name && name.trim() ? name : id;
    const safeName = base.replace(/[^A-Za-z0-9._-]/g, '_') || id;
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
    await api.downloadFile(`/search/saved/${id}/export`, `${safeName}-search-${ts}.csv`);
  }
}

const savedSearchService = new SavedSearchService();
export default savedSearchService;
