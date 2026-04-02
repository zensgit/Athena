import api from './api';

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
  title?: string;
  description?: string;
  dataType: PropertyDataType;
  mandatory: boolean;
  multiValued: boolean;
  defaultValue?: string | null;
  indexed: boolean;
  protectedField: boolean;
  qualifiedName: string;
  constraints: ConstraintDefinition[];
}

export interface TypeDefinition {
  id: string;
  name: string;
  title?: string;
  description?: string;
  parentName?: string | null;
  qualifiedName: string;
  mandatoryAspects: string[];
  properties: PropertyDefinition[];
}

export interface AspectDefinition {
  id: string;
  name: string;
  title?: string;
  description?: string;
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

class ContentModelService {
  async listModels(): Promise<ContentModelDefinition[]> {
    return api.get<ContentModelDefinition[]>('/content-models');
  }

  async getModel(modelId: string): Promise<ContentModelDefinition> {
    return api.get<ContentModelDefinition>(`/content-models/${modelId}`);
  }

  async createModel(payload: CreateContentModelRequest): Promise<ContentModelDefinition> {
    return api.post<ContentModelDefinition>('/content-models', payload);
  }

  async updateModel(modelId: string, name: string, description: string): Promise<ContentModelDefinition> {
    return api.put<ContentModelDefinition>(`/content-models/${modelId}`, null, {
      params: { name, description },
    });
  }

  async activateModel(modelId: string): Promise<ContentModelDefinition> {
    return api.post<ContentModelDefinition>(`/content-models/${modelId}/activate`);
  }

  async deactivateModel(modelId: string): Promise<ContentModelDefinition> {
    return api.post<ContentModelDefinition>(`/content-models/${modelId}/deactivate`);
  }

  async deleteModel(modelId: string): Promise<void> {
    return api.delete(`/content-models/${modelId}`);
  }

  // ---- types ---------------------------------------------------------------

  async addType(modelId: string, type: { name: string; title?: string; description?: string; parentName?: string }): Promise<TypeDefinition> {
    return api.post<TypeDefinition>(`/content-models/${modelId}/types`, type);
  }

  async updateType(typeId: string, params: { title?: string; description?: string; parentName?: string }): Promise<TypeDefinition> {
    return api.put<TypeDefinition>(`/content-models/types/${typeId}`, null, { params });
  }

  async deleteType(typeId: string): Promise<void> {
    return api.delete(`/content-models/types/${typeId}`);
  }

  // ---- aspects -------------------------------------------------------------

  async addAspect(modelId: string, aspect: { name: string; title?: string; description?: string; parentName?: string }): Promise<AspectDefinition> {
    return api.post<AspectDefinition>(`/content-models/${modelId}/aspects`, aspect);
  }

  async updateAspect(aspectId: string, params: { title?: string; description?: string; parentName?: string }): Promise<AspectDefinition> {
    return api.put<AspectDefinition>(`/content-models/aspects/${aspectId}`, null, { params });
  }

  async deleteAspect(aspectId: string): Promise<void> {
    return api.delete(`/content-models/aspects/${aspectId}`);
  }

  // ---- properties ----------------------------------------------------------

  async addPropertyToType(typeId: string, property: { name: string; title?: string; dataType: PropertyDataType; mandatory?: boolean; multiValued?: boolean; defaultValue?: string }): Promise<PropertyDefinition> {
    return api.post<PropertyDefinition>(`/content-models/types/${typeId}/properties`, property);
  }

  async addPropertyToAspect(aspectId: string, property: { name: string; title?: string; dataType: PropertyDataType; mandatory?: boolean; multiValued?: boolean; defaultValue?: string }): Promise<PropertyDefinition> {
    return api.post<PropertyDefinition>(`/content-models/aspects/${aspectId}/properties`, property);
  }

  async deleteProperty(propertyId: string): Promise<void> {
    return api.delete(`/content-models/properties/${propertyId}`);
  }

  // ---- constraints ---------------------------------------------------------

  async addConstraint(propertyId: string, constraint: { constraintType: ConstraintType; parameters: Record<string, unknown> }): Promise<ConstraintDefinition> {
    return api.post<ConstraintDefinition>(`/content-models/properties/${propertyId}/constraints`, constraint);
  }

  async deleteConstraint(constraintId: string): Promise<void> {
    return api.delete(`/content-models/constraints/${constraintId}`);
  }
}

const contentModelService = new ContentModelService();
export default contentModelService;
