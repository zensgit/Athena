import { DEFAULT_INLINE_SCRIPT, formatScriptResult, parseScriptTagsInput } from './scriptUtils';

test('formatScriptResult preserves strings', () => {
  expect(formatScriptResult('ok')).toBe('ok');
});

test('formatScriptResult pretty prints objects', () => {
  expect(formatScriptResult({ ok: true, count: 2 })).toBe('{\n  "ok": true,\n  "count": 2\n}');
});

test('formatScriptResult prints null for missing result', () => {
  expect(formatScriptResult(undefined)).toBe('null');
});

test('parseScriptTagsInput trims and filters tags', () => {
  expect(parseScriptTagsInput('ops, governance , ,script')).toEqual(['ops', 'governance', 'script']);
});

test('default inline script includes logger and returned object', () => {
  expect(DEFAULT_INLINE_SCRIPT.includes('logger.info')).toBe(true);
  expect(DEFAULT_INLINE_SCRIPT.includes('doubledCount')).toBe(true);
});
