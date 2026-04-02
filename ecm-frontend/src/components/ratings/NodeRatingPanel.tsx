import React, { useEffect, useState } from 'react';
import {
  Box,
  IconButton,
  Rating as MuiRating,
  Typography,
  Chip,
  CircularProgress,
  Tooltip,
} from '@mui/material';
import { ThumbUp, ThumbUpOutlined } from '@mui/icons-material';
import ratingService, { RatingSummary, MyRatings } from 'services/ratingService';
import { toast } from 'react-toastify';

interface NodeRatingPanelProps {
  nodeId: string;
}

const NodeRatingPanel: React.FC<NodeRatingPanelProps> = ({ nodeId }) => {
  const [summary, setSummary] = useState<RatingSummary | null>(null);
  const [mine, setMine] = useState<MyRatings | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const [s, m] = await Promise.all([
        ratingService.getSummary(nodeId),
        ratingService.getMyRatings(nodeId),
      ]);
      setSummary(s);
      setMine(m);
    } catch { /* api interceptor handles */ }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [nodeId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleLikeToggle = async () => {
    try {
      if (mine?.likeScore) {
        await ratingService.removeRating(nodeId, 'LIKES');
      } else {
        await ratingService.rate(nodeId, 'LIKES', 1);
      }
      await load();
    } catch { toast.error('Failed to update like'); }
  };

  const handleStarChange = async (_: unknown, value: number | null) => {
    try {
      if (value == null || value === 0) {
        await ratingService.removeRating(nodeId, 'FIVE_STAR');
      } else {
        await ratingService.rate(nodeId, 'FIVE_STAR', value);
      }
      await load();
    } catch { toast.error('Failed to update rating'); }
  };

  if (loading) return <CircularProgress size={20} />;

  const liked = Boolean(mine?.likeScore);
  const likeCount = summary?.likes.count ?? 0;
  const starAvg = summary?.fivestar.average ?? 0;
  const starCount = summary?.fivestar.count ?? 0;

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>Ratings</Typography>

      {/* Likes row */}
      <Box display="flex" alignItems="center" gap={1} mb={1.5}>
        <Tooltip title={liked ? 'Remove like' : 'Like this document'}>
          <IconButton size="small" color={liked ? 'primary' : 'default'} onClick={() => void handleLikeToggle()}>
            {liked ? <ThumbUp fontSize="small" /> : <ThumbUpOutlined fontSize="small" />}
          </IconButton>
        </Tooltip>
        <Chip label={`${likeCount} like${likeCount !== 1 ? 's' : ''}`} size="small" variant="outlined" />
      </Box>

      {/* Five-star row */}
      <Box display="flex" alignItems="center" gap={1}>
        <MuiRating
          name="node-rating"
          value={mine?.starScore ?? 0}
          onChange={handleStarChange}
          size="small"
        />
        <Typography variant="body2" color="text.secondary">
          {starAvg > 0 ? `${starAvg.toFixed(1)} avg` : 'No ratings'}
          {starCount > 0 && ` (${starCount})`}
        </Typography>
      </Box>
    </Box>
  );
};

export default NodeRatingPanel;
