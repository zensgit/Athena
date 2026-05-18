import api from './api';

export const CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE =
  'Content model endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type ModelStatus = 'DRAFT' | 'ACTIVE' | 'DISABLED';
export type PropertyDataType =
  | 'TEXT'
  | 'MLTEXT'
  | 'INT'
  | 'LONG'
  | 'FLOAT'
  | 'DOUBLE'
  | 'DATE'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'URI'
  | 'NODEREF'
  | 'QNAME'
  | 'CATEGORY'
  | 'LOCALE'
  | 'CONTENT';

export type ConstraintType = 'REGEX' | 'LIST' | 'RANGE' | 'LENGTH';

export interface ConstraintDefinition {
  id: string;
  constraintType: ConstraintType;
  parameters: Record<string, unknown>;
}

export interface PropertyDefinition {
  id: string;
  name: string;
  title?: string | null;
  description?: string | null;
  dataType: PropertyDataType;
  mandatory: boolean;
  multiValued: boolean;
  defaultValue?: string | null;
  indexed: boolean;
  protectedField: boolean;
  encrypted?: boolean;
  qualifiedName: string;
  constraints: ConstraintDefinition[];
}

export interface PropertyDefinitionRequest {
  name: string;
  title?: string;
  dataType: PropertyDataType;
  mandatory?: boolean;
  multiValued?: boolean;
  defaultValue?: string;
  encrypted?: boolean;
}

export interface TypeDefinition {
  id: string;
  name: string;
  title?: string | null;
  description?: string | null;
  parentName?: string | null;
  qualifiedName: string;
  mandatoryAspects: string[];
  properties: PropertyDefinition[];
}

export interface AspectDefinition {
  id: string;
  name: string;
  title?: string | null;
  description?: string | null;
  parentName?: string | null;
  qualifiedName: string;
  properties: PropertyDefinition[];
}

export interface ContentModelDefinition {
  id: string;
  namespaceUri: string;
  prefix: string;
  name: string;
  description?: string | null;
  author?: string | null;
  status: ModelStatus;
  versionLabel?: string | null;
  types: TypeDefinition[];
  aspects: AspectDefinition[];
}

export interface CreateContentModelRequest {
  namespaceUri: string;
  prefix: string;
  name: string;
  description?: string;
  author?: string;
  versionLabel?: string;
}

const MODEL_STATUSES: ModelStatus[] = ['DRAFT', 'ACTIVE', 'DISABLED'];
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

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isNullableString = (value: unknown): value is string | null =>
  value === null || typeof value === 'string';

const isOptionalNullableString = (value: unknown): value is string | null | undefined =>
  value === undefined || isNullableString(value);

const isStringArray = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every((item) => typeof item === 'string');

const isPlainParameters = (value: unknown): value is Record<string, unknown> =>
  isRecord(value);

const isModelStatus = (value: unknown): value is ModelStatus =>
  typeof value === 'string' && MODEL_STATUSES.includes(value as ModelStatus);

const isPropertyDataType = (value: unknown): value is PropertyDataType =>
  typeof value === 'string' && PROPERTY_DATA_TYPES.includes(value as PropertyDataType);

const isConstraintType = (value: unknown): value is ConstraintType =>
  typeof value === 'string' && CONSTRAINT_TYPES.includes(value as ConstraintType);

function assertContentModelResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(CONTENT_MODEL_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertConstraintDefinition = (value: unknown): ConstraintDefinition => {
  assertContentModelResponse(isRecord(value));
  assertContentModelResponse(typeof value.id === 'string');
  assertContentModelResponse(isConstraintType(value.constraintType));
  assertContentModelResponse(isPlainParameters(value.parameters));

  return value as ConstraintDefinition;
};

const assertConstraintDefinitions = (value: unknown): ConstraintDefinition[] => {
  assertContentModelResponse(Array.isArray(value));
  return value.map(assertConstraintDefinition);
};

const assertPropertyDefinition = (value: unknown): PropertyDefinition => {
  assertContentModelResponse(isRecord(value));
  assertContentModelResponse(typeof value.id === 'string');
  assertContentModelResponse(typeof value.name === 'string');
  assertContentModelResponse(isOptionalNullableString(value.title));
  assertContentModelResponse(isOptionalNullableString(value.description));
  assertContentModelResponse(isPropertyDataType(value.dataType));
  assertContentModelResponse(typeof value.mandatory === 'boolean');
  assertContentModelResponse(typeof value.multiValued === 'boolean');
  assertContentModelResponse(isOptionalNullableString(value.defaultValue));
  assertContentModelResponse(typeof value.indexed === 'boolean');
  assertContentModelResponse(typeof value.protectedField === 'boolean');
  assertContentModelResponse(typeof value.encrypted === 'boolean');
  assertContentModelResponse(typeof value.qualifiedName === 'string');
  const constraints = assertConstraintDefinitions(value.constraints);

  return {
    ...value,
    constraints,
  } as PropertyDefinition;
};

const assertPropertyDefinitions = (value: unknown): PropertyDefinition[] => {
  assertContentModelResponse(Array.isArray(value));
  return value.map(assertPropertyDefinition);
};

const assertTypeDefinition = (value: unknown): TypeDefinition => {
  assertContentModelResponse(isRecord(value));
  assertContentModelResponse(typeof value.id === 'string');
  assertContentModelResponse(typeof value.name === 'string');
  assertContentModelResponse(isOptionalNullableString(value.title));
  assertContentModelResponse(isOptionalNullableString(value.description));
  assertContentModelResponse(isOptionalNullableString(value.parentName));
  assertContentModelResponse(typeof value.qualifiedName === 'string');
  assertContentModelResponse(isStringArray(value.mandatoryAspects));
  const properties = assertPropertyDefinitions(value.properties);

  return {
    ...value,
    properties,
  } as TypeDefinition;
};

const assertTypeDefinitions = (value: unknown): TypeDefinition[] => {
  assertContentModelResponse(Array.isArray(value));
  return value.map(assertTypeDefinition);
};

const assertAspectDefinition = (value: unknown): AspectDefinition => {
  assertContentModelResponse(isRecord(value));
  assertContentModelResponse(typeof value.id === 'string');
  assertContentModelResponse(typeof value.name === 'string');
  assertContentModelResponse(isOptionalNullableString(value.title));
  assertContentModelResponse(isOptionalNullableString(value.description));
  assertContentModelResponse(isOptionalNullableString(value.parentName));
  assertContentModelResponse(typeof value.qualifiedName === 'string');
  const properties = assertPropertyDefinitions(value.properties);

  return {
    ...value,
    properties,
  } as AspectDefinition;
};

const assertAspectDefinitions = (value: unknown): AspectDefinition[] => {
  assertContentModelResponse(Array.isArray(value));
  return value.map(assertAspectDefinition);
};

const assertContentModelDefinition = (value: unknown): ContentModelDefinition => {
  assertContentModelResponse(isRecord(value));
  assertContentModelResponse(typeof value.id === 'string');
  assertContentModelResponse(typeof value.namespaceUri === 'string');
  assertContentModelResponse(typeof value.prefix === 'string');
  assertContentModelResponse(typeof value.name === 'string');
  assertContentModelResponse(isOptionalNullableString(value.description));
  assertContentModelResponse(isOptionalNullableString(value.author));
  assertContentModelResponse(isModelStatus(value.status));
  assertContentModelResponse(isOptionalNullableString(value.versionLabel));
  const types = assertTypeDefinitions(value.types);
  const aspects = assertAspectDefinitions(value.aspects);

  return {
    ...value,
    types,
    aspects,
  } as ContentModelDefinition;
};

const assertContentModelDefinitions = (value: unknown): ContentModelDefinition[] => {
  assertContentModelResponse(Array.isArray(value));
  return value.map(assertContentModelDefinition);
};

class ContentModelService {
  async listModels(): Promise<ContentModelDefinition[]> {
    const response = await api.get<unknown>('/content-models');
    return assertContentModelDefinitions(response);
  }

  async getModel(modelId: string): Promise<ContentModelDefinition> {
    const response = await api.get<unknown>(`/content-models/${modelId}`);
    return assertContentModelDefinition(response);
  }

  async createModel(payload: CreateContentModelRequest): Promise<ContentModelDefinition> {
    const response = await api.post<unknown>('/content-models', payload);
    return assertContentModelDefinition(response);
  }

  async updateModel(modelId: string, name: string, description: string): Promise<ContentModelDefinition> {
    const response = await api.put<unknown>(`/content-models/${modelId}`, null, {
      params: { name, description },
    });
    return assertContentModelDefinition(response);
  }

  async activateModel(modelId: string): Promise<ContentModelDefinition> {
    const response = await api.post<unknown>(`/content-models/${modelId}/activate`);
    return assertContentModelDefinition(response);
  }

  async deactivateModel(modelId: string): Promise<ContentModelDefinition> {
    const response = await api.post<unknown>(`/content-models/${modelId}/deactivate`);
    return assertContentModelDefinition(response);
  }

  async deleteModel(modelId: string): Promise<void> {
    return api.delete(`/content-models/${modelId}`);
  }

  // ---- types ---------------------------------------------------------------

  async addType(modelId: string, type: { name: string; title?: string; description?: string; parentName?: string }): Promise<TypeDefinition> {
    const response = await api.post<unknown>(`/content-models/${modelId}/types`, type);
    return assertTypeDefinition(response);
  }

  async updateType(typeId: string, params: { title?: string; description?: string; parentName?: string }): Promise<TypeDefinition> {
    const response = await api.put<unknown>(`/content-models/types/${typeId}`, null, { params });
    return assertTypeDefinition(response);
  }

  async deleteType(typeId: string): Promise<void> {
    return api.delete(`/content-models/types/${typeId}`);
  }

  // ---- aspects -------------------------------------------------------------

  async addAspect(modelId: string, aspect: { name: string; title?: string; description?: string; parentName?: string }): Promise<AspectDefinition> {
    const response = await api.post<unknown>(`/content-models/${modelId}/aspects`, aspect);
    return assertAspectDefinition(response);
  }

  async updateAspect(aspectId: string, params: { title?: string; description?: string; parentName?: string }): Promise<AspectDefinition> {
    const response = await api.put<unknown>(`/content-models/aspects/${aspectId}`, null, { params });
    return assertAspectDefinition(response);
  }

  async deleteAspect(aspectId: string): Promise<void> {
    return api.delete(`/content-models/aspects/${aspectId}`);
  }

  // ---- properties ----------------------------------------------------------

  async addPropertyToType(typeId: string, property: PropertyDefinitionRequest): Promise<PropertyDefinition> {
    const response = await api.post<unknown>(`/content-models/types/${typeId}/properties`, property);
    return assertPropertyDefinition(response);
  }

  async addPropertyToAspect(aspectId: string, property: PropertyDefinitionRequest): Promise<PropertyDefinition> {
    const response = await api.post<unknown>(`/content-models/aspects/${aspectId}/properties`, property);
    return assertPropertyDefinition(response);
  }

  async deleteProperty(propertyId: string): Promise<void> {
    return api.delete(`/content-models/properties/${propertyId}`);
  }

  // ---- constraints ---------------------------------------------------------

  async addConstraint(propertyId: string, constraint: { constraintType: ConstraintType; parameters: Record<string, unknown> }): Promise<ConstraintDefinition> {
    const response = await api.post<unknown>(`/content-models/properties/${propertyId}/constraints`, constraint);
    return assertConstraintDefinition(response);
  }

  async deleteConstraint(constraintId: string): Promise<void> {
    return api.delete(`/content-models/constraints/${constraintId}`);
  }
}

const contentModelService = new ContentModelService();
export default contentModelService;
