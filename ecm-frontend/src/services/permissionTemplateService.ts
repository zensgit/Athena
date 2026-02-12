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

export interface PermissionTemplateVersion {
  id: string;
  templateId: string;
  versionNumber: number;
  name: string;
  description?: string;
  entryCount: number;
  createdBy?: string;
  createdDate?: string;
}

export interface PermissionTemplateVersionDetail {
  id: string;
  templateId: string;
  versionNumber: number;
  name: string;
  description?: string;
  entries: PermissionTemplateEntry[];
  createdBy?: string;
  createdDate?: string;
}

export type PermissionTemplateVersionDiffExportFormat = 'csv' | 'json';

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

  async listVersions(id: string): Promise<PermissionTemplateVersion[]> {
    return api.get<PermissionTemplateVersion[]>(`/security/permission-templates/${id}/versions`);
  }

  async rollbackVersion(templateId: string, versionId: string): Promise<PermissionTemplate> {
    return api.post<PermissionTemplate>(
      `/security/permission-templates/${templateId}/versions/${versionId}/rollback`,
    );
  }

  async getVersionDetail(templateId: string, versionId: string): Promise<PermissionTemplateVersionDetail> {
    return api.get<PermissionTemplateVersionDetail>(
      `/security/permission-templates/${templateId}/versions/${versionId}`,
    );
  }

  async exportVersionDiff(
    templateId: string,
    fromVersionId: string,
    toVersionId: string,
    format: PermissionTemplateVersionDiffExportFormat,
  ): Promise<Blob> {
    return api.getBlob(`/security/permission-templates/${templateId}/versions/diff/export`, {
      params: {
        from: fromVersionId,
        to: toVersionId,
        format,
      },
    });
  }
}

const permissionTemplateService = new PermissionTemplateService();
export default permissionTemplateService;
