import React, { useEffect, useState } from 'react';
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
  Chip,
  TextField,
  Autocomplete,
  FormControlLabel,
  CircularProgress,
  Tabs,
  Tab,
} from '@mui/material';
import {
  Close,
  Person,
  Group,
  Add,
  Delete,
} from '@mui/icons-material';
import { PermissionType } from '@/types';
import { useAppDispatch, useAppSelector } from '@/store';
import { setPermissionsDialogOpen } from '@/store/slices/uiSlice';
import nodeService from '@/services/nodeService';
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
  ADD_CHILDREN: 'Add Children',
  READ_PERMISSIONS: 'Read Permissions',
  WRITE_PERMISSIONS: 'Change Permissions',
  EXECUTE: 'Execute',
};

const PermissionsDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { permissionsDialogOpen, selectedNodeId } = useAppSelector((state) => state.ui);
  const [loading, setLoading] = useState(false);
  const [tabValue, setTabValue] = useState(0);
  const [inheritPermissions, setInheritPermissions] = useState(true);
  const [permissions, setPermissions] = useState<PermissionEntry[]>([]);
  const [newPrincipal, setNewPrincipal] = useState('');
  const [availableUsers] = useState(['admin', 'user1', 'user2', 'editor1', 'viewer1']);
  const [availableGroups] = useState(['administrators', 'editors', 'viewers', 'everyone']);

  useEffect(() => {
    if (permissionsDialogOpen && selectedNodeId) {
      loadPermissions();
    }
  }, [permissionsDialogOpen, selectedNodeId]);

  const loadPermissions = async () => {
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
    } catch (error) {
      toast.error('Failed to load permissions');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    dispatch(setPermissionsDialogOpen(false));
    setTabValue(0);
    setPermissions([]);
    setNewPrincipal('');
  };

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleInheritChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
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

    try {
      await nodeService.setPermission(selectedNodeId, principal, permission, allowed);
      
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

  const handleAddPrincipal = () => {
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
  };

  const handleRemovePrincipal = async (principal: string) => {
    if (!selectedNodeId) return;

    try {
      // Remove all permissions for this principal
      const entry = permissions.find((p) => p.principal === principal);
      if (entry) {
        for (const permission of Object.keys(entry.permissions)) {
          await nodeService.setPermission(
            selectedNodeId,
            principal,
            permission as PermissionType,
            false
          );
        }
      }

      setPermissions(permissions.filter((p) => p.principal !== principal));
      toast.success('Principal removed');
    } catch (error) {
      toast.error('Failed to remove principal');
    }
  };

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
                <IconButton
                  size="small"
                  onClick={() => handleRemovePrincipal(entry.principal)}
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
        <FormControlLabel
          control={
            <Switch
              checked={inheritPermissions}
              onChange={handleInheritChange}
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
                  disabled={!newPrincipal}
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
                  disabled={!newPrincipal}
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