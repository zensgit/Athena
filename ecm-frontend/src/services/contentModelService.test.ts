import api from './api';
import contentModelService, {
  AspectDefinition,
  ConstraintDefinition,
  CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE,
  ContentModelDefinition,
  PropertyDefinition,
  TypeDefinition,
} from './contentModelService';

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

const constraint: ConstraintDefinition = {
  id: 'constraint-1',
  constraintType: 'LIST',
  parameters: {
    allowedValues: ['alpha', 'beta'],
  },
};

const property: PropertyDefinition = {
  id: 'prop-1',
  name: 'secretCode',
  title: 'Secret Code',
  description: null,
  dataType: 'TEXT',
  mandatory: false,
  multiValued: false,
  defaultValue: null,
  indexed: true,
  protectedField: false,
  encrypted: true,
  qualifiedName: 'acme:secretCode',
  constraints: [constraint],
};

const typeDefinition: TypeDefinition = {
  id: 'type-1',
  name: 'caseFile',
  title: 'Case File',
  description: null,
  parentName: 'cm:content',
  qualifiedName: 'acme:caseFile',
  mandatoryAspects: ['cm:auditable'],
  properties: [property],
};

const aspectDefinition: AspectDefinition = {
  id: 'aspect-1',
  name: 'retention',
  title: 'Retention',
  description: null,
  parentName: null,
  qualifiedName: 'acme:retention',
  properties: [property],
};

const model: ContentModelDefinition = {
  id: 'model-1',
  namespaceUri: 'https://example.test/model/acme',
  prefix: 'acme',
  name: 'Acme Model',
  description: null,
  author: 'admin',
  status: 'DRAFT',
  versionLabel: '1.0',
  types: [typeDefinition],
  aspects: [aspectDefinition],
};

describe('contentModelService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('lists models and guards nested DTOs', async () => {
    mockedApi.get.mockResolvedValueOnce([model]);

    await expect(contentModelService.listModels()).resolves.toEqual([model]);

    expect(mockedApi.get).toHaveBeenCalledWith('/content-models');
  });

  it('gets a model by id', async () => {
    mockedApi.get.mockResolvedValueOnce(model);

    await expect(contentModelService.getModel('model-1')).resolves.toEqual(model);

    expect(mockedApi.get).toHaveBeenCalledWith('/content-models/model-1');
  });

  it('creates, updates, activates, and deactivates models without changing paths or params', async () => {
    mockedApi.post
      .mockResolvedValueOnce(model)
      .mockResolvedValueOnce({ ...model, status: 'ACTIVE' })
      .mockResolvedValueOnce({ ...model, status: 'DISABLED' });
    mockedApi.put.mockResolvedValueOnce({ ...model, name: 'Renamed' });

    await expect(
      contentModelService.createModel({
        namespaceUri: 'https://example.test/model/acme',
        prefix: 'acme',
        name: 'Acme Model',
      })
    ).resolves.toEqual(model);
    await expect(contentModelService.updateModel('model-1', 'Renamed', 'Updated')).resolves.toMatchObject({
      name: 'Renamed',
    });
    await expect(contentModelService.activateModel('model-1')).resolves.toMatchObject({
      status: 'ACTIVE',
    });
    await expect(contentModelService.deactivateModel('model-1')).resolves.toMatchObject({
      status: 'DISABLED',
    });

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/content-models', {
      namespaceUri: 'https://example.test/model/acme',
      prefix: 'acme',
      name: 'Acme Model',
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/content-models/model-1', null, {
      params: { name: 'Renamed', description: 'Updated' },
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/content-models/model-1/activate');
    expect(mockedApi.post).toHaveBeenNthCalledWith(3, '/content-models/model-1/deactivate');
  });

  it('adds and updates type definitions', async () => {
    mockedApi.post.mockResolvedValueOnce(typeDefinition);
    mockedApi.put.mockResolvedValueOnce({ ...typeDefinition, title: 'Updated Type' });

    await expect(
      contentModelService.addType('model-1', {
        name: 'caseFile',
        title: 'Case File',
      })
    ).resolves.toEqual(typeDefinition);
    await expect(
      contentModelService.updateType('type-1', {
        title: 'Updated Type',
        parentName: 'cm:content',
      })
    ).resolves.toMatchObject({ title: 'Updated Type' });

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/model-1/types', {
      name: 'caseFile',
      title: 'Case File',
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/content-models/types/type-1', null, {
      params: { title: 'Updated Type', parentName: 'cm:content' },
    });
  });

  it('adds and updates aspect definitions', async () => {
    mockedApi.post.mockResolvedValueOnce(aspectDefinition);
    mockedApi.put.mockResolvedValueOnce({ ...aspectDefinition, title: 'Updated Aspect' });

    await expect(
      contentModelService.addAspect('model-1', {
        name: 'retention',
      })
    ).resolves.toEqual(aspectDefinition);
    await expect(
      contentModelService.updateAspect('aspect-1', {
        title: 'Updated Aspect',
      })
    ).resolves.toMatchObject({ title: 'Updated Aspect' });

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/model-1/aspects', {
      name: 'retention',
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/content-models/aspects/aspect-1', null, {
      params: { title: 'Updated Aspect' },
    });
  });

  it('sends the encrypted flag when adding a property to a type and guards the response', async () => {
    mockedApi.post.mockResolvedValueOnce(property);

    await expect(
      contentModelService.addPropertyToType('type-1', {
        name: 'secretCode',
        title: 'Secret Code',
        dataType: 'TEXT',
        encrypted: true,
      })
    ).resolves.toEqual(property);

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/types/type-1/properties', {
      name: 'secretCode',
      title: 'Secret Code',
      dataType: 'TEXT',
      encrypted: true,
    });
  });

  it('sends the encrypted flag when adding a property to an aspect and guards the response', async () => {
    mockedApi.post.mockResolvedValueOnce(property);

    await expect(
      contentModelService.addPropertyToAspect('aspect-1', {
        name: 'retentionCode',
        title: 'Retention Code',
        dataType: 'TEXT',
        encrypted: true,
      })
    ).resolves.toEqual(property);

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/aspects/aspect-1/properties', {
      name: 'retentionCode',
      title: 'Retention Code',
      dataType: 'TEXT',
      encrypted: true,
    });
  });

  it('adds constraints and keeps parameters as a guarded object', async () => {
    mockedApi.post.mockResolvedValueOnce(constraint);

    await expect(
      contentModelService.addConstraint('prop-1', {
        constraintType: 'LIST',
        parameters: { allowedValues: ['alpha', 'beta'] },
      })
    ).resolves.toEqual(constraint);

    expect(mockedApi.post).toHaveBeenCalledWith('/content-models/properties/prop-1/constraints', {
      constraintType: 'LIST',
      parameters: { allowedValues: ['alpha', 'beta'] },
    });
  });

  it('keeps delete endpoints as no-content calls', async () => {
    mockedApi.delete.mockResolvedValue(undefined);

    await expect(contentModelService.deleteModel('model-1')).resolves.toBeUndefined();
    await expect(contentModelService.deleteType('type-1')).resolves.toBeUndefined();
    await expect(contentModelService.deleteAspect('aspect-1')).resolves.toBeUndefined();
    await expect(contentModelService.deleteProperty('prop-1')).resolves.toBeUndefined();
    await expect(contentModelService.deleteConstraint('constraint-1')).resolves.toBeUndefined();

    expect(mockedApi.delete).toHaveBeenNthCalledWith(1, '/content-models/model-1');
    expect(mockedApi.delete).toHaveBeenNthCalledWith(2, '/content-models/types/type-1');
    expect(mockedApi.delete).toHaveBeenNthCalledWith(3, '/content-models/aspects/aspect-1');
    expect(mockedApi.delete).toHaveBeenNthCalledWith(4, '/content-models/properties/prop-1');
    expect(mockedApi.delete).toHaveBeenNthCalledWith(5, '/content-models/constraints/constraint-1');
  });

  it('rejects HTML fallback list responses', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(contentModelService.listModels()).rejects.toThrow(
      CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects invalid model status enums', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...model, status: 'PUBLISHED' }]);

    await expect(contentModelService.listModels()).rejects.toThrow(
      CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed nested mandatory aspect lists', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        ...model,
        types: [{ ...typeDefinition, mandatoryAspects: ['cm:auditable', 42] }],
      },
    ]);

    await expect(contentModelService.listModels()).rejects.toThrow(
      CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed nested properties', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...property, encrypted: 'yes' });

    await expect(
      contentModelService.addPropertyToType('type-1', {
        name: 'secretCode',
        dataType: 'TEXT',
      })
    ).rejects.toThrow(CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects invalid property data types', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...property, dataType: 'PASSWORD' });

    await expect(
      contentModelService.addPropertyToAspect('aspect-1', {
        name: 'secretCode',
        dataType: 'TEXT',
      })
    ).rejects.toThrow(CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed constraint parameters', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...constraint, parameters: ['alpha'] });

    await expect(
      contentModelService.addConstraint('prop-1', {
        constraintType: 'LIST',
        parameters: { allowedValues: ['alpha'] },
      })
    ).rejects.toThrow(CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
