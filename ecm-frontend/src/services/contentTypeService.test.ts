import api from './api';
import contentTypeService, {
  CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE,
  ContentTypeCreateRequest,
  ContentTypeDefinition,
  ContentTypePropertyDefinition,
} from './contentTypeService';

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

const invoiceProperty: ContentTypePropertyDefinition = {
  name: 'amount',
  title: 'Amount',
  type: 'monetary',
  required: true,
  searchable: true,
  defaultValue: null,
  options: null,
  regex: null,
};

const statusProperty: ContentTypePropertyDefinition = {
  name: 'status',
  title: 'Status',
  type: 'select',
  required: false,
  searchable: true,
  defaultValue: 'draft',
  options: ['draft', 'approved'],
  regex: null,
};

const contentType: ContentTypeDefinition = {
  id: 'type-uuid-1',
  name: 'ecm:invoice',
  displayName: 'Invoice',
  description: null,
  parentType: 'ecm:document',
  properties: [invoiceProperty, statusProperty],
};

const minimalContentType: ContentTypeDefinition = {
  id: 'type-uuid-2',
  name: 'ecm:minimal',
  displayName: 'Minimal',
  description: null,
  parentType: null,
  properties: [],
};

const createRequest: ContentTypeCreateRequest = {
  name: 'ecm:invoice',
  displayName: 'Invoice',
  description: 'Invoice metadata',
  parentType: 'ecm:document',
  properties: [invoiceProperty, statusProperty],
};

describe('contentTypeService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded content type lists and accepts nullable backend fields', async () => {
    mockedApi.get.mockResolvedValueOnce([contentType, minimalContentType]);

    await expect(contentTypeService.listTypes()).resolves.toEqual([
      contentType,
      minimalContentType,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/types');
  });

  it('rejects HTML fallback for listTypes', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(contentTypeService.listTypes()).rejects.toThrow(
      CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed content type list entries', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...contentType, properties: [{ ...invoiceProperty, type: 'uuid' }] }]);

    await expect(contentTypeService.listTypes()).rejects.toThrow(
      CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded getType readbacks', async () => {
    mockedApi.get.mockResolvedValueOnce(contentType);

    await expect(contentTypeService.getType('ecm:invoice')).resolves.toEqual(contentType);

    expect(mockedApi.get).toHaveBeenCalledWith('/types/ecm:invoice');
  });

  it('rejects malformed getType readbacks', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...contentType, displayName: null });

    await expect(contentTypeService.getType('ecm:invoice')).rejects.toThrow(
      CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded createType readbacks and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(contentType);

    await expect(contentTypeService.createType(createRequest)).resolves.toEqual(contentType);

    expect(mockedApi.post).toHaveBeenCalledWith('/types', createRequest);
  });

  it('rejects malformed createType readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...contentType, properties: null });

    await expect(contentTypeService.createType(createRequest)).rejects.toThrow(
      CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded updateType readbacks and forwards payload', async () => {
    const updated = { ...contentType, displayName: 'Vendor Invoice' };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      contentTypeService.updateType('ecm:invoice', { displayName: 'Vendor Invoice' }),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith('/types/ecm:invoice', {
      displayName: 'Vendor Invoice',
    });
  });

  it('rejects malformed updateType readbacks', async () => {
    mockedApi.put.mockResolvedValueOnce({
      ...contentType,
      properties: [{ ...statusProperty, options: ['draft', 42] }],
    });

    await expect(
      contentTypeService.updateType('ecm:invoice', { displayName: 'Vendor Invoice' }),
    ).rejects.toThrow(CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps deleteType as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await contentTypeService.deleteType('ecm:invoice');

    expect(mockedApi.delete).toHaveBeenCalledWith('/types/ecm:invoice');
  });

  it('keeps applyType as an effect endpoint and forwards query params', async () => {
    mockedApi.post.mockResolvedValueOnce({ id: 'node-uuid-1' });

    await contentTypeService.applyType('node-uuid-1', 'ecm:invoice', {
      amount: '42.00',
    });

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/types/nodes/node-uuid-1/apply',
      { amount: '42.00' },
      { params: { type: 'ecm:invoice' } },
    );
  });
});
