const HIGH_PRECISION_QUERY_SUFFIXES = [
  '.bin',
  '.dat',
  '.tmp',
  '.dwg',
  '.dxf',
  '.stp',
  '.step',
  '.igs',
  '.iges',
  '.x_t',
  '.x_b',
  '.7z',
  '.rar',
  '.zip',
];

const TEXT_LIKE_SUFFIXES = new Set([
  '.txt',
  '.pdf',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.csv',
  '.json',
  '.xml',
  '.md',
  '.rtf',
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.webp',
]);

const FILENAME_QUERY_PATTERN = /^[^\s/\\]+\.[a-z0-9]{1,10}$/i;

export const shouldSuppressStaleFallbackForQuery = (query?: string): boolean => {
  const normalized = (query || '').trim().toLowerCase();
  if (!normalized) {
    return false;
  }
  if (normalized.includes(' ')) {
    return false;
  }
  if (HIGH_PRECISION_QUERY_SUFFIXES.some((suffix) => normalized.endsWith(suffix))) {
    return true;
  }
  if (Array.from(TEXT_LIKE_SUFFIXES).some((suffix) => normalized.endsWith(suffix))) {
    return false;
  }

  const hasLongDigitRun = /\d{8,}/.test(normalized);
  const hasStructuredSeparators = /[-_.]/.test(normalized);
  if (!hasLongDigitRun || !hasStructuredSeparators || normalized.length < 24) {
    return false;
  }

  const vowelCount = (normalized.match(/[aeiou]/g) || []).length;
  return vowelCount <= Math.floor(normalized.length / 6);
};

export const shouldSkipSpellcheckForQuery = (query?: string): boolean => {
  const normalized = (query || '').trim().toLowerCase();
  if (!normalized || normalized.length < 3) {
    return true;
  }
  if (normalized.includes(' ')) {
    return false;
  }
  if (/[\\/]/.test(normalized)) {
    return true;
  }
  if (FILENAME_QUERY_PATTERN.test(normalized)) {
    return true;
  }
  if (shouldSuppressStaleFallbackForQuery(normalized)) {
    return true;
  }

  const hasLongDigitRun = /\d{6,}/.test(normalized);
  const hasStructuredSeparators = /[-_.]/.test(normalized);
  return hasLongDigitRun && hasStructuredSeparators && normalized.length >= 16;
};
