import api from './api';
import siteService, {
  MembershipRequestDto,
  SITE_UNEXPECTED_RESPONSE_MESSAGE,
  SiteDto,
  SiteMemberDto,
} from './siteService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const site: SiteDto = {
  id: 'site-uuid-1',
  siteId: 'engineering',
  title: 'Engineering',
  description: 'Engineering site',
  visibility: 'PUBLIC',
  status: 'ACTIVE',
  rootFolderId: 'root-folder-uuid-1',
  rootFolderTitle: 'engineering',
  rootFolderPath: '/Sites/engineering',
  createdBy: 'alice',
  createdDate: '2026-05-14T10:00:00',
  lastModifiedDate: '2026-05-14T10:00:00',
  deleted: false,
  deletedAt: null,
  deletedBy: null,
};

const siteWithNullableDetails: SiteDto = {
  id: 'site-uuid-2',
  siteId: 'minimal',
  title: 'Minimal',
  description: null,
  visibility: 'PRIVATE',
  status: 'ACTIVE',
  rootFolderId: null,
  rootFolderTitle: null,
  rootFolderPath: null,
  createdBy: 'bob',
  createdDate: '2026-05-14T10:05:00',
  lastModifiedDate: null,
  deleted: false,
  deletedAt: null,
  deletedBy: null,
};

const membershipRequest: MembershipRequestDto = {
  username: 'carol',
  siteId: 'engineering',
  siteTitle: 'Engineering',
  role: 'CONSUMER',
  message: 'please add me',
  status: 'PENDING',
  requestedAt: '2026-05-14T11:00:00',
};

const membershipRequestWithNullableDetails: MembershipRequestDto = {
  username: 'dave',
  siteId: 'engineering',
  siteTitle: null,
  role: 'CONSUMER',
  message: null,
  status: 'APPROVED',
  requestedAt: null,
  decisionBy: null,
  decisionAt: null,
  decisionComment: null,
};

const siteMember: SiteMemberDto = {
  id: 'member-uuid-1',
  siteId: 'engineering',
  username: 'alice',
  role: 'MANAGER',
  joinedAt: '2026-05-14T09:00:00',
};

const siteMemberWithNullableDetails: SiteMemberDto = {
  id: 'member-uuid-2',
  siteId: 'engineering',
  username: 'bob',
  role: 'CONSUMER',
  joinedAt: null,
};

describe('siteService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded sites for listSites and accepts nullable detail fields', async () => {
    mockedApi.get.mockResolvedValueOnce([site, siteWithNullableDetails]);

    await expect(siteService.listSites()).resolves.toEqual([site, siteWithNullableDetails]);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites', {
      params: { includeArchived: false },
    });
  });

  it('passes includeArchived flag through to listSites', async () => {
    mockedApi.get.mockResolvedValueOnce([site]);

    await siteService.listSites(true);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites', {
      params: { includeArchived: true },
    });
  });

  it('rejects HTML fallback for listSites', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(siteService.listSites()).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed array entries for listSites', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...site, visibility: 'public' }]);

    await expect(siteService.listSites()).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded getSite readback', async () => {
    mockedApi.get.mockResolvedValueOnce(site);

    await expect(siteService.getSite('engineering')).resolves.toEqual(site);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering');
  });

  it('rejects malformed getSite readback', async () => {
    mockedApi.get.mockResolvedValueOnce({ ...site, deleted: 'false' });

    await expect(siteService.getSite('engineering')).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded createSite readback and forwards payload', async () => {
    const payload = {
      siteId: 'engineering',
      title: 'Engineering',
      description: 'Engineering site',
      visibility: 'PUBLIC' as const,
    };
    mockedApi.post.mockResolvedValueOnce(site);

    await expect(siteService.createSite(payload)).resolves.toEqual(site);

    expect(mockedApi.post).toHaveBeenCalledWith('/sites', payload);
  });

  it('rejects malformed createSite readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...site, status: 'UNKNOWN' });

    await expect(
      siteService.createSite({ siteId: 'engineering', title: 'Engineering' }),
    ).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded updateSite readback and forwards payload', async () => {
    const updated = { ...site, title: 'Eng Team' };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      siteService.updateSite('engineering', { title: 'Eng Team' }),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith('/sites/engineering', { title: 'Eng Team' });
  });

  it('rejects malformed updateSite readback', async () => {
    mockedApi.put.mockResolvedValueOnce({ ...site, id: 42 });

    await expect(
      siteService.updateSite('engineering', { title: 'Eng Team' }),
    ).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps deleteSite wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await siteService.deleteSite('engineering');

    expect(mockedApi.delete).toHaveBeenCalledWith('/sites/engineering');
  });

  it('returns guarded membership requests for getMembershipRequests', async () => {
    mockedApi.get.mockResolvedValueOnce([membershipRequest, membershipRequestWithNullableDetails]);

    await expect(siteService.getMembershipRequests('engineering')).resolves.toEqual([
      membershipRequest,
      membershipRequestWithNullableDetails,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/membership-requests');
  });

  it('rejects HTML fallback for getMembershipRequests', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(siteService.getMembershipRequests('engineering')).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed array entries for getMembershipRequests', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...membershipRequest, role: 7 }]);

    await expect(siteService.getMembershipRequests('engineering')).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded requestMembership readback and forwards payload', async () => {
    const payload = { siteTitle: 'Engineering', role: 'CONSUMER' as const, message: 'please add me' };
    mockedApi.post.mockResolvedValueOnce(membershipRequest);

    await expect(siteService.requestMembership('engineering', payload)).resolves.toEqual(
      membershipRequest,
    );

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/membership-requests',
      payload,
    );
  });

  it('rejects malformed requestMembership readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...membershipRequest, username: null });

    await expect(siteService.requestMembership('engineering', {})).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded approveMembershipRequest readback and forwards comment', async () => {
    const approved = { ...membershipRequest, status: 'APPROVED', decisionBy: 'alice' };
    mockedApi.post.mockResolvedValueOnce(approved);

    await expect(
      siteService.approveMembershipRequest('engineering', 'carol', 'welcome'),
    ).resolves.toEqual(approved);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/membership-requests/carol/approve',
      { comment: 'welcome' },
    );
  });

  it('approveMembershipRequest sends empty body when comment is omitted', async () => {
    mockedApi.post.mockResolvedValueOnce(membershipRequest);

    await siteService.approveMembershipRequest('engineering', 'carol');

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/membership-requests/carol/approve',
      {},
    );
  });

  it('rejects malformed approveMembershipRequest readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...membershipRequest, status: null });

    await expect(
      siteService.approveMembershipRequest('engineering', 'carol'),
    ).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded rejectMembershipRequest readback and forwards comment', async () => {
    const rejected = { ...membershipRequest, status: 'REJECTED', decisionBy: 'alice' };
    mockedApi.post.mockResolvedValueOnce(rejected);

    await expect(
      siteService.rejectMembershipRequest('engineering', 'carol', 'sorry'),
    ).resolves.toEqual(rejected);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/membership-requests/carol/reject',
      { comment: 'sorry' },
    );
  });

  it('rejectMembershipRequest sends empty body when comment is omitted', async () => {
    mockedApi.post.mockResolvedValueOnce(membershipRequest);

    await siteService.rejectMembershipRequest('engineering', 'carol');

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/membership-requests/carol/reject',
      {},
    );
  });

  it('rejects malformed rejectMembershipRequest readback', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      siteService.rejectMembershipRequest('engineering', 'carol'),
    ).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps withdrawMembershipRequest wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await siteService.withdrawMembershipRequest('engineering');

    expect(mockedApi.delete).toHaveBeenCalledWith('/sites/engineering/membership-requests');
  });

  it('returns guarded members for getMembers and accepts nullable joinedAt', async () => {
    mockedApi.get.mockResolvedValueOnce([siteMember, siteMemberWithNullableDetails]);

    await expect(siteService.getMembers('engineering')).resolves.toEqual([
      siteMember,
      siteMemberWithNullableDetails,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/members');
  });

  it('rejects HTML fallback for getMembers', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(siteService.getMembers('engineering')).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed array entries for getMembers', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...siteMember, username: 99 }]);

    await expect(siteService.getMembers('engineering')).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded addMember readback and forwards payload with default role', async () => {
    mockedApi.post.mockResolvedValueOnce(siteMemberWithNullableDetails);

    await expect(siteService.addMember('engineering', 'bob')).resolves.toEqual(
      siteMemberWithNullableDetails,
    );

    expect(mockedApi.post).toHaveBeenCalledWith('/sites/engineering/members', {
      username: 'bob',
      role: 'CONSUMER',
    });
  });

  it('returns guarded addMember readback with explicit role', async () => {
    mockedApi.post.mockResolvedValueOnce(siteMember);

    await expect(siteService.addMember('engineering', 'alice', 'MANAGER')).resolves.toEqual(
      siteMember,
    );

    expect(mockedApi.post).toHaveBeenCalledWith('/sites/engineering/members', {
      username: 'alice',
      role: 'MANAGER',
    });
  });

  it('rejects malformed addMember readback', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...siteMember, role: 'OWNER' });

    await expect(siteService.addMember('engineering', 'alice')).rejects.toThrow(
      SITE_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded updateMemberRole readback and forwards payload', async () => {
    const updated = { ...siteMember, role: 'COLLABORATOR' as const };
    mockedApi.put.mockResolvedValueOnce(updated);

    await expect(
      siteService.updateMemberRole('engineering', 'alice', 'COLLABORATOR'),
    ).resolves.toEqual(updated);

    expect(mockedApi.put).toHaveBeenCalledWith('/sites/engineering/members/alice', {
      role: 'COLLABORATOR',
    });
  });

  it('rejects malformed updateMemberRole readback', async () => {
    mockedApi.put.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      siteService.updateMemberRole('engineering', 'alice', 'COLLABORATOR'),
    ).rejects.toThrow(SITE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('keeps removeMember wiring as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await siteService.removeMember('engineering', 'alice');

    expect(mockedApi.delete).toHaveBeenCalledWith('/sites/engineering/members/alice');
  });
});
