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

const isSiteInvitationDto = (value: unknown): value is SiteInvitationDto => {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = value as { id?: unknown; sendAttemptCount?: unknown };
  return typeof candidate.id === 'string' && typeof candidate.sendAttemptCount === 'number';
};

class SiteInvitationService {
  async listInvitations(siteId: string): Promise<SiteInvitationDto[]> {
    const result = await api.get<SiteInvitationDto[]>(`/sites/${siteId}/invitations`);
    if (!Array.isArray(result)) {
      // Phase 5 Mocked may return SPA HTML for unmocked routes. Surface the
      // synthetic error so the page renders a sensible message rather than
      // crashing on `.map` of a non-array value.
      throw new Error(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result;
  }

  createInvitation(siteId: string, data: InviteRequest): Promise<SiteInvitationDto> {
    return api.post<SiteInvitationDto>(`/sites/${siteId}/invitations`, data);
  }

  cancelInvitation(siteId: string, invitationId: string): Promise<void> {
    return api.delete<void>(`/sites/${siteId}/invitations/${invitationId}`);
  }

  acceptInvitation(data: TokenRequest): Promise<SiteInvitationDto> {
    return api.post<SiteInvitationDto>('/invitations/accept', data);
  }

  rejectInvitation(data: TokenRequest): Promise<SiteInvitationDto> {
    return api.post<SiteInvitationDto>('/invitations/reject', data);
  }

  async resendInvitation(siteId: string, invitationId: string): Promise<SiteInvitationDto> {
    const result = await api.post<SiteInvitationDto>(
      `/sites/${siteId}/invitations/${invitationId}/resend`,
    );
    if (!isSiteInvitationDto(result)) {
      throw new Error(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result;
  }
}

const siteInvitationService = new SiteInvitationService();
export default siteInvitationService;
