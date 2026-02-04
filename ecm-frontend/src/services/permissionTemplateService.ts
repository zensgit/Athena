import api from './api';

export type PermissionTemplateEntry = {
  authority: string;
  authorityType: 'USER' | 'GROUP';
  permissionSet: string;
};

export interface PermissionTemplate {
  id: string;
  name: string;
  description?: string;
  entries: PermissionTemplateEntry[];
  createdBy?: string;
  createdDate?: string;
}

export interface PermissionTemplateCreateRequest {
  name: string;
  description?: string;
  entries: PermissionTemplateEntry[];
}

export interface PermissionTemplateUpdateRequest {
  name?: string;
  description?: string;
  entries?: PermissionTemplateEntry[];
}

class PermissionTemplateService {
  async list(): Promise<PermissionTemplate[]> {
    return api.get<PermissionTemplate[]>('/security/permission-templates');
  }

  async create(payload: PermissionTemplateCreateRequest): Promise<PermissionTemplate> {
    return api.post<PermissionTemplate>('/security/permission-templates', payload);
  }

  async update(id: string, payload: PermissionTemplateUpdateRequest): Promise<PermissionTemplate> {
    return api.put<PermissionTemplate>(`/security/permission-templates/${id}`, payload);
  }

  async remove(id: string): Promise<void> {
    return api.delete(`/security/permission-templates/${id}`);
  }

  async apply(id: string, nodeId: string, replace = false): Promise<void> {
    return api.post(`/security/permission-templates/${id}/apply`, null, {
      params: { nodeId, replace },
    });
  }
}

const permissionTemplateService = new PermissionTemplateService();
export default permissionTemplateService;
