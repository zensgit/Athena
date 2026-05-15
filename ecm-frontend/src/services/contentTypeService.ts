import api from './api';

export const CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE =
  'Content type endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type ContentTypePropertyType =
  | 'text'
  | 'long_text'
  | 'number'
  | 'integer'
  | 'float'
  | 'monetary'
  | 'date'
  | 'boolean'
  | 'list'
  | 'select'
  | 'url'
  | 'documentlink';

export interface ContentTypePropertyDefinition {
  name: string;
  title: string;
  type: ContentTypePropertyType;
  required: boolean;
  searchable: boolean;
  defaultValue?: string | null;
  options?: string[] | null;
  regex?: string | null;
}

export interface ContentTypeDefinition {
  id: string;
  name: string;
  displayName: string;
  description?: string | null;
  parentType?: string | null;
  properties: ContentTypePropertyDefinition[];
}

export interface ContentTypeCreateRequest {
  name: string;
  displayName: string;
  description?: string;
  parentType?: string;
  properties: ContentTypePropertyDefinition[];
}

export interface ContentTypeUpdateRequest {
  displayName?: string;
  description?: string;
  parentType?: string;
  properties?: ContentTypePropertyDefinition[];
}

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const CONTENT_TYPE_PROPERTY_TYPES: ContentTypePropertyType[] = [
  'text',
  'long_text',
  'number',
  'integer',
  'float',
  'monetary',
  'date',
  'boolean',
  'list',
  'select',
  'url',
  'documentlink',
];

const isPropertyType = (value: unknown): value is ContentTypePropertyType => (
  typeof value === 'string' && (CONTENT_TYPE_PROPERTY_TYPES as string[]).includes(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isStringArrayOrNullish = (value: unknown): value is string[] | null | undefined => (
  value === null || value === undefined || (
    Array.isArray(value) && value.every((item) => typeof item === 'string')
  )
);

const assertUnexpectedResponse = (): never => {
  throw new Error(CONTENT_TYPE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isPropertyDefinition = (value: unknown): value is ContentTypePropertyDefinition => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.name === 'string'
    && typeof value.title === 'string'
    && isPropertyType(value.type)
    && typeof value.required === 'boolean'
    && typeof value.searchable === 'boolean'
    && isStringOrNullish(value.defaultValue)
    && isStringArrayOrNullish(value.options)
    && isStringOrNullish(value.regex);
};

const isContentTypeDefinition = (value: unknown): value is ContentTypeDefinition => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && typeof value.displayName === 'string'
    && isStringOrNullish(value.description)
    && isStringOrNullish(value.parentType)
    && Array.isArray(value.properties)
    && value.properties.every(isPropertyDefinition);
};

const assertContentTypeDefinition = (value: unknown): ContentTypeDefinition => (
  isContentTypeDefinition(value) ? value : assertUnexpectedResponse()
);

const assertContentTypeDefinitionArray = (value: unknown): ContentTypeDefinition[] => {
  if (!Array.isArray(value) || !value.every(isContentTypeDefinition)) {
    return assertUnexpectedResponse();
  }
  return value;
};

class ContentTypeService {
  async listTypes(): Promise<ContentTypeDefinition[]> {
    const result = await api.get<unknown>('/types');
    return assertContentTypeDefinitionArray(result);
  }

  async getType(name: string): Promise<ContentTypeDefinition> {
    const result = await api.get<unknown>(`/types/${name}`);
    return assertContentTypeDefinition(result);
  }

  async createType(data: ContentTypeCreateRequest): Promise<ContentTypeDefinition> {
    const result = await api.post<unknown>('/types', data);
    return assertContentTypeDefinition(result);
  }

  async updateType(name: string, data: ContentTypeUpdateRequest): Promise<ContentTypeDefinition> {
    const result = await api.put<unknown>(`/types/${name}`, data);
    return assertContentTypeDefinition(result);
  }

  async deleteType(name: string): Promise<void> {
    return api.delete(`/types/${name}`);
  }

  async applyType(nodeId: string, typeName: string, properties: Record<string, any>): Promise<void> {
    await api.post(`/types/nodes/${nodeId}/apply`, properties, { params: { type: typeName } });
  }
}

const contentTypeService = new ContentTypeService();
export default contentTypeService;
