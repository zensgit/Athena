import { SavedSearch } from 'services/savedSearchService';
import { SearchCriteria } from 'types';

const KNOWN_PREVIEW_STATUSES = new Set([
  'READY',
  'PROCESSING',
  'QUEUED',
  'FAILED',
  'UNSUPPORTED',
  'PENDING',
]);

const PREVIEW_STATUS_ALIAS_MAP: Record<string, string> = {
  IN_PROGRESS: 'PROCESSING',
  RUNNING: 'PROCESSING',
  WAITING: 'QUEUED',
  ERROR: 'FAILED',
  UNSUPPORTED_MEDIA_TYPE: 'UNSUPPORTED',
  UNSUPPORTED_MIME: 'UNSUPPORTED',
  PREVIEW_UNSUPPORTED: 'UNSUPPORTED',
};

const asRecord = (input: unknown): Record<string, unknown> | undefined => {
  if (input && typeof input === 'object' && !Array.isArray(input)) {
    return input as Record<string, unknown>;
  }
  if (typeof input === 'string') {
    try {
      const parsed = JSON.parse(input);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      // ignore invalid JSON strings
    }
  }
  return undefined;
};

const normalizeList = (input: unknown) =>
  Array.isArray(input)
    ? input
      .map((value) => (
        typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
          ? String(value).trim()
          : ''
      ))
      .filter((value) => value.length > 0)
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

const asNonNegativeNumber = (input: unknown) => {
  if (typeof input === 'number' && Number.isFinite(input)) {
    return input >= 0 ? input : undefined;
  }
  if (typeof input === 'string') {
    const parsed = Number(input);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
  }
  return undefined;
};

const asBoolean = (input: unknown) => {
  if (typeof input === 'boolean') {
    return input;
  }
  if (typeof input === 'number') {
    if (input === 1) return true;
    if (input === 0) return false;
  }
  if (typeof input === 'string') {
    const normalized = input.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
    if (normalized === '1') return true;
    if (normalized === '0') return false;
    if (normalized === 'yes') return true;
    if (normalized === 'no') return false;
  }
  return undefined;
};

const asString = (input: unknown) =>
  typeof input === 'string' && input.trim().length > 0 ? input.trim() : undefined;

const asDateString = (input: unknown) => {
  const value = asString(input);
  if (!value) {
    return undefined;
  }
  return Number.isNaN(Date.parse(value)) ? undefined : value;
};

const asRange = (input: unknown): { from?: string; to?: string } => {
  const record = asRecord(input);
  if (!record) {
    return {};
  }
  return {
    from: asDateString(record.from),
    to: asDateString(record.to),
  };
};

const normalizeStatusToken = (value: unknown) => {
  const raw = String(value).trim().toUpperCase();
  if (!raw) {
    return undefined;
  }
  if (KNOWN_PREVIEW_STATUSES.has(raw)) {
    return raw;
  }
  if (PREVIEW_STATUS_ALIAS_MAP[raw]) {
    return PREVIEW_STATUS_ALIAS_MAP[raw];
  }
  if (raw.includes('UNSUPPORTED')) {
    return 'UNSUPPORTED';
  }
  return undefined;
};

const normalizeStatusList = (input: unknown) => {
  const rawValues = Array.isArray(input)
    ? input
    : (typeof input === 'string' ? input.split(',') : []);

  return Array.from(
    new Set(
      rawValues
        .map((value) => normalizeStatusToken(value))
        .filter((value): value is string => Boolean(value))
    )
  );
};

export const buildSearchCriteriaFromSavedSearch = (item: SavedSearch): SearchCriteria => {
  const queryParams = asRecord(item.queryParams) || {};
  const filters =
    asRecord(queryParams.filters)
    || asRecord(queryParams.filter)
    || asRecord(queryParams.criteria)
    || {};

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

  const mimeTypes = normalizeList(getFilterValue('mimeTypes', 'mimeType', 'mimetype'));
  const createdByList = normalizeList(getFilterValue('createdByList', 'creators', 'creator', 'createdByUser'));
  const properties = normalizeProperties(getFilterValue('properties'));
  const createdByDirect = asString(getFilterValue('createdBy', 'creator', 'createdByUser'));
  const createdBy = createdByDirect || createdByList[0] || undefined;
  const contentTypeDirect = asString(getFilterValue('contentType', 'mimeType', 'mimetype'));
  const contentType = contentTypeDirect || mimeTypes[0] || undefined;
  const previewStatuses = normalizeStatusList(
    getFilterValue('previewStatuses', 'previewStatus')
  );
  const createdRange = asRange(getFilterValue('createdRange', 'createdDateRange'));
  const modifiedRange = asRange(getFilterValue('modifiedRange', 'modifiedDateRange'));
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
    createdFrom: asDateString(getFilterValue('createdFrom', 'dateFrom')) || createdRange.from,
    createdTo: asDateString(getFilterValue('createdTo', 'dateTo')) || createdRange.to,
    modifiedFrom: asDateString(getFilterValue('modifiedFrom', 'dateModifiedFrom')) || modifiedRange.from,
    modifiedTo: asDateString(getFilterValue('modifiedTo', 'dateModifiedTo')) || modifiedRange.to,
    tags: normalizeList(getFilterValue('tags')),
    categories: normalizeList(getFilterValue('categories')),
    correspondents: normalizeList(getFilterValue('correspondents')),
    previewStatuses,
    minSize: asNonNegativeNumber(getFilterValue('minSize')),
    maxSize: asNonNegativeNumber(getFilterValue('maxSize')),
    path: asString(getFilterValue('path', 'pathPrefix', 'pathStartsWith')),
    folderId: asString(getFilterValue('folderId')),
    includeChildren: asBoolean(getFilterValue('includeChildren')),
  };
};
