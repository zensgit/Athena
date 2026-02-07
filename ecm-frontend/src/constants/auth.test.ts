import { resolvePositiveIntEnv } from './auth';

describe('resolvePositiveIntEnv', () => {
  test('returns fallback for undefined', () => {
    expect(resolvePositiveIntEnv(undefined, 123)).toBe(123);
  });

  test('returns fallback for non-numeric values', () => {
    expect(resolvePositiveIntEnv('abc', 123)).toBe(123);
  });

  test('returns fallback for zero and negatives', () => {
    expect(resolvePositiveIntEnv('0', 123)).toBe(123);
    expect(resolvePositiveIntEnv('-5', 123)).toBe(123);
  });

  test('accepts positive integers', () => {
    expect(resolvePositiveIntEnv('42', 123)).toBe(42);
  });

  test('floors positive floats', () => {
    expect(resolvePositiveIntEnv('12.9', 123)).toBe(12);
  });
});
