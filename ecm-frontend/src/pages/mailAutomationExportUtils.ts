export interface DiagnosticsExportRunIdInput {
  debugRunId?: string | null;
  fetchRunId?: string | null;
  debugAt?: string | null;
  fetchAt?: string | null;
}

const parseTimestamp = (value?: string | null): number => {
  if (!value) {
    return Number.NaN;
  }
  return new Date(value).getTime();
};

export const sanitizeRunIdForFilename = (runId?: string | null): string => {
  const trimmed = (runId || '').trim();
  if (!trimmed) {
    return '';
  }
  return trimmed
    .replace(/[^a-zA-Z0-9_-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
};

export const resolveDiagnosticsExportRunId = (input: DiagnosticsExportRunIdInput): string => {
  const debugRunId = (input.debugRunId || '').trim();
  const fetchRunId = (input.fetchRunId || '').trim();
  if (!debugRunId && !fetchRunId) {
    return '';
  }
  if (debugRunId && fetchRunId) {
    const debugAt = parseTimestamp(input.debugAt);
    const fetchAt = parseTimestamp(input.fetchAt);
    if (Number.isFinite(debugAt) && Number.isFinite(fetchAt)) {
      return debugAt >= fetchAt ? debugRunId : fetchRunId;
    }
    return debugRunId;
  }
  return debugRunId || fetchRunId;
};

