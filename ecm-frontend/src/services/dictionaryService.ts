import api from './api';
import {
  AspectDefinition,
  ConstraintDefinition,
  ConstraintType,
  PropertyDataType,
  PropertyDefinition,
  TypeDefinition,
} from './contentModelService';

export const DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE =
  'Data dictionary endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const PROPERTY_DATA_TYPES: PropertyDataType[] = [
  'TEXT',
  'MLTEXT',
  'INT',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DATE',
  'DATETIME',
  'BOOLEAN',
  'URI',
  'NODEREF',
  'QNAME',
  'CATEGORY',
  'LOCALE',
  'CONTENT',
];

const CONSTRAINT_TYPES: ConstraintType[] = ['REGEX', 'LIST', 'RANGE', 'LENGTH'];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((item) => typeof item === 'string')
);

const isPropertyDataType = (value: unknown): value is PropertyDataType => (
  typeof value === 'string' && (PROPERTY_DATA_TYPES as string[]).includes(value)
);

const isConstraintType = (value: unknown): value is ConstraintType => (
  typeof value === 'string' && (CONSTRAINT_TYPES as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(DICTIONARY_UNEXPECTED_RESPONSE_MESSAGE);
};

const isConstraintDefinition = (value: unknown): value is ConstraintDefinition => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && isConstraintType(value.constraintType)
    && isObject(value.parameters);
};

const isPropertyDefinition = (value: unknown): value is PropertyDefinition => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string'
    || typeof value.name !== 'string'
    || typeof value.qualifiedName !== 'string') {
    return false;
  }
  if (!isPropertyDataType(value.dataType)) {
    return false;
  }
  if (typeof value.mandatory !== 'boolean'
    || typeof value.multiValued !== 'boolean'
    || typeof value.indexed !== 'boolean'
    || typeof value.protectedField !== 'boolean') {
    return false;
  }
  if (value.encrypted !== undefined && typeof value.encrypted !== 'boolean') {
    return false;
  }
  if (!isStringOrNullish(value.title)
    || !isStringOrNullish(value.description)
    || !isStringOrNullish(value.defaultValue)) {
    return false;
  }
  if (!Array.isArray(value.constraints) || !value.constraints.every(isConstraintDefinition)) {
    return false;
  }
  return true;
};

const isTypeDefinition = (value: unknown): value is TypeDefinition => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string'
    || typeof value.name !== 'string'
    || typeof value.qualifiedName !== 'string') {
    return false;
  }
  if (!isStringOrNullish(value.title)
    || !isStringOrNullish(value.description)
    || !isStringOrNullish(value.parentName)) {
    return false;
  }
  if (!isStringArray(value.mandatoryAspects)) {
    return false;
  }
  if (!Array.isArray(value.properties) || !value.properties.every(isPropertyDefinition)) {
    return false;
  }
  return true;
};

const isAspectDefinition = (value: unknown): value is AspectDefinition => {
  if (!isObject(value)) {
    return false;
  }
  if (typeof value.id !== 'string'
    || typeof value.name !== 'string'
    || typeof value.qualifiedName !== 'string') {
    return false;
  }
  if (!isStringOrNullish(value.title)
    || !isStringOrNullish(value.description)
    || !isStringOrNullish(value.parentName)) {
    return false;
  }
  if (!Array.isArray(value.properties) || !value.properties.every(isPropertyDefinition)) {
    return false;
  }
  return true;
};

const assertTypeDefinition = (value: unknown): TypeDefinition => (
  isTypeDefinition(value) ? value : assertUnexpectedResponse()
);

const assertTypeDefinitionList = (value: unknown): TypeDefinition[] => {
  if (!Array.isArray(value) || !value.every(isTypeDefinition)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const assertAspectDefinition = (value: unknown): AspectDefinition => (
  isAspectDefinition(value) ? value : assertUnexpectedResponse()
);

const assertAspectDefinitionList = (value: unknown): AspectDefinition[] => {
  if (!Array.isArray(value) || !value.every(isAspectDefinition)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const assertPropertyDefinitionList = (value: unknown): PropertyDefinition[] => {
  if (!Array.isArray(value) || !value.every(isPropertyDefinition)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const assertStringList = (value: unknown): string[] => (
  isStringArray(value) ? value : assertUnexpectedResponse()
);

class DictionaryService {
  async listTypes(): Promise<TypeDefinition[]> {
    const result = await api.get<unknown>('/dictionary/types');
    return assertTypeDefinitionList(result);
  }

  async getType(qualifiedName: string): Promise<TypeDefinition> {
    const result = await api.get<unknown>(
      `/dictionary/types/${encodeURIComponent(qualifiedName)}`
    );
    return assertTypeDefinition(result);
  }

  async getTypeProperties(qualifiedName: string): Promise<PropertyDefinition[]> {
    const result = await api.get<unknown>(
      `/dictionary/types/${encodeURIComponent(qualifiedName)}/properties`
    );
    return assertPropertyDefinitionList(result);
  }

  async getTypeHierarchy(qualifiedName: string): Promise<string[]> {
    const result = await api.get<unknown>(
      `/dictionary/types/${encodeURIComponent(qualifiedName)}/hierarchy`
    );
    return assertStringList(result);
  }

  async getMandatoryAspects(qualifiedName: string): Promise<string[]> {
    const result = await api.get<unknown>(
      `/dictionary/types/${encodeURIComponent(qualifiedName)}/mandatory-aspects`
    );
    return assertStringList(result);
  }

  async listAspects(): Promise<AspectDefinition[]> {
    const result = await api.get<unknown>('/dictionary/aspects');
    return assertAspectDefinitionList(result);
  }

  async getAspect(qualifiedName: string): Promise<AspectDefinition> {
    const result = await api.get<unknown>(
      `/dictionary/aspects/${encodeURIComponent(qualifiedName)}`
    );
    return assertAspectDefinition(result);
  }

  async getAspectProperties(qualifiedName: string): Promise<PropertyDefinition[]> {
    const result = await api.get<unknown>(
      `/dictionary/aspects/${encodeURIComponent(qualifiedName)}/properties`
    );
    return assertPropertyDefinitionList(result);
  }
}

const dictionaryService = new DictionaryService();
export default dictionaryService;
