export const DEFAULT_INLINE_SCRIPT = [
  "logger.info('Running script for', site.id);",
  '({',
  "  greeting: 'Hello ' + user.name,",
  '  site: site.title,',
  '  doubledCount: documentCount * 2',
  '});',
].join('\n');

export const formatScriptResult = (result: unknown): string => {
  if (result === null || result === undefined) {
    return 'null';
  }
  if (typeof result === 'string') {
    return result;
  }
  return JSON.stringify(result, null, 2);
};

export const parseScriptTagsInput = (input: string): string[] =>
  input
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);
