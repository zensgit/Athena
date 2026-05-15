import api from './api';
import opsPolicyService, {
  OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE,
  OpsPolicyDomainState,
  OpsPolicyHistoryResponse,
  OpsPolicyProfile,
  OpsPolicyRollbackResponse,
  OpsPolicyUpdateResponse,
} from './opsPolicyService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    put: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const policyProfile: OpsPolicyProfile = {
  key: 'temporary',
  label: 'Temporary failures',
  maxAttempts: 3,
  retryDelayMs: 5000,
  backoffMultiplier: 2,
  quietPeriodMs: 30000,
  builtIn: true,
};

const domainState: OpsPolicyDomainState = {
  domain: 'PREVIEW',
  currentVersion: 4,
  updatedAt: '2026-05-15T08:00:00Z',
  actor: 'admin',
  reason: 'tune retry behavior',
  policies: [policyProfile],
};

const updateResponse: OpsPolicyUpdateResponse = {
  domain: 'PREVIEW',
  currentVersion: 5,
  updatedAt: '2026-05-15T08:10:00Z',
  actor: 'admin',
  reason: 'raise attempts',
  updatedPolicy: policyProfile,
  policies: [policyProfile],
  error: null,
};

const rollbackResponse: OpsPolicyRollbackResponse = {
  domain: 'PREVIEW',
  previousVersion: 5,
  rolledBackToVersion: 4,
  currentVersion: 6,
  updatedAt: '2026-05-15T08:20:00Z',
  actor: 'admin',
  reason: 'rollback',
  policies: [policyProfile],
  error: null,
};

const historyResponse: OpsPolicyHistoryResponse = {
  domain: 'PREVIEW',
  currentVersion: 5,
  history: [
    {
      version: 5,
      updatedAt: '2026-05-15T08:10:00Z',
      actor: 'admin',
      reason: 'raise attempts',
    },
  ],
};

describe('opsPolicyService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('gets the default PREVIEW domain and returns guarded domain state', async () => {
    mockedApi.get.mockResolvedValueOnce(domainState);

    await expect(opsPolicyService.getDomain()).resolves.toEqual(domainState);

    expect(mockedApi.get).toHaveBeenCalledWith('/ops/policies', {
      params: { domain: 'PREVIEW' },
    });
  });

  it('rejects HTML fallback for domain state', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(opsPolicyService.getDomain()).rejects.toThrow(
      OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed policy profiles in domain state', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...domainState,
      policies: [{ ...policyProfile, maxAttempts: '3' }],
    });

    await expect(opsPolicyService.getDomain()).rejects.toThrow(
      OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('updates a policy with an encoded domain and returns a guarded response', async () => {
    mockedApi.put.mockResolvedValueOnce(updateResponse);
    const payload = {
      profileKey: 'temporary',
      maxAttempts: 4,
      reason: 'raise attempts',
    };

    await expect(opsPolicyService.updatePolicy('Preview Domain', payload)).resolves.toEqual(
      updateResponse
    );

    expect(mockedApi.put).toHaveBeenCalledWith('/ops/policies/Preview%20Domain', payload);
  });

  it('accepts a null updatedPolicy and error string in update responses', async () => {
    const response: OpsPolicyUpdateResponse = {
      ...updateResponse,
      updatedPolicy: null,
      policies: [],
      error: 'profileKey is required',
    };
    mockedApi.put.mockResolvedValueOnce(response);

    await expect(opsPolicyService.updatePolicy('PREVIEW', {
      profileKey: 'missing',
    })).resolves.toEqual(response);
  });

  it('accepts update responses with optional error omitted', async () => {
    const { error, ...responseWithoutError } = updateResponse;
    mockedApi.put.mockResolvedValueOnce(responseWithoutError);

    await expect(opsPolicyService.updatePolicy('PREVIEW', {
      profileKey: 'temporary',
    })).resolves.toEqual(responseWithoutError);
    expect(error).toBeNull();
  });

  it('rejects malformed updatedPolicy values', async () => {
    mockedApi.put.mockResolvedValueOnce({
      ...updateResponse,
      updatedPolicy: { ...policyProfile, builtIn: 'true' },
    });

    await expect(opsPolicyService.updatePolicy('PREVIEW', {
      profileKey: 'temporary',
    })).rejects.toThrow(OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rolls back with encoded domain and default empty payload', async () => {
    mockedApi.post.mockResolvedValueOnce(rollbackResponse);

    await expect(opsPolicyService.rollback('Preview Domain')).resolves.toEqual(rollbackResponse);

    expect(mockedApi.post).toHaveBeenCalledWith('/ops/policies/Preview%20Domain/rollback', {});
  });

  it('forwards explicit rollback payloads', async () => {
    mockedApi.post.mockResolvedValueOnce(rollbackResponse);
    const payload = { targetVersion: 3, reason: 'operator request' };

    await expect(opsPolicyService.rollback('PREVIEW', payload)).resolves.toEqual(rollbackResponse);

    expect(mockedApi.post).toHaveBeenCalledWith('/ops/policies/PREVIEW/rollback', payload);
  });

  it('rejects malformed rollback version fields', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...rollbackResponse,
      rolledBackToVersion: '4',
    });

    await expect(opsPolicyService.rollback('PREVIEW')).rejects.toThrow(
      OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('gets default history params and returns a guarded response', async () => {
    mockedApi.get.mockResolvedValueOnce(historyResponse);

    await expect(opsPolicyService.getHistory()).resolves.toEqual(historyResponse);

    expect(mockedApi.get).toHaveBeenCalledWith('/ops/policies/PREVIEW/history', {
      params: { limit: 20 },
    });
  });

  it('gets custom history params with an encoded domain', async () => {
    mockedApi.get.mockResolvedValueOnce(historyResponse);

    await expect(opsPolicyService.getHistory('Preview Domain', 5)).resolves.toEqual(
      historyResponse
    );

    expect(mockedApi.get).toHaveBeenCalledWith('/ops/policies/Preview%20Domain/history', {
      params: { limit: 5 },
    });
  });

  it('rejects malformed history entries', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...historyResponse,
      history: [{ ...historyResponse.history[0], version: Number.NaN }],
    });

    await expect(opsPolicyService.getHistory()).rejects.toThrow(
      OPS_POLICY_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
