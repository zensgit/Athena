import { shouldSuppressStaleFallbackForQuery } from './searchFallbackUtils';

describe('searchFallbackUtils', () => {
  test('suppresses stale fallback for high-precision binary filename queries', () => {
    expect(shouldSuppressStaleFallbackForQuery('e2e-preview-failure-1770563224443.bin')).toBe(true);
    expect(shouldSuppressStaleFallbackForQuery('model-20260212-133045.dwg')).toBe(true);
  });

  test('does not suppress stale fallback for common document file extensions', () => {
    expect(shouldSuppressStaleFallbackForQuery('e2e-fallback-governance-1770563224443.txt')).toBe(false);
    expect(shouldSuppressStaleFallbackForQuery('J0924032-02.pdf')).toBe(false);
  });

  test('does not suppress stale fallback for plain natural-language query', () => {
    expect(shouldSuppressStaleFallbackForQuery('project plan')).toBe(false);
    expect(shouldSuppressStaleFallbackForQuery('invoice')).toBe(false);
  });

  test('suppresses stale fallback for highly structured id-like tokens', () => {
    expect(shouldSuppressStaleFallbackForQuery('aabf9284-1770563224443-zzzzzzz')).toBe(true);
  });
});
