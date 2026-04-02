import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import RatingBadge from './RatingBadge';
import ratingService from 'services/ratingService';

jest.mock('services/ratingService');
const mockedService = ratingService as jest.Mocked<typeof ratingService>;

const emptySummary = {
  likes: { count: 0, average: 0, total: 0 },
  fivestar: { count: 0, average: 0, total: 0 },
};

const populatedSummary = {
  likes: { count: 3, average: 1, total: 3 },
  fivestar: { count: 5, average: 4.2, total: 21 },
};

describe('RatingBadge', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('renders nothing when no ratings and not compact', async () => {
    mockedService.getSummary.mockResolvedValue(emptySummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: null, starScore: null });

    const { container } = render(<RatingBadge nodeId="node-1" />);

    await waitFor(() => {
      expect(mockedService.getSummary).toHaveBeenCalledWith('node-1');
    });
    expect(container.textContent).toBe('');
  });

  it('renders like count when ratings exist', async () => {
    mockedService.getSummary.mockResolvedValue(populatedSummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: null, starScore: null });

    render(<RatingBadge nodeId="node-2" />);

    const likeCount = await screen.findByText('3');
    expect(likeCount).toBeTruthy();
  });

  it('renders star average when star ratings exist', async () => {
    mockedService.getSummary.mockResolvedValue(populatedSummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: null, starScore: null });

    render(<RatingBadge nodeId="node-2b" />);

    const starAvg = await screen.findByText('4.2');
    expect(starAvg).toBeTruthy();
  });

  it('shows filled like icon when user has liked', async () => {
    mockedService.getSummary.mockResolvedValue(populatedSummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: 1, starScore: null });

    render(<RatingBadge nodeId="node-3" />);

    const unlikeButton = await screen.findByRole('button', { name: /unlike/i });
    expect(unlikeButton).toBeTruthy();
  });

  it('shows outline like icon when user has not liked', async () => {
    mockedService.getSummary.mockResolvedValue(populatedSummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: null, starScore: null });

    render(<RatingBadge nodeId="node-4" />);

    const likeButton = await screen.findByRole('button', { name: /^like$/i });
    expect(likeButton).toBeTruthy();
  });

  it('calls rate() on like toggle when not liked', async () => {
    mockedService.getSummary.mockResolvedValue(populatedSummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: null, starScore: null });
    mockedService.rate.mockResolvedValue({ id: 'r1', userId: 'alice', scheme: 'LIKES', score: 1, createdAt: '' });

    render(<RatingBadge nodeId="node-5" />);

    const likeButton = await screen.findByRole('button', { name: /^like$/i });
    fireEvent.click(likeButton);

    await waitFor(() => {
      expect(mockedService.rate).toHaveBeenCalledWith('node-5', 'LIKES', 1);
    });
  });

  it('calls removeRating() on like toggle when already liked', async () => {
    mockedService.getSummary.mockResolvedValue(populatedSummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: 1, starScore: null });
    mockedService.removeRating.mockResolvedValue(undefined);

    render(<RatingBadge nodeId="node-6" />);

    const unlikeButton = await screen.findByRole('button', { name: /unlike/i });
    fireEvent.click(unlikeButton);

    await waitFor(() => {
      expect(mockedService.removeRating).toHaveBeenCalledWith('node-6', 'LIKES');
    });
  });

  it('renders in compact mode even with zero counts', async () => {
    mockedService.getSummary.mockResolvedValue(emptySummary);
    mockedService.getMyRatings.mockResolvedValue({ likeScore: null, starScore: null });

    render(<RatingBadge nodeId="node-7" compact />);

    const likeButton = await screen.findByRole('button', { name: /^like$/i });
    expect(likeButton).toBeTruthy();
  });
});
