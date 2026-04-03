import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  IconButton,
  Pagination,
  Paper,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import { CheckCircle, Delete, DoneAll, OpenInNew, Refresh } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import notificationService, { NotificationDto, NotificationPage } from 'services/notificationService';
import {
  formatNotificationLabel,
  formatNotificationSummary,
  getNotificationLinkTargets,
} from 'utils/notificationUtils';

type ViewMode = 'all' | 'unread';

const NotificationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [mode, setMode] = useState<ViewMode>('unread');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<NotificationPage | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);

  const notifyInboxChanged = () => {
    window.dispatchEvent(new Event('athena:notifications-changed'));
  };

  const load = async () => {
    setLoading(true);
    try {
      const [pageData, count] = await Promise.all([
        mode === 'unread'
          ? notificationService.getUnread(page)
          : notificationService.getInbox(page),
        notificationService.getUnreadCount(),
      ]);
      setData(pageData);
      setUnreadCount(count);
    } catch { toast.error('Failed to load notifications'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [mode, page]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleMarkRead = async (id: string) => {
    try {
      await notificationService.markRead(id);
      await load();
      notifyInboxChanged();
    } catch { toast.error('Failed to mark as read'); }
  };

  const handleMarkAllRead = async () => {
    try {
      const marked = await notificationService.markAllRead();
      toast.success(`${marked} notification${marked !== 1 ? 's' : ''} marked as read`);
      await load();
      notifyInboxChanged();
    } catch { toast.error('Failed to mark all as read'); }
  };

  const handleDelete = async (id: string) => {
    try {
      await notificationService.deleteNotification(id);
      await load();
      notifyInboxChanged();
    } catch { toast.error('Failed to delete notification'); }
  };

  const handleOpenTarget = async (notification: NotificationDto, href: string) => {
    if (!notification.read) {
      try {
        await notificationService.markRead(notification.id);
        notifyInboxChanged();
      } catch {
        // Ignore mark-read failure and continue the drill-down action.
      }
    }
    navigate(href);
  };

  const activityColor = (type: string) => {
    if (type?.includes('created') || type?.includes('added')) return 'success' as const;
    if (type?.includes('deleted') || type?.includes('removed') || type?.includes('rejected')) return 'error' as const;
    if (type?.includes('locked') || type?.includes('approved')) return 'warning' as const;
    return 'default' as const;
  };

  return (
    <Box maxWidth={800}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">
            Notifications
            {unreadCount > 0 && (
              <Chip label={unreadCount} color="error" size="small" sx={{ ml: 1, verticalAlign: 'middle' }} />
            )}
          </Typography>
          <Typography variant="body2" color="text.secondary">Your notification inbox</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant={mode === 'unread' ? 'contained' : 'outlined'}
            size="small"
            onClick={() => { setMode('unread'); setPage(0); }}
          >
            Unread
          </Button>
          <Button
            variant={mode === 'all' ? 'contained' : 'outlined'}
            size="small"
            onClick={() => { setMode('all'); setPage(0); }}
          >
            All
          </Button>
          {unreadCount > 0 && (
            <Button variant="outlined" size="small" startIcon={<DoneAll />} onClick={() => void handleMarkAllRead()}>
              Mark All Read
            </Button>
          )}
          <Button variant="outlined" size="small" startIcon={<Refresh />} onClick={() => void load()} disabled={loading}>
            Refresh
          </Button>
        </Stack>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <>
          <Stack spacing={1}>
            {data?.content.map((n) => (
              <Card
                key={n.id}
                variant="outlined"
                sx={{ bgcolor: n.read ? 'transparent' : 'action.hover', borderLeft: n.read ? undefined : '3px solid', borderLeftColor: n.read ? undefined : 'primary.main' }}
              >
                <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                  <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                    <Box display="flex" gap={1.5} alignItems="center" flex={1}>
                      <Chip label={n.activityType || 'unknown'} size="small" color={activityColor(n.activityType)} variant="outlined" />
                      <Box>
                        <Typography variant="body2" fontWeight={600}>
                          {formatNotificationLabel(n)}
                        </Typography>
                        <Typography variant="body2">
                          <strong>{n.actorUserId}</strong>
                          {n.nodeName && <> &middot; <em>{n.nodeName}</em></>}
                          {n.siteId && <> &middot; {n.siteId}</>}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                          {formatNotificationSummary(n)}
                        </Typography>
                        <Stack direction="row" spacing={0.75} mt={0.75} flexWrap="wrap">
                          {getNotificationLinkTargets(n).map((target) => (
                            <Button
                              key={`${n.id}-${target.label}`}
                              size="small"
                              variant="text"
                              startIcon={<OpenInNew fontSize="small" />}
                              onClick={() => void handleOpenTarget(n, target.href)}
                            >
                              {target.label}
                            </Button>
                          ))}
                        </Stack>
                      </Box>
                    </Box>
                    <Box display="flex" alignItems="center" gap={0.5}>
                      <Typography variant="caption" color="text.secondary" whiteSpace="nowrap">
                        {new Date(n.createdAt).toLocaleString()}
                      </Typography>
                      {!n.read && (
                        <Tooltip title="Mark as read">
                          <IconButton size="small" onClick={() => void handleMarkRead(n.id)}>
                            <CheckCircle fontSize="small" color="primary" />
                          </IconButton>
                        </Tooltip>
                      )}
                      <Tooltip title="Delete">
                        <IconButton size="small" onClick={() => void handleDelete(n.id)}>
                          <Delete fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            ))}
            {(!data || data.content.length === 0) && (
              <Paper sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  {mode === 'unread' ? 'No unread notifications' : 'No notifications'}
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

export default NotificationsPage;
