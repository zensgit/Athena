import React, { useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  TextField,
  Typography,
  Tabs,
  Tab,
  CircularProgress,
  Chip,
  Tooltip,
} from '@mui/material';
import { Close, Add, Delete, OpenInNew } from '@mui/icons-material';
import nodeService, { NodeRelationEdge } from 'services/nodeService';
import { toast } from 'react-toastify';
import { useAppSelector } from 'store';
import authService from 'services/authService';

interface AssociationManagerProps {
  open: boolean;
  onClose: () => void;
  selectedNodeId?: string;
}

const AssociationManager: React.FC<AssociationManagerProps> = ({ open, onClose, selectedNodeId }) => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );

  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(false);

  // peer associations
  const [targets, setTargets] = useState<NodeRelationEdge[]>([]);
  const [sources, setSources] = useState<NodeRelationEdge[]>([]);
  const [addTargetId, setAddTargetId] = useState('');
  const [addTargetAssocType, setAddTargetAssocType] = useState('cm:references');

  // secondary children
  const [secChildren, setSecChildren] = useState<NodeRelationEdge[]>([]);
  const [secParents, setSecParents] = useState<NodeRelationEdge[]>([]);
  const [addChildId, setAddChildId] = useState('');

  const loadAll = async () => {
    if (!selectedNodeId) return;
    setLoading(true);
    try {
      const [t, s, sc, sp] = await Promise.all([
        nodeService.getTargetAssociations(selectedNodeId),
        nodeService.getSourceAssociations(selectedNodeId),
        nodeService.getSecondaryChildren(selectedNodeId),
        nodeService.getSecondaryParents(selectedNodeId),
      ]);
      setTargets(t);
      setSources(s);
      setSecChildren(sc);
      setSecParents(sp);
    } catch {
      toast.error('Failed to load associations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && selectedNodeId) {
      void loadAll();
      setTab(0);
      setAddTargetId('');
      setAddChildId('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, selectedNodeId]);

  const handleCreateTarget = async () => {
    if (!selectedNodeId || !addTargetId.trim()) { toast.warn('Target node ID required'); return; }
    try {
      await nodeService.createTargetAssociation(selectedNodeId, addTargetId.trim(), addTargetAssocType);
      toast.success('Peer association created');
      setAddTargetId('');
      await loadAll();
    } catch { toast.error('Failed to create association'); }
  };

  const handleRemoveTarget = async (targetId: string) => {
    if (!selectedNodeId) return;
    try {
      await nodeService.removeTargetAssociation(selectedNodeId, targetId);
      toast.success('Association removed');
      await loadAll();
    } catch { toast.error('Failed to remove association'); }
  };

  const handleAddSecChild = async () => {
    if (!selectedNodeId || !addChildId.trim()) { toast.warn('Child node ID required'); return; }
    try {
      await nodeService.addSecondaryChild(selectedNodeId, addChildId.trim());
      toast.success('Secondary child added');
      setAddChildId('');
      await loadAll();
    } catch { toast.error('Failed to add secondary child'); }
  };

  const handleRemoveSecChild = async (childId: string) => {
    if (!selectedNodeId) return;
    try {
      await nodeService.removeSecondaryChild(selectedNodeId, childId);
      toast.success('Secondary child removed');
      await loadAll();
    } catch { toast.error('Failed to remove secondary child'); }
  };

  const navigateToNode = (nodeId: string) => {
    window.open(`/browse/${nodeId}`, '_blank');
  };

  const renderEdgeTable = (
    edges: NodeRelationEdge[],
    emptyMessage: string,
    showDelete: boolean,
    onDelete?: (id: string) => void,
    nodeIdField: 'source' | 'target' = 'target'
  ) => (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Path</TableCell>
            <TableCell align="right" width={100}>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {edges.map((edge) => {
            const node = nodeIdField === 'target' ? edge.target : edge.source;
            return (
              <TableRow key={edge.relationId} hover>
                <TableCell>{node.name}</TableCell>
                <TableCell><Chip label={edge.relationType} size="small" variant="outlined" /></TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.secondary" noWrap sx={{ maxWidth: 200, display: 'block' }}>
                    {node.path}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Open in browser">
                    <IconButton size="small" onClick={() => navigateToNode(node.id)}>
                      <OpenInNew sx={{ fontSize: 14 }} />
                    </IconButton>
                  </Tooltip>
                  {showDelete && canWrite && onDelete && (
                    <Tooltip title="Remove">
                      <IconButton size="small" color="error" onClick={() => void onDelete(node.id)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
          {edges.length === 0 && (
            <TableRow><TableCell colSpan={4} align="center">{emptyMessage}</TableCell></TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Associations
        <IconButton aria-label="close" onClick={onClose} sx={{ position: 'absolute', right: 8, top: 8 }}>
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label={`Targets (${targets.length})`} />
          <Tab label={`Sources (${sources.length})`} />
          <Tab label={`Sec. Children (${secChildren.length})`} />
          <Tab label={`Sec. Parents (${secParents.length})`} />
        </Tabs>

        {loading ? (
          <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
        ) : (
          <>
            {/* Peer targets */}
            {tab === 0 && (
              <>
                {canWrite && (
                  <Box display="flex" gap={1} mb={2}>
                    <TextField size="small" label="Target Node ID" value={addTargetId} onChange={(e) => setAddTargetId(e.target.value)} sx={{ flex: 1 }} />
                    <TextField size="small" label="Assoc Type" value={addTargetAssocType} onChange={(e) => setAddTargetAssocType(e.target.value)} sx={{ width: 180 }} />
                    <Button variant="contained" startIcon={<Add />} onClick={() => void handleCreateTarget()} disabled={!addTargetId.trim()}>Add</Button>
                  </Box>
                )}
                {renderEdgeTable(targets, 'No target associations', true, handleRemoveTarget, 'target')}
              </>
            )}

            {/* Peer sources (read-only — can't delete incoming) */}
            {tab === 1 && renderEdgeTable(sources, 'No source associations', false, undefined, 'source')}

            {/* Secondary children */}
            {tab === 2 && (
              <>
                {canWrite && (
                  <Box display="flex" gap={1} mb={2}>
                    <TextField size="small" label="Child Node ID" value={addChildId} onChange={(e) => setAddChildId(e.target.value)} sx={{ flex: 1 }} />
                    <Button variant="contained" startIcon={<Add />} onClick={() => void handleAddSecChild()} disabled={!addChildId.trim()}>Add</Button>
                  </Box>
                )}
                {renderEdgeTable(secChildren, 'No secondary children', true, handleRemoveSecChild, 'target')}
              </>
            )}

            {/* Secondary parents (read-only) */}
            {tab === 3 && renderEdgeTable(secParents, 'No secondary parents', false, undefined, 'source')}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default AssociationManager;
