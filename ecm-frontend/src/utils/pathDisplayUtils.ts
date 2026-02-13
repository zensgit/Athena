type FormatBreadcrumbPathOptions = {
  nodeName?: string | null;
  maxSegments?: number;
};

export function formatBreadcrumbPath(
  rawPath: string | null | undefined,
  options: FormatBreadcrumbPathOptions = {},
): string {
  const trimmed = (rawPath || '').trim();
  if (!trimmed) {
    return '';
  }

  const normalized = trimmed.replace(/\/+/g, '/').replace(/^\/|\/$/g, '');
  if (!normalized) {
    return '';
  }

  const segments = normalized.split('/').filter(Boolean);
  if (segments.length === 0) {
    return '';
  }

  const nodeName = (options.nodeName || '').trim();
  if (nodeName && segments[segments.length - 1] === nodeName) {
    segments.pop();
  }

  const maxSegments = Number.isFinite(options.maxSegments as number)
    ? Math.max(1, (options.maxSegments as number) || 4)
    : 4;

  if (segments.length > maxSegments) {
    return ['...', ...segments.slice(-maxSegments)].join(' / ');
  }

  return segments.join(' / ');
}
