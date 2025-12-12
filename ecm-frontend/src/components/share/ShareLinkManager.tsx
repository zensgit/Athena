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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Typography,
} from '@mui/material';
import { Close, Add, ContentCopy, Delete, Block } from '@mui/icons-material';
import shareLinkService, { ShareLink, SharePermission } from 'services/shareLinkService';
import { toast } from 'react-toastify';

interface ShareLinkManagerProps {
  open: boolean;
  onClose: () => void;
  selectedNodeId?: string;
}

const ShareLinkManager: React.FC<ShareLinkManagerProps> = ({ open, onClose, selectedNodeId }) => {
  const [links, setLinks] = useState<ShareLink[]>([]);
  const [loading, setLoading] = useState(false);
  const [createMode, setCreateMode] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    permissionLevel: 'VIEW' as SharePermission,
    expiryDate: '',
    maxAccessCount: '',
    password: '',
    allowedIps: '',
  });

  const loadLinks = async () => {
    if (!selectedNodeId) return;
    setLoading(true);
    try {
      const res = await shareLinkService.getLinksForNode(selectedNodeId);
      setLinks(res);
    } catch {
      toast.error('Failed to load share links');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      loadLinks();
      setCreateMode(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, selectedNodeId]);

  const resetForm = () => {
    setFormData({
      name: '',
      permissionLevel: 'VIEW',
      expiryDate: '',
      maxAccessCount: '',
      password: '',
      allowedIps: '',
    });
  };

  const handleCreate = async () => {
    if (!selectedNodeId) return;
    try {
      const payload = {
        name: formData.name || undefined,
        permissionLevel: formData.permissionLevel,
        expiryDate: formData.expiryDate ? new Date(formData.expiryDate).toISOString() : null,
        maxAccessCount: formData.maxAccessCount ? Number(formData.maxAccessCount) : null,
        password: formData.password || null,
        allowedIps: formData.allowedIps || null,
      };
      await shareLinkService.createLink(selectedNodeId, payload);
      toast.success('Share link created');
      resetForm();
      setCreateMode(false);
      loadLinks();
    } catch {
      toast.error('Failed to create share link');
    }
  };

  const handleCopy = async (token: string) => {
    const url = `${window.location.origin}/api/v1/share/access/${token}`;
    try {
      await navigator.clipboard.writeText(url);
      toast.success('Link copied');
    } catch {
      toast.error('Failed to copy link');
    }
  };

  const handleDeactivate = async (token: string) => {
    try {
      await shareLinkService.deactivateLink(token);
      toast.success('Share link deactivated');
      loadLinks();
    } catch {
      toast.error('Failed to deactivate link');
    }
  };

  const handleDelete = async (token: string) => {
    if (!window.confirm('Delete this share link permanently?')) return;
    try {
      await shareLinkService.deleteLink(token);
      toast.success('Share link deleted');
      loadLinks();
    } catch {
      toast.error('Failed to delete link');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Share Links
        <IconButton
          aria-label="close"
          onClick={onClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box display="flex" gap={2} mb={2}>
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => setCreateMode((v) => !v)}
            disabled={!selectedNodeId}
          >
            New Share Link
          </Button>
        </Box>

        {createMode && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>
              Create Share Link
            </Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <TextField
                label="Name"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                size="small"
              />
              <FormControl size="small">
                <InputLabel>Permission</InputLabel>
                <Select
                  label="Permission"
                  value={formData.permissionLevel}
                  onChange={(e) =>
                    setFormData({ ...formData, permissionLevel: e.target.value as SharePermission })
                  }
                >
                  <MenuItem value="VIEW">View</MenuItem>
                  <MenuItem value="COMMENT">Comment</MenuItem>
                  <MenuItem value="EDIT">Edit</MenuItem>
                </Select>
              </FormControl>
              <TextField
                label="Expiry Date"
                type="datetime-local"
                value={formData.expiryDate}
                onChange={(e) => setFormData({ ...formData, expiryDate: e.target.value })}
                size="small"
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="Max Access Count"
                type="number"
                value={formData.maxAccessCount}
                onChange={(e) => setFormData({ ...formData, maxAccessCount: e.target.value })}
                size="small"
              />
              <TextField
                label="Password (optional)"
                type="password"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                size="small"
              />
              <TextField
                label="Allowed IPs (comma-separated)"
                value={formData.allowedIps}
                onChange={(e) => setFormData({ ...formData, allowedIps: e.target.value })}
                size="small"
              />
            </Box>
            <Box display="flex" gap={1} mt={2}>
              <Button variant="contained" onClick={handleCreate}>
                Create
              </Button>
              <Button
                variant="outlined"
                onClick={() => {
                  resetForm();
                  setCreateMode(false);
                }}
              >
                Cancel
              </Button>
            </Box>
          </Paper>
        )}

        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Token</TableCell>
                  <TableCell>Permission</TableCell>
                  <TableCell>Expires</TableCell>
                  <TableCell>Access</TableCell>
                  <TableCell align="right" width={160}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {links.map((link) => (
                  <TableRow key={link.token} hover>
                    <TableCell>{link.name || '-'}</TableCell>
                    <TableCell>{link.token}</TableCell>
                    <TableCell>{link.permissionLevel}</TableCell>
                    <TableCell>{link.expiryDate ? new Date(link.expiryDate).toLocaleString() : '-'}</TableCell>
                    <TableCell>
                      {link.accessCount}/{link.maxAccessCount ?? '∞'}
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => handleCopy(link.token)}>
                        <ContentCopy fontSize="small" />
                      </IconButton>
                      {link.active && (
                        <IconButton size="small" onClick={() => handleDeactivate(link.token)}>
                          <Block fontSize="small" />
                        </IconButton>
                      )}
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDelete(link.token)}
                      >
                        <Delete fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
                {links.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} align="center">
                      No share links
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default ShareLinkManager;

