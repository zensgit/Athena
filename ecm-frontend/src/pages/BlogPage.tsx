import React, { useEffect, useState } from 'react';
import {
  Box, Button, Card, CardContent, Chip, CircularProgress, Dialog,
  DialogActions, DialogContent, DialogTitle, IconButton, Pagination,
  Paper, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import { Add, Delete, Edit, Publish, Unpublished, Refresh } from '@mui/icons-material';
import { useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import blogService, { BlogPostDto, BlogPostPage } from 'services/blogService';
import { useAppSelector } from 'store';
import authService from 'services/authService';

type ViewFilter = 'all' | 'published' | 'drafts';

const BlogPage: React.FC = () => {
  const { siteId } = useParams<{ siteId: string }>();
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const currentUsername = effectiveUser?.username;
  const isAdmin = Boolean(effectiveUser?.roles?.includes('ROLE_ADMIN'));

  const [data, setData] = useState<BlogPostPage | null>(null);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<ViewFilter>('all');
  const [loading, setLoading] = useState(false);
  const [selectedPost, setSelectedPost] = useState<BlogPostDto | null>(null);

  // create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [createTitle, setCreateTitle] = useState('');
  const [createContent, setCreateContent] = useState('');
  const [createTags, setCreateTags] = useState('');

  // edit dialog
  const [editOpen, setEditOpen] = useState(false);
  const [editPost, setEditPost] = useState<BlogPostDto | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editTags, setEditTags] = useState('');

  const load = async () => {
    if (!siteId) return;
    setLoading(true);
    try {
      let result: BlogPostPage;
      if (filter === 'published') {
        result = await blogService.listPosts(siteId, page, 20, 'PUBLISHED');
      } else if (filter === 'drafts') {
        result = await blogService.listDrafts(siteId, page);
      } else {
        result = await blogService.listPosts(siteId, page);
      }
      setData(result);
    } catch { toast.error('Failed to load blog posts'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [siteId, page, filter]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = async () => {
    if (!siteId || !createTitle.trim()) { toast.warn('Title required'); return; }
    try {
      const tags = createTags.trim() ? createTags.split(',').map((t) => t.trim()).filter(Boolean) : undefined;
      await blogService.createPost(siteId, createTitle.trim(), createContent.trim() || undefined, tags);
      setCreateOpen(false);
      setCreateTitle('');
      setCreateContent('');
      setCreateTags('');
      toast.success('Draft created');
      await load();
    } catch { toast.error('Failed to create post'); }
  };

  const handlePublish = async (postId: string) => {
    if (!siteId) return;
    try {
      await blogService.publish(siteId, postId);
      toast.success('Published');
      await load();
      if (selectedPost?.id === postId) setSelectedPost(null);
    } catch { toast.error('Failed to publish'); }
  };

  const handleUnpublish = async (postId: string) => {
    if (!siteId) return;
    try {
      await blogService.unpublish(siteId, postId);
      toast.success('Reverted to draft');
      await load();
    } catch { toast.error('Failed to unpublish'); }
  };

  const handleDelete = async (postId: string) => {
    if (!siteId || !window.confirm('Delete this post?')) return;
    try {
      await blogService.deletePost(siteId, postId);
      if (selectedPost?.id === postId) setSelectedPost(null);
      toast.success('Post deleted');
      await load();
    } catch { toast.error('Failed to delete'); }
  };

  const openEdit = (post: BlogPostDto) => {
    setEditPost(post);
    setEditTitle(post.title);
    setEditContent(post.content || '');
    setEditTags(post.tags.join(', '));
    setEditOpen(true);
  };

  const handleEdit = async () => {
    if (!siteId || !editPost || !editTitle.trim()) { toast.warn('Title required'); return; }
    try {
      const tags = editTags.trim() ? editTags.split(',').map((t) => t.trim()).filter(Boolean) : undefined;
      await blogService.updatePost(siteId, editPost.id, {
        title: editTitle.trim(),
        content: editContent.trim() || undefined,
        tags,
      });
      setEditOpen(false);
      setEditPost(null);
      toast.success('Post updated');
      await load();
    } catch { toast.error('Failed to update'); }
  };

  const canModify = (post: BlogPostDto) => post.createdBy === currentUsername || isAdmin;

  return (
    <Box maxWidth={1000}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Blog — {siteId}</Typography>
          <Typography variant="body2" color="text.secondary">Site blog posts</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          {(['all', 'published', 'drafts'] as ViewFilter[]).map((f) => (
            <Button key={f} size="small" variant={filter === f ? 'contained' : 'outlined'} onClick={() => { setFilter(f); setPage(0); }}>
              {f.charAt(0).toUpperCase() + f.slice(1)}
            </Button>
          ))}
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void load()} disabled={loading}>Refresh</Button>
          <Button variant="contained" startIcon={<Add />} onClick={() => setCreateOpen(true)}>New Post</Button>
        </Stack>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <Stack direction="row" spacing={2} alignItems="flex-start">
          <Box flex={1}>
            <Stack spacing={1}>
              {data?.content.map((post) => (
                <Card key={post.id} variant="outlined" sx={{ cursor: 'pointer', borderLeft: selectedPost?.id === post.id ? '3px solid' : undefined, borderLeftColor: 'primary.main' }} onClick={() => setSelectedPost(post)}>
                  <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                    <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                      <Box>
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="subtitle2">{post.title}</Typography>
                          <Chip label={post.status} size="small" color={post.status === 'PUBLISHED' ? 'success' : 'default'} variant="outlined" />
                        </Box>
                        <Typography variant="caption" color="text.secondary">
                          by {post.createdBy} &middot; {post.publishedDate ? new Date(post.publishedDate).toLocaleDateString() : new Date(post.createdDate).toLocaleDateString()}
                        </Typography>
                      </Box>
                      {canModify(post) && (
                        <Stack direction="row" spacing={0.5}>
                          <Tooltip title="Edit"><IconButton size="small" onClick={(e) => { e.stopPropagation(); openEdit(post); }}><Edit fontSize="small" /></IconButton></Tooltip>
                          {post.status === 'DRAFT' && (
                            <Tooltip title="Publish"><IconButton size="small" color="success" onClick={(e) => { e.stopPropagation(); void handlePublish(post.id); }}><Publish fontSize="small" /></IconButton></Tooltip>
                          )}
                          {post.status === 'PUBLISHED' && (
                            <Tooltip title="Unpublish"><IconButton size="small" onClick={(e) => { e.stopPropagation(); void handleUnpublish(post.id); }}><Unpublished fontSize="small" /></IconButton></Tooltip>
                          )}
                          <Tooltip title="Delete"><IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); void handleDelete(post.id); }}><Delete fontSize="small" /></IconButton></Tooltip>
                        </Stack>
                      )}
                    </Box>
                    {post.tags.length > 0 && (
                      <Box mt={0.5}>{post.tags.map((t) => <Chip key={t} label={t} size="small" sx={{ mr: 0.5 }} />)}</Box>
                    )}
                  </CardContent>
                </Card>
              ))}
              {(!data || data.content.length === 0) && (
                <Paper sx={{ p: 3, textAlign: 'center' }}><Typography color="text.secondary">No posts</Typography></Paper>
              )}
            </Stack>
            {data && data.totalPages > 1 && (
              <Box display="flex" justifyContent="center" mt={2}>
                <Pagination count={data.totalPages} page={page + 1} onChange={(_, v) => setPage(v - 1)} />
              </Box>
            )}
          </Box>

          {selectedPost && (
            <Paper variant="outlined" sx={{ flex: 1, p: 2, maxHeight: 600, overflow: 'auto' }}>
              <Typography variant="h6">{selectedPost.title}</Typography>
              <Typography variant="caption" color="text.secondary" display="block" mb={2}>
                {selectedPost.createdBy} &middot; {selectedPost.status}
                {selectedPost.publishedDate && ` &middot; Published ${new Date(selectedPost.publishedDate).toLocaleDateString()}`}
              </Typography>
              <Typography variant="body1" whiteSpace="pre-wrap">{selectedPost.content || 'No content'}</Typography>
            </Paper>
          )}
        </Stack>
      )}

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>New Blog Post</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Title" value={createTitle} onChange={(e) => setCreateTitle(e.target.value)} required fullWidth />
            <TextField label="Content" value={createContent} onChange={(e) => setCreateContent(e.target.value)} fullWidth multiline minRows={4} />
            <TextField label="Tags (comma-separated)" value={createTags} onChange={(e) => setCreateTags(e.target.value)} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleCreate()}>Create Draft</Button>
        </DialogActions>
      </Dialog>

      {/* Edit post dialog */}
      <Dialog open={editOpen} onClose={() => setEditOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Edit Blog Post</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Title" value={editTitle} onChange={(e) => setEditTitle(e.target.value)} required fullWidth />
            <TextField label="Content" value={editContent} onChange={(e) => setEditContent(e.target.value)} fullWidth multiline minRows={4} />
            <TextField label="Tags (comma-separated)" value={editTags} onChange={(e) => setEditTags(e.target.value)} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleEdit()}>Save</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default BlogPage;
