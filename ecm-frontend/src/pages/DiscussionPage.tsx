import React, { useEffect, useState } from 'react';
import {
  Box, Button, Card, CardContent, Chip, CircularProgress, Dialog,
  DialogActions, DialogContent, DialogTitle, IconButton, Pagination,
  Paper, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import { Add, ChatBubbleOutline, Delete, Lock, PushPin, Refresh } from '@mui/icons-material';
import { useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import discussionService, { ReplyDto, TopicDto, TopicPage } from 'services/discussionService';
import { useAppSelector } from 'store';
import authService from 'services/authService';

const DiscussionPage: React.FC = () => {
  const { siteId } = useParams<{ siteId: string }>();
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const currentUsername = effectiveUser?.username;
  const isAdmin = Boolean(effectiveUser?.roles?.includes('ROLE_ADMIN'));
  const [topics, setTopics] = useState<TopicPage | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);

  // topic detail
  const [selectedTopic, setSelectedTopic] = useState<TopicDto | null>(null);
  const [replies, setReplies] = useState<ReplyDto[]>([]);
  const [replyText, setReplyText] = useState('');

  // create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [createTitle, setCreateTitle] = useState('');
  const [createContent, setCreateContent] = useState('');

  const loadTopics = async () => {
    if (!siteId) return;
    setLoading(true);
    try {
      const data = await discussionService.listTopics(siteId, page);
      setTopics(data);
    } catch { toast.error('Failed to load discussions'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void loadTopics(); }, [siteId, page]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadReplies = async (topic: TopicDto) => {
    if (!siteId) return;
    setSelectedTopic(topic);
    try {
      const data = await discussionService.listReplies(siteId, topic.id);
      setReplies(data.content);
    } catch { toast.error('Failed to load replies'); }
  };

  const handleCreate = async () => {
    if (!siteId || !createTitle.trim()) { toast.warn('Title is required'); return; }
    try {
      await discussionService.createTopic(siteId, createTitle.trim(), createContent.trim() || undefined);
      setCreateOpen(false);
      setCreateTitle('');
      setCreateContent('');
      toast.success('Topic created');
      await loadTopics();
    } catch { toast.error('Failed to create topic'); }
  };

  const handleReply = async () => {
    if (!siteId || !selectedTopic || !replyText.trim()) return;
    try {
      await discussionService.createReply(siteId, selectedTopic.id, replyText.trim());
      setReplyText('');
      toast.success('Reply posted');
      await loadReplies(selectedTopic);
    } catch { toast.error('Failed to post reply'); }
  };

  const handleDeleteTopic = async (topicId: string) => {
    if (!siteId || !window.confirm('Delete this topic and all replies?')) return;
    try {
      await discussionService.deleteTopic(siteId, topicId);
      if (selectedTopic?.id === topicId) setSelectedTopic(null);
      toast.success('Topic deleted');
      await loadTopics();
    } catch { toast.error('Failed to delete'); }
  };

  const statusIcon = (status: string) => {
    if (status === 'PINNED') return <PushPin fontSize="small" color="warning" />;
    if (status === 'CLOSED') return <Lock fontSize="small" color="disabled" />;
    return null;
  };

  return (
    <Box maxWidth={1000}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Discussions — {siteId}</Typography>
          <Typography variant="body2" color="text.secondary">Site discussion forum</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void loadTopics()} disabled={loading}>Refresh</Button>
          <Button variant="contained" startIcon={<Add />} onClick={() => setCreateOpen(true)}>New Topic</Button>
        </Stack>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <Stack direction="row" spacing={2} alignItems="flex-start">
          {/* topic list */}
          <Box flex={1}>
            <Stack spacing={1}>
              {topics?.content.map((topic) => (
                <Card
                  key={topic.id}
                  variant="outlined"
                  sx={{ cursor: 'pointer', borderLeft: selectedTopic?.id === topic.id ? '3px solid' : undefined, borderLeftColor: 'primary.main' }}
                  onClick={() => void loadReplies(topic)}
                >
                  <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                    <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                      <Box>
                        <Box display="flex" alignItems="center" gap={0.5}>
                          {statusIcon(topic.status)}
                          <Typography variant="subtitle2">{topic.title}</Typography>
                        </Box>
                        <Typography variant="caption" color="text.secondary">
                          by {topic.createdBy} &middot; {new Date(topic.createdDate).toLocaleDateString()}
                          &middot; <ChatBubbleOutline sx={{ fontSize: 12, verticalAlign: 'middle' }} /> {topic.replyCount}
                        </Typography>
                      </Box>
                      {(topic.createdBy === currentUsername || isAdmin) && (
                        <Tooltip title="Delete">
                          <IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); void handleDeleteTopic(topic.id); }}>
                            <Delete fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Box>
                    {topic.tags.length > 0 && (
                      <Box mt={0.5}>{topic.tags.map((t) => <Chip key={t} label={t} size="small" sx={{ mr: 0.5 }} />)}</Box>
                    )}
                  </CardContent>
                </Card>
              ))}
              {(!topics || topics.content.length === 0) && (
                <Paper sx={{ p: 3, textAlign: 'center' }}><Typography color="text.secondary">No topics yet</Typography></Paper>
              )}
            </Stack>
            {topics && topics.totalPages > 1 && (
              <Box display="flex" justifyContent="center" mt={2}>
                <Pagination count={topics.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} />
              </Box>
            )}
          </Box>

          {/* reply pane */}
          {selectedTopic && (
            <Paper variant="outlined" sx={{ flex: 1, p: 2, maxHeight: 600, overflow: 'auto' }}>
              <Typography variant="h6" gutterBottom>{selectedTopic.title}</Typography>
              {selectedTopic.content && <Typography variant="body2" mb={2}>{selectedTopic.content}</Typography>}
              <Stack spacing={1} mb={2}>
                {replies.map((r) => (
                  <Paper key={r.id} variant="outlined" sx={{ p: 1.5, ml: r.parentReplyId ? 3 : 0 }}>
                    <Typography variant="body2">{r.content}</Typography>
                    <Typography variant="caption" color="text.secondary">{r.createdBy} &middot; {new Date(r.createdDate).toLocaleString()}</Typography>
                  </Paper>
                ))}
                {replies.length === 0 && <Typography variant="body2" color="text.secondary">No replies yet</Typography>}
              </Stack>
              {selectedTopic.status !== 'CLOSED' && (
                <Box display="flex" gap={1}>
                  <TextField size="small" fullWidth placeholder="Write a reply..." value={replyText} onChange={(e) => setReplyText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && void handleReply()} />
                  <Button variant="contained" onClick={() => void handleReply()} disabled={!replyText.trim()}>Reply</Button>
                </Box>
              )}
              {selectedTopic.status === 'CLOSED' && (
                <Typography variant="body2" color="text.secondary">This topic is closed for replies.</Typography>
              )}
            </Paper>
          )}
        </Stack>
      )}

      {/* create topic dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>New Discussion Topic</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Title" value={createTitle} onChange={(e) => setCreateTitle(e.target.value)} required fullWidth />
            <TextField label="Content" value={createContent} onChange={(e) => setCreateContent(e.target.value)} fullWidth multiline minRows={3} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleCreate()}>Create</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default DiscussionPage;
