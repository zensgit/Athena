import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Avatar,
  IconButton,
  Menu,
  MenuItem,
  Chip,
  CircularProgress,
  Collapse,
  Badge,
} from '@mui/material';
import {
  Send,
  MoreVert,
  Edit,
  Delete,
  Reply,
  ThumbUp,
  Favorite,
  ExpandMore,
  ExpandLess,
  Close,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { useAppSelector } from 'store';
import commentService from 'services/commentService';
import { toast } from 'react-toastify';

interface Comment {
  id: string;
  content: string;
  author: string;
  created: string;
  edited?: string;
  editor?: string;
  level: number;
  reactions: Array<{ type: string; user: string; date: string }>;
  mentionedUsers: string[];
  replies?: Comment[];
}

interface CommentSectionProps {
  nodeId: string;
}

const CommentSection: React.FC<CommentSectionProps> = ({ nodeId }) => {
  const { user } = useAppSelector((state) => state.auth);
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [replyTo, setReplyTo] = useState<{ id: string; author: string } | null>(null);
  const [editingComment, setEditingComment] = useState<{ id: string; content: string } | null>(null);
  const [expandedComments, setExpandedComments] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadComments();
  }, [loadComments]);

  const loadComments = useCallback(async () => {
    setLoading(true);
    try {
      const commentTree = await commentService.getCommentTree(nodeId);
      setComments(commentTree);
    } catch (error) {
      toast.error('Failed to load comments');
    } finally {
      setLoading(false);
    }
  }, [nodeId]);

  const handleSubmitComment = async () => {
    if (!newComment.trim()) return;

    setSubmitting(true);
    try {
      await commentService.addComment(nodeId, newComment, replyTo?.id);
      toast.success('Comment added');
      setNewComment('');
      setReplyTo(null);
      await loadComments();
    } catch (error) {
      toast.error('Failed to add comment');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEditComment = async (commentId: string, newContent: string) => {
    try {
      await commentService.editComment(commentId, newContent);
      toast.success('Comment updated');
      setEditingComment(null);
      await loadComments();
    } catch (error) {
      toast.error('Failed to update comment');
    }
  };

  const handleDeleteComment = async (commentId: string) => {
    if (window.confirm('Delete this comment?')) {
      try {
        await commentService.deleteComment(commentId);
        toast.success('Comment deleted');
        await loadComments();
      } catch (error) {
        toast.error('Failed to delete comment');
      }
    }
  };

  const handleReaction = async (commentId: string, reactionType: string) => {
    try {
      const comment = findComment(commentId, comments);
      if (comment) {
        const existingReaction = comment.reactions.find((r) => r.user === user?.username);
        
        if (existingReaction && existingReaction.type === reactionType) {
          await commentService.removeReaction(commentId);
        } else {
          await commentService.addReaction(commentId, reactionType);
        }
        
        await loadComments();
      }
    } catch (error) {
      toast.error('Failed to update reaction');
    }
  };

  const findComment = (id: string, commentList: Comment[]): Comment | null => {
    for (const comment of commentList) {
      if (comment.id === id) return comment;
      if (comment.replies) {
        const found = findComment(id, comment.replies);
        if (found) return found;
      }
    }
    return null;
  };

  const toggleExpanded = (commentId: string) => {
    const newExpanded = new Set(expandedComments);
    if (newExpanded.has(commentId)) {
      newExpanded.delete(commentId);
    } else {
      newExpanded.add(commentId);
    }
    setExpandedComments(newExpanded);
  };

  const formatMentions = (content: string) => {
    return content.replace(/@(\w+)/g, '<span class="mention">@$1</span>');
  };

  const getReactionCounts = (reactions: Array<{ type: string; user: string }>) => {
    const counts: Record<string, number> = {};
    reactions.forEach((reaction) => {
      counts[reaction.type] = (counts[reaction.type] || 0) + 1;
    });
    return counts;
  };

  const renderComment = (comment: Comment, depth = 0) => {
    const isAuthor = comment.author === user?.username;
    const isEditing = editingComment?.id === comment.id;
    const hasReplies = comment.replies && comment.replies.length > 0;
    const isExpanded = expandedComments.has(comment.id);
    const reactionCounts = getReactionCounts(comment.reactions);
    const userReaction = comment.reactions.find((r) => r.user === user?.username);

    return (
      <Box key={comment.id} sx={{ ml: Math.min(depth * 4, 16) }}>
        <Paper
          sx={{
            p: 2,
            mb: 1,
            bgcolor: depth > 0 ? 'background.default' : 'background.paper',
          }}
        >
          <Box display="flex" justifyContent="space-between" alignItems="flex-start">
            <Box display="flex" gap={2} flex={1}>
              <Avatar sx={{ width: 32, height: 32 }}>
                {comment.author.charAt(0).toUpperCase()}
              </Avatar>
              <Box flex={1}>
                <Box display="flex" alignItems="center" gap={1} mb={1}>
                  <Typography variant="subtitle2">{comment.author}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {format(new Date(comment.created), 'PPp')}
                  </Typography>
                  {comment.edited && (
                    <Chip label="edited" size="small" variant="outlined" />
                  )}
                </Box>

                {isEditing ? (
                  <Box>
                    <TextField
                      fullWidth
                      multiline
                      rows={3}
                      value={editingComment.content}
                      onChange={(e) =>
                        setEditingComment({ ...editingComment, content: e.target.value })
                      }
                      variant="outlined"
                      size="small"
                    />
                    <Box display="flex" gap={1} mt={1}>
                      <Button
                        size="small"
                        variant="contained"
                        onClick={() =>
                          handleEditComment(comment.id, editingComment.content)
                        }
                      >
                        Save
                      </Button>
                      <Button size="small" onClick={() => setEditingComment(null)}>
                        Cancel
                      </Button>
                    </Box>
                  </Box>
                ) : (
                  <Typography
                    variant="body2"
                    dangerouslySetInnerHTML={{ __html: formatMentions(comment.content) }}
                    sx={{
                      '& .mention': {
                        color: 'primary.main',
                        fontWeight: 'bold',
                      },
                    }}
                  />
                )}

                <Box display="flex" alignItems="center" gap={1} mt={1}>
                  <IconButton
                    size="small"
                    onClick={() => handleReaction(comment.id, 'thumbsup')}
                    color={userReaction?.type === 'thumbsup' ? 'primary' : 'default'}
                  >
                    <Badge badgeContent={reactionCounts.thumbsup || 0} color="secondary">
                      <ThumbUp fontSize="small" />
                    </Badge>
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={() => handleReaction(comment.id, 'heart')}
                    color={userReaction?.type === 'heart' ? 'error' : 'default'}
                  >
                    <Badge badgeContent={reactionCounts.heart || 0} color="secondary">
                      <Favorite fontSize="small" />
                    </Badge>
                  </IconButton>
                  <Button
                    size="small"
                    startIcon={<Reply />}
                    onClick={() => setReplyTo({ id: comment.id, author: comment.author })}
                  >
                    Reply
                  </Button>
                  {hasReplies && (
                    <Button
                      size="small"
                      startIcon={isExpanded ? <ExpandLess /> : <ExpandMore />}
                      onClick={() => toggleExpanded(comment.id)}
                    >
                      {comment.replies!.length} {comment.replies!.length === 1 ? 'reply' : 'replies'}
                    </Button>
                  )}
                </Box>
              </Box>
            </Box>

            {isAuthor && (
              <CommentMenu
                onEdit={() =>
                  setEditingComment({ id: comment.id, content: comment.content })
                }
                onDelete={() => handleDeleteComment(comment.id)}
              />
            )}
          </Box>
        </Paper>

        {hasReplies && (
          <Collapse in={isExpanded}>
            {comment.replies!.map((reply) => renderComment(reply, depth + 1))}
          </Collapse>
        )}
      </Box>
    );
  };

  const CommentMenu: React.FC<{ onEdit: () => void; onDelete: () => void }> = ({
    onEdit,
    onDelete,
  }) => {
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    return (
      <>
        <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)}>
          <MoreVert fontSize="small" />
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={() => setAnchorEl(null)}
        >
          <MenuItem
            onClick={() => {
              onEdit();
              setAnchorEl(null);
            }}
          >
            <Edit fontSize="small" sx={{ mr: 1 }} /> Edit
          </MenuItem>
          <MenuItem
            onClick={() => {
              onDelete();
              setAnchorEl(null);
            }}
          >
            <Delete fontSize="small" sx={{ mr: 1 }} /> Delete
          </MenuItem>
        </Menu>
      </>
    );
  };

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Comments ({comments.length})
      </Typography>

      <Paper sx={{ p: 2, mb: 2 }}>
        {replyTo && (
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
            <Typography variant="caption" color="text.secondary">
              Replying to @{replyTo.author}
            </Typography>
            <IconButton size="small" onClick={() => setReplyTo(null)}>
              <Close fontSize="small" />
            </IconButton>
          </Box>
        )}
        <TextField
          fullWidth
          multiline
          rows={3}
          placeholder="Add a comment... Use @username to mention someone"
          value={newComment}
          onChange={(e) => setNewComment(e.target.value)}
          variant="outlined"
        />
        <Box display="flex" justifyContent="flex-end" mt={1}>
          <Button
            variant="contained"
            endIcon={<Send />}
            onClick={handleSubmitComment}
            disabled={!newComment.trim() || submitting}
          >
            {submitting ? <CircularProgress size={20} /> : 'Comment'}
          </Button>
        </Box>
      </Paper>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}>
          <CircularProgress />
        </Box>
      ) : comments.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">
            No comments yet. Be the first to comment!
          </Typography>
        </Paper>
      ) : (
        <Box>{comments.map((comment) => renderComment(comment))}</Box>
      )}
    </Box>
  );
};

export default CommentSection;
