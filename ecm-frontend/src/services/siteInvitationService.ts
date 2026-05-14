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

class SiteInvitationService {
  async listInvitations(siteId: string): Promise<SiteInvitationDto[]> {
    const result = await api.get<SiteInvitationDto[]>(`/sites/${siteId}/invitations`);
    return assertSiteInvitationArray(result);
  }

  async createInvitation(siteId: string, data: InviteRequest): Promise<SiteInvitationDto> {
    const result = await api.post<SiteInvitationDto>(`/sites/${siteId}/invitations`, data);
    return assertSiteInvitationDto(result);
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
