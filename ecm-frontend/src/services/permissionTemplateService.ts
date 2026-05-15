import api from './api';

export const PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE =
  'Permission template endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type PermissionTemplateEntry = {
  authority: string;
  authorityType: 'USER' | 'GROUP' | 'ROLE' | 'EVERYONE';
  permissionSet: string;
};

export interface PermissionTemplate {
  id: string;
  name: string;
  description?: string | null;
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
  description?: string | null;
  entryCount: number;
  createdBy?: string;
  createdDate?: string;
}

export interface PermissionTemplateVersionDetail {
  id: string;
  templateId: string;
  versionNumber: number;
  name: string;
  description?: string | null;
  entries: PermissionTemplateEntry[];
  createdBy?: string;
  createdDate?: string;
}

export type PermissionTemplateVersionDiffExportFormat = 'csv' | 'json';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isStringOrUndefined = (value: unknown): value is string | undefined => (
  value === undefined || typeof value === 'string'
);

const PERMISSION_TEMPLATE_AUTHORITY_TYPES: PermissionTemplateEntry['authorityType'][] = [
  'USER',
  'GROUP',
  'ROLE',
  'EVERYONE',
];

const isAuthorityType = (value: unknown): value is PermissionTemplateEntry['authorityType'] => (
  typeof value === 'string' && (PERMISSION_TEMPLATE_AUTHORITY_TYPES as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
};

const isPermissionTemplateEntry = (value: unknown): value is PermissionTemplateEntry => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.authority === 'string'
    && isAuthorityType(value.authorityType)
    && typeof value.permissionSet === 'string';
};

const isPermissionTemplateEntryArray = (value: unknown): value is PermissionTemplateEntry[] => (
  Array.isArray(value) && value.every(isPermissionTemplateEntry)
);

const isPermissionTemplate = (value: unknown): value is PermissionTemplate => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && isPermissionTemplateEntryArray(value.entries)
    && isStringOrUndefined(value.createdBy)
    && isStringOrUndefined(value.createdDate);
};

const assertPermissionTemplate = (value: unknown): PermissionTemplate => (
  isPermissionTemplate(value) ? value : assertUnexpectedResponse()
);

const assertPermissionTemplateArray = (value: unknown): PermissionTemplate[] => {
  if (!Array.isArray(value) || !value.every(isPermissionTemplate)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const isPermissionTemplateVersion = (value: unknown): value is PermissionTemplateVersion => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.templateId === 'string'
    && isNumber(value.versionNumber)
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && isNumber(value.entryCount)
    && isStringOrUndefined(value.createdBy)
    && isStringOrUndefined(value.createdDate);
};

const assertPermissionTemplateVersionArray = (value: unknown): PermissionTemplateVersion[] => {
  if (!Array.isArray(value) || !value.every(isPermissionTemplateVersion)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const isPermissionTemplateVersionDetail = (
  value: unknown,
): value is PermissionTemplateVersionDetail => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.templateId === 'string'
    && isNumber(value.versionNumber)
    && typeof value.name === 'string'
    && isStringOrNullish(value.description)
    && isPermissionTemplateEntryArray(value.entries)
    && isStringOrUndefined(value.createdBy)
    && isStringOrUndefined(value.createdDate);
};

const assertPermissionTemplateVersionDetail = (
  value: unknown,
): PermissionTemplateVersionDetail => (
  isPermissionTemplateVersionDetail(value) ? value : assertUnexpectedResponse()
);

class PermissionTemplateService {
  async list(): Promise<PermissionTemplate[]> {
    const result = await api.get<unknown>('/security/permission-templates');
    return assertPermissionTemplateArray(result);
  }

  async create(payload: PermissionTemplateCreateRequest): Promise<PermissionTemplate> {
    const result = await api.post<unknown>('/security/permission-templates', payload);
    return assertPermissionTemplate(result);
  }

  async update(id: string, payload: PermissionTemplateUpdateRequest): Promise<PermissionTemplate> {
    const result = await api.put<unknown>(`/security/permission-templates/${id}`, payload);
    return assertPermissionTemplate(result);
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
    const result = await api.get<unknown>(`/security/permission-templates/${id}/versions`);
    return assertPermissionTemplateVersionArray(result);
  }

  async rollbackVersion(templateId: string, versionId: string): Promise<PermissionTemplate> {
    const result = await api.post<unknown>(
      `/security/permission-templates/${templateId}/versions/${versionId}/rollback`,
    );
    return assertPermissionTemplate(result);
  }

  async getVersionDetail(templateId: string, versionId: string): Promise<PermissionTemplateVersionDetail> {
    const result = await api.get<unknown>(
      `/security/permission-templates/${templateId}/versions/${versionId}`,
    );
    return assertPermissionTemplateVersionDetail(result);
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
