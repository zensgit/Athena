import api from './api';
import ratingService, {
  MyRatings,
  RatingDto,
  RatingSummary,
  RATING_UNEXPECTED_RESPONSE_MESSAGE,
} from './ratingService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const rating: RatingDto = {
  id: 'rating-1',
  userId: 'alice',
  scheme: 'FIVE_STAR',
  score: 5,
  createdAt: '2026-05-14T00:00:00',
};

const summary: RatingSummary = {
  likes: { count: 2, average: 1, total: 2 },
  fivestar: { count: 3, average: 4.3, total: 13 },
};

const mine: MyRatings = {
  likeScore: 1,
  starScore: null,
};

describe('ratingService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded rating lists', async () => {
    mockedApi.get.mockResolvedValueOnce([rating]);

    await expect(ratingService.listRatings('node-1')).resolves.toEqual([rating]);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/ratings');
  });

  it('rejects HTML fallback for rating lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(ratingService.listRatings('node-1')).rejects.toThrow(
      RATING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed rating list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...rating, scheme: 'BAD_SCHEME' }]);

    await expect(ratingService.listRatings('node-1')).rejects.toThrow(
      RATING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded mutation readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce(rating);

    await expect(ratingService.rate('node-1', 'FIVE_STAR', 5)).resolves.toEqual(rating);

    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/ratings', {
      scheme: 'FIVE_STAR',
      score: 5,
    });
  });

  it('rejects malformed mutation readbacks', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...rating, score: '5' });

    await expect(ratingService.rate('node-1', 'FIVE_STAR', 5)).rejects.toThrow(
      RATING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded rating summary responses', async () => {
    mockedApi.get.mockResolvedValueOnce(summary);

    await expect(ratingService.getSummary('node-1')).resolves.toEqual(summary);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/ratings/summary');
  });

  it('rejects malformed rating summary responses', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...summary,
      fivestar: { ...summary.fivestar, average: '4.3' },
    });

    await expect(ratingService.getSummary('node-1')).rejects.toThrow(
      RATING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('returns guarded current-user rating responses', async () => {
    mockedApi.get.mockResolvedValueOnce(mine);

    await expect(ratingService.getMyRatings('node-1')).resolves.toEqual(mine);

    expect(mockedApi.get).toHaveBeenCalledWith('/nodes/node-1/ratings/mine');
  });

  it('rejects malformed current-user rating responses', async () => {
    mockedApi.get.mockResolvedValueOnce({ likeScore: false, starScore: null });

    await expect(ratingService.getMyRatings('node-1')).rejects.toThrow(
      RATING_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('keeps delete endpoint wiring unchanged', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await ratingService.removeRating('node-1', 'LIKES');

    expect(mockedApi.delete).toHaveBeenCalledWith('/nodes/node-1/ratings/LIKES');
  });
});
