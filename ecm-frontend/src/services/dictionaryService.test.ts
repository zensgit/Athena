import api from './api';
import dictionaryService, {
  DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
} from './dictionaryService';
import {
  AspectDefinition,
  ConstraintDefinition,
  PropertyDefinition,
  TypeDefinition,
} from './contentModelService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const constraint: ConstraintDefinition = {
  id: 'c1',
  constraintType: 'REGEX',
  parameters: { pattern: '^[A-Z]+$' },
};

const property: PropertyDefinition = {
  id: 'p1',
  name: 'title',
  title: 'Title',
  description: 'Document title',
  dataType: 'TEXT',
  mandatory: false,
  multiValued: false,
  defaultValue: null,
  indexed: true,
  protectedField: false,
  encrypted: false,
  qualifiedName: 'cm:title',
  constraints: [constraint],
};

const typeDefinition: TypeDefinition = {
  id: 't1',
  name: 'content',
  title: 'Content',
  description: 'Base content type',
  parentName: null,
  qualifiedName: 'cm:content',
  mandatoryAspects: ['cm:auditable'],
  properties: [property],
};

const aspectDefinition: AspectDefinition = {
  id: 'a1',
  name: 'titled',
  title: 'Titled',
  description: 'Adds a title',
  parentName: null,
  qualifiedName: 'cm:titled',
  properties: [property],
};

describe('dictionaryService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('listTypes', () => {
    it('returns guarded types and forwards the endpoint path', async () => {
      mockedApi.get.mockResolvedValueOnce([typeDefinition]);

      await expect(dictionaryService.listTypes()).resolves.toEqual([typeDefinition]);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/types');
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dictionaryService.listTypes()).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a type entry missing mandatoryAspects array', async () => {
      mockedApi.get.mockResolvedValueOnce([{ ...typeDefinition, mandatoryAspects: 'cm:auditable' }]);

      await expect(dictionaryService.listTypes()).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getType', () => {
    it('encodes the qualified name in the path and returns the type', async () => {
      mockedApi.get.mockResolvedValueOnce(typeDefinition);

      await expect(dictionaryService.getType('cm:content')).resolves.toEqual(typeDefinition);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/types/cm%3Acontent');
    });

    it('rejects a malformed type with non-string qualifiedName', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...typeDefinition, qualifiedName: 42 });

      await expect(dictionaryService.getType('cm:content')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getTypeProperties', () => {
    it('returns guarded properties for a type', async () => {
      mockedApi.get.mockResolvedValueOnce([property]);

      await expect(dictionaryService.getTypeProperties('cm:content')).resolves.toEqual([property]);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/types/cm%3Acontent/properties');
    });

    it('rejects a property whose dataType is not in the union', async () => {
      mockedApi.get.mockResolvedValueOnce([{ ...property, dataType: 'JSON' }]);

      await expect(dictionaryService.getTypeProperties('cm:content')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a property whose constraint parameters are not a plain object', async () => {
      const bad: unknown = {
        ...property,
        constraints: [{ ...constraint, parameters: ['pattern'] }],
      };
      mockedApi.get.mockResolvedValueOnce([bad]);

      await expect(dictionaryService.getTypeProperties('cm:content')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getTypeHierarchy', () => {
    it('returns guarded hierarchy strings', async () => {
      mockedApi.get.mockResolvedValueOnce(['cm:content', 'sys:base']);

      await expect(dictionaryService.getTypeHierarchy('cm:content')).resolves.toEqual([
        'cm:content',
        'sys:base',
      ]);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/types/cm%3Acontent/hierarchy');
    });

    it('rejects a hierarchy entry that is not a string', async () => {
      mockedApi.get.mockResolvedValueOnce(['cm:content', 7]);

      await expect(dictionaryService.getTypeHierarchy('cm:content')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects HTML fallback for hierarchy', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dictionaryService.getTypeHierarchy('cm:content')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getMandatoryAspects', () => {
    it('returns guarded mandatory aspect strings', async () => {
      mockedApi.get.mockResolvedValueOnce(['cm:auditable']);

      await expect(dictionaryService.getMandatoryAspects('cm:content')).resolves.toEqual([
        'cm:auditable',
      ]);
      expect(mockedApi.get).toHaveBeenCalledWith(
        '/dictionary/types/cm%3Acontent/mandatory-aspects',
      );
    });

    it('rejects a non-array response', async () => {
      mockedApi.get.mockResolvedValueOnce({ aspects: ['cm:auditable'] });

      await expect(dictionaryService.getMandatoryAspects('cm:content')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('listAspects', () => {
    it('returns guarded aspects and forwards the endpoint path', async () => {
      mockedApi.get.mockResolvedValueOnce([aspectDefinition]);

      await expect(dictionaryService.listAspects()).resolves.toEqual([aspectDefinition]);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/aspects');
    });

    it('rejects an aspect entry missing the properties array', async () => {
      const bad: unknown = { ...aspectDefinition, properties: null };
      mockedApi.get.mockResolvedValueOnce([bad]);

      await expect(dictionaryService.listAspects()).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(dictionaryService.listAspects()).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getAspect', () => {
    it('encodes the qualified name and returns the aspect', async () => {
      mockedApi.get.mockResolvedValueOnce(aspectDefinition);

      await expect(dictionaryService.getAspect('cm:titled')).resolves.toEqual(aspectDefinition);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/aspects/cm%3Atitled');
    });

    it('accepts nullable optional fields on the aspect', async () => {
      const nullable: AspectDefinition = {
        ...aspectDefinition,
        title: undefined,
        description: undefined,
        parentName: null,
        properties: [],
      };
      mockedApi.get.mockResolvedValueOnce(nullable);

      await expect(dictionaryService.getAspect('cm:titled')).resolves.toEqual(nullable);
    });

    it('rejects a malformed parentName type', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...aspectDefinition, parentName: 0 });

      await expect(dictionaryService.getAspect('cm:titled')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });

  describe('getAspectProperties', () => {
    it('returns guarded properties for an aspect', async () => {
      mockedApi.get.mockResolvedValueOnce([property]);

      await expect(dictionaryService.getAspectProperties('cm:titled')).resolves.toEqual([property]);
      expect(mockedApi.get).toHaveBeenCalledWith('/dictionary/aspects/cm%3Atitled/properties');
    });

    it('rejects a property with non-boolean mandatory flag', async () => {
      mockedApi.get.mockResolvedValueOnce([{ ...property, mandatory: 'true' }]);

      await expect(dictionaryService.getAspectProperties('cm:titled')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });

    it('rejects a property with an invalid constraintType', async () => {
      const bad: unknown = {
        ...property,
        constraints: [{ ...constraint, constraintType: 'PATTERN' }],
      };
      mockedApi.get.mockResolvedValueOnce([bad]);

      await expect(dictionaryService.getAspectProperties('cm:titled')).rejects.toThrow(
        DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE,
      );
    });
  });
});
