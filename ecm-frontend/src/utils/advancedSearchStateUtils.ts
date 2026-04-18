import { SearchCriteria } from 'types';
import { normalizePreviewStatusTokens } from './searchPrefillUtils';

export type AdvancedSearchDateRange = 'all' | 'today' | 'week' | 'month';

export type AdvancedSearchCriteriaState = {
  query: string;
  previewStatuses: string[];
  lockState: 'all' | 'locked' | 'unlocked';
  lockOwner: string;
  checkoutState: 'all' | 'checkedOut' | 'available';
  checkoutUser: string;
  recordOnly: boolean;
  recordCategoryPaths: string[];
  dateRange: AdvancedSearchDateRange;
  mimeTypes: string[];
  creators: string[];
  tags: string[];
  categories: string[];
  minSize?: number;
  maxSize?: number;
};

export type AdvancedSearchFilterState = AdvancedSearchCriteriaState;

export type AdvancedSearchUrlState = AdvancedSearchCriteriaState & {
  page: number;
};

const PREVIEW_STATUS_VALUES = ['READY', 'PROCESSING', 'QUEUED', 'FAILED', 'UNSUPPORTED', 'PENDING'] as const;
const DATE_RANGE_VALUES = ['all', 'today', 'week', 'month'] as const;
const LOCK_STATE_VALUES = ['all', 'locked', 'unlocked'] as const;
const CHECKOUT_STATE_VALUES = ['all', 'checkedOut', 'available'] as const;

const parseCsvValues = (rawValue: string | null): string[] => {
  if (!rawValue) {
    return [];
  }
  return Array.from(new Set(rawValue.split(',').map((value) => value.trim()).filter(Boolean)));
};

const parsePreviewStatuses = (rawValue: string | null): string[] => {
  const parsed = normalizePreviewStatusTokens(rawValue);
  const allowed = new Set(PREVIEW_STATUS_VALUES);
  return parsed.filter((value) => allowed.has(value as (typeof PREVIEW_STATUS_VALUES)[number]));
};

const parseDateRange = (rawValue: string | null): AdvancedSearchDateRange => {
  if (!rawValue) {
    return 'all';
  }
  return DATE_RANGE_VALUES.includes(rawValue as (typeof DATE_RANGE_VALUES)[number])
    ? (rawValue as AdvancedSearchDateRange)
    : 'all';
};

const parseCheckoutState = (rawValue: string | null): AdvancedSearchCriteriaState['checkoutState'] => {
  if (!rawValue) {
    return 'all';
  }
  return CHECKOUT_STATE_VALUES.includes(rawValue as (typeof CHECKOUT_STATE_VALUES)[number])
    ? (rawValue as AdvancedSearchCriteriaState['checkoutState'])
    : 'all';
};

const parseLockState = (rawValue: string | null): AdvancedSearchCriteriaState['lockState'] => {
  if (!rawValue) {
    return 'all';
  }
  return LOCK_STATE_VALUES.includes(rawValue as (typeof LOCK_STATE_VALUES)[number])
    ? (rawValue as AdvancedSearchCriteriaState['lockState'])
    : 'all';
};

const parseOptionalNumber = (rawValue: string | null): number | undefined => {
  if (!rawValue) {
    return undefined;
  }
  const value = Number(rawValue);
  if (!Number.isFinite(value) || value < 0) {
    return undefined;
  }
  return value;
};

const parseBooleanFlag = (rawValue: string | null): boolean => {
  if (!rawValue) {
    return false;
  }
  return rawValue.trim().toLowerCase() === 'true';
};

const asRecord = (value: unknown): Record<string, unknown> | null => {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
    } catch {
      // ignore invalid JSON strings
    }
  }
  return null;
};

const parseTemplateValues = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return Array.from(
      new Set(
        value
          .map((item) => (
            typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean'
              ? String(item).trim()
              : ''
          ))
          .filter((item) => item.length > 0)
      )
    );
  }
  if (typeof value === 'string') {
    return parseCsvValues(value);
  }
  return [];
};

const parseTemplatePreviewStatuses = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return parsePreviewStatuses(value.map((item) => String(item)).join(','));
  }
  if (typeof value === 'string') {
    return parsePreviewStatuses(value);
  }
  return [];
};

const parseTemplateDateRange = (value: unknown): AdvancedSearchDateRange => {
  if (typeof value !== 'string') {
    return 'all';
  }
  return parseDateRange(value);
};

const parseTemplateOptionalNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value >= 0 ? value : undefined;
  }
  if (typeof value === 'string') {
    return parseOptionalNumber(value);
  }
  return undefined;
};

const normalizeCriteriaValues = (values?: string[]) =>
  (values || [])
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
    .sort();

export const resolveModifiedFromDate = (range: AdvancedSearchDateRange): string | undefined => {
  const now = new Date();
  switch (range) {
    case 'today': {
      const start = new Date(now);
      start.setHours(0, 0, 0, 0);
      return start.toISOString();
    }
    case 'week': {
      const start = new Date(now);
      start.setDate(start.getDate() - 7);
      return start.toISOString();
    }
    case 'month': {
      const start = new Date(now);
      start.setMonth(start.getMonth() - 1);
      return start.toISOString();
    }
    default:
      return undefined;
  }
};

export const parseAdvancedSearchUrlState = (search: string): AdvancedSearchUrlState => {
  const params = new URLSearchParams(search);
  const queryFromUrl = (params.get('q') || '').trim();
  const parsedPage = Number(params.get('page') || '1');
  const pageFromUrl = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 1;

  return {
    query: queryFromUrl,
    page: pageFromUrl,
    previewStatuses: parsePreviewStatuses(params.get('previewStatus')),
    lockState: parseLockState(params.get('lockState')),
    lockOwner: (params.get('lockOwner') || params.get('lockedBy') || '').trim(),
    checkoutState: parseCheckoutState(params.get('checkoutState')),
    checkoutUser: (params.get('checkoutUser') || '').trim(),
    recordOnly: parseBooleanFlag(params.get('recordOnly')),
    recordCategoryPaths: parseCsvValues(params.get('recordCategoryPaths')),
    dateRange: parseDateRange(params.get('dateRange')),
    mimeTypes: parseCsvValues(params.get('mimeTypes')),
    creators: parseCsvValues(params.get('creators')),
    tags: parseCsvValues(params.get('tags')),
    categories: parseCsvValues(params.get('categories')),
    minSize: parseOptionalNumber(params.get('minSize')),
    maxSize: parseOptionalNumber(params.get('maxSize')),
  };
};

export const buildAdvancedSearchUrlSearch = (state: AdvancedSearchUrlState): string => {
  const params = new URLSearchParams();
  const normalizedQuery = state.query.trim();
  const normalizedLockState = state.lockState ?? 'all';
  const normalizedLockOwner = state.lockOwner?.trim() || '';
  const normalizedCheckoutState = state.checkoutState ?? 'all';
  const normalizedCheckoutUser = state.checkoutUser?.trim() || '';
  if (normalizedQuery) {
    params.set('q', normalizedQuery);
  }
  if (state.page > 1) {
    params.set('page', String(state.page));
  }
  if (state.previewStatuses.length > 0) {
    params.set('previewStatus', state.previewStatuses.join(','));
  }
  if (normalizedLockState !== 'all') {
    params.set('lockState', normalizedLockState);
  }
  if (normalizedLockOwner) {
    params.set('lockOwner', normalizedLockOwner);
  }
  if (normalizedCheckoutState !== 'all') {
    params.set('checkoutState', normalizedCheckoutState);
  }
  if (normalizedCheckoutUser) {
    params.set('checkoutUser', normalizedCheckoutUser);
  }
  if (state.recordOnly) {
    params.set('recordOnly', 'true');
  }
  if (state.recordCategoryPaths.length > 0) {
    params.set('recordCategoryPaths', state.recordCategoryPaths.join(','));
  }
  if (state.dateRange !== 'all') {
    params.set('dateRange', state.dateRange);
  }
  if (state.mimeTypes.length > 0) params.set('mimeTypes', state.mimeTypes.join(','));
  if (state.creators.length > 0) params.set('creators', state.creators.join(','));
  if (state.tags.length > 0) params.set('tags', state.tags.join(','));
  if (state.categories.length > 0) params.set('categories', state.categories.join(','));
  if (state.minSize !== undefined) params.set('minSize', String(state.minSize));
  if (state.maxSize !== undefined) params.set('maxSize', String(state.maxSize));
  return params.toString();
};

export const hasRestorableAdvancedSearchState = (state: AdvancedSearchUrlState): boolean =>
  Boolean(
    state.query
    || state.previewStatuses.length > 0
    || state.lockState !== 'all'
    || state.lockOwner.trim().length > 0
    || state.checkoutState !== 'all'
    || state.checkoutUser.trim().length > 0
    || state.recordOnly
    || state.recordCategoryPaths.length > 0
    || state.dateRange !== 'all'
    || state.mimeTypes.length > 0
    || state.creators.length > 0
    || state.tags.length > 0
    || state.categories.length > 0
    || state.minSize !== undefined
    || state.maxSize !== undefined
    || state.page > 1
  );

export const buildSearchCriteriaFromAdvancedState = (
  state: AdvancedSearchCriteriaState,
  page: number,
  size: number
): SearchCriteria => ({
  name: state.query,
  mimeTypes: state.mimeTypes.length > 0 ? state.mimeTypes : undefined,
  locked: state.lockState === 'locked' ? true : state.lockState === 'unlocked' ? false : undefined,
  lockedBy: state.lockOwner?.trim() || undefined,
  checkedOut: state.checkoutState === 'checkedOut' ? true : state.checkoutState === 'available' ? false : undefined,
  checkoutUser: state.checkoutUser?.trim() || undefined,
  recordOnly: state.recordOnly || undefined,
  recordCategoryPaths: state.recordCategoryPaths.length > 0 ? state.recordCategoryPaths : undefined,
  createdByList: state.creators.length > 0 ? state.creators : undefined,
  tags: state.tags.length > 0 ? state.tags : undefined,
  categories: state.categories.length > 0 ? state.categories : undefined,
  previewStatuses: state.previewStatuses.length > 0 ? state.previewStatuses : undefined,
  modifiedFrom: resolveModifiedFromDate(state.dateRange),
  minSize: state.minSize,
  maxSize: state.maxSize,
  page: page - 1,
  size,
});

export const buildAdvancedSearchCriteriaKey = (criteria: AdvancedSearchCriteriaState) =>
  JSON.stringify({
    previewStatuses: normalizeCriteriaValues(criteria.previewStatuses),
    lockState: criteria.lockState ?? 'all',
    lockOwner: criteria.lockOwner?.trim() || '',
    checkoutState: criteria.checkoutState ?? 'all',
    checkoutUser: criteria.checkoutUser?.trim() || '',
    recordOnly: criteria.recordOnly ?? false,
    recordCategoryPaths: normalizeCriteriaValues(criteria.recordCategoryPaths),
    dateRange: criteria.dateRange,
    mimeTypes: normalizeCriteriaValues(criteria.mimeTypes),
    creators: normalizeCriteriaValues(criteria.creators),
    tags: normalizeCriteriaValues(criteria.tags),
    categories: normalizeCriteriaValues(criteria.categories),
    minSize: criteria.minSize ?? null,
    maxSize: criteria.maxSize ?? null,
  });

export const hasActiveAdvancedSearchCriteria = (criteria: AdvancedSearchCriteriaState) =>
  Boolean(
    criteria.query.trim()
    || criteria.previewStatuses.length > 0
    || (criteria.lockState ?? 'all') !== 'all'
    || (criteria.lockOwner?.trim().length || 0) > 0
    || (criteria.checkoutState ?? 'all') !== 'all'
    || (criteria.checkoutUser?.trim().length || 0) > 0
    || criteria.recordOnly
    || criteria.recordCategoryPaths.length > 0
    || criteria.dateRange !== 'all'
    || criteria.mimeTypes.length > 0
    || criteria.creators.length > 0
    || criteria.tags.length > 0
    || criteria.categories.length > 0
    || criteria.minSize !== undefined
    || criteria.maxSize !== undefined
  );

export const resolveTemplateQueryState = (queryParamsRaw: unknown): AdvancedSearchCriteriaState => {
  const queryParams = asRecord(queryParamsRaw) || {};
  const filters = asRecord(queryParams.filters)
    || asRecord(queryParams.filter)
    || asRecord(queryParams.criteria)
    || {};

  const getValue = (...keys: string[]): unknown => {
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

  const queryCandidate = [queryParams.query, queryParams.q, queryParams.queryString]
    .find((value) => typeof value === 'string' && value.trim().length > 0);

  return {
    query: typeof queryCandidate === 'string' ? queryCandidate.trim() : '',
    previewStatuses: parseTemplatePreviewStatuses(getValue('previewStatuses', 'previewStatus')),
    lockState: parseLockState(
      typeof getValue('lockState') === 'string'
        ? (getValue('lockState') as string)
        : typeof getValue('locked') === 'boolean'
          ? ((getValue('locked') as boolean) ? 'locked' : 'unlocked')
          : typeof getValue('locked') === 'string'
            ? (((getValue('locked') as string).trim().toLowerCase() === 'true')
              ? 'locked'
              : ((getValue('locked') as string).trim().toLowerCase() === 'false' ? 'unlocked' : 'all'))
            : null
    ),
    lockOwner: typeof getValue('lockOwner', 'lockedBy') === 'string'
      ? (getValue('lockOwner', 'lockedBy') as string).trim()
      : '',
    checkoutState: parseCheckoutState(
      typeof getValue('checkoutState') === 'string'
        ? (getValue('checkoutState') as string)
        : typeof getValue('checkedOut') === 'boolean'
          ? ((getValue('checkedOut') as boolean) ? 'checkedOut' : 'available')
          : typeof getValue('checkedOut') === 'string'
            ? (((getValue('checkedOut') as string).trim().toLowerCase() === 'true')
              ? 'checkedOut'
              : ((getValue('checkedOut') as string).trim().toLowerCase() === 'false' ? 'available' : 'all'))
            : null
    ),
    checkoutUser: typeof getValue('checkoutUser', 'checkedOutBy') === 'string'
      ? (getValue('checkoutUser', 'checkedOutBy') as string).trim()
      : '',
    recordOnly: typeof getValue('recordOnly', 'record') === 'boolean'
      ? Boolean(getValue('recordOnly', 'record'))
      : typeof getValue('recordOnly', 'record') === 'string'
        ? parseBooleanFlag(getValue('recordOnly', 'record') as string)
        : false,
    recordCategoryPaths: parseTemplateValues(getValue('recordCategoryPaths', 'recordCategoryPath')),
    dateRange: parseTemplateDateRange(getValue('dateRange')),
    mimeTypes: parseTemplateValues(getValue('mimeTypes', 'mimeType', 'mimetype')),
    creators: parseTemplateValues(getValue('creators', 'createdByList', 'createdBy', 'creator', 'createdByUser')),
    tags: parseTemplateValues(getValue('tags')),
    categories: parseTemplateValues(getValue('categories')),
    minSize: parseTemplateOptionalNumber(getValue('minSize')),
    maxSize: parseTemplateOptionalNumber(getValue('maxSize')),
  };
};
