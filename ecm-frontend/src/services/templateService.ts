import api from './api';

export const TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE =
  'Template endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export interface TemplateDefinitionDto {
  id: string;
  name: string;
  templatePath: string;
  description?: string | null;
  engine: string;
  content: string;
  tags: string[];
  active: boolean;
  createdBy: string;
  createdDate: string;
  lastModifiedDate?: string | null;
}

export interface TemplateMutationRequest {
  name: string;
  templatePath: string;
  description?: string;
  content: string;
  tags?: string[];
  active?: boolean;
}

export interface TemplateExecutionRequest {
  templatePath?: string;
  templateContent?: string;
  model?: Record<string, unknown>;
}

export interface TemplateExecutionResult {
  rendered: string;
  templatePath?: string | null;
  storedTemplate: boolean;
  outputLength: number;
  executedAt: string;
}

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((item) => typeof item === 'string')
);

const isNullableString = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isTemplateDefinitionDto = (value: unknown): value is TemplateDefinitionDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && typeof value.templatePath === 'string'
    && isNullableString(value.description)
    && typeof value.engine === 'string'
    && typeof value.content === 'string'
    && isStringArray(value.tags)
    && typeof value.active === 'boolean'
    && typeof value.createdBy === 'string'
    && typeof value.createdDate === 'string'
    && isNullableString(value.lastModifiedDate);
};

const assertTemplateDefinitionDto = (value: unknown): TemplateDefinitionDto => (
  isTemplateDefinitionDto(value) ? value : assertUnexpectedResponse()
);

const assertTemplateDefinitionArray = (value: unknown): TemplateDefinitionDto[] => {
  if (!Array.isArray(value) || !value.every(isTemplateDefinitionDto)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const isTemplateExecutionResult = (value: unknown): value is TemplateExecutionResult => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.rendered === 'string'
    && isNullableString(value.templatePath)
    && typeof value.storedTemplate === 'boolean'
    && isFiniteNumber(value.outputLength)
    && typeof value.executedAt === 'string';
};

const assertTemplateExecutionResult = (value: unknown): TemplateExecutionResult => (
  isTemplateExecutionResult(value) ? value : assertUnexpectedResponse()
);

class TemplateService {
  async listTemplates(): Promise<TemplateDefinitionDto[]> {
    const result = await api.get<unknown>('/templates');
    return assertTemplateDefinitionArray(result);
  }

  async getTemplate(templateId: string): Promise<TemplateDefinitionDto> {
    const result = await api.get<unknown>(`/templates/${templateId}`);
    return assertTemplateDefinitionDto(result);
  }

  async createTemplate(payload: TemplateMutationRequest): Promise<TemplateDefinitionDto> {
    const result = await api.post<unknown>('/templates', payload);
    return assertTemplateDefinitionDto(result);
  }

  async updateTemplate(templateId: string, payload: TemplateMutationRequest): Promise<TemplateDefinitionDto> {
    const result = await api.put<unknown>(`/templates/${templateId}`, payload);
    return assertTemplateDefinitionDto(result);
  }

  async deleteTemplate(templateId: string): Promise<void> {
    return api.delete(`/templates/${templateId}`);
  }

  async executeTemplate(payload: TemplateExecutionRequest): Promise<TemplateExecutionResult> {
    const result = await api.post<unknown>('/templates/execute', payload);
    return assertTemplateExecutionResult(result);
  }
}

const templateService = new TemplateService();
export default templateService;
