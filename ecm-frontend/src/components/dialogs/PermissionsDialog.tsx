import React, { useCallback, useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  IconButton,
  Typography,
  Box,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  TextField,
  Autocomplete,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  CircularProgress,
  Tabs,
  Tab,
  Divider,
  Chip,
} from '@mui/material';
import {
  Close,
  Person,
  Group,
  Add,
  Delete,
  ContentCopy,
} from '@mui/icons-material';
import { PermissionType } from 'types';
import { useAppDispatch, useAppSelector } from 'store';
import { setPermissionsDialogOpen } from 'store/slices/uiSlice';
import nodeService, { PermissionSetMetadata } from 'services/nodeService';
import userGroupService from 'services/userGroupService';
import { toast } from 'react-toastify';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`permissions-tabpanel-${index}`}
      aria-labelledby={`permissions-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 2 }}>{children}</Box>}
    </div>
  );
}

interface PermissionEntry {
  principal: string;
  principalType: 'user' | 'group';
  permissions: {
    [key in PermissionType]?: boolean;
  };
}

const PERMISSION_LABELS: Record<PermissionType, string> = {
  READ: 'Read',
  WRITE: 'Write',
  DELETE: 'Delete',
  CREATE_CHILDREN: 'Create Children',
  DELETE_CHILDREN: 'Delete Children',
  EXECUTE: 'Execute',
  CHANGE_PERMISSIONS: 'Change Permissions',
  TAKE_OWNERSHIP: 'Take Ownership',
  CHECKOUT: 'Checkout',
  CHECKIN: 'Checkin',
  CANCEL_CHECKOUT: 'Cancel Checkout',
  APPROVE: 'Approve',
  REJECT: 'Reject',
};

const PermissionsDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { permissionsDialogOpen, selectedNodeId } = useAppSelector((state) => state.ui);
  const user = useAppSelector((state) => state.auth.user);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const [loading, setLoading] = useState(false);
  const [tabValue, setTabValue] = useState(0);
  const [inheritPermissions, setInheritPermissions] = useState(true);
  const [permissions, setPermissions] = useState<PermissionEntry[]>([]);
  const [permissionSets, setPermissionSets] = useState<Record<string, PermissionType[]>>({});
  const [permissionSetMetadata, setPermissionSetMetadata] = useState<PermissionSetMetadata[]>([]);
  const [newPrincipal, setNewPrincipal] = useState('');
  const [availableUsers, setAvailableUsers] = useState<string[]>([]);
  const [availableGroups, setAvailableGroups] = useState<string[]>([]);
  const [nodePath, setNodePath] = useState<string>('');

  const loadPermissions = useCallback(async () => {
    if (!selectedNodeId) return;

    setLoading(true);
    try {
      const nodePermissions = await nodeService.getPermissions(selectedNodeId);
      
      // Transform permissions data to our format
      const entries: PermissionEntry[] = [];
      Object.entries(nodePermissions).forEach(([principal, perms]) => {
        const entry: PermissionEntry = {
          principal,
          principalType: principal.startsWith('GROUP_') ? 'group' : 'user',
          permissions: {},
        };
        
        perms.forEach((perm) => {
          if (perm.allowed) {
            entry.permissions[perm.permission as PermissionType] = true;
          }
        });
        
        entries.push(entry);
      });
      
      setPermissions(entries);

      // Load current inherit flag from node
      const node = await nodeService.getNode(selectedNodeId);
      setInheritPermissions(node.inheritPermissions ?? true);
      setNodePath(node.path || '');
    } catch (error) {
      toast.error('Failed to load permissions');
    } finally {
      setLoading(false);
    }
  }, [selectedNodeId]);

  const loadPermissionSets = useCallback(async () => {
    try {
      const [sets, metadata] = await Promise.all([
        nodeService.getPermissionSets().catch(() => ({})),
        nodeService.getPermissionSetMetadata().catch(() => []),
      ]);
      setPermissionSets(sets ?? {});
      setPermissionSetMetadata(metadata ?? []);
    } catch {
      setPermissionSets({});
      setPermissionSetMetadata([]);
    }
  }, []);

  const loadPrincipals = useCallback(async () => {
    try {
      const [users, groups] = await Promise.all([
        userGroupService.listUsers(),
        userGroupService.listGroups(),
      ]);
      setAvailableUsers(users.map((u) => u.username));
      setAvailableGroups(groups.map((g) => g.name));
    } catch {
      // ignore principal load failures
    }
  }, []);

  useEffect(() => {
    if (permissionsDialogOpen && selectedNodeId) {
      loadPermissions();
      loadPrincipals();
      loadPermissionSets();
    }
  }, [permissionsDialogOpen, selectedNodeId, loadPermissions, loadPrincipals, loadPermissionSets]);

  const handleClose = () => {
    dispatch(setPermissionsDialogOpen(false));
    setTabValue(0);
    setPermissions([]);
    setNewPrincipal('');
    setNodePath('');
  };

  const handleCopyAcl = async () => {
    const lines = permissions.map((entry) => {
      const principal = entry.principal.replace('GROUP_', '');
      const type = entry.principalType.toUpperCase();
      const granted = Object.keys(entry.permissions)
        .filter((perm) => entry.permissions[perm as PermissionType])
        .join(', ');
      return `${principal}\t${type}\t${granted || '-'}`;
    });
    const header = `Inherit: ${inheritPermissions ? 'true' : 'false'}`;
    try {
      await navigator.clipboard.writeText([header, ...lines].join('\n'));
      toast.success('ACL copied to clipboard');
    } catch {
      toast.error('Failed to copy ACL');
    }
  };

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleInheritChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    if (!canWrite) {
      return;
    }
    const inherit = event.target.checked;
    setInheritPermissions(inherit);
    
    if (selectedNodeId) {
      try {
        await nodeService.setInheritPermissions(selectedNodeId, inherit);
        toast.success('Inheritance setting updated');
      } catch (error) {
        toast.error('Failed to update inheritance setting');
      }
    }
  };

  const handlePermissionChange = async (
    principal: string,
    permission: PermissionType,
    allowed: boolean
  ) => {
    if (!selectedNodeId) return;
    if (!canWrite) return;

    try {
      const authorityType = principal.startsWith('GROUP_') ? 'GROUP' : 'USER';
      const authority = principal.replace('GROUP_', '');
      await nodeService.setPermission(selectedNodeId, authority, authorityType, permission, allowed);
      
      setPermissions((prev) =>
        prev.map((entry) =>
          entry.principal === principal
            ? {
                ...entry,
                permissions: {
                  ...entry.permissions,
                  [permission]: allowed,
                },
              }
            : entry
        )
      );
      
      toast.success('Permission updated');
    } catch (error) {
      toast.error('Failed to update permission');
    }
  };

  const handleApplyPermissionSet = async (principal: string, permissionSet: string) => {
    if (!selectedNodeId || !canWrite || !permissionSet) {
      return;
    }

    try {
      const authorityType = principal.startsWith('GROUP_') ? 'GROUP' : 'USER';
      const authority = principal.replace('GROUP_', '');
      await nodeService.applyPermissionSet(selectedNodeId, authority, authorityType, permissionSet, true);

      const fallbackPermissions = permissionSets[permissionSet]
        || permissionSetMetadata.find((set) => set.name === permissionSet)?.permissions
        || [];
      const nextPermissions = fallbackPermissions.reduce<PermissionEntry['permissions']>((acc, perm) => {
        acc[perm] = true;
        return acc;
      }, {});

      setPermissions((prev) =>
        prev.map((entry) =>
          entry.principal === principal
            ? { ...entry, permissions: nextPermissions }
            : entry
        )
      );

      toast.success(`Applied ${permissionSet} permissions`);
    } catch {
      toast.error('Failed to apply permission set');
    }
  };

  const handleAddPrincipal = () => {
    if (!canWrite) {
      return;
    }
    if (!newPrincipal) return;

    const principalType = tabValue === 0 ? 'user' : 'group';
    const principal = principalType === 'group' ? `GROUP_${newPrincipal}` : newPrincipal;

    if (permissions.find((p) => p.principal === principal)) {
      toast.warning('Principal already exists');
      return;
    }

    const newEntry: PermissionEntry = {
      principal,
      principalType,
      permissions: { READ: true },
    };

    setPermissions([...permissions, newEntry]);
    setNewPrincipal('');

    if (selectedNodeId) {
      const authorityType = principalType === 'group' ? 'GROUP' : 'USER';
      nodeService
        .setPermission(selectedNodeId, newPrincipal, authorityType, 'READ', true)
        .catch(() => toast.error('Failed to add principal'));
    }
  };

  const handleRemovePrincipal = async (principal: string) => {
    if (!selectedNodeId) return;
    if (!canWrite) return;

    try {
      // Remove all permissions for this principal
      const entry = permissions.find((p) => p.principal === principal);
      if (entry) {
        const authority = principal.replace('GROUP_', '');
        for (const permission of Object.keys(entry.permissions)) {
          await nodeService.removePermission(
            selectedNodeId,
            authority,
            permission as PermissionType
          );
        }
      }

      setPermissions(permissions.filter((p) => p.principal !== principal));
      toast.success('Principal removed');
    } catch (error) {
      toast.error('Failed to remove principal');
    }
  };

  const permissionSetOptions = permissionSetMetadata.length > 0
    ? [...permissionSetMetadata].sort((a, b) => {
      const left = a.order ?? 0;
      const right = b.order ?? 0;
      if (left !== right) {
        return left - right;
      }
      return a.name.localeCompare(b.name);
    })
    : Object.keys(permissionSets).sort().map((name) => ({
      name,
      label: name,
      description: '',
      permissions: permissionSets[name] || [],
    }));
  const inheritanceChain = nodePath.split('/').filter(Boolean);

  const renderPermissionsTable = (entries: PermissionEntry[]) => (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Principal</TableCell>
            {Object.keys(PERMISSION_LABELS).map((perm) => (
              <TableCell key={perm} align="center" sx={{ minWidth: 80 }}>
                {PERMISSION_LABELS[perm as PermissionType]}
              </TableCell>
            ))}
            <TableCell align="center" sx={{ minWidth: 140 }}>
              Preset
            </TableCell>
            <TableCell align="center">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {entries.map((entry) => (
            <TableRow key={entry.principal}>
              <TableCell>
                <Box display="flex" alignItems="center" gap={1}>
                  {entry.principalType === 'group' ? (
                    <Group fontSize="small" />
                  ) : (
                    <Person fontSize="small" />
                  )}
                  <Typography variant="body2">
                    {entry.principal.replace('GROUP_', '')}
                  </Typography>
                </Box>
              </TableCell>
              {Object.keys(PERMISSION_LABELS).map((perm) => (
                <TableCell key={perm} align="center">
                  <Switch
                    size="small"
                    checked={entry.permissions[perm as PermissionType] || false}
                    disabled={!canWrite}
                    onChange={(e) =>
                      handlePermissionChange(
                        entry.principal,
                        perm as PermissionType,
                        e.target.checked
                      )
                    }
                  />
                </TableCell>
              ))}
              <TableCell align="center">
                <FormControl size="small" sx={{ minWidth: 120 }}>
                  <InputLabel id={`permission-set-${entry.principal}`}>Preset</InputLabel>
                  <Select
                    labelId={`permission-set-${entry.principal}`}
                    label="Preset"
                    value=""
                    onChange={(event) => handleApplyPermissionSet(entry.principal, String(event.target.value))}
                    disabled={!canWrite || permissionSetOptions.length === 0}
                  >
                    <MenuItem value="" disabled>
                      Select
                    </MenuItem>
                    {permissionSetOptions.map((option) => (
                      <MenuItem key={option.name} value={option.name}>
                        <Box display="flex" flexDirection="column">
                          <Typography variant="body2">{option.label || option.name}</Typography>
                          {option.description && (
                            <Typography variant="caption" color="text.secondary">
                              {option.description}
                            </Typography>
                          )}
                        </Box>
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </TableCell>
              <TableCell align="center">
                <IconButton
                  size="small"
                  onClick={() => handleRemovePrincipal(entry.principal)}
                  disabled={!canWrite}
                >
                  <Delete fontSize="small" />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );

  return (
    <Dialog
      open={permissionsDialogOpen}
      onClose={handleClose}
      maxWidth="lg"
      fullWidth
    >
      <DialogTitle>
        Manage Permissions
        <IconButton
          aria-label="close"
          onClick={handleClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
          <Box>
            <Typography variant="subtitle2">Inheritance path</Typography>
            {inheritanceChain.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Path unavailable
              </Typography>
            ) : (
              <Box display="flex" flexWrap="wrap" gap={0.5} mt={0.5}>
                {inheritanceChain.map((segment, index) => (
                  <Box key={`${segment}-${index}`} display="flex" alignItems="center" gap={0.5}>
                    <Chip label={segment} size="small" variant="outlined" />
                    {index < inheritanceChain.length - 1 && (
                      <Typography variant="caption" color="text.secondary">
                        /
                      </Typography>
                    )}
                  </Box>
                ))}
              </Box>
            )}
            <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>
              Permissions inherit from the root to this node unless disabled below.
            </Typography>
          </Box>
          <Button
            variant="outlined"
            size="small"
            startIcon={<ContentCopy />}
            onClick={handleCopyAcl}
          >
            Copy ACL
          </Button>
        </Box>
        <Divider sx={{ mb: 2 }} />
        <FormControlLabel
          control={
            <Switch
              checked={inheritPermissions}
              onChange={handleInheritChange}
              disabled={!canWrite}
            />
          }
          label="Inherit permissions from parent"
          sx={{ mb: 2 }}
        />

        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <>
            <Tabs value={tabValue} onChange={handleTabChange}>
              <Tab label="Users" />
              <Tab label="Groups" />
            </Tabs>

            <TabPanel value={tabValue} index={0}>
              <Box display="flex" gap={1} mb={2}>
                <Autocomplete
                  value={newPrincipal}
                  onChange={(event, newValue) => setNewPrincipal(newValue || '')}
                  options={availableUsers}
                  sx={{ flex: 1 }}
                  renderInput={(params) => (
                    <TextField {...params} label="Add User" size="small" />
                  )}
                />
                <Button
                  variant="contained"
                  startIcon={<Add />}
                  onClick={handleAddPrincipal}
                  disabled={!canWrite || !newPrincipal}
                >
                  Add
                </Button>
              </Box>
              {renderPermissionsTable(
                permissions.filter((p) => p.principalType === 'user')
              )}
            </TabPanel>

            <TabPanel value={tabValue} index={1}>
              <Box display="flex" gap={1} mb={2}>
                <Autocomplete
                  value={newPrincipal}
                  onChange={(event, newValue) => setNewPrincipal(newValue || '')}
                  options={availableGroups}
                  sx={{ flex: 1 }}
                  renderInput={(params) => (
                    <TextField {...params} label="Add Group" size="small" />
                  )}
                />
                <Button
                  variant="contained"
                  startIcon={<Add />}
                  onClick={handleAddPrincipal}
                  disabled={!canWrite || !newPrincipal}
                >
                  Add
                </Button>
              </Box>
              {renderPermissionsTable(
                permissions.filter((p) => p.principalType === 'group')
              )}
            </TabPanel>
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

export default PermissionsDialog;
