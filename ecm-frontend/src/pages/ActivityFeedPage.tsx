import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Pagination,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Refresh } from '@mui/icons-material';
import { toast } from 'react-toastify';
import activityService, { ActivityPage } from 'services/activityService';
import { useAppSelector } from 'store';
import authService from 'services/authService';

type FeedScope = 'global' | 'mine' | 'site';

const ActivityFeedPage: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();

  const [scope, setScope] = useState<FeedScope>('mine');
  const [siteId, setSiteId] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ActivityPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [queryApplied, setQueryApplied] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const preselectedSiteId = params.get('siteId')?.trim();
    const preselectedScope = params.get('scope')?.trim();
    if (preselectedSiteId) {
      setScope('site');
      setSiteId(preselectedSiteId);
    } else if (preselectedScope === 'global' || preselectedScope === 'mine' || preselectedScope === 'site') {
      setScope(preselectedScope);
    }
    setQueryApplied(true);
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      let result: ActivityPage;
      if (scope === 'mine' && effectiveUser?.username) {
        result = await activityService.getUserFeed(effectiveUser.username, page);
      } else if (scope === 'site' && siteId.trim()) {
        result = await activityService.getSiteFeed(siteId.trim(), page);
      } else {
        result = await activityService.getGlobalFeed(page);
      }
      setData(result);
    } catch { toast.error('Failed to load activity feed'); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    if (!queryApplied) return;
    void load();
  }, [queryApplied, scope, page, siteId, effectiveUser?.username]); // eslint-disable-line react-hooks/exhaustive-deps

  const activityColor = (type: string) => {
    if (type.includes('created')) return 'success' as const;
    if (type.includes('deleted')) return 'error' as const;
    if (type.includes('locked')) return 'warning' as const;
    return 'default' as const;
  };

  return (
    <Box maxWidth={900}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Activity Feed</Typography>
          <Typography variant="body2" color="text.secondary">Recent actions across the repository</Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Scope</InputLabel>
            <Select label="Scope" value={scope} onChange={(e) => { setScope(e.target.value as FeedScope); setPage(0); }}>
              <MenuItem value="mine">My Activity</MenuItem>
              <MenuItem value="global">Global</MenuItem>
              <MenuItem value="site">By Site</MenuItem>
            </Select>
          </FormControl>
          {scope === 'site' && (
            <TextField
              size="small"
              label="Site ID"
              value={siteId}
              onChange={(e) => setSiteId(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void load()}
              sx={{ width: 140 }}
            />
          )}
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void load()} disabled={loading}>Refresh</Button>
        </Stack>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <>
          <Stack spacing={1.5}>
            {data?.content.map((activity) => (
              <Card key={activity.id} variant="outlined">
                <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                  <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                    <Box display="flex" gap={1.5} alignItems="center">
                      <Chip label={activity.activityType} size="small" color={activityColor(activity.activityType)} variant="outlined" />
                      <Box>
                        <>
                          <Typography variant="body2">
                            <strong>{activity.userId}</strong>
                            {activity.nodeName && <> &middot; <em>{activity.nodeName}</em></>}
                          </Typography>
                          {activity.summary?.action && (
                            <Typography variant="caption" color="text.secondary">{String(activity.summary.action)}</Typography>
                          )}
                        </>
                      </Box>
                    </Box>
                    <Typography variant="caption" color="text.secondary" whiteSpace="nowrap">
                      {new Date(activity.postedAt).toLocaleString()}
                    </Typography>
                  </Box>
                </CardContent>
              </Card>
            ))}
            {(!data || data.content.length === 0) && (
              <Paper sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">No activities to display</Typography>
              </Paper>
            )}
          </Stack>
          {data && data.totalPages > 1 && (
            <Box display="flex" justifyContent="center" mt={2}>
              <Pagination count={data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} />
            </Box>
          )}
        </>
      )}
    </Box>
  );
};

export default ActivityFeedPage;
