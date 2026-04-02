import React, { useCallback, useEffect, useState } from 'react';
import { Box, Chip, IconButton, Tooltip, Typography } from '@mui/material';
import { ThumbUp, ThumbUpOutlined, Star } from '@mui/icons-material';
import ratingService, { RatingSummary, MyRatings } from 'services/ratingService';

interface RatingBadgeProps {
  nodeId: string;
  compact?: boolean;
}

/**
 * Lightweight inline rating badge for list/grid views.
 * Shows like count + star average, with a single-click like toggle.
 */
const RatingBadge: React.FC<RatingBadgeProps> = ({ nodeId, compact = false }) => {
  const [summary, setSummary] = useState<RatingSummary | null>(null);
  const [mine, setMine] = useState<MyRatings | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, m] = await Promise.all([
        ratingService.getSummary(nodeId),
        ratingService.getMyRatings(nodeId),
      ]);
      setSummary(s);
      setMine(m);
    } catch { /* silent */ }
  }, [nodeId]);

  useEffect(() => { void load(); }, [load]);

  const handleLikeToggle = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (loading) return;
    setLoading(true);
    try {
      if (mine?.likeScore) {
        await ratingService.removeRating(nodeId, 'LIKES');
      } else {
        await ratingService.rate(nodeId, 'LIKES', 1);
      }
      await load();
    } catch { /* silent */ }
    finally { setLoading(false); }
  };

  const liked = Boolean(mine?.likeScore);
  const likeCount = summary?.likes.count ?? 0;
  const starAvg = summary?.fivestar.average ?? 0;
  const starCount = summary?.fivestar.count ?? 0;

  if (!summary) return null;
  if (likeCount === 0 && starCount === 0 && !compact) return null;

  return (
    <Box display="flex" alignItems="center" gap={0.5} onClick={(e) => e.stopPropagation()}>
      <Tooltip title={liked ? 'Unlike' : 'Like'}>
        <IconButton
          size="small"
          color={liked ? 'primary' : 'default'}
          onClick={handleLikeToggle}
          sx={{ p: 0.25 }}
        >
          {liked ? <ThumbUp sx={{ fontSize: 14 }} /> : <ThumbUpOutlined sx={{ fontSize: 14 }} />}
        </IconButton>
      </Tooltip>
      {likeCount > 0 && (
        <Typography variant="caption" color="text.secondary" sx={{ minWidth: 12 }}>
          {likeCount}
        </Typography>
      )}
      {starCount > 0 && (
        <Tooltip title={`${starAvg.toFixed(1)} avg (${starCount} rating${starCount !== 1 ? 's' : ''})`}>
          <Chip
            icon={<Star sx={{ fontSize: 12 }} />}
            label={starAvg.toFixed(1)}
            size="small"
            variant="outlined"
            sx={{ height: 20, '& .MuiChip-label': { px: 0.5, fontSize: '0.7rem' } }}
          />
        </Tooltip>
      )}
    </Box>
  );
};

export default RatingBadge;
