import api from './api';

export const CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE =
  'Correspondent endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type MatchAlgorithm = 'AUTO' | 'ANY' | 'ALL' | 'EXACT' | 'REGEX' | 'FUZZY';

export interface Correspondent {
  id: string;
  name: string;
  matchAlgorithm: MatchAlgorithm;
  matchPattern?: string | null;
  insensitive: boolean;
  email?: string | null;
  phone?: string | null;
  createdDate?: string | null;
  createdBy?: string | null;
  lastModifiedDate?: string | null;
  lastModifiedBy?: string | null;
}

export interface CorrespondentPage {
  content: Correspondent[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CreateCorrespondentRequest {
  name: string;
  matchAlgorithm: MatchAlgorithm;
  matchPattern?: string | null;
  insensitive: boolean;
  email?: string | null;
  phone?: string | null;
}

export type UpdateCorrespondentRequest = Partial<CreateCorrespondentRequest>;

const MATCH_ALGORITHMS: MatchAlgorithm[] = ['AUTO', 'ANY', 'ALL', 'EXACT', 'REGEX', 'FUZZY'];

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isMatchAlgorithm = (value: unknown): value is MatchAlgorithm => (
  typeof value === 'string' && (MATCH_ALGORITHMS as string[]).includes(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(CORRESPONDENT_UNEXPECTED_RESPONSE_MESSAGE);
};

const isCorrespondent = (value: unknown): value is Correspondent => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.name === 'string'
    && isMatchAlgorithm(value.matchAlgorithm)
    && typeof value.insensitive === 'boolean'
    && isStringOrNullish(value.matchPattern)
    && isStringOrNullish(value.email)
    && isStringOrNullish(value.phone)
    && isStringOrNullish(value.createdDate)
    && isStringOrNullish(value.createdBy)
    && isStringOrNullish(value.lastModifiedDate)
    && isStringOrNullish(value.lastModifiedBy);
};

const assertCorrespondent = (value: unknown): Correspondent => (
  isCorrespondent(value) ? value : assertUnexpectedResponse()
);

const isCorrespondentPage = (value: unknown): value is CorrespondentPage => {
  if (!isObject(value) || !Array.isArray(value.content)) {
    return false;
  }
  return value.content.every(isCorrespondent)
    && isNumber(value.totalElements)
    && isNumber(value.totalPages)
    && isNumber(value.number)
    && isNumber(value.size);
};

const assertCorrespondentPage = (value: unknown): CorrespondentPage => (
  isCorrespondentPage(value) ? value : assertUnexpectedResponse()
);

class CorrespondentService {
  async list(page = 0, size = 200): Promise<Correspondent[]> {
    const result = await api.get<unknown>('/correspondents', {
      params: { page, size, sort: 'name,asc' },
    });
    return assertCorrespondentPage(result).content;
  }

  async create(payload: CreateCorrespondentRequest): Promise<Correspondent> {
    const result = await api.post<unknown>('/correspondents', payload);
    return assertCorrespondent(result);
  }

  async update(id: string, payload: UpdateCorrespondentRequest): Promise<Correspondent> {
    const result = await api.put<unknown>(`/correspondents/${id}`, payload);
    return assertCorrespondent(result);
  }
}

const correspondentService = new CorrespondentService();
export default correspondentService;
