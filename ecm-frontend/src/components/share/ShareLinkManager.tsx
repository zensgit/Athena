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
  Collapse,
  Chip,
  Tooltip,
} from '@mui/material';
import {
  Close,
  Add,
  ContentCopy,
  Delete,
  Block,
  PlayArrow,
  ExpandMore,
  ExpandLess,
  BarChart,
} from '@mui/icons-material';
import shareLinkService, {
  ShareLink,
  SharePermission,
  AccessLogEntry,
  AccessStats,
} from 'services/shareLinkService';
import { toast } from 'react-toastify';
import { useAppSelector } from 'store';
import authService from 'services/authService';

interface ShareLinkManagerProps {
  open: boolean;
  onClose: () => void;
  selectedNodeId?: string;
}

const ShareLinkManager: React.FC<ShareLinkManagerProps> = ({ open, onClose, selectedNodeId }) => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );
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

  // access stats / log state
  const [expandedToken, setExpandedToken] = useState<string | null>(null);
  const [accessStats, setAccessStats] = useState<Record<string, AccessStats>>({});
  const [accessLog, setAccessLog] = useState<Record<string, AccessLogEntry[]>>({});
  const [logLoading, setLogLoading] = useState(false);

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
      setExpandedToken(null);
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
    if (!canWrite) { toast.error('Requires write permission'); return; }
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
      await loadLinks();
    } catch { toast.error('Failed to create share link'); }
  };

  const handleCopy = async (token: string) => {
    const url = `${window.location.origin}/api/v1/share/access/${token}`;
    try {
      await navigator.clipboard.writeText(url);
      toast.success('Link copied');
    } catch { toast.error('Failed to copy link'); }
  };

  const handleDeactivate = async (token: string) => {
    if (!canWrite) return;
    try {
      await shareLinkService.deactivateLink(token);
      toast.success('Share link deactivated');
      await loadLinks();
    } catch { toast.error('Failed to deactivate link'); }
  };

  const handleReactivate = async (token: string) => {
    if (!canWrite) return;
    try {
      await shareLinkService.reactivateLink(token);
      toast.success('Share link reactivated');
      await loadLinks();
    } catch { toast.error('Failed to reactivate link'); }
  };

  const handleDelete = async (token: string) => {
    if (!canWrite) return;
    if (!window.confirm('Delete this share link permanently?')) return;
    try {
      await shareLinkService.deleteLink(token);
      toast.success('Share link deleted');
      await loadLinks();
    } catch { toast.error('Failed to delete link'); }
  };

  const toggleExpand = async (token: string) => {
    if (expandedToken === token) {
      setExpandedToken(null);
      return;
    }
    setExpandedToken(token);
    setLogLoading(true);
    try {
      const [stats, log] = await Promise.all([
        shareLinkService.getAccessStats(token),
        shareLinkService.getAccessLog(token),
      ]);
      setAccessStats((prev) => ({ ...prev, [token]: stats }));
      setAccessLog((prev) => ({ ...prev, [token]: log }));
    } catch { toast.error('Failed to load access data'); }
    finally { setLogLoading(false); }
  };

  const statusChip = (link: ShareLink) => {
    if (!link.active) return <Chip label="Inactive" size="small" color="default" />;
    if (!link.isValid) return <Chip label="Expired" size="small" color="warning" />;
    return <Chip label="Active" size="small" color="success" />;
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Share Links
        <IconButton aria-label="close" onClick={onClose} sx={{ position: 'absolute', right: 8, top: 8 }}>
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box display="flex" gap={2} mb={2}>
          <Button variant="contained" startIcon={<Add />} onClick={() => setCreateMode((v) => !v)} disabled={!selectedNodeId || !canWrite}>
            New Share Link
          </Button>
        </Box>

        {canWrite && createMode && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>Create Share Link</Typography>
            <Box display="flex" flexDirection="column" gap={2}>
              <TextField label="Name" value={formData.name} onChange={(e) => setFormData({ ...formData, name: e.target.value })} size="small" />
              <FormControl size="small">
                <InputLabel>Permission</InputLabel>
                <Select label="Permission" value={formData.permissionLevel} onChange={(e) => setFormData({ ...formData, permissionLevel: e.target.value as SharePermission })}>
                  <MenuItem value="VIEW">View</MenuItem>
                  <MenuItem value="COMMENT">Comment</MenuItem>
                  <MenuItem value="EDIT">Edit</MenuItem>
                </Select>
              </FormControl>
              <TextField label="Expiry Date" type="datetime-local" value={formData.expiryDate} onChange={(e) => setFormData({ ...formData, expiryDate: e.target.value })} size="small" InputLabelProps={{ shrink: true }} />
              <TextField label="Max Access Count" type="number" value={formData.maxAccessCount} onChange={(e) => setFormData({ ...formData, maxAccessCount: e.target.value })} size="small" />
              <TextField label="Password (optional)" type="password" value={formData.password} onChange={(e) => setFormData({ ...formData, password: e.target.value })} size="small" />
              <TextField label="Allowed IPs (comma-separated)" value={formData.allowedIps} onChange={(e) => setFormData({ ...formData, allowedIps: e.target.value })} size="small" />
            </Box>
            <Box display="flex" gap={1} mt={2}>
              <Button variant="contained" onClick={() => void handleCreate()}>Create</Button>
              <Button variant="outlined" onClick={() => { resetForm(); setCreateMode(false); }}>Cancel</Button>
            </Box>
          </Paper>
        )}

        {loading ? (
          <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
        ) : (
          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell width={40} />
                  <TableCell>Name</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Permission</TableCell>
                  <TableCell>Expires</TableCell>
                  <TableCell>Access</TableCell>
                  <TableCell align="right" width={200}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {links.map((link) => (
                  <React.Fragment key={link.token}>
                    <TableRow hover>
                      <TableCell>
                        <IconButton size="small" onClick={() => void toggleExpand(link.token)}>
                          {expandedToken === link.token ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                        </IconButton>
                      </TableCell>
                      <TableCell>{link.name || link.token.slice(0, 8) + '...'}</TableCell>
                      <TableCell>{statusChip(link)}</TableCell>
                      <TableCell>{link.permissionLevel}</TableCell>
                      <TableCell>{link.expiryDate ? new Date(link.expiryDate).toLocaleString() : 'Never'}</TableCell>
                      <TableCell>
                        {link.accessCount}/{link.maxAccessCount ?? '\u221e'}
                        {link.passwordProtected && <Chip label="PWD" size="small" sx={{ ml: 0.5 }} variant="outlined" />}
                        {link.hasIpRestrictions && <Chip label="IP" size="small" sx={{ ml: 0.5 }} variant="outlined" />}
                      </TableCell>
                      <TableCell align="right">
                        <Tooltip title="Copy link"><IconButton size="small" onClick={() => void handleCopy(link.token)}><ContentCopy fontSize="small" /></IconButton></Tooltip>
                        {canWrite && link.active && (
                          <Tooltip title="Deactivate"><IconButton size="small" onClick={() => void handleDeactivate(link.token)}><Block fontSize="small" /></IconButton></Tooltip>
                        )}
                        {canWrite && !link.active && (
                          <Tooltip title="Reactivate"><IconButton size="small" color="success" onClick={() => void handleReactivate(link.token)}><PlayArrow fontSize="small" /></IconButton></Tooltip>
                        )}
                        {canWrite && (
                          <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => void handleDelete(link.token)}><Delete fontSize="small" /></IconButton></Tooltip>
                        )}
                      </TableCell>
                    </TableRow>

                    {/* Expandable access stats + log row */}
                    <TableRow>
                      <TableCell colSpan={7} sx={{ p: 0, border: 0 }}>
                        <Collapse in={expandedToken === link.token} timeout="auto" unmountOnExit>
                          <Box sx={{ p: 2, bgcolor: 'action.hover' }}>
                            {logLoading ? <CircularProgress size={20} /> : (
                              <>
                                {accessStats[link.token] && (
                                  <Box display="flex" gap={3} mb={1.5}>
                                    <Box><Typography variant="caption" color="text.secondary">Total</Typography><Typography variant="h6">{accessStats[link.token].totalAccesses}</Typography></Box>
                                    <Box><Typography variant="caption" color="text.secondary">Successful</Typography><Typography variant="h6" color="success.main">{accessStats[link.token].successfulAccesses}</Typography></Box>
                                    <Box><Typography variant="caption" color="text.secondary">Failed</Typography><Typography variant="h6" color="error.main">{accessStats[link.token].failedAccesses}</Typography></Box>
                                  </Box>
                                )}
                                {accessLog[link.token] && accessLog[link.token].length > 0 ? (
                                  <Table size="small">
                                    <TableHead><TableRow>
                                      <TableCell>Time</TableCell><TableCell>IP</TableCell><TableCell>Result</TableCell><TableCell>Reason</TableCell>
                                    </TableRow></TableHead>
                                    <TableBody>
                                      {accessLog[link.token].slice(0, 10).map((entry) => (
                                        <TableRow key={entry.id}>
                                          <TableCell>{new Date(entry.accessedAt).toLocaleString()}</TableCell>
                                          <TableCell>{entry.clientIp || '-'}</TableCell>
                                          <TableCell>{entry.success ? <Chip label="OK" size="small" color="success" /> : <Chip label="Fail" size="small" color="error" />}</TableCell>
                                          <TableCell>{entry.failureReason || '-'}</TableCell>
                                        </TableRow>
                                      ))}
                                    </TableBody>
                                  </Table>
                                ) : (
                                  <Typography variant="body2" color="text.secondary">No access attempts recorded</Typography>
                                )}
                              </>
                            )}
                          </Box>
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  </React.Fragment>
                ))}
                {links.length === 0 && (
                  <TableRow><TableCell colSpan={7} align="center">No share links</TableCell></TableRow>
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
