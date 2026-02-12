import { SavedSearch } from 'services/savedSearchService';
import { SearchCriteria } from 'types';

const normalizeList = (input: unknown) =>
  Array.isArray(input)
    ? input.map((value) => String(value).trim()).filter((value) => value.length > 0)
    : [];

const normalizeProperties = (input: unknown) => {
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    return undefined;
  }

  const entries = Object.entries(input as Record<string, unknown>)
    .filter(([key, value]) => key.trim().length > 0 && value !== undefined && value !== null);

  if (entries.length === 0) {
    return undefined;
  }

  return Object.fromEntries(entries);
};

export const buildSearchCriteriaFromSavedSearch = (item: SavedSearch): SearchCriteria => {
  const queryParams = item.queryParams || {};
  const filters = (queryParams.filters || {}) as Record<string, any>;
  const mimeTypes = Array.isArray(filters.mimeTypes) ? filters.mimeTypes : [];
  const createdByList = normalizeList(filters.createdByList);
  const properties = normalizeProperties(filters.properties ?? (queryParams as Record<string, unknown>).properties);

  return {
    name: typeof queryParams.query === 'string' ? queryParams.query : '',
    contentType: typeof mimeTypes[0] === 'string' ? mimeTypes[0] : '',
    mimeTypes: normalizeList(filters.mimeTypes),
    aspects: normalizeList(filters.aspects),
    properties,
    createdBy: typeof filters.createdBy === 'string' ? filters.createdBy : '',
    createdByList: createdByList.length ? createdByList : undefined,
    createdFrom: typeof filters.dateFrom === 'string' ? filters.dateFrom : undefined,
    createdTo: typeof filters.dateTo === 'string' ? filters.dateTo : undefined,
    modifiedFrom: typeof filters.modifiedFrom === 'string' ? filters.modifiedFrom : undefined,
    modifiedTo: typeof filters.modifiedTo === 'string' ? filters.modifiedTo : undefined,
    tags: normalizeList(filters.tags),
    categories: normalizeList(filters.categories),
    correspondents: normalizeList(filters.correspondents),
    previewStatuses: normalizeList(filters.previewStatuses),
    minSize: typeof filters.minSize === 'number' ? filters.minSize : undefined,
    maxSize: typeof filters.maxSize === 'number' ? filters.maxSize : undefined,
    path: typeof filters.path === 'string' ? filters.path : undefined,
    folderId: typeof filters.folderId === 'string' ? filters.folderId : undefined,
    includeChildren: typeof filters.includeChildren === 'boolean' ? filters.includeChildren : undefined,
  };
};
