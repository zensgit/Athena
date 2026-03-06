import { resolveDiagnosticsExportRunId, sanitizeRunIdForFilename } from './mailAutomationExportUtils';

describe('mailAutomationExportUtils', () => {
  describe('sanitizeRunIdForFilename', () => {
    it('returns empty string for empty run id', () => {
      expect(sanitizeRunIdForFilename('')).toBe('');
      expect(sanitizeRunIdForFilename('   ')).toBe('');
      expect(sanitizeRunIdForFilename(undefined)).toBe('');
    });

    it('sanitizes unsafe characters for filename usage', () => {
      expect(sanitizeRunIdForFilename(' run/id:abc?123 ')).toBe('run-id-abc-123');
      expect(sanitizeRunIdForFilename('a___b')).toBe('a___b');
    });
  });

  describe('resolveDiagnosticsExportRunId', () => {
    it('returns empty when both run ids are missing', () => {
      expect(resolveDiagnosticsExportRunId({})).toBe('');
    });

    it('returns available run id when only one exists', () => {
      expect(resolveDiagnosticsExportRunId({ debugRunId: 'debug-1' })).toBe('debug-1');
      expect(resolveDiagnosticsExportRunId({ fetchRunId: 'fetch-1' })).toBe('fetch-1');
    });

    it('prefers latest timestamp when both run ids are present', () => {
      expect(
        resolveDiagnosticsExportRunId({
          debugRunId: 'debug-2',
          fetchRunId: 'fetch-2',
          debugAt: '2026-03-06T10:00:00Z',
          fetchAt: '2026-03-06T09:59:00Z',
        })
      ).toBe('debug-2');

      expect(
        resolveDiagnosticsExportRunId({
          debugRunId: 'debug-3',
          fetchRunId: 'fetch-3',
          debugAt: '2026-03-06T08:00:00Z',
          fetchAt: '2026-03-06T08:01:00Z',
        })
      ).toBe('fetch-3');
    });

    it('falls back to debug run id when timestamps are not both valid', () => {
      expect(
        resolveDiagnosticsExportRunId({
          debugRunId: 'debug-4',
          fetchRunId: 'fetch-4',
          debugAt: 'invalid',
          fetchAt: '2026-03-06T08:01:00Z',
        })
      ).toBe('debug-4');
    });
  });
});

