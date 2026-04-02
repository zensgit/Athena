import { SearchPrefill } from 'store/slices/uiSlice';
import { parseAdvancedSearchUrlState, resolveModifiedFromDate } from './advancedSearchStateUtils';

const PREVIEW_STATUS_ALIAS_MAP: Record<string, string> = {
  IN_PROGRESS: 'PROCESSING',
  RUNNING: 'PROCESSING',
  WAITING: 'QUEUED',
  ERROR: 'FAILED',
  UNSUPPORTED_MEDIA_TYPE: 'UNSUPPORTED',
  UNSUPPORTED_MIME: 'UNSUPPORTED',
  PREVIEW_UNSUPPORTED: 'UNSUPPORTED',
};

const KNOWN_PREVIEW_STATUSES = new Set([
  'READY',
  'PROCESSING',
  'QUEUED',
  'FAILED',
  'UNSUPPORTED',
  'PENDING',
]);

const parseCsv = (value: string | null) =>
  Array.from(new Set((value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)));

export const normalizePreviewStatusTokens = (
  input: string[] | string | null | undefined
) =>
  Array.from(
    new Set(
      (Array.isArray(input) ? input : parseCsv(input || null))
        .map((status) => {
          const raw = status.trim().toUpperCase();
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
        })
        .filter((status): status is string => Boolean(status))
    )
  );

export const buildSearchPrefillFromAdvancedSearchUrl = (
  pathname: string,
  search: string
): Partial<SearchPrefill> => {
  if (pathname !== '/search') {
    return {};
  }

  const state = parseAdvancedSearchUrlState(search);
  const params = new URLSearchParams(search);
  const query = (params.get('q') || params.get('query') || state.query).trim();
  const previewStatuses = state.previewStatuses.length > 0
    ? state.previewStatuses
    : normalizePreviewStatusTokens(params.get('previewStatus'));
  const mimeTypes = state.mimeTypes.length > 0 ? state.mimeTypes : parseCsv(params.get('mimeTypes'));
  const creators = state.creators.length > 0 ? state.creators : parseCsv(params.get('creators'));
  const tags = state.tags.length > 0 ? state.tags : parseCsv(params.get('tags'));
  const categories = state.categories.length > 0 ? state.categories : parseCsv(params.get('categories'));
  const minSize = state.minSize;
  const maxSize = state.maxSize;
  const modifiedFrom = resolveModifiedFromDate(state.dateRange);

  const prefillPayload: Partial<SearchPrefill> = {};
  if (query) prefillPayload.name = query;
  if (previewStatuses.length > 0) prefillPayload.previewStatuses = previewStatuses;
  if (state.lockState === 'locked') prefillPayload.locked = true;
  if (state.lockState === 'unlocked') prefillPayload.locked = false;
  if (state.lockOwner.trim()) prefillPayload.lockedBy = state.lockOwner.trim();
  if (state.checkoutState === 'checkedOut') prefillPayload.checkedOut = true;
  if (state.checkoutState === 'available') prefillPayload.checkedOut = false;
  if (state.checkoutUser.trim()) prefillPayload.checkoutUser = state.checkoutUser.trim();
  if (mimeTypes.length === 1) prefillPayload.contentType = mimeTypes[0];
  if (creators.length === 1) prefillPayload.createdBy = creators[0];
  if (tags.length > 0) prefillPayload.tags = tags;
  if (categories.length > 0) prefillPayload.categories = categories;
  if (minSize !== undefined) prefillPayload.minSize = minSize;
  if (maxSize !== undefined) prefillPayload.maxSize = maxSize;
  if (modifiedFrom) prefillPayload.modifiedFrom = modifiedFrom;

  return prefillPayload;
};
