import api from './api';

export interface LocalizedContentDto {
  id: string;
  nodeId: string;
  locale: string;
  title: string | null;
  description: string | null;
  createdDate: string;
  createdBy: string;
  lastModifiedDate: string | null;
}

export interface LocalizedContentRequest {
  title?: string;
  description?: string;
}

class LocalizedContentService {
  async listLocalizations(nodeId: string): Promise<LocalizedContentDto[]> {
    return api.get<LocalizedContentDto[]>(`/nodes/${nodeId}/localizations`);
  }

  async upsertLocalization(
    nodeId: string,
    locale: string,
    data: LocalizedContentRequest
  ): Promise<LocalizedContentDto> {
    return api.put<LocalizedContentDto>(
      `/nodes/${nodeId}/localizations/${encodeURIComponent(locale)}`,
      data
    );
  }

  async deleteLocalization(nodeId: string, locale: string): Promise<void> {
    return api.delete<void>(`/nodes/${nodeId}/localizations/${encodeURIComponent(locale)}`);
  }

  async resolveLocalization(nodeId: string): Promise<LocalizedContentDto> {
    const acceptLanguage =
      (navigator.languages && navigator.languages.length > 0
        ? navigator.languages.join(',')
        : navigator.language) || 'en';
    return api.get<LocalizedContentDto>(`/nodes/${nodeId}/localization`, {
      headers: { 'Accept-Language': acceptLanguage },
    });
  }
}

const localizedContentService = new LocalizedContentService();
export default localizedContentService;
