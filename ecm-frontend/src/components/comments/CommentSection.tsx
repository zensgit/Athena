import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Avatar,
  Badge,
  Box,
  Button,
  ButtonBase,
  Chip,
  CircularProgress,
  Collapse,
  IconButton,
  Menu,
  MenuItem,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import {
  Close,
  Delete,
  Edit,
  ExpandLess,
  ExpandMore,
  Favorite,
  MoreVert,
  Reply,
  Search,
  Send,
  ThumbUp,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { toast } from 'react-toastify';
import { User } from 'types';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from 'store';
import commentService from 'services/commentService';
import peopleService from 'services/peopleService';

interface Comment {
  id: string;
  content: string;
  author: string;
  created: string;
  edited?: string | null;
  editor?: string | null;
  level: number;
  reactions: Array<{ type: string; user: string; date: string }>;
  mentionedUsers: string[];
  replies?: Comment[];
}

interface CommentSectionProps {
  nodeId: string;
  initialDraftText?: string | null;
  draftVersion?: number;
  initialCommentId?: string | null;
}

const mentionPattern = /@([A-Za-z0-9._-]+)/g;

const extractMentionQuery = (value: string) => {
  const match = value.match(/(?:^|\s)@([A-Za-z0-9._-]{1,50})$/);
  return match?.[1] || '';
};

const replaceTrailingMention = (content: string, username: string) => (
  content.replace(/@([A-Za-z0-9._-]{1,50})$/, `@${username} `)
);

const findCommentPath = (commentId: string, tree: Comment[]): string[] | null => {
  for (const comment of tree) {
    if (comment.id === commentId) {
      return [comment.id];
    }
    const childPath = findCommentPath(commentId, comment.replies || []);
    if (childPath) {
      return [comment.id, ...childPath];
    }
  }
  return null;
};

const CommentSection: React.FC<CommentSectionProps> = ({
  nodeId,
  initialDraftText,
  draftVersion,
  initialCommentId,
}) => {
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [replyTo, setReplyTo] = useState<{ id: string; author: string } | null>(null);
  const [editingComment, setEditingComment] = useState<{ id: string; content: string } | null>(null);
  const [expandedComments, setExpandedComments] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<Comment[] | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [mentionSuggestions, setMentionSuggestions] = useState<User[]>([]);
  const [mentionLoading, setMentionLoading] = useState(false);

  const loadComments = useCallback(async () => {
    setLoading(true);
    try {
      const commentTree = await commentService.getCommentTree(nodeId);
      setComments(commentTree);
    } catch {
      toast.error('Failed to load comments');
    } finally {
      setLoading(false);
    }
  }, [nodeId]);

  useEffect(() => {
    void loadComments();
  }, [loadComments]);

  useEffect(() => {
    if (initialDraftText === undefined) {
      return;
    }
    const nextDraft = initialDraftText || '';
    if (nextDraft) {
      setNewComment(nextDraft);
    }
  }, [initialDraftText, draftVersion]);

  useEffect(() => {
    if (!initialCommentId || comments.length === 0) {
      return;
    }
    const path = findCommentPath(initialCommentId, comments);
    if (!path) {
      return;
    }
    if (path.length > 1) {
      setExpandedComments((current) => {
        const next = new Set(current);
        path.slice(0, -1).forEach((commentId) => next.add(commentId));
        return next;
      });
    }
    const timer = window.setTimeout(() => {
      const target = document.getElementById(`comment-thread-${initialCommentId}`);
      target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 120);
    return () => {
      window.clearTimeout(timer);
    };
  }, [comments, initialCommentId]);

  useEffect(() => {
    const trimmedQuery = searchQuery.trim();
    if (!trimmedQuery) {
      setSearchResults(null);
      setSearchError(null);
      setSearching(false);
      return;
    }

    let cancelled = false;
    setSearching(true);
    setSearchError(null);

    const timer = window.setTimeout(() => {
      commentService.searchComments(nodeId, trimmedQuery)
        .then((matches) => {
          if (!cancelled) {
            setSearchResults(matches || []);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setSearchResults([]);
            setSearchError('Failed to search comments');
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearching(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [nodeId, searchQuery]);

  useEffect(() => {
    const mentionQuery = extractMentionQuery(newComment);
    if (!mentionQuery) {
      setMentionSuggestions([]);
      setMentionLoading(false);
      return;
    }

    let cancelled = false;
    setMentionLoading(true);

    const timer = window.setTimeout(() => {
      peopleService.search(mentionQuery, 0, 6)
        .then((page) => {
          if (!cancelled) {
            setMentionSuggestions(page.content || []);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setMentionSuggestions([]);
          }
        })
        .finally(() => {
          if (!cancelled) {
            setMentionLoading(false);
          }
        });
    }, 200);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [newComment]);

  const handleSubmitComment = async () => {
    if (!newComment.trim()) {
      return;
    }

    setSubmitting(true);
    try {
      await commentService.addComment(nodeId, newComment, replyTo?.id);
      toast.success('Comment added');
      setNewComment('');
      setReplyTo(null);
      setMentionSuggestions([]);
      await loadComments();
    } catch {
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
    } catch {
      toast.error('Failed to update comment');
    }
  };

  const handleDeleteComment = async (commentId: string) => {
    if (!window.confirm('Delete this comment?')) {
      return;
    }

    try {
      await commentService.deleteComment(commentId);
      toast.success('Comment deleted');
      await loadComments();
    } catch {
      toast.error('Failed to delete comment');
    }
  };

  const handleReaction = async (commentId: string, reactionType: string) => {
    try {
      const comment = findComment(commentId, comments);
      if (!comment) {
        return;
      }

      const existingReaction = comment.reactions.find((reaction) => reaction.user === user?.username);
      if (existingReaction?.type === reactionType) {
        await commentService.removeReaction(commentId);
      } else {
        await commentService.addReaction(commentId, reactionType);
      }

      await loadComments();
    } catch {
      toast.error('Failed to update reaction');
    }
  };

  const findComment = (id: string, commentList: Comment[]): Comment | null => {
    for (const comment of commentList) {
      if (comment.id === id) {
        return comment;
      }
      if (comment.replies) {
        const found = findComment(id, comment.replies);
        if (found) {
          return found;
        }
      }
    }
    return null;
  };

  const toggleExpanded = (commentId: string) => {
    setExpandedComments((current) => {
      const next = new Set(current);
      if (next.has(commentId)) {
        next.delete(commentId);
      } else {
        next.add(commentId);
      }
      return next;
    });
  };

  const getReactionCounts = (reactions: Array<{ type: string; user: string }>) => {
    const counts: Record<string, number> = {};
    reactions.forEach((reaction) => {
      counts[reaction.type] = (counts[reaction.type] || 0) + 1;
    });
    return counts;
  };

  const renderCommentContent = (content: string) => {
    const parts = content.split(mentionPattern);
    if (parts.length === 1) {
      return content;
    }

    return parts.map((part, index) => {
      if (index % 2 === 1) {
        return (
          <Box
            component="span"
            key={`mention-${part}-${index}`}
            sx={{ color: 'primary.main', fontWeight: 700 }}
          >
            @{part}
          </Box>
        );
      }
      return <React.Fragment key={`text-${index}`}>{part}</React.Fragment>;
    });
  };

  const applyMentionSuggestion = (username: string) => {
    setNewComment((current) => replaceTrailingMention(current, username));
    setMentionSuggestions([]);
  };

  const openPeopleDirectoryProfile = (username?: string) => {
    if (!username) {
      return;
    }
    navigate(`/people-directory?username=${encodeURIComponent(username)}`);
  };

  const displayedComments = useMemo(() => {
    if (searchQuery.trim()) {
      return searchResults || [];
    }
    return comments;
  }, [comments, searchQuery, searchResults]);

  const renderComment = (comment: Comment, depth = 0) => {
    const isAuthor = comment.author === user?.username;
    const isEditing = editingComment?.id === comment.id;
    const hasReplies = (comment.replies || []).length > 0;
    const isExpanded = expandedComments.has(comment.id);
    const reactionCounts = getReactionCounts(comment.reactions);
    const userReaction = comment.reactions.find((reaction) => reaction.user === user?.username);

    return (
      <Box key={comment.id} id={`comment-thread-${comment.id}`} sx={{ ml: Math.min(depth * 4, 16) }}>
        <Paper
          sx={{
            p: 2,
            mb: 1,
            bgcolor: depth > 0 ? 'background.default' : 'background.paper',
            border: comment.id === initialCommentId ? '1px solid' : undefined,
            borderColor: comment.id === initialCommentId ? 'primary.main' : undefined,
            boxShadow: comment.id === initialCommentId ? 2 : undefined,
          }}
        >
          <Box display="flex" justifyContent="space-between" alignItems="flex-start">
            <Box display="flex" gap={2} flex={1}>
              <ButtonBase
                onClick={() => openPeopleDirectoryProfile(comment.author)}
                sx={{
                  borderRadius: '50%',
                  alignSelf: 'flex-start',
                }}
                aria-label={`Open profile for ${comment.author}`}
              >
                <Avatar sx={{ width: 32, height: 32 }}>
                  {comment.author.charAt(0).toUpperCase()}
                </Avatar>
              </ButtonBase>
              <Box flex={1}>
                <Box display="flex" alignItems="center" gap={1} mb={1} flexWrap="wrap">
                  <ButtonBase
                    onClick={() => openPeopleDirectoryProfile(comment.author)}
                    sx={{
                      borderRadius: 1,
                      px: 0.5,
                      py: 0.25,
                      mr: -0.5,
                      color: 'primary.main',
                      textAlign: 'left',
                    }}
                    aria-label={`Open profile for ${comment.author}`}
                  >
                    <Typography variant="subtitle2" sx={{ color: 'inherit', fontWeight: 600 }}>
                      {comment.author}
                    </Typography>
                  </ButtonBase>
                  <Typography variant="caption" color="text.secondary">
                    {format(new Date(comment.created), 'PPp')}
                  </Typography>
                  {comment.edited && (
                    <Chip label="edited" size="small" variant="outlined" />
                  )}
                  {(comment.mentionedUsers || []).map((mentionedUser) => (
                    <Chip
                      key={`${comment.id}-${mentionedUser}`}
                      size="small"
                      variant="outlined"
                      label={`@${mentionedUser}`}
                    />
                  ))}
                </Box>

                {isEditing ? (
                  <Box>
                    <TextField
                      fullWidth
                      multiline
                      rows={3}
                      value={editingComment.content}
                      onChange={(event) => setEditingComment({ ...editingComment, content: event.target.value })}
                      variant="outlined"
                      size="small"
                    />
                    <Box display="flex" gap={1} mt={1}>
                      <Button
                        size="small"
                        variant="contained"
                        onClick={() => handleEditComment(comment.id, editingComment.content)}
                      >
                        Save
                      </Button>
                      <Button size="small" onClick={() => setEditingComment(null)}>
                        Cancel
                      </Button>
                    </Box>
                  </Box>
                ) : (
                  <Typography variant="body2">
                    {renderCommentContent(comment.content)}
                  </Typography>
                )}

                <Box display="flex" alignItems="center" gap={1} mt={1} flexWrap="wrap">
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
                    onClick={() => {
                      setReplyTo({ id: comment.id, author: comment.author });
                      setNewComment((current) => (current ? current : `@${comment.author} `));
                    }}
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
                onEdit={() => setEditingComment({ id: comment.id, content: comment.content })}
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

  const CommentMenu: React.FC<{ onEdit: () => void; onDelete: () => void }> = ({ onEdit, onDelete }) => {
    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

    return (
      <>
        <IconButton size="small" onClick={(event) => setAnchorEl(event.currentTarget)}>
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
      <Box
        display="flex"
        justifyContent="space-between"
        alignItems={{ xs: 'flex-start', md: 'center' }}
        gap={1}
        flexDirection={{ xs: 'column', md: 'row' }}
        mb={1}
      >
        <Typography variant="h6">
          Comments ({comments.length})
        </Typography>
        <TextField
          size="small"
          label="Search comments"
          value={searchQuery}
          onChange={(event) => setSearchQuery(event.target.value)}
          placeholder="Search content, author, mentions"
          InputProps={{ startAdornment: <Search fontSize="small" sx={{ mr: 1, color: 'text.secondary' }} /> }}
          sx={{ minWidth: { xs: '100%', md: 260 } }}
        />
      </Box>

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
          onChange={(event) => setNewComment(event.target.value)}
          variant="outlined"
        />
        {(mentionLoading || mentionSuggestions.length > 0) && (
          <Box display="flex" gap={1} flexWrap="wrap" mt={1} alignItems="center">
            <Typography variant="caption" color="text.secondary">
              Mention suggestions
            </Typography>
            {mentionLoading && <CircularProgress size={14} />}
            {mentionSuggestions.map((person) => (
              <Chip
                key={person.username}
                size="small"
                clickable
                onClick={() => applyMentionSuggestion(person.username)}
                label={person.firstName || person.lastName
                  ? `${person.username} · ${[person.firstName, person.lastName].filter(Boolean).join(' ')}`
                  : person.username}
              />
            ))}
          </Box>
        )}
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

      {searchQuery.trim() && (
        <Box display="flex" gap={1} flexWrap="wrap" alignItems="center" mb={2}>
          <Chip size="small" variant="outlined" label={`Search: ${searchQuery.trim()}`} />
          {searching && <CircularProgress size={14} />}
          {searchResults && !searching && (
            <Chip size="small" variant="outlined" label={`Matches ${searchResults.length}`} />
          )}
          {searchError && (
            <Typography variant="caption" color="error">
              {searchError}
            </Typography>
          )}
        </Box>
      )}

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}>
          <CircularProgress />
        </Box>
      ) : displayedComments.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">
            {searchQuery.trim()
              ? 'No comments matched the current search.'
              : 'No comments yet. Be the first to comment!'}
          </Typography>
        </Paper>
      ) : (
        <Box>{displayedComments.map((comment) => renderComment(comment, comment.level || 0))}</Box>
      )}
    </Box>
  );
};

export default CommentSection;
