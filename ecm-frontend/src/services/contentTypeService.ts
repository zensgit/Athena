import api from './api';

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
  defaultValue?: string;
  options?: string[];
  regex?: string;
}

export interface ContentTypeDefinition {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  parentType?: string;
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

class ContentTypeService {
  async listTypes(): Promise<ContentTypeDefinition[]> {
    return api.get<ContentTypeDefinition[]>('/types');
  }

  async getType(name: string): Promise<ContentTypeDefinition> {
    return api.get<ContentTypeDefinition>(`/types/${name}`);
  }

  async createType(data: ContentTypeCreateRequest): Promise<ContentTypeDefinition> {
    return api.post<ContentTypeDefinition>('/types', data);
  }

  async updateType(name: string, data: ContentTypeUpdateRequest): Promise<ContentTypeDefinition> {
    return api.put<ContentTypeDefinition>(`/types/${name}`, data);
  }

  async deleteType(name: string): Promise<void> {
    return api.delete(`/types/${name}`);
  }

  async applyType(nodeId: string, typeName: string, properties: Record<string, any>): Promise<void> {
    return api.post(`/types/nodes/${nodeId}/apply`, properties, { params: { type: typeName } });
  }
}

const contentTypeService = new ContentTypeService();
export default contentTypeService;
