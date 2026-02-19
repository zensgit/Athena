import { buildSearchErrorRecovery, classifySearchError, resolveSearchErrorMessage } from './searchErrorUtils';

describe('searchErrorUtils', () => {
  test('classifies auth errors from status', () => {
    const category = classifySearchError({ response: { status: 401 } });
    expect(category).toBe('authorization');
  });

  test('classifies query errors from text', () => {
    const category = classifySearchError(new Error('invalid query syntax near "*"'));
    expect(category).toBe('query');
  });

  test('classifies transient errors from network text', () => {
    const category = classifySearchError(new Error('Network Error'));
    expect(category).toBe('transient');
  });

  test('resolves backend message with fallback', () => {
    const message = resolveSearchErrorMessage({
      response: {
        data: {
          message: 'Search backend unavailable',
        },
      },
    });
    expect(message).toBe('Search backend unavailable');
  });

  test('builds non-retry recovery for query errors', () => {
    const recovery = buildSearchErrorRecovery(new Error('parse exception: malformed query'));
    expect(recovery.category).toBe('query');
    expect(recovery.canRetry).toBe(false);
    expect(recovery.hint).toContain('Update query or filters');
  });

  test('builds retryable recovery for server errors', () => {
    const recovery = buildSearchErrorRecovery({ response: { status: 503 } });
    expect(recovery.category).toBe('server');
    expect(recovery.canRetry).toBe(true);
  });
});
