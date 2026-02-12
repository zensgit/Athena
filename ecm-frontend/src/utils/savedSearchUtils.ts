import { SavedSearch } from 'services/savedSearchService';
import { SearchCriteria } from 'types';

const normalizeList = (input: unknown) =>
  Array.isArray(input)
    ? input.map((value) => String(value).trim()).filter((value) => value.length > 0)
    : (typeof input === 'string'
      ? input
        .split(',')
        .map((value) => value.trim())
        .filter((value) => value.length > 0)
      : []);

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

const asBoolean = (input: unknown) => {
  if (typeof input === 'boolean') {
    return input;
  }
  if (typeof input === 'string') {
    const normalized = input.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  return undefined;
};

const asString = (input: unknown) =>
  typeof input === 'string' ? input : undefined;

const normalizeStatusList = (input: unknown) => {
  if (Array.isArray(input)) {
    return input
      .map((value) => String(value).trim().toUpperCase())
      .filter((value) => value.length > 0);
  }
  if (typeof input === 'string') {
    return input
      .split(',')
      .map((value) => value.trim().toUpperCase())
      .filter((value) => value.length > 0);
  }
  return [];
};

export const buildSearchCriteriaFromSavedSearch = (item: SavedSearch): SearchCriteria => {
  const queryParams = (item.queryParams || {}) as Record<string, unknown>;
  const filters = (queryParams.filters || {}) as Record<string, unknown>;

  const getFilterValue = (...keys: string[]) => {
    for (const key of keys) {
      if (filters[key] !== undefined) {
        return filters[key];
      }
    }
    for (const key of keys) {
      if (queryParams[key] !== undefined) {
        return queryParams[key];
      }
    }
    return undefined;
  };

  const mimeTypes = normalizeList(getFilterValue('mimeTypes'));
  const createdByList = normalizeList(getFilterValue('createdByList', 'creators'));
  const properties = normalizeProperties(getFilterValue('properties'));
  const createdBy = typeof getFilterValue('createdBy') === 'string'
    ? (getFilterValue('createdBy') as string)
    : (createdByList[0] || undefined);
  const contentType = typeof getFilterValue('contentType') === 'string'
    ? (getFilterValue('contentType') as string)
    : (mimeTypes[0] || undefined);
  const previewStatuses = normalizeStatusList(
    getFilterValue('previewStatuses', 'previewStatus')
  );
  const name = asString(queryParams.query)
    || asString(queryParams.q)
    || asString(queryParams.queryString)
    || '';

  return {
    name,
    contentType,
    mimeTypes,
    aspects: normalizeList(getFilterValue('aspects')),
    properties,
    createdBy,
    createdByList: createdByList.length ? createdByList : undefined,
    createdFrom: asString(getFilterValue('createdFrom', 'dateFrom')),
    createdTo: asString(getFilterValue('createdTo', 'dateTo')),
    modifiedFrom: typeof getFilterValue('modifiedFrom') === 'string' ? (getFilterValue('modifiedFrom') as string) : undefined,
    modifiedTo: typeof getFilterValue('modifiedTo') === 'string' ? (getFilterValue('modifiedTo') as string) : undefined,
    tags: normalizeList(getFilterValue('tags')),
    categories: normalizeList(getFilterValue('categories')),
    correspondents: normalizeList(getFilterValue('correspondents')),
    previewStatuses,
    minSize: asNumber(getFilterValue('minSize')),
    maxSize: asNumber(getFilterValue('maxSize')),
    path: asString(getFilterValue('path', 'pathPrefix')),
    folderId: typeof getFilterValue('folderId') === 'string' ? (getFilterValue('folderId') as string) : undefined,
    includeChildren: asBoolean(getFilterValue('includeChildren')),
  };
};
