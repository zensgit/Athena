import React, { useEffect, useState } from 'react';
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
import { useNavigate } from 'react-router-dom';
import nodeService, { NodeCheckoutGraph } from 'services/nodeService';
import {
  formatCheckoutGraphEdgeLabel,
  formatCheckoutGraphNodeLabel,
  formatCheckoutGraphSummary,
  getCheckoutGraphNodes,
} from 'utils/checkoutGraphUtils';

interface CheckoutGraphDialogProps {
  open: boolean;
  nodeId: string | null;
  nodeName?: string;
  onClose: () => void;
}

const CheckoutGraphDialog: React.FC<CheckoutGraphDialogProps> = ({ open, nodeId, nodeName, onClose }) => {
  const navigate = useNavigate();
  const [graph, setGraph] = useState<NodeCheckoutGraph | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !nodeId) {
      setGraph(null);
      setError(null);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(null);

    nodeService.getNodeRelationCheckoutGraph(nodeId)
      .then((result) => {
        if (!active) {
          return;
        }
        setGraph(result);
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setError('Failed to load checkout graph');
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [open, nodeId]);

  const handleOpenDestination = () => {
    const destinationId = graph?.destinationNode?.id;
    if (!destinationId) {
      return;
    }
    navigate(`/browse/${destinationId}`);
    onClose();
  };

  const graphNodes = graph ? getCheckoutGraphNodes(graph) : [];

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        Checkout Graph{nodeName ? `: ${nodeName}` : ''}
      </DialogTitle>
      <DialogContent>
        {loading ? (
          <Box display="flex" justifyContent="center" py={4}>
            <CircularProgress />
          </Box>
        ) : error ? (
          <Alert severity="error">{error}</Alert>
        ) : graph ? (
          <Stack spacing={2}>
            <Alert severity={graph.checkedOut ? 'info' : 'success'}>
              {formatCheckoutGraphSummary(graph)}
            </Alert>
            {graph.blockingReason && (
              <Alert severity="warning">{graph.blockingReason}</Alert>
            )}
            {graphNodes.length > 0 && (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {graphNodes.map((node) => (
                  <Chip
                    key={`${node.kind}-${node.id}`}
                    size="small"
                    variant="outlined"
                    color={node.kind === 'WORKING_COPY' ? 'info' : node.kind === 'DESTINATION_FOLDER' ? 'secondary' : 'default'}
                    label={formatCheckoutGraphNodeLabel(node)}
                  />
                ))}
              </Stack>
            )}
            {graph.edges.length > 0 && (
              <>
                <Divider />
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Graph edges
                  </Typography>
                  <Stack spacing={0.75}>
                    {graph.edges.map((edge) => (
                      <Typography key={`${edge.relationType}-${edge.sourceId}-${edge.targetId}`} variant="body2" color="text.secondary">
                        {formatCheckoutGraphEdgeLabel(edge)}
                      </Typography>
                    ))}
                  </Stack>
                </Box>
              </>
            )}
          </Stack>
        ) : (
          <Alert severity="info">Checkout graph unavailable.</Alert>
        )}
      </DialogContent>
      <DialogActions>
        {graph?.destinationNode?.id && (
          <Button onClick={handleOpenDestination}>Open Check-In Target</Button>
        )}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default CheckoutGraphDialog;
