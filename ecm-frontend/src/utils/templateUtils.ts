export const DEFAULT_TEMPLATE_MODEL_INPUT = JSON.stringify(
  {
    user: {
      name: 'Athena Admin',
      email: 'admin@example.com',
    },
    site: {
      id: 'finance',
      title: 'Finance',
    },
    documentCount: 12,
  },
  null,
  2
);

export const DEFAULT_INLINE_TEMPLATE = [
  ['Hello ', '${', 'user.name', '}!'].join(''),
  ['Site: ', '${', 'site.title', '}'].join(''),
  ['Documents: ', '${', 'documentCount', '}'].join(''),
].join('\n');

export const parseTemplateModelInput = (input: string): Record<string, unknown> => {
  if (!input.trim()) {
    return {};
  }
  const parsed = JSON.parse(input);
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('Template model must be a JSON object');
  }
  return parsed as Record<string, unknown>;
};

export const parseTemplateTagsInput = (input: string): string[] =>
  input
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean);
