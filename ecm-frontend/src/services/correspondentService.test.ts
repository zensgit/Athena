import api from './api';
import correspondentService, {
  CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
  Correspondent,
  CorrespondentPage,
  CreateCorrespondentRequest,
} from './correspondentService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const correspondent: Correspondent = {
  id: 'corr-1',
  name: 'China Telecom',
  matchAlgorithm: 'EXACT',
  matchPattern: 'china telecom',
  insensitive: true,
  email: 'billing@chinatelecom.example',
  phone: '+86-10-0000-0000',
  createdDate: '2026-05-01T10:00:00Z',
  createdBy: 'admin',
  lastModifiedDate: '2026-05-10T12:00:00Z',
  lastModifiedBy: 'admin',
};

const correspondentWithNullableDetails: Correspondent = {
  id: 'corr-2',
  name: 'Anonymous',
  matchAlgorithm: 'AUTO',
  insensitive: false,
  matchPattern: null,
  email: null,
  phone: null,
  createdDate: null,
  createdBy: null,
  lastModifiedDate: null,
  lastModifiedBy: null,
};

const correspondentPage: CorrespondentPage = {
  content: [correspondent, correspondentWithNullableDetails],
  totalElements: 2,
  totalPages: 1,
  number: 0,
  size: 200,
};

describe('correspondentService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded correspondent content for list and forwards page params', async () => {
    mockedApi.get.mockResolvedValueOnce(correspondentPage);

    await expect(correspondentService.list()).resolves.toEqual(correspondentPage.content);

    expect(mockedApi.get).toHaveBeenCalledWith('/correspondents', {
      params: { page: 0, size: 200, sort: 'name,asc' },
    });
  });

  it('forwards custom page/size params to list', async () => {
    mockedApi.get.mockResolvedValueOnce(correspondentPage);

    await correspondentService.list(2, 50);

    expect(mockedApi.get).toHaveBeenCalledWith('/correspondents', {
      params: { page: 2, size: 50, sort: 'name,asc' },
    });
  });

  it('accepts a page whose items use nullable optional fields', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...correspondentPage,
      content: [correspondentWithNullableDetails],
    });

    await expect(correspondentService.list()).resolves.toEqual([correspondentWithNullableDetails]);
  });

  it('rejects HTML fallback for list', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(correspondentService.list()).rejects.toThrow(
      CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a list response that is not a page envelope', async () => {
    mockedApi.get.mockResolvedValueOnce([correspondent]);

    await expect(correspondentService.list()).rejects.toThrow(
      CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a list page with a malformed content item', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...correspondentPage,
      content: [{ ...correspondent, name: 42 }],
    });

    await expect(correspondentService.list()).rejects.toThrow(
      CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a list page with an unsupported matchAlgorithm', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...correspondentPage,
      content: [{ ...correspondent, matchAlgorithm: 'UNKNOWN' }],
    });

    await expect(correspondentService.list()).rejects.toThrow(
      CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a list page where insensitive is not boolean', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...correspondentPage,
      content: [{ ...correspondent, insensitive: 'yes' }],
    });

    await expect(correspondentService.list()).rejects.toThrow(
      CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects a list page where an optional audit field is not a string-or-null', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...correspondentPage,
      content: [{ ...correspondent, createdBy: 42 }],
    });

    await expect(correspondentService.list()).rejects.toThrow(
      CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded create readback and forwards payload', async () => {
    const payload: CreateCorrespondentRequest = {
      name: 'China Telecom',
      matchAlgorithm: 'EXACT',
      matchPattern: 'china telecom',
      insensitive: true,
      email: 'billing@chinatelecom.example',
      phone: '+86-10-0000-0000',
    };
    mockedApi.post.mockResolvedValueOnce(correspondent);

    await expect(correspondentService.create(payload)).resolves.toEqual(correspondent);

    expect(mockedApi.post).toHaveBeenCalledWith('/correspondents', payload);
  });

  it('rejects malformed create readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...correspondent, id: null });

    await expect(
      correspondentService.create({
        name: 'X',
        matchAlgorithm: 'AUTO',
        insensitive: true,
      }),
    ).rejects.toThrow(CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded update readback and forwards payload', async () => {
    const updated: Correspondent = { ...correspondent, name: 'China Telecom (HK)' };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      correspondentService.update('corr-1', { name: 'China Telecom (HK)' }),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith('/correspondents/corr-1', {
      name: 'China Telecom (HK)',
    });
  });

  it('rejects malformed update readback', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...correspondent, matchAlgorithm: 'NOPE' });

    await expect(
      correspondentService.update('corr-1', { name: 'X' }),
    ).rejects.toThrow(CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
