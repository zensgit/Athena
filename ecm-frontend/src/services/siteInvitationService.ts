import api from './api';

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
}

export interface InviteRequest {
  inviteeEmail: string;
  invitedRole?: string;
  message?: string;
}

export interface TokenRequest {
  token: string;
}

class SiteInvitationService {
  listInvitations(siteId: string): Promise<SiteInvitationDto[]> {
    return api.get<SiteInvitationDto[]>(`/sites/${siteId}/invitations`);
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
}

const siteInvitationService = new SiteInvitationService();
export default siteInvitationService;
