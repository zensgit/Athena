import api from './api';
import scriptService, {
  ScriptDefinitionDto,
  ScriptExecutionResult,
  SCRIPT_UNEXPECTED_RESPONSE_MESSAGE,
} from './scriptService';

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

const script: ScriptDefinitionDto = {
  id: 'script-1',
  name: 'Review Reminder',
  scriptPath: 'scripts/review-reminder.js',
  description: null,
  engine: 'GRAALJS',
  content: 'return true;',
  tags: ['review', 'automation'],
  active: true,
  createdBy: 'alice',
  createdDate: '2026-05-15T00:00:00Z',
  lastModifiedDate: null,
};

const mutation = {
  name: script.name,
  scriptPath: script.scriptPath,
  content: script.content,
  tags: script.tags,
  active: script.active,
};

const executionResult: ScriptExecutionResult = {
  result: { ok: true },
  logs: ['started', 'done'],
  scriptPath: script.scriptPath,
  storedScript: true,
  durationMs: 42,
  executedAt: '2026-05-15T00:00:01Z',
};

describe('scriptService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded script lists and keeps endpoint path', async () => {
    mockedApi.get.mockResolvedValueOnce([script]);

    await expect(scriptService.listScripts()).resolves.toEqual([script]);

    expect(mockedApi.get).toHaveBeenCalledWith('/scripts');
  });

  it('rejects HTML fallback for script lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(scriptService.listScripts()).rejects.toThrow(
      SCRIPT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed script list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...script, tags: ['review', 42] }]);

    await expect(scriptService.listScripts()).rejects.toThrow(
      SCRIPT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded script details', async () => {
    mockedApi.get.mockResolvedValueOnce(script);

    await expect(scriptService.getScript('script-1')).resolves.toEqual(script);

    expect(mockedApi.get).toHaveBeenCalledWith('/scripts/script-1');
  });

  it('rejects malformed script details', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...script, active: 'true' });

    await expect(scriptService.getScript('script-1')).rejects.toThrow(
      SCRIPT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded create-script readbacks and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(script);

    await expect(scriptService.createScript(mutation)).resolves.toEqual(script);

    expect(mockedApi.post).toHaveBeenCalledWith('/scripts', mutation);
  });

  it('returns guarded update-script readbacks and forwards payload', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...script, description: 'updated' });

    await expect(scriptService.updateScript('script-1', mutation)).resolves.toEqual({
      ...script,
      description: 'updated',
    });

    expect(mockedApi.put).toHaveBeenCalledWith('/scripts/script-1', mutation);
  });

  it('does not require a response body for deletes', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(scriptService.deleteScript('script-1')).resolves.toBeUndefined();

    expect(mockedApi.delete).toHaveBeenCalledWith('/scripts/script-1');
  });

  it('returns guarded execution results and forwards payload', async () => {
    const payload = {
      scriptPath: script.scriptPath,
      model: { nodeId: 'node-1' },
      timeoutMs: 1000,
    };
    mockedApi.post.mockResolvedValueOnce(executionResult);

    await expect(scriptService.executeScript(payload)).resolves.toEqual(executionResult);

    expect(mockedApi.post).toHaveBeenCalledWith('/scripts/execute', payload);
  });

  it('accepts inline execution results with null result and null scriptPath', async () => {
    const inlineResult: ScriptExecutionResult = {
      ...executionResult,
      result: null,
      scriptPath: null,
      storedScript: false,
    };
    mockedApi.post.mockResolvedValueOnce(inlineResult);

    await expect(
      scriptService.executeScript({ scriptContent: 'return null;' }),
    ).resolves.toEqual(inlineResult);
  });

  it('rejects execution results missing the result field', async () => {
    const missingResult: Partial<ScriptExecutionResult> = { ...executionResult };
    delete missingResult.result;
    mockedApi.post.mockResolvedValueOnce(missingResult);

    await expect(scriptService.executeScript({ scriptPath: script.scriptPath })).rejects.toThrow(
      SCRIPT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed execution logs', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...executionResult,
      logs: ['ok', { message: 'bad' }],
    });

    await expect(scriptService.executeScript({ scriptPath: script.scriptPath })).rejects.toThrow(
      SCRIPT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });
});
