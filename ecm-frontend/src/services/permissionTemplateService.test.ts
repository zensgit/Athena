import api from './api';
import permissionTemplateService, {
  PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
  PermissionTemplate,
  PermissionTemplateVersion,
  PermissionTemplateVersionDetail,
} from './permissionTemplateService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    getBlob: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const template: PermissionTemplate = {
  id: 'template-uuid-1',
  name: 'Engineering Editors',
  description: 'Editors for engineering content',
  entries: [
    { authority: 'alice', authorityType: 'USER', permissionSet: 'EDITOR' },
    { authority: 'eng-team', authorityType: 'GROUP', permissionSet: 'CONTRIBUTOR' },
    { authority: 'ROLE_ADMIN', authorityType: 'ROLE', permissionSet: 'COORDINATOR' },
    { authority: 'EVERYONE', authorityType: 'EVERYONE', permissionSet: 'CONSUMER' },
  ],
  createdBy: 'admin',
  createdDate: '2026-05-14T10:00:00',
};

const templateWithNullableDetails: PermissionTemplate = {
  id: 'template-uuid-2',
  name: 'Minimal',
  description: null,
  entries: [],
};

const version: PermissionTemplateVersion = {
  id: 'version-uuid-1',
  templateId: 'template-uuid-1',
  versionNumber: 3,
  name: 'Engineering Editors',
  description: 'Editors for engineering content',
  entryCount: 2,
  createdBy: 'admin',
  createdDate: '2026-05-14T10:05:00',
};

const versionWithNullableDetails: PermissionTemplateVersion = {
  id: 'version-uuid-2',
  templateId: 'template-uuid-1',
  versionNumber: 1,
  name: 'Engineering Editors',
  description: null,
  entryCount: 0,
};

const versionDetail: PermissionTemplateVersionDetail = {
  id: 'version-uuid-1',
  templateId: 'template-uuid-1',
  versionNumber: 3,
  name: 'Engineering Editors',
  description: 'Editors for engineering content',
  entries: [
    { authority: 'alice', authorityType: 'USER', permissionSet: 'EDITOR' },
  ],
  createdBy: 'admin',
  createdDate: '2026-05-14T10:05:00',
};

const versionDetailWithNullableDetails: PermissionTemplateVersionDetail = {
  id: 'version-uuid-2',
  templateId: 'template-uuid-1',
  versionNumber: 1,
  name: 'Engineering Editors',
  description: null,
  entries: [],
};

describe('permissionTemplateService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded templates for list and accepts nullable detail fields', async () => {
    mockedApi.get.mockResolvedValueOnce([template, templateWithNullableDetails]);

    await expect(permissionTemplateService.list()).resolves.toEqual([
      template,
      templateWithNullableDetails,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/security/permission-templates');
  });

  it('rejects HTML fallback for list', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(permissionTemplateService.list()).rejects.toThrow(
      PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed array entries for list', async () => {
    mockedApi.get.mockResolvedValueOnce([
      { ...template, entries: [{ authority: 'alice', authorityType: 'OWNER', permissionSet: 'EDITOR' }] },
    ]);

    await expect(permissionTemplateService.list()).rejects.toThrow(
      PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects non-array body for list', async () => {
    mockedApi.get.mockResolvedValueOnce({ items: [] });

    await expect(permissionTemplateService.list()).rejects.toThrow(
      PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded create readback and forwards payload', async () => {
    const payload = {
      name: 'Engineering Editors',
      description: 'Editors for engineering content',
      entries: [{ authority: 'alice', authorityType: 'USER' as const, permissionSet: 'EDITOR' }],
    };
    mockedApi.post.mockResolvedValueOnce(template);

    await expect(permissionTemplateService.create(payload)).resolves.toEqual(template);

    expect(mockedApi.post).toHaveBeenCalledWith('/security/permission-templates', payload);
  });

  it('rejects malformed create readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...template, id: 42 });

    await expect(
      permissionTemplateService.create({ name: 'X', entries: [] }),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects HTML fallback for create', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      permissionTemplateService.create({ name: 'X', entries: [] }),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded update readback and forwards payload', async () => {
    const updated: PermissionTemplate = { ...template, name: 'Renamed' };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      permissionTemplateService.update('template-uuid-1', { name: 'Renamed' }),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1',
      { name: 'Renamed' },
    );
  });

  it('rejects malformed update readback', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...template, entries: [{ authority: 'x' }] });

    await expect(
      permissionTemplateService.update('template-uuid-1', { name: 'Renamed' }),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps remove wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await permissionTemplateService.remove('template-uuid-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/security/permission-templates/template-uuid-1');
  });

  it('keeps apply wiring as a no-content endpoint with query params', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);

    await permissionTemplateService.apply('template-uuid-1', 'node-uuid-1', true);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/apply',
      null,
      { params: { nodeId: 'node-uuid-1', replace: true } },
    );
  });

  it('apply defaults replace flag to false', async () => {
    mockedApi.post.mockResolvedValueOnce(undefined);

    await permissionTemplateService.apply('template-uuid-1', 'node-uuid-1');

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/apply',
      null,
      { params: { nodeId: 'node-uuid-1', replace: false } },
    );
  });

  it('returns guarded versions for listVersions and accepts nullable detail fields', async () => {
    mockedApi.get.mockResolvedValueOnce([version, versionWithNullableDetails]);

    await expect(permissionTemplateService.listVersions('template-uuid-1')).resolves.toEqual([
      version,
      versionWithNullableDetails,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/versions',
    );
  });

  it('rejects HTML fallback for listVersions', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      permissionTemplateService.listVersions('template-uuid-1'),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed array entries for listVersions', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...version, entryCount: 'two' }]);

    await expect(
      permissionTemplateService.listVersions('template-uuid-1'),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded rollbackVersion readback and posts to rollback path', async () => {
    mockedApi.post.mockResolvedValueOnce(template);

    await expect(
      permissionTemplateService.rollbackVersion('template-uuid-1', 'version-uuid-2'),
    ).resolves.toEqual(template);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/versions/version-uuid-2/rollback',
    );
  });

  it('rejects malformed rollbackVersion readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...template, name: null });

    await expect(
      permissionTemplateService.rollbackVersion('template-uuid-1', 'version-uuid-2'),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded getVersionDetail readback and accepts nullable detail fields', async () => {
    mockedApi.get.mockResolvedValueOnce(versionDetailWithNullableDetails);

    await expect(
      permissionTemplateService.getVersionDetail('template-uuid-1', 'version-uuid-2'),
    ).resolves.toEqual(versionDetailWithNullableDetails);

    expect(mockedApi.get).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/versions/version-uuid-2',
    );
  });

  it('returns guarded getVersionDetail readback with full detail fields', async () => {
    mockedApi.get.mockResolvedValueOnce(versionDetail);

    await expect(
      permissionTemplateService.getVersionDetail('template-uuid-1', 'version-uuid-1'),
    ).resolves.toEqual(versionDetail);
  });

  it('rejects malformed getVersionDetail readback', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...versionDetail, versionNumber: 'three' });

    await expect(
      permissionTemplateService.getVersionDetail('template-uuid-1', 'version-uuid-1'),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects HTML fallback for getVersionDetail', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      permissionTemplateService.getVersionDetail('template-uuid-1', 'version-uuid-1'),
    ).rejects.toThrow(PERMISSION_TEMPLATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('exportVersionDiff returns a Blob and forwards query params', async () => {
    const blob = new Blob(['csv,data'], { type: 'text/csv' });
    mockedApi.getBlob.mockResolvedValueOnce(blob);

    await expect(
      permissionTemplateService.exportVersionDiff(
        'template-uuid-1',
        'version-uuid-1',
        'version-uuid-2',
        'csv',
      ),
    ).resolves.toBe(blob);

    expect(mockedApi.getBlob).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/versions/diff/export',
      {
        params: {
          from: 'version-uuid-1',
          to: 'version-uuid-2',
          format: 'csv',
        },
      },
    );
  });

  it('exportVersionDiff forwards json format', async () => {
    const blob = new Blob(['{}'], { type: 'application/json' });
    mockedApi.getBlob.mockResolvedValueOnce(blob);

    await permissionTemplateService.exportVersionDiff(
      'template-uuid-1',
      'version-uuid-1',
      'version-uuid-2',
      'json',
    );

    expect(mockedApi.getBlob).toHaveBeenCalledWith(
      '/security/permission-templates/template-uuid-1/versions/diff/export',
      {
        params: {
          from: 'version-uuid-1',
          to: 'version-uuid-2',
          format: 'json',
        },
      },
    );
  });
});
