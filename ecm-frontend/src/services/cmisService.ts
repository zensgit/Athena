import api from './api';

export type CmisRepositoryInfo = {
  repositoryId: string;
  repositoryName: string;
  vendorName: string;
  productName: string;
  productVersion: string;
  cmisVersionSupported: string;
  rootFolderId: string;
  capabilities: string[];
};

export type CmisTypeDefinition = {
  id: string;
  displayName: string;
  baseTypeId: string;
  creatable: boolean;
  fileable: boolean;
  queryable: boolean;
  propertyIds: string[];
};

export type CmisTypeChildrenResponse = {
  types: CmisTypeDefinition[];
  totalNumItems: number;
  hasMoreItems: boolean;
};

export type CmisQueryRow = Record<string, unknown>;

export type CmisQueryResponse = {
  repositoryId: string;
  statement: string;
  objects: CmisQueryRow[];
  skipCount: number;
  maxItems: number;
  totalNumItems: number;
  hasMoreItems: boolean;
};

class CmisService {
  getRepositoryInfo(): Promise<CmisRepositoryInfo> {
    return api.get<CmisRepositoryInfo>('/cmis/browser', {
      params: { cmisselector: 'repositoryInfo' },
    });
  }

  getTypeChildren(): Promise<CmisTypeChildrenResponse> {
    return api.get<CmisTypeChildrenResponse>('/cmis/browser', {
      params: { cmisselector: 'typeChildren' },
    });
  }

  query(statement: string, skipCount = 0, maxItems = 50): Promise<CmisQueryResponse> {
    return api.get<CmisQueryResponse>('/cmis/browser', {
      params: { cmisselector: 'query', statement, skipCount, maxItems },
    });
  }
}

const cmisService = new CmisService();
export default cmisService;
