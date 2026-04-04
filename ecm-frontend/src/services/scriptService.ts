import api from './api';

export interface ScriptDefinitionDto {
  id: string;
  name: string;
  scriptPath: string;
  description?: string | null;
  engine: string;
  content: string;
  tags: string[];
  active: boolean;
  createdBy: string;
  createdDate: string;
  lastModifiedDate?: string | null;
}

export interface ScriptMutationRequest {
  name: string;
  scriptPath: string;
  description?: string;
  content: string;
  tags?: string[];
  active?: boolean;
}

export interface ScriptExecutionRequest {
  scriptPath?: string;
  scriptContent?: string;
  model?: Record<string, unknown>;
  timeoutMs?: number;
}

export interface ScriptExecutionResult {
  result: unknown;
  logs: string[];
  scriptPath?: string | null;
  storedScript: boolean;
  durationMs: number;
  executedAt: string;
}

class ScriptService {
  async listScripts(): Promise<ScriptDefinitionDto[]> {
    return api.get<ScriptDefinitionDto[]>('/scripts');
  }

  async getScript(scriptId: string): Promise<ScriptDefinitionDto> {
    return api.get<ScriptDefinitionDto>(`/scripts/${scriptId}`);
  }

  async createScript(payload: ScriptMutationRequest): Promise<ScriptDefinitionDto> {
    return api.post<ScriptDefinitionDto>('/scripts', payload);
  }

  async updateScript(scriptId: string, payload: ScriptMutationRequest): Promise<ScriptDefinitionDto> {
    return api.put<ScriptDefinitionDto>(`/scripts/${scriptId}`, payload);
  }

  async deleteScript(scriptId: string): Promise<void> {
    return api.delete(`/scripts/${scriptId}`);
  }

  async executeScript(payload: ScriptExecutionRequest): Promise<ScriptExecutionResult> {
    return api.post<ScriptExecutionResult>('/scripts/execute', payload);
  }
}

const scriptService = new ScriptService();
export default scriptService;
