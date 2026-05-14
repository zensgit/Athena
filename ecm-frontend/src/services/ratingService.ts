import api from './api';

export const RATING_UNEXPECTED_RESPONSE_MESSAGE =
  'Rating endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

export type RatingScheme = 'LIKES' | 'FIVE_STAR';

const isRatingScheme = (value: unknown): value is RatingScheme => (
  value === 'LIKES' || value === 'FIVE_STAR'
);

export interface RatingDto {
  id: string;
  userId: string;
  scheme: RatingScheme;
  score: number;
  createdAt: string;
}

export interface SchemeSummary {
  count: number;
  average: number;
  total: number;
}

export interface RatingSummary {
  likes: SchemeSummary;
  fivestar: SchemeSummary;
}

export interface MyRatings {
  likeScore: number | null;
  starScore: number | null;
}

const assertUnexpectedResponse = (): never => {
  throw new Error(RATING_UNEXPECTED_RESPONSE_MESSAGE);
};

const isRatingDto = (value: unknown): value is RatingDto => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.userId === 'string'
    && isRatingScheme(value.scheme)
    && isNumber(value.score)
    && typeof value.createdAt === 'string';
};

const assertRatingDto = (value: unknown): RatingDto => (
  isRatingDto(value) ? value : assertUnexpectedResponse()
);

const assertRatingList = (value: unknown): RatingDto[] => {
  if (!Array.isArray(value) || !value.every(isRatingDto)) {
    throw new Error(RATING_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const isSchemeSummary = (value: unknown): value is SchemeSummary => {
  if (!isObject(value)) {
    return false;
  }
  return isNumber(value.count)
    && isNumber(value.average)
    && isNumber(value.total);
};

const isRatingSummary = (value: unknown): value is RatingSummary => {
  if (!isObject(value)) {
    return false;
  }
  return isSchemeSummary(value.likes) && isSchemeSummary(value.fivestar);
};

const assertRatingSummary = (value: unknown): RatingSummary => (
  isRatingSummary(value) ? value : assertUnexpectedResponse()
);

const isNullableNumber = (value: unknown): value is number | null => (
  value === null || isNumber(value)
);

const isMyRatings = (value: unknown): value is MyRatings => {
  if (!isObject(value)) {
    return false;
  }
  return isNullableNumber(value.likeScore) && isNullableNumber(value.starScore);
};

const assertMyRatings = (value: unknown): MyRatings => (
  isMyRatings(value) ? value : assertUnexpectedResponse()
);

class RatingService {
  async listRatings(nodeId: string): Promise<RatingDto[]> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/ratings`);
    return assertRatingList(result);
  }

  async rate(nodeId: string, scheme: RatingScheme, score: number): Promise<RatingDto> {
    const result = await api.post<unknown>(`/nodes/${nodeId}/ratings`, { scheme, score });
    return assertRatingDto(result);
  }

  async removeRating(nodeId: string, scheme: RatingScheme): Promise<void> {
    return api.delete(`/nodes/${nodeId}/ratings/${scheme}`);
  }

  async getSummary(nodeId: string): Promise<RatingSummary> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/ratings/summary`);
    return assertRatingSummary(result);
  }

  async getMyRatings(nodeId: string): Promise<MyRatings> {
    const result = await api.get<unknown>(`/nodes/${nodeId}/ratings/mine`);
    return assertMyRatings(result);
  }
}

const ratingService = new RatingService();
export default ratingService;
