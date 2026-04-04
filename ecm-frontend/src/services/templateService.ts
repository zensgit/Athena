import api from './api';

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

class TemplateService {
  async listTemplates(): Promise<TemplateDefinitionDto[]> {
    return api.get<TemplateDefinitionDto[]>('/templates');
  }

  async getTemplate(templateId: string): Promise<TemplateDefinitionDto> {
    return api.get<TemplateDefinitionDto>(`/templates/${templateId}`);
  }

  async createTemplate(payload: TemplateMutationRequest): Promise<TemplateDefinitionDto> {
    return api.post<TemplateDefinitionDto>('/templates', payload);
  }

  async updateTemplate(templateId: string, payload: TemplateMutationRequest): Promise<TemplateDefinitionDto> {
    return api.put<TemplateDefinitionDto>(`/templates/${templateId}`, payload);
  }

  async deleteTemplate(templateId: string): Promise<void> {
    return api.delete(`/templates/${templateId}`);
  }

  async executeTemplate(payload: TemplateExecutionRequest): Promise<TemplateExecutionResult> {
    return api.post<TemplateExecutionResult>('/templates/execute', payload);
  }
}

const templateService = new TemplateService();
export default templateService;
