import contentModelService from './contentModelService';
import api from './api';

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

describe('contentModelService encrypted property authoring', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('sends the encrypted flag when adding a property to a type', async () => {
    mockedApi.post.mockResolvedValue({
      id: 'prop-1',
      name: 'secretCode',
      dataType: 'TEXT',
      mandatory: false,
      multiValued: false,
      indexed: false,
      protectedField: false,
      encrypted: true,
      qualifiedName: 'acme:secretCode',
      constraints: [],
    });

    await contentModelService.addPropertyToType('type-1', {
      name: 'secretCode',
      title: 'Secret Code',
      dataType: 'TEXT',
      encrypted: true,
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/types/type-1/properties', {
      name: 'secretCode',
      title: 'Secret Code',
      dataType: 'TEXT',
      encrypted: true,
    });
  });

  it('sends the encrypted flag when adding a property to an aspect', async () => {
    mockedApi.post.mockResolvedValue({
      id: 'prop-2',
      name: 'retentionCode',
      dataType: 'TEXT',
      mandatory: false,
      multiValued: false,
      indexed: false,
      protectedField: false,
      encrypted: true,
      qualifiedName: 'acme:retentionCode',
      constraints: [],
    });

    await contentModelService.addPropertyToAspect('aspect-1', {
      name: 'retentionCode',
      title: 'Retention Code',
      dataType: 'TEXT',
      encrypted: true,
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/aspects/aspect-1/properties', {
      name: 'retentionCode',
      title: 'Retention Code',
      dataType: 'TEXT',
      encrypted: true,
    });
  });
});
