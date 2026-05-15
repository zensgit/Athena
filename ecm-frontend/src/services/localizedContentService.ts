import api from './api';

export const LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE =
  'Localized content endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNull = (value: unknown): value is string | null => (
  value === null || typeof value === 'string'
);

const assertUnexpectedResponse = (): never => {
  throw new Error(LOCALIZED_CONTENT_UNEXPECTED_RESPONSE_MESSAGE);
};

const isLocalizedContentDto = (value: unknown): value is LocalizedContentDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.nodeId === 'string'
    && typeof value.locale === 'string'
    && isStringOrNull(value.title)
    && isStringOrNull(value.description)
    && typeof value.createdDate === 'string'
    && typeof value.createdBy === 'string'
    && isStringOrNull(value.lastModifiedDate);
};

const assertLocalizedContentDto = (value: unknown): LocalizedContentDto => (
  isLocalizedContentDto(value) ? value : assertUnexpectedResponse()
);

const assertLocalizedContentDtoArray = (value: unknown): LocalizedContentDto[] => {
  if (!Array.isArray(value) || !value.every(isLocalizedContentDto)) {
    return assertUnexpectedResponse();
  }
  return value;
};

class LocalizedContentService {
  async listLocalizations(nodeId: string): Promise<LocalizedContentDto[]> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/localizations`);
    return assertLocalizedContentDtoArray(result);
  }

  async upsertLocalization(
    nodeId: string,
    locale: string,
    data: LocalizedContentRequest
  ): Promise<LocalizedContentDto> {
    const result = await api.put<unknown>(
      `/nodes/${nodeId}/localizations/${encodeURIComponent(locale)}`,
      data
    );
    return assertLocalizedContentDto(result);
  }

  async deleteLocalization(nodeId: string, locale: string): Promise<void> {
    return api.delete<void>(`/nodes/${nodeId}/localizations/${encodeURIComponent(locale)}`);
  }

  async resolveLocalization(nodeId: string): Promise<LocalizedContentDto> {
    const acceptLanguage =
      (navigator.languages && navigator.languages.length > 0
        ? navigator.languages.join(',')
        : navigator.language) || 'en';
    const result = await api.get<unknown>(`/nodes/${nodeId}/localization`, {
      headers: { 'Accept-Language': acceptLanguage },
    });
    return assertLocalizedContentDto(result);
  }
}

const localizedContentService = new LocalizedContentService();
export default localizedContentService;
