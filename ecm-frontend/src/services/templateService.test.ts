import api from './api';
import templateService, {
  TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
  TemplateDefinitionDto,
  TemplateExecutionResult,
  TemplateMutationRequest,
} from './templateService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const namePlaceholder = ['$', '{name}'].join('');
const firstNamePlaceholder = ['$', '{firstName}'].join('');

const template: TemplateDefinitionDto = {
  id: 'template-1',
  name: 'Welcome email',
  templatePath: 'email/welcome.ftl',
  description: 'Welcome template',
  engine: 'FREEMARKER',
  content: 'Hello ' + namePlaceholder,
  tags: ['email', 'welcome'],
  active: true,
  createdBy: 'admin',
  createdDate: '2026-05-14T10:00:00',
  lastModifiedDate: '2026-05-14T10:05:00',
};

const templateWithNullableDetails: TemplateDefinitionDto = {
  ...template,
  id: 'template-2',
  name: 'Inline',
  templatePath: 'inline.ftl',
  description: null,
  tags: [],
  lastModifiedDate: null,
};

const mutation: TemplateMutationRequest = {
  name: 'Welcome email',
  templatePath: 'email/welcome.ftl',
  description: 'Welcome template',
  content: 'Hello ' + namePlaceholder,
  tags: ['email', 'welcome'],
  active: true,
};

const executionResult: TemplateExecutionResult = {
  rendered: 'Hello Alice',
  templatePath: 'email/welcome.ftl',
  storedTemplate: true,
  outputLength: 11,
  executedAt: '2026-05-14T10:10:00',
};

describe('templateService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded template lists and accepts nullable backend fields', async () => {
    mockedApi.get.mockResolvedValueOnce([template, templateWithNullableDetails]);

    await expect(templateService.listTemplates()).resolves.toEqual([
      template,
      templateWithNullableDetails,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/templates');
  });

  it('rejects HTML fallback for template lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(templateService.listTemplates()).rejects.toThrow(
      TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed template list entries', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...template, tags: ['email', 42] }]);

    await expect(templateService.listTemplates()).rejects.toThrow(
      TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded getTemplate readbacks', async () => {
    mockedApi.get.mockResolvedValueOnce(template);

    await expect(templateService.getTemplate('template-1')).resolves.toEqual(template);

    expect(mockedApi.get).toHaveBeenCalledWith('/templates/template-1');
  });

  it('rejects malformed getTemplate readbacks', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...template, active: 'true' });

    await expect(templateService.getTemplate('template-1')).rejects.toThrow(
      TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded createTemplate readbacks and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(template);

    await expect(templateService.createTemplate(mutation)).resolves.toEqual(template);

    expect(mockedApi.post).toHaveBeenCalledWith('/templates', mutation);
  });

  it('rejects malformed createTemplate readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...template, createdBy: null });

    await expect(templateService.createTemplate(mutation)).rejects.toThrow(
      TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded updateTemplate readbacks and forwards payload', async () => {
    const updated = { ...template, content: 'Hello ' + firstNamePlaceholder };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(templateService.updateTemplate('template-1', mutation)).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith('/templates/template-1', mutation);
  });

  it('rejects malformed updateTemplate readbacks', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...template, content: null });

    await expect(templateService.updateTemplate('template-1', mutation)).rejects.toThrow(
      TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded stored-template execution results', async () => {
    mockedApi.post.mockResolvedValueOnce(executionResult);

    await expect(
      templateService.executeTemplate({
        templatePath: 'email/welcome.ftl',
        model: { name: 'Alice' },
      }),
    ).resolves.toEqual(executionResult);

    expect(mockedApi.post).toHaveBeenCalledWith('/templates/execute', {
      templatePath: 'email/welcome.ftl',
      model: { name: 'Alice' },
    });
  });

  it('returns guarded inline execution results with null templatePath', async () => {
    const inlineResult = {
      ...executionResult,
      templatePath: null,
      storedTemplate: false,
    };
    mockedApi.post.mockResolvedValueOnce(inlineResult);

    await expect(
      templateService.executeTemplate({
        templateContent: 'Hello ' + namePlaceholder,
        model: { name: 'Alice' },
      }),
    ).resolves.toEqual(inlineResult);
  });

  it('rejects malformed execution results', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...executionResult, outputLength: '11' });

    await expect(
      templateService.executeTemplate({
        templatePath: 'email/welcome.ftl',
      }),
    ).rejects.toThrow(TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps deleteTemplate as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await templateService.deleteTemplate('template-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/templates/template-1');
  });
});
