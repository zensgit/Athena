import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

const annotation = {
  id: 'annotation-1',
  page: 1,
  x: 0.25,
  y: 0.75,
  text: 'Review clause',
  color: '#1976d2',
  createdBy: 'admin',
  createdAt: '2026-05-21T09:00:00Z',
};

const annotationState = {
  annotations: [annotation],
  updatedBy: 'admin',
  updatedAt: '2026-05-21T09:01:00Z',
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService PDF annotation response shape guards', () => {
  it('guards getPdfAnnotations and preserves endpoint shape', async () => {
    mockedApi.get.mockResolvedValueOnce(annotationState);

    await expect(nodeService.getPdfAnnotations('doc-1')).resolves.toEqual(annotationState);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/documents/doc-1/annotations');
  });

  it('guards savePdfAnnotations and preserves body shape', async () => {
    mockedApi.post.mockResolvedValueOnce(annotationState);

    await expect(nodeService.savePdfAnnotations('doc-1', [annotation])).resolves.toEqual(annotationState);
    expect(mockedApi.post).toHaveBeenLastCalledWith('/documents/doc-1/annotations', {
      annotations: [annotation],
    });
  });

  it('normalizes nullable annotation and state fields to frontend optional/null contracts', async () => {
    mockedApi.get.mockResolvedValueOnce({
      annotations: [
        {
          page: 2,
          x: 0,
          y: 1,
          text: null,
          color: null,
          createdBy: null,
          createdAt: null,
        },
      ],
      updatedBy: undefined,
      updatedAt: undefined,
    });

    await expect(nodeService.getPdfAnnotations('doc-1')).resolves.toEqual({
      annotations: [
        {
          id: undefined,
          page: 2,
          x: 0,
          y: 1,
          text: '',
          color: undefined,
          createdBy: undefined,
          createdAt: undefined,
        },
      ],
      updatedBy: null,
      updatedAt: null,
    });
  });

  it('throws the shared node sentinel for malformed annotation state responses', async () => {
    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getPdfAnnotations('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({ updatedBy: 'admin' });
    await expect(nodeService.getPdfAnnotations('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce({
      ...annotationState,
      annotations: [{ ...annotation, x: '0.25' }],
    });
    await expect(nodeService.getPdfAnnotations('doc-1')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.post.mockResolvedValueOnce(null);
    await expect(nodeService.savePdfAnnotations('doc-1', [annotation])).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
