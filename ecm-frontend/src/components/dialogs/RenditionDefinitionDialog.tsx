import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import AutorenewIcon from '@mui/icons-material/Autorenew';
import RefreshIcon from '@mui/icons-material/Refresh';
import nodeService, { NodeRenditionDefinitionStatus } from 'services/nodeService';
import { toast } from 'react-toastify';
import {
  applyRenditionMutationToDefinitions,
  formatRenditionMutationSummary,
  formatRenditionDefinitionLine,
  getRenditionDefinitionDisplayState,
} from 'utils/renditionDefinitionUtils';

interface RenditionDefinitionDialogProps {
  open: boolean;
  nodeId: string | null;
  nodeName?: string;
  onClose: () => void;
}

const stateChipColor = (
  state: string
): 'default' | 'success' | 'warning' | 'error' | 'info' | 'secondary' => {
  switch (state) {
    case 'ready':
      return 'success';
    case 'processing':
    case 'pending':
      return 'warning';
    case 'failed':
      return 'error';
    case 'stale':
      return 'secondary';
    case 'unsupported':
    case 'not applicable':
      return 'default';
    default:
      return 'info';
  }
};

const RenditionDefinitionDialog: React.FC<RenditionDefinitionDialogProps> = ({
  open,
  nodeId,
  nodeName,
  onClose,
}) => {
  const [definitions, setDefinitions] = useState<NodeRenditionDefinitionStatus[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionKey, setActionKey] = useState<string | null>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);
  const [refreshNonce, setRefreshNonce] = useState(0);

  useEffect(() => {
    setActionFeedback(null);
  }, [open, nodeId]);

  useEffect(() => {
    if (!open || !nodeId) {
      setDefinitions([]);
      setError(null);
      setLoading(false);
      setActionKey(null);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    nodeService.getNodeRenditionDefinitions(nodeId)
      .then((result) => {
        if (active) {
          setDefinitions(result || []);
        }
      })
      .catch(() => {
        if (active) {
          setError('Failed to load rendition registry');
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [open, nodeId, refreshNonce]);

  const sortedDefinitions = useMemo(
    () => [...definitions].sort((left, right) => {
      if (left.sortOrder !== right.sortOrder) {
        return left.sortOrder - right.sortOrder;
      }
      return left.label.localeCompare(right.label);
    }),
    [definitions]
  );

  const handleOpenContent = (definition: NodeRenditionDefinitionStatus) => {
    if (!definition.contentUrl) {
      return;
    }
    window.open(definition.contentUrl, '_blank', 'noopener,noreferrer');
  };

  const getActionErrorMessage = (fallback: string, errorValue: unknown) => {
    const responseMessage = (errorValue as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return responseMessage;
    }
    const message = (errorValue as { message?: unknown })?.message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
    return fallback;
  };

  const handleRequeue = async (definition: NodeRenditionDefinitionStatus) => {
    if (!nodeId) {
      return;
    }
    setActionKey(`requeue:${definition.renditionKey}`);
    try {
      const result = await nodeService.requeueNodeRendition(nodeId, definition.renditionKey);
      setDefinitions((current) => applyRenditionMutationToDefinitions(current, result));
      setActionFeedback(formatRenditionMutationSummary(result) || `${definition.label} requeued`);
      toast.success(result.message || `${definition.label} requeued`);
      setRefreshNonce((current) => current + 1);
    } catch (errorValue) {
      toast.error(getActionErrorMessage(`Failed to requeue ${definition.label}`, errorValue));
    } finally {
      setActionKey(null);
    }
  };

  const handleInvalidateAndRequeue = async (definition: NodeRenditionDefinitionStatus) => {
    if (!nodeId) {
      return;
    }
    setActionKey(`invalidate:${definition.renditionKey}`);
    try {
      const result = await nodeService.invalidateNodeRendition(nodeId, definition.renditionKey, {
        requeue: true,
        forceQueue: true,
      });
      setDefinitions((current) => applyRenditionMutationToDefinitions(current, result));
      setActionFeedback(
        formatRenditionMutationSummary(result) || `${definition.label} invalidated and requeued`
      );
      toast.success(result.message || `${definition.label} invalidated and requeued`);
      setRefreshNonce((current) => current + 1);
    } catch (errorValue) {
      toast.error(getActionErrorMessage(`Failed to invalidate ${definition.label}`, errorValue));
    } finally {
      setActionKey(null);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        Rendition Registry{nodeName ? `: ${nodeName}` : ''}
      </DialogTitle>
      <DialogContent>
        {loading ? (
          <Box display="flex" justifyContent="center" py={4}>
            <CircularProgress />
          </Box>
        ) : error ? (
          <Alert severity="error">{error}</Alert>
        ) : sortedDefinitions.length > 0 ? (
          <Stack spacing={2}>
            <Alert severity="info">
              {sortedDefinitions.length} rendition definition{sortedDefinitions.length === 1 ? '' : 's'} registered for this node.
            </Alert>
            {actionFeedback && (
              <Alert severity="success">{actionFeedback}</Alert>
            )}
            {sortedDefinitions.map((definition, index) => {
              const displayState = getRenditionDefinitionDisplayState(definition);
              return (
                <React.Fragment key={`${definition.renditionKey}-${definition.label}`}>
                  {index > 0 && <Divider />}
                  <Stack spacing={1.25}>
                    <Box display="flex" justifyContent="space-between" gap={2} alignItems="flex-start">
                      <Box>
                        <Typography variant="subtitle2">{definition.label}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {formatRenditionDefinitionLine(definition)}
                        </Typography>
                      </Box>
                      <Chip
                        size="small"
                        variant="outlined"
                        color={stateChipColor(displayState)}
                        label={displayState}
                      />
                    </Box>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Chip size="small" variant="outlined" label={definition.renditionKey} />
                      <Chip size="small" variant="outlined" label={definition.targetMimeType || 'unknown target mime'} />
                      {definition.generationMode && (
                        <Chip size="small" variant="outlined" label={definition.generationMode} />
                      )}
                      {definition.dependencyRenditionKey && (
                        <Chip size="small" variant="outlined" label={`depends on ${definition.dependencyRenditionKey}`} />
                      )}
                    </Stack>
                    {!definition.applicable && definition.applicabilityReason && (
                      <Typography variant="body2" color="text.secondary">
                        {definition.applicabilityReason}
                      </Typography>
                    )}
                    {definition.mutationBlockedReason && (
                      <Typography variant="body2" color="text.secondary">
                        {definition.mutationBlockedReason}
                      </Typography>
                    )}
                    {definition.available && definition.contentUrl && (
                      <Box display="flex" gap={1} flexWrap="wrap">
                        <Button
                          size="small"
                          startIcon={<OpenInNewIcon fontSize="small" />}
                          onClick={() => handleOpenContent(definition)}
                        >
                          Open Rendition
                        </Button>
                      </Box>
                    )}
                    {(definition.canRequeue || definition.canInvalidate) && (
                      <Box display="flex" gap={1} flexWrap="wrap">
                        {definition.canRequeue && (
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<AutorenewIcon fontSize="small" />}
                            disabled={Boolean(actionKey)}
                            onClick={() => void handleRequeue(definition)}
                          >
                            {actionKey === `requeue:${definition.renditionKey}` ? 'Requeueing...' : 'Requeue'}
                          </Button>
                        )}
                        {definition.canInvalidate && (
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<RefreshIcon fontSize="small" />}
                            disabled={Boolean(actionKey)}
                            onClick={() => void handleInvalidateAndRequeue(definition)}
                          >
                            {actionKey === `invalidate:${definition.renditionKey}`
                              ? 'Invalidating...'
                              : 'Invalidate + Requeue'}
                          </Button>
                        )}
                      </Box>
                    )}
                  </Stack>
                </React.Fragment>
              );
            })}
          </Stack>
        ) : (
          <Alert severity="info">No rendition definitions available.</Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default RenditionDefinitionDialog;
