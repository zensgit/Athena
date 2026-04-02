import api from './api';
import {
  AspectDefinition,
  PropertyDefinition,
  TypeDefinition,
} from './contentModelService';

class DictionaryService {
  async listTypes(): Promise<TypeDefinition[]> {
    return api.get<TypeDefinition[]>('/dictionary/types');
  }

  async getType(qualifiedName: string): Promise<TypeDefinition> {
    return api.get<TypeDefinition>(`/dictionary/types/${encodeURIComponent(qualifiedName)}`);
  }

  async getTypeProperties(qualifiedName: string): Promise<PropertyDefinition[]> {
    return api.get<PropertyDefinition[]>(
      `/dictionary/types/${encodeURIComponent(qualifiedName)}/properties`
    );
  }

  async getTypeHierarchy(qualifiedName: string): Promise<string[]> {
    return api.get<string[]>(`/dictionary/types/${encodeURIComponent(qualifiedName)}/hierarchy`);
  }

  async getMandatoryAspects(qualifiedName: string): Promise<string[]> {
    return api.get<string[]>(
      `/dictionary/types/${encodeURIComponent(qualifiedName)}/mandatory-aspects`
    );
  }

  async listAspects(): Promise<AspectDefinition[]> {
    return api.get<AspectDefinition[]>('/dictionary/aspects');
  }

  async getAspect(qualifiedName: string): Promise<AspectDefinition> {
    return api.get<AspectDefinition>(`/dictionary/aspects/${encodeURIComponent(qualifiedName)}`);
  }

  async getAspectProperties(qualifiedName: string): Promise<PropertyDefinition[]> {
    return api.get<PropertyDefinition[]>(
      `/dictionary/aspects/${encodeURIComponent(qualifiedName)}/properties`
    );
  }
}

const dictionaryService = new DictionaryService();
export default dictionaryService;
