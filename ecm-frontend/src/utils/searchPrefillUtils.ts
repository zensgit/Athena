import { SearchPrefill } from 'store/slices/uiSlice';

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

const parseOptionalNumber = (value: string | null) => {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
};

const resolveModifiedFromByRange = (raw: string | null) => {
  const now = new Date();
  if (raw === 'today') {
    const today = new Date(now);
    today.setHours(0, 0, 0, 0);
    return today.toISOString();
  }
  if (raw === 'week') {
    const week = new Date(now);
    week.setDate(now.getDate() - 7);
    return week.toISOString();
  }
  if (raw === 'month') {
    const month = new Date(now);
    month.setMonth(now.getMonth() - 1);
    return month.toISOString();
  }
  return undefined;
};

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

  const params = new URLSearchParams(search);
  const query = (params.get('q') || params.get('query') || '').trim();
  const previewStatuses = normalizePreviewStatusTokens(params.get('previewStatus'));
  const mimeTypes = parseCsv(params.get('mimeTypes'));
  const creators = parseCsv(params.get('creators'));
  const tags = parseCsv(params.get('tags'));
  const categories = parseCsv(params.get('categories'));
  const minSize = parseOptionalNumber(params.get('minSize'));
  const maxSize = parseOptionalNumber(params.get('maxSize'));
  const modifiedFrom = resolveModifiedFromByRange(params.get('dateRange'));

  const prefillPayload: Partial<SearchPrefill> = {};
  if (query) prefillPayload.name = query;
  if (previewStatuses.length > 0) prefillPayload.previewStatuses = previewStatuses;
  if (mimeTypes.length === 1) prefillPayload.contentType = mimeTypes[0];
  if (creators.length === 1) prefillPayload.createdBy = creators[0];
  if (tags.length > 0) prefillPayload.tags = tags;
  if (categories.length > 0) prefillPayload.categories = categories;
  if (minSize !== undefined) prefillPayload.minSize = minSize;
  if (maxSize !== undefined) prefillPayload.maxSize = maxSize;
  if (modifiedFrom) prefillPayload.modifiedFrom = modifiedFrom;

  return prefillPayload;
};
