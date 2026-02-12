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

const asNumber = (input: unknown) => {
  if (typeof input === 'number' && Number.isFinite(input)) {
    return input;
  }
  if (typeof input === 'string') {
    const parsed = Number(input);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
};

export const buildSearchCriteriaFromSavedSearch = (item: SavedSearch): SearchCriteria => {
  const queryParams = (item.queryParams || {}) as Record<string, unknown>;
  const filters = (queryParams.filters || {}) as Record<string, unknown>;

  const getFilterValue = (key: string) =>
    filters[key] !== undefined ? filters[key] : queryParams[key];

  const mimeTypes = normalizeList(getFilterValue('mimeTypes'));
  const createdByList = normalizeList(getFilterValue('createdByList'));
  const properties = normalizeProperties(getFilterValue('properties'));
  const createdBy = typeof getFilterValue('createdBy') === 'string'
    ? (getFilterValue('createdBy') as string)
    : (createdByList[0] || undefined);
  const contentType = typeof getFilterValue('contentType') === 'string'
    ? (getFilterValue('contentType') as string)
    : (mimeTypes[0] || undefined);

  return {
    name: typeof queryParams.query === 'string'
      ? queryParams.query
      : (typeof queryParams.q === 'string' ? queryParams.q : ''),
    contentType,
    mimeTypes,
    aspects: normalizeList(getFilterValue('aspects')),
    properties,
    createdBy,
    createdByList: createdByList.length ? createdByList : undefined,
    createdFrom: typeof getFilterValue('dateFrom') === 'string' ? (getFilterValue('dateFrom') as string) : undefined,
    createdTo: typeof getFilterValue('dateTo') === 'string' ? (getFilterValue('dateTo') as string) : undefined,
    modifiedFrom: typeof getFilterValue('modifiedFrom') === 'string' ? (getFilterValue('modifiedFrom') as string) : undefined,
    modifiedTo: typeof getFilterValue('modifiedTo') === 'string' ? (getFilterValue('modifiedTo') as string) : undefined,
    tags: normalizeList(getFilterValue('tags')),
    categories: normalizeList(getFilterValue('categories')),
    correspondents: normalizeList(getFilterValue('correspondents')),
    previewStatuses: normalizeList(getFilterValue('previewStatuses')),
    minSize: asNumber(getFilterValue('minSize')),
    maxSize: asNumber(getFilterValue('maxSize')),
    path: typeof getFilterValue('path') === 'string' ? (getFilterValue('path') as string) : undefined,
    folderId: typeof getFilterValue('folderId') === 'string' ? (getFilterValue('folderId') as string) : undefined,
    includeChildren: typeof getFilterValue('includeChildren') === 'boolean'
      ? (getFilterValue('includeChildren') as boolean)
      : undefined,
  };
};
