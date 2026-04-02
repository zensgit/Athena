import api from './api';

export type RatingScheme = 'LIKES' | 'FIVE_STAR';

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

class RatingService {
  async listRatings(nodeId: string): Promise<RatingDto[]> {
    return api.get<RatingDto[]>(`/nodes/${nodeId}/ratings`);
  }

  async rate(nodeId: string, scheme: RatingScheme, score: number): Promise<RatingDto> {
    return api.post<RatingDto>(`/nodes/${nodeId}/ratings`, { scheme, score });
  }

  async removeRating(nodeId: string, scheme: RatingScheme): Promise<void> {
    return api.delete(`/nodes/${nodeId}/ratings/${scheme}`);
  }

  async getSummary(nodeId: string): Promise<RatingSummary> {
    return api.get<RatingSummary>(`/nodes/${nodeId}/ratings/summary`);
  }

  async getMyRatings(nodeId: string): Promise<MyRatings> {
    return api.get<MyRatings>(`/nodes/${nodeId}/ratings/mine`);
  }
}

const ratingService = new RatingService();
export default ratingService;
