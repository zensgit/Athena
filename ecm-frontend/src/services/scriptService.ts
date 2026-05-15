import api from './api';

export const SCRIPT_UNEXPECTED_RESPONSE_MESSAGE =
  'Script endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isNullableString = (value: unknown): value is string | null | undefined =>
  value === null || value === undefined || typeof value === 'string';

function assertScriptResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(SCRIPT_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((item) => typeof item === 'string');

const assertScriptDefinition = (value: unknown): ScriptDefinitionDto => {
  assertScriptResponse(isRecord(value));
  assertScriptResponse(typeof value.id === 'string');
  assertScriptResponse(typeof value.name === 'string');
  assertScriptResponse(typeof value.scriptPath === 'string');
  assertScriptResponse(isNullableString(value.description));
  assertScriptResponse(typeof value.engine === 'string');
  assertScriptResponse(typeof value.content === 'string');
  assertScriptResponse(isStringArray(value.tags));
  assertScriptResponse(typeof value.active === 'boolean');
  assertScriptResponse(typeof value.createdBy === 'string');
  assertScriptResponse(typeof value.createdDate === 'string');
  assertScriptResponse(isNullableString(value.lastModifiedDate));

  return value as unknown as ScriptDefinitionDto;
};

const assertScriptDefinitions = (value: unknown): ScriptDefinitionDto[] => {
  assertScriptResponse(Array.isArray(value));
  return value.map(assertScriptDefinition);
};

const assertScriptExecutionResult = (value: unknown): ScriptExecutionResult => {
  assertScriptResponse(isRecord(value));
  assertScriptResponse(Object.prototype.hasOwnProperty.call(value, 'result'));
  assertScriptResponse(isStringArray(value.logs));
  assertScriptResponse(isNullableString(value.scriptPath));
  assertScriptResponse(typeof value.storedScript === 'boolean');
  assertScriptResponse(typeof value.durationMs === 'number');
  assertScriptResponse(typeof value.executedAt === 'string');

  return value as unknown as ScriptExecutionResult;
};

class ScriptService {
  async listScripts(): Promise<ScriptDefinitionDto[]> {
    const result = await api.get<unknown>('/scripts');
    return assertScriptDefinitions(result);
  }

  async getScript(scriptId: string): Promise<ScriptDefinitionDto> {
    const result = await api.get<unknown>(`/scripts/${scriptId}`);
    return assertScriptDefinition(result);
  }

  async createScript(payload: ScriptMutationRequest): Promise<ScriptDefinitionDto> {
    const result = await api.post<unknown>('/scripts', payload);
    return assertScriptDefinition(result);
  }

  async updateScript(scriptId: string, payload: ScriptMutationRequest): Promise<ScriptDefinitionDto> {
    const result = await api.put<unknown>(`/scripts/${scriptId}`, payload);
    return assertScriptDefinition(result);
  }

  async deleteScript(scriptId: string): Promise<void> {
    return api.delete(`/scripts/${scriptId}`);
  }

  async executeScript(payload: ScriptExecutionRequest): Promise<ScriptExecutionResult> {
    const result = await api.post<unknown>('/scripts/execute', payload);
    return assertScriptExecutionResult(result);
  }
}

const scriptService = new ScriptService();
export default scriptService;
