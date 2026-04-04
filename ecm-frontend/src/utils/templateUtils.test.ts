import {
  DEFAULT_TEMPLATE_MODEL_INPUT,
  parseTemplateModelInput,
  parseTemplateTagsInput,
} from './templateUtils';

test('parseTemplateModelInput parses object input', () => {
  const model = parseTemplateModelInput('{"name":"Athena","count":3}');

  expect(model).toEqual({ name: 'Athena', count: 3 });
});

test('parseTemplateModelInput rejects non-object json', () => {
  expect(() => parseTemplateModelInput('["Athena"]')).toThrow('Template model must be a JSON object');
});

test('parseTemplateModelInput returns empty object for blank input', () => {
  expect(parseTemplateModelInput('   ')).toEqual({});
});

test('parseTemplateTagsInput trims and filters tags', () => {
  expect(parseTemplateTagsInput('mail, finance , ,alert')).toEqual(['mail', 'finance', 'alert']);
});

test('default template model input is object-like json', () => {
  expect(parseTemplateModelInput(DEFAULT_TEMPLATE_MODEL_INPUT)).toBeTruthy();
});
