import api from './api';
import siteInvitationService, {
  BulkInviteRequest,
  BulkInviteResponse,
  InviteRequest,
  SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE,
  SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE,
  SiteInvitationDto,
} from './siteInvitationService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const invitation: SiteInvitationDto = {
  id: 'invitation-1',
  siteId: 'site-uuid-1',
  siteTitle: 'Engineering',
  inviteeEmail: 'new.user@example.com',
  inviteeUsername: null,
  invitedRole: 'CONSUMER',
  status: 'PENDING',
  message: null,
  invitedBy: 'manager',
  expiresAt: '2026-05-21T00:00:00',
  acceptedAt: null,
  createdDate: '2026-05-14T00:00:00',
  lastSendAttemptAt: null,
  lastSendStatus: null,
  lastSendError: null,
  sendAttemptCount: 0,
  lastSentAt: null,
};

const sentInvitation: SiteInvitationDto = {
  ...invitation,
  lastSendAttemptAt: '2026-05-14T00:01:00',
  lastSendStatus: 'SENT',
  sendAttemptCount: 1,
  lastSentAt: '2026-05-14T00:01:00',
};

describe('siteInvitationService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded invitation lists with nullable send tracking fields', async () => {
    mockedApi.get.mockResolvedValueOnce([invitation, sentInvitation]);

    await expect(siteInvitationService.listInvitations('engineering')).resolves.toEqual([
      invitation,
      sentInvitation,
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/sites/engineering/invitations');
  });

  it('rejects HTML fallback for invitation lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(siteInvitationService.listInvitations('engineering')).rejects.toThrow(
      SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed invitation list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...invitation, sendAttemptCount: '0' }]);

    await expect(siteInvitationService.listInvitations('engineering')).rejects.toThrow(
      SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded createInvitation readbacks and forwards payload', async () => {
    const request: InviteRequest = {
      inviteeEmail: 'new.user@example.com',
      invitedRole: 'CONSUMER',
      message: 'Join the site',
    };
    mockedApi.post.mockResolvedValueOnce({ ...invitation, message: request.message });

    await expect(siteInvitationService.createInvitation('engineering', request)).resolves.toEqual({
      ...invitation,
      message: request.message,
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/sites/engineering/invitations', request);
  });

  it('rejects malformed createInvitation readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...invitation, lastSendStatus: 'QUEUED' });

    await expect(
      siteInvitationService.createInvitation('engineering', {
        inviteeEmail: 'new.user@example.com',
      }),
    ).rejects.toThrow(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded accept and reject readbacks', async () => {
    const accepted = {
      ...invitation,
      status: 'ACCEPTED',
      acceptedAt: '2026-05-14T00:02:00',
    };
    const rejected = { ...invitation, status: 'REJECTED' };
    mockedApi.post.mockResolvedValueOnce(accepted).mockResolvedValueOnce(rejected);

    await expect(siteInvitationService.acceptInvitation({ token: 'accept-token' })).resolves.toEqual(accepted);
    await expect(siteInvitationService.rejectInvitation({ token: 'reject-token' })).resolves.toEqual(rejected);

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/invitations/accept', {
      token: 'accept-token',
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/invitations/reject', {
      token: 'reject-token',
    });
  });

  it('rejects malformed accept and reject readbacks', async () => {
    mockedApi.post
      .mockResolvedValueOnce({ ...invitation, acceptedAt: 42 })
      .mockResolvedValueOnce({ ...invitation, id: null });

    await expect(siteInvitationService.acceptInvitation({ token: 'accept-token' })).rejects.toThrow(
      SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE,
    );
    await expect(siteInvitationService.rejectInvitation({ token: 'reject-token' })).rejects.toThrow(
      SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded resend readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce(sentInvitation);

    await expect(siteInvitationService.resendInvitation('engineering', 'invitation-1')).resolves.toEqual(
      sentInvitation,
    );

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/invitations/invitation-1/resend',
    );
  });

  it('keeps cancelInvitation as a no-content endpoint', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await siteInvitationService.cancelInvitation('engineering', 'invitation-1');

    expect(mockedApi.delete).toHaveBeenCalledWith('/sites/engineering/invitations/invitation-1');
  });

  // ======================================================================
  // Bulk create — predicate shape lock + HTML-fallback sentinel + forwarding
  // ======================================================================

  it('returns guarded bulk responses with mixed SUCCESS + FAILED rows', async () => {
    const successRow: BulkInviteResponse['results'][number] = {
      inviteeEmail: 'alice@example.com',
      status: 'SUCCESS',
      invitation: sentInvitation,
      errorCategory: null,
      errorMessage: null,
    };
    const failedRow: BulkInviteResponse['results'][number] = {
      inviteeEmail: 'dup@example.com',
      status: 'FAILED',
      invitation: null,
      errorCategory: 'DUPLICATE_PENDING',
      errorMessage: 'A pending invitation already exists for this email in this site.',
    };
    const response: BulkInviteResponse = { results: [successRow, failedRow] };
    mockedApi.post.mockResolvedValueOnce(response);

    const request: BulkInviteRequest = {
      inviteeEmails: ['alice@example.com', 'dup@example.com'],
      invitedRole: 'CONSUMER',
    };

    await expect(
      siteInvitationService.createInvitationsBulk('engineering', request),
    ).resolves.toEqual(response);

    expect(mockedApi.post).toHaveBeenCalledWith(
      '/sites/engineering/invitations/bulk',
      request,
    );
  });

  it('rejects HTML fallback for bulk create', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      siteInvitationService.createInvitationsBulk('engineering', {
        inviteeEmails: ['alice@example.com'],
      }),
    ).rejects.toThrow(SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects bulk responses missing the results array', async () => {
    mockedApi.post.mockResolvedValueOnce({ status: 'SUCCESS' });

    await expect(
      siteInvitationService.createInvitationsBulk('engineering', {
        inviteeEmails: ['alice@example.com'],
      }),
    ).rejects.toThrow(SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects bulk SUCCESS rows that omit the invitation payload', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: [
        {
          inviteeEmail: 'alice@example.com',
          status: 'SUCCESS',
          invitation: null,
          errorCategory: null,
          errorMessage: null,
        },
      ],
    });

    await expect(
      siteInvitationService.createInvitationsBulk('engineering', {
        inviteeEmails: ['alice@example.com'],
      }),
    ).rejects.toThrow(SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects bulk FAILED rows that include an invitation payload (shape drift)', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: [
        {
          inviteeEmail: 'dup@example.com',
          status: 'FAILED',
          invitation: sentInvitation,
          errorCategory: 'DUPLICATE_PENDING',
          errorMessage: 'duplicate',
        },
      ],
    });

    await expect(
      siteInvitationService.createInvitationsBulk('engineering', {
        inviteeEmails: ['dup@example.com'],
      }),
    ).rejects.toThrow(SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects unknown errorCategory values', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: [
        {
          inviteeEmail: 'alice@example.com',
          status: 'FAILED',
          invitation: null,
          errorCategory: 'UNKNOWN_FUTURE_CATEGORY',
          errorMessage: 'whatever',
        },
      ],
    });

    await expect(
      siteInvitationService.createInvitationsBulk('engineering', {
        inviteeEmails: ['alice@example.com'],
      }),
    ).rejects.toThrow(SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
