import api from './api';
import type { SiteMemberRole } from './siteService';

export interface SiteInvitationDto {
  id: string;
  siteId: string;
  siteTitle: string;
  inviteeEmail: string;
  inviteeUsername: string | null;
  invitedRole: string;
  status: string;
  message: string | null;
  invitedBy: string;
  expiresAt: string;
  acceptedAt: string | null;
  createdDate: string;
  // Send-status fields. Every nullable field below can be null at runtime
  // when no send attempt has happened or when the latest attempt did not
  // succeed; treat null explicitly in the UI (no `null` literals rendered).
  lastSendAttemptAt: string | null;
  lastSendStatus: 'SENT' | 'FAILED' | null;
  lastSendError: string | null;
  sendAttemptCount: number;
  lastSentAt: string | null;
}

export interface InviteRequest {
  inviteeEmail: string;
  invitedRole?: SiteMemberRole;
  message?: string;
}

export interface BulkInviteRequest {
  inviteeEmails: string[];
  invitedRole?: SiteMemberRole;
  message?: string;
}

export type BulkInviteErrorCategory =
  | 'INVALID_EMAIL'
  | 'DUPLICATE_PENDING'
  | 'INTERNAL_ERROR';

export interface BulkInviteResult {
  inviteeEmail: string;
  status: 'SUCCESS' | 'FAILED';
  invitation: SiteInvitationDto | null;
  errorCategory: BulkInviteErrorCategory | null;
  errorMessage: string | null;
}

export interface BulkInviteResponse {
  results: BulkInviteResult[];
}

export interface TokenRequest {
  token: string;
}

// Phase 5 Mocked harness can serve SPA index.html with HTTP 200 for unmocked
// routes. A naive consumer would crash on `response.id`. Surface a recognizable
// synthetic error so the dialog's error path renders a sensible operator-facing
// message instead. Exporting the literal as a constant keeps service / page /
// test in sync. See feedback_phase5_mocked_html_fallback.md.
export const SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE =
  'Site invitation resend endpoint returned an unexpected response. Mocked CI gate may not cover it; runtime configuration may be missing.';

// Separate sentinel for the bulk-create endpoint so a future regression
// pointing the dialog's error toast at the wrong source is debuggable.
// Bulk shape is structurally distinct (results array) from single-invite
// shape (a SiteInvitationDto), so a different message text helps narrow.
export const SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE =
  'Site invitation bulk endpoint returned an unexpected response. Mocked CI gate may not cover it; runtime configuration may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNullableString = (value: unknown): value is string | null => (
  value === null || typeof value === 'string'
);

const isLastSendStatus = (value: unknown): value is SiteInvitationDto['lastSendStatus'] => (
  value === null || value === 'SENT' || value === 'FAILED'
);

const isSiteInvitationDto = (value: unknown): value is SiteInvitationDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.siteId === 'string'
    && typeof value.siteTitle === 'string'
    && typeof value.inviteeEmail === 'string'
    && isNullableString(value.inviteeUsername)
    && typeof value.invitedRole === 'string'
    && typeof value.status === 'string'
    && isNullableString(value.message)
    && typeof value.invitedBy === 'string'
    && typeof value.expiresAt === 'string'
    && isNullableString(value.acceptedAt)
    && typeof value.createdDate === 'string'
    && isNullableString(value.lastSendAttemptAt)
    && isLastSendStatus(value.lastSendStatus)
    && isNullableString(value.lastSendError)
    && typeof value.sendAttemptCount === 'number'
    && Number.isFinite(value.sendAttemptCount)
    && isNullableString(value.lastSentAt);
};

const assertUnexpectedResponse = (): never => {
  throw new Error(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertSiteInvitationDto = (value: unknown): SiteInvitationDto => (
  isSiteInvitationDto(value) ? value : assertUnexpectedResponse()
);

const assertSiteInvitationArray = (value: unknown): SiteInvitationDto[] => {
  if (!Array.isArray(value) || !value.every(isSiteInvitationDto)) {
    return assertUnexpectedResponse();
  }
  return value;
};

const isBulkErrorCategory = (
  value: unknown,
): value is BulkInviteErrorCategory | null => (
  value === null
    || value === 'INVALID_EMAIL'
    || value === 'DUPLICATE_PENDING'
    || value === 'INTERNAL_ERROR'
);

const isBulkInviteResult = (value: unknown): value is BulkInviteResult => {
  if (!isObject(value)) {
    return false;
  }
  const status = value.status;
  if (status !== 'SUCCESS' && status !== 'FAILED') {
    return false;
  }
  if (typeof value.inviteeEmail !== 'string') {
    return false;
  }
  // invitation: SiteInvitationDto when SUCCESS, null when FAILED
  if (status === 'SUCCESS') {
    if (!isSiteInvitationDto(value.invitation)) {
      return false;
    }
  } else if (value.invitation !== null) {
    // FAILED rows must carry null invitation; if the backend ever changes this,
    // we want the predicate to fail loudly so the gate catches the drift.
    return false;
  }
  if (!isBulkErrorCategory(value.errorCategory)) {
    return false;
  }
  if (!isNullableString(value.errorMessage)) {
    return false;
  }
  return true;
};

const assertBulkInviteUnexpectedResponse = (): never => {
  throw new Error(SITE_INVITATION_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertBulkInviteResponse = (value: unknown): BulkInviteResponse => {
  if (!isObject(value)) {
    return assertBulkInviteUnexpectedResponse();
  }
  if (!Array.isArray(value.results) || !value.results.every(isBulkInviteResult)) {
    return assertBulkInviteUnexpectedResponse();
  }
  // Cast through `unknown` per TS strict-mode `Conversion of type ... may be a
  // mistake` rule. The predicate above proves the shape; the unknown step is
  // a syntactic acknowledgement of the cast.
  return value as unknown as BulkInviteResponse;
};

class SiteInvitationService {
  async listInvitations(siteId: string): Promise<SiteInvitationDto[]> {
    const result = await api.get<SiteInvitationDto[]>(`/sites/${siteId}/invitations`);
    return assertSiteInvitationArray(result);
  }

  async createInvitation(siteId: string, data: InviteRequest): Promise<SiteInvitationDto> {
    const result = await api.post<SiteInvitationDto>(`/sites/${siteId}/invitations`, data);
    return assertSiteInvitationDto(result);
  }

  async createInvitationsBulk(
    siteId: string,
    data: BulkInviteRequest,
  ): Promise<BulkInviteResponse> {
    const result = await api.post<BulkInviteResponse>(
      `/sites/${siteId}/invitations/bulk`,
      data,
    );
    return assertBulkInviteResponse(result);
  }

  cancelInvitation(siteId: string, invitationId: string): Promise<void> {
    return api.delete<void>(`/sites/${siteId}/invitations/${invitationId}`);
  }

  async acceptInvitation(data: TokenRequest): Promise<SiteInvitationDto> {
    const result = await api.post<SiteInvitationDto>('/invitations/accept', data);
    return assertSiteInvitationDto(result);
  }

  async rejectInvitation(data: TokenRequest): Promise<SiteInvitationDto> {
    const result = await api.post<SiteInvitationDto>('/invitations/reject', data);
    return assertSiteInvitationDto(result);
  }

  async resendInvitation(siteId: string, invitationId: string): Promise<SiteInvitationDto> {
    const result = await api.post<SiteInvitationDto>(
      `/sites/${siteId}/invitations/${invitationId}/resend`,
    );
    return assertSiteInvitationDto(result);
  }
}

const siteInvitationService = new SiteInvitationService();
export default siteInvitationService;
