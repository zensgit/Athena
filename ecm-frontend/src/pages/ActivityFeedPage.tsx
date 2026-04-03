import React, { useDeferredValue, useEffect, useState } from 'react';
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
import followingService, { FollowSubscriptionDto } from 'services/followingService';
import { useAppSelector } from 'store';
import authService from 'services/authService';
import {
  ActivityTargetKind,
  formatActivityLabel,
  formatActivitySummary,
  getActivityLinkTargets,
  matchesActivityFilter,
  matchesActivityTargetKind,
} from 'utils/siteActivityUtils';
import { getFollowTargetLink, groupFollowSubscriptions } from 'utils/followingUtils';

type FeedScope = 'global' | 'mine' | 'site' | 'following';

const ActivityFeedPage: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();

  const [scope, setScope] = useState<FeedScope>('mine');
  const [siteId, setSiteId] = useState('');
  const [activityFilter, setActivityFilter] = useState('');
  const [targetKind, setTargetKind] = useState<ActivityTargetKind | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<ActivityPage | null>(null);
  const [subscriptions, setSubscriptions] = useState<FollowSubscriptionDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [queryApplied, setQueryApplied] = useState(false);
  const deferredActivityFilter = useDeferredValue(activityFilter.trim());

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const preselectedSiteId = params.get('siteId')?.trim();
    const preselectedScope = params.get('scope')?.trim();
    const preselectedFilter = params.get('type')?.trim();
    const preselectedTarget = params.get('target')?.trim();
    if (preselectedSiteId) {
      setScope('site');
      setSiteId(preselectedSiteId);
    } else if (preselectedScope === 'global' || preselectedScope === 'mine' || preselectedScope === 'site' || preselectedScope === 'following') {
      setScope(preselectedScope);
    }
    if (preselectedFilter) {
      setActivityFilter(preselectedFilter);
    }
    if (preselectedTarget === 'ALL' || preselectedTarget === 'SITE' || preselectedTarget === 'NODE' || preselectedTarget === 'USER') {
      setTargetKind(preselectedTarget);
    }
    setQueryApplied(true);
  }, []);

  useEffect(() => {
    if (!queryApplied) return;
    const params = new URLSearchParams(window.location.search);
    params.set('scope', scope);
    if (scope === 'site' && siteId.trim()) {
      params.set('siteId', siteId.trim());
    } else {
      params.delete('siteId');
    }
    if (activityFilter.trim()) {
      params.set('type', activityFilter.trim());
    } else {
      params.delete('type');
    }
    if (targetKind !== 'ALL') {
      params.set('target', targetKind);
    } else {
      params.delete('target');
    }
    const nextSearch = params.toString();
    const nextUrl = `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}`;
    window.history.replaceState(null, '', nextUrl);
  }, [activityFilter, queryApplied, scope, siteId, targetKind]);

  const load = async () => {
    setLoading(true);
    try {
      let result: ActivityPage;
      if (scope === 'mine' && effectiveUser?.username) {
        result = await activityService.getUserFeed(effectiveUser.username, page);
      } else if (scope === 'following') {
        const [followingFeed, followingSubscriptions] = await Promise.all([
          activityService.getFollowingFeed(page),
          followingService.list(),
        ]);
        result = followingFeed;
        setSubscriptions(followingSubscriptions);
      } else if (scope === 'site' && siteId.trim()) {
        result = await activityService.getSiteFeed(siteId.trim(), page);
        setSubscriptions([]);
      } else {
        result = await activityService.getGlobalFeed(page);
        setSubscriptions([]);
      }
      if (scope === 'mine') {
        setSubscriptions([]);
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

  const visibleActivities = data?.content.filter(
    (activity) =>
      matchesActivityFilter(activity, deferredActivityFilter)
      && matchesActivityTargetKind(activity, targetKind)
  ) ?? [];
  const groupedSubscriptions = groupFollowSubscriptions(subscriptions, targetKind);

  const handleUnfollow = async (subscription: FollowSubscriptionDto) => {
    try {
      await followingService.unfollow(subscription.targetType, subscription.targetId);
      setSubscriptions((prev) =>
        prev.filter((item) => item.id !== subscription.id)
      );
      toast.success('Subscription removed');
      if (scope === 'following') {
        void load();
      }
    } catch {
      toast.error('Failed to remove subscription');
    }
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
              <MenuItem value="following">Following</MenuItem>
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
          <TextField
            size="small"
            label="Type Filter"
            value={activityFilter}
            onChange={(event) => {
              setActivityFilter(event.target.value);
              setPage(0);
            }}
            sx={{ width: 180 }}
          />
          <FormControl size="small" sx={{ minWidth: 130 }}>
            <InputLabel>Target</InputLabel>
            <Select
              label="Target"
              value={targetKind}
              onChange={(event) => {
                setTargetKind(event.target.value as ActivityTargetKind | 'ALL');
                setPage(0);
              }}
            >
              <MenuItem value="ALL">All Targets</MenuItem>
              <MenuItem value="SITE">Sites</MenuItem>
              <MenuItem value="NODE">Nodes</MenuItem>
              <MenuItem value="USER">Users</MenuItem>
            </Select>
          </FormControl>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void load()} disabled={loading}>Refresh</Button>
        </Stack>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
          <>
            {scope === 'following' && (
              <Card variant="outlined" sx={{ mb: 2 }}>
                <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                    <Box>
                      <Typography variant="h6">My Following</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {subscriptions.length} active subscriptions
                      </Typography>
                    </Box>
                  </Box>
                  {groupedSubscriptions.length === 0 ? (
                    <Paper sx={{ p: 2, textAlign: 'center' }} variant="outlined">
                      <Typography color="text.secondary">
                        No followed targets match the current target filter.
                      </Typography>
                    </Paper>
                  ) : (
                    <Stack spacing={1.25}>
                      {groupedSubscriptions.map((group) => (
                        <Box key={group.targetType}>
                          <Stack direction="row" spacing={1} alignItems="center" mb={0.75}>
                            <Typography variant="subtitle2">{group.label}</Typography>
                            <Chip size="small" label={group.subscriptions.length} variant="outlined" />
                          </Stack>
                          <Stack spacing={0.75}>
                            {group.subscriptions.map((subscription) => {
                              const targetLink = getFollowTargetLink(subscription);
                              return (
                                <Paper key={subscription.id} variant="outlined" sx={{ p: 1.25 }}>
                                  <Box display="flex" justifyContent="space-between" alignItems="center" gap={1}>
                                    <Box minWidth={0}>
                                      <Typography variant="body2" fontWeight={500} noWrap>
                                        {subscription.targetId}
                                      </Typography>
                                      <Typography variant="caption" color="text.secondary">
                                        Following since {new Date(subscription.createdAt).toLocaleString()}
                                      </Typography>
                                    </Box>
                                    <Stack direction="row" spacing={0.75}>
                                      <Button
                                        size="small"
                                        variant="text"
                                        onClick={() => window.open(targetLink.href, '_blank')}
                                      >
                                        {targetLink.label}
                                      </Button>
                                      <Button
                                        size="small"
                                        color="warning"
                                        variant="outlined"
                                        onClick={() => void handleUnfollow(subscription)}
                                      >
                                        Unfollow
                                      </Button>
                                    </Stack>
                                  </Box>
                                </Paper>
                              );
                            })}
                          </Stack>
                        </Box>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>
            )}
            <Stack spacing={1.5}>
            {visibleActivities.map((activity) => (
              <Card key={activity.id} variant="outlined">
                <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                  <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                    <Box display="flex" gap={1.5} alignItems="center">
                      <Chip label={formatActivityLabel(activity.activityType)} size="small" color={activityColor(activity.activityType)} variant="outlined" />
                      <Box>
                        <>
                          <Typography variant="body2">
                            <strong>{activity.userId}</strong>
                            {activity.nodeName && <> &middot; <em>{activity.nodeName}</em></>}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" display="block">
                            {formatActivitySummary(activity)}
                          </Typography>
                          <Stack direction="row" spacing={0.75} mt={0.75}>
                            {getActivityLinkTargets(activity).map((target) => (
                              <Button
                                key={`${activity.id}-${target.label}`}
                                size="small"
                                variant="text"
                                onClick={() => window.open(target.href, '_blank')}
                              >
                                {target.label}
                              </Button>
                            ))}
                          </Stack>
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
            {(!data || visibleActivities.length === 0) && (
              <Paper sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  {data && data.content.length > 0 ? 'No activities match the current filter' : 'No activities to display'}
                </Typography>
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
