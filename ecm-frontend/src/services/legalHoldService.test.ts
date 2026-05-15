import api from './api';
import legalHoldService, {
  LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
  LegalHoldDetail,
  LegalHoldItem,
  LegalHoldSummary,
} from './legalHoldService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const summary: LegalHoldSummary = {
  id: 'hold-1',
  name: 'Q1 Investigation',
  description: 'Hold for internal review',
  status: 'ACTIVE',
  itemCount: 2,
  createdBy: 'admin',
  createdDate: '2026-05-01T10:00:00Z',
  releasedBy: null,
  releasedAt: null,
};

const summaryWithNullableDetails: LegalHoldSummary = {
  id: 'hold-2',
  name: 'Empty Hold',
  description: null,
  status: 'RELEASED',
  itemCount: 0,
  createdBy: null,
  createdDate: null,
  releasedBy: null,
  releasedAt: null,
};

const item: LegalHoldItem = {
  nodeId: 'node-1',
  nodeName: 'evidence.pdf',
  nodeType: 'DOCUMENT',
  nodePath: '/tenant/a/cases/2026',
  addedAt: '2026-05-02T11:00:00Z',
  addedBy: 'admin',
};

const itemWithNullableDetails: LegalHoldItem = {
  nodeId: 'node-2',
  nodeName: null,
  nodeType: null,
  nodePath: null,
  addedAt: null,
  addedBy: null,
};

const detail: LegalHoldDetail = {
  ...summary,
  releaseComment: null,
  items: [item, itemWithNullableDetails],
};

const detailWithNullableTopLevel: LegalHoldDetail = {
  ...summaryWithNullableDetails,
  releaseComment: null,
  items: [],
};

describe('legalHoldService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded summaries for listHolds and forwards the list path', async () => {
    mockedApi.get.mockResolvedValueOnce([summary, summaryWithNullableDetails]);

    await expect(legalHoldService.listHolds()).resolves.toEqual([
      summary,
      summaryWithNullableDetails,
    ]);
    expect(mockedApi.get).toHaveBeenCalledWith('/legal-holds');
  });

  it('rejects HTML fallback for listHolds', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(legalHoldService.listHolds()).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a listHolds response that is not an array', async () => {
    mockedApi.get.mockResolvedValueOnce({ content: [summary] });

    await expect(legalHoldService.listHolds()).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a listHolds item with an unsupported status', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...summary, status: 'PENDING' }]);

    await expect(legalHoldService.listHolds()).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a listHolds item with non-finite itemCount', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...summary, itemCount: '2' }]);

    await expect(legalHoldService.listHolds()).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a listHolds item with a non-string-or-null audit field', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...summary, createdBy: 42 }]);

    await expect(legalHoldService.listHolds()).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded detail with nullable fields and items for getHold', async () => {
    mockedApi.get.mockResolvedValueOnce(detailWithNullableTopLevel);

    await expect(legalHoldService.getHold('hold-2')).resolves.toEqual(
      detailWithNullableTopLevel,
    );
    expect(mockedApi.get).toHaveBeenCalledWith('/legal-holds/hold-2');
  });

  it('rejects HTML fallback for getHold', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(legalHoldService.getHold('hold-1')).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a getHold response missing the items array', async () => {
    const { items: _items, ...detailWithoutItems } = detail;
    mockedApi.get.mockResolvedValueOnce(detailWithoutItems);

    await expect(legalHoldService.getHold('hold-1')).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a getHold response with a malformed item', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...detail,
      items: [{ ...item, nodeId: null }],
    });

    await expect(legalHoldService.getHold('hold-1')).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a getHold response with a non-string-or-null releaseComment', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...detail, releaseComment: 42 });

    await expect(legalHoldService.getHold('hold-1')).rejects.toThrow(
      LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded detail and forwards payload/path for createHold', async () => {
    const payload = { name: 'Q1 Investigation', description: 'Hold for internal review' };
    mockedApi.post.mockResolvedValueOnce(detail);

    await expect(legalHoldService.createHold(payload)).resolves.toEqual(detail);
    expect(mockedApi.post).toHaveBeenCalledWith('/legal-holds', payload);
  });

  it('rejects a malformed createHold readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...detail, id: null });

    await expect(
      legalHoldService.createHold({ name: 'X' }),
    ).rejects.toThrow(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded detail and forwards payload/path for addItems', async () => {
    const payload = { nodeIds: ['node-1', 'node-2'] };
    mockedApi.post.mockResolvedValueOnce(detail);

    await expect(legalHoldService.addItems('hold-1', payload)).resolves.toEqual(detail);
    expect(mockedApi.post).toHaveBeenCalledWith('/legal-holds/hold-1/items', payload);
  });

  it('rejects a malformed addItems readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...detail, status: 'PENDING' });

    await expect(
      legalHoldService.addItems('hold-1', { nodeIds: ['node-1'] }),
    ).rejects.toThrow(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded detail and forwards path for removeItem', async () => {
    mockedApi.delete.mockResolvedValueOnce(detail);

    await expect(legalHoldService.removeItem('hold-1', 'node-1')).resolves.toEqual(detail);
    expect(mockedApi.delete).toHaveBeenCalledWith('/legal-holds/hold-1/items/node-1');
  });

  it('rejects a malformed removeItem readback', async () => {
    mockedApi.delete.mockResolvedValueOnce({ ...detail, items: [{ nodeId: 42 }] });

    await expect(
      legalHoldService.removeItem('hold-1', 'node-1'),
    ).rejects.toThrow(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded detail and forwards payload/path for releaseHold', async () => {
    const payload = { comment: 'Investigation closed' };
    mockedApi.post.mockResolvedValueOnce({
      ...detail,
      status: 'RELEASED' as const,
      releasedBy: 'admin',
      releasedAt: '2026-05-10T15:00:00Z',
      releaseComment: 'Investigation closed',
    });

    const result = await legalHoldService.releaseHold('hold-1', payload);

    expect(result.status).toBe('RELEASED');
    expect(result.releaseComment).toBe('Investigation closed');
    expect(mockedApi.post).toHaveBeenCalledWith('/legal-holds/hold-1/release', payload);
  });

  it('rejects a malformed releaseHold readback', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      legalHoldService.releaseHold('hold-1', {}),
    ).rejects.toThrow(LEGAL_HOLD_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
