import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
  Checkbox,
  Alert,
  Tooltip,
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
import nodeService, { PermissionDecision, PermissionSetMetadata } from 'services/nodeService';
import userGroupService from 'services/userGroupService';
import permissionTemplateService, { PermissionTemplate } from 'services/permissionTemplateService';
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
    [key in PermissionType]?: 'ALLOW' | 'DENY';
  };
  inheritance?: {
    [key in PermissionType]?: 'INHERITED' | 'EXPLICIT' | 'MIXED';
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
  const [permissionTemplates, setPermissionTemplates] = useState<PermissionTemplate[]>([]);
  const [permissionTemplatesLoading, setPermissionTemplatesLoading] = useState(false);
  const [permissionTemplatesError, setPermissionTemplatesError] = useState<string | null>(null);
  const [selectedTemplateId, setSelectedTemplateId] = useState('');
  const [replaceTemplate, setReplaceTemplate] = useState(false);
  const [permissionDiagnostics, setPermissionDiagnostics] = useState<PermissionDecision | null>(null);
  const [permissionDiagnosticsLoading, setPermissionDiagnosticsLoading] = useState(false);
  const [permissionDiagnosticsError, setPermissionDiagnosticsError] = useState<string | null>(null);
  const [diagnosticPermissionType, setDiagnosticPermissionType] = useState<PermissionType>('READ');
  const [diagnosticUsername, setDiagnosticUsername] = useState('');
  const [newPrincipal, setNewPrincipal] = useState('');
  const [availableUsers, setAvailableUsers] = useState<string[]>([]);
  const [availableGroups, setAvailableGroups] = useState<string[]>([]);
  const [nodePath, setNodePath] = useState<string>('');

  const diagnosticsMatchedGrants = useMemo(() => {
    if (!permissionDiagnostics) {
      return [];
    }
    const allowed = new Set(permissionDiagnostics.allowedAuthorities || []);
    const denied = new Set(permissionDiagnostics.deniedAuthorities || []);
    const combined = Array.from(new Set([...allowed, ...denied])).sort((a, b) => a.localeCompare(b));

    return combined.map((authority) => {
      const direct = permissions.find((entry) => entry.principal === authority);
      const groupKey = `GROUP_${authority}`;
      const group = direct ? null : permissions.find((entry) => entry.principal === groupKey);
      const resolved = direct || group || null;
      const resolvedKey = resolved?.principal || (allowed.has(authority) || denied.has(authority) ? authority : '');
      const inheritance = resolved?.inheritance?.[diagnosticPermissionType] || 'EXPLICIT';
      const permissionState = resolved?.permissions?.[diagnosticPermissionType] || null;

      return {
        authority,
        resolvedKey,
        hasAllow: allowed.has(authority),
        hasDeny: denied.has(authority),
        inheritance,
        permissionState,
      };
    });
  }, [diagnosticPermissionType, permissionDiagnostics, permissions]);

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
          inheritance: {},
        };
        
        perms.forEach((perm) => {
          const key = perm.permission as PermissionType;
          const current = entry.permissions[key];
          const origin = perm.inherited ? 'INHERITED' : 'EXPLICIT';
          const currentOrigin = entry.inheritance?.[key];
          if (!currentOrigin) {
            entry.inheritance![key] = origin;
          } else if (currentOrigin !== origin) {
            entry.inheritance![key] = 'MIXED';
          }
          if (perm.allowed) {
            if (current !== 'DENY') {
              entry.permissions[key] = 'ALLOW';
            }
          } else {
            entry.permissions[key] = 'DENY';
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

  const loadPermissionDiagnostics = useCallback(async (options?: { silent?: boolean }) => {
    if (!selectedNodeId) return;
    const silent = options?.silent === true;
    if (!silent) {
      setPermissionDiagnosticsLoading(true);
    }
    try {
      const targetUsername = diagnosticUsername.trim();
      const decision = await nodeService.getPermissionDiagnostics(
        selectedNodeId,
        diagnosticPermissionType,
        targetUsername.length > 0 ? targetUsername : undefined
      );
      setPermissionDiagnostics(decision);
      setPermissionDiagnosticsError(null);
    } catch {
      setPermissionDiagnosticsError('Failed to load permission diagnostics');
    } finally {
      setPermissionDiagnosticsLoading(false);
    }
  }, [selectedNodeId, diagnosticPermissionType, diagnosticUsername]);

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

  const loadPermissionTemplates = useCallback(async () => {
    if (!user?.roles?.includes('ROLE_ADMIN')) {
      setPermissionTemplates([]);
      setPermissionTemplatesError(null);
      setPermissionTemplatesLoading(false);
      return;
    }
    setPermissionTemplatesLoading(true);
    try {
      const templates = await permissionTemplateService.list();
      setPermissionTemplates(templates ?? []);
      setPermissionTemplatesError(null);
    } catch {
      setPermissionTemplates([]);
      setPermissionTemplatesError('Failed to load permission templates');
    } finally {
      setPermissionTemplatesLoading(false);
    }
  }, [user?.roles]);

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
      loadPermissionTemplates();
    }
  }, [permissionsDialogOpen, selectedNodeId, loadPermissions, loadPrincipals, loadPermissionSets, loadPermissionTemplates]);

  useEffect(() => {
    if (permissionsDialogOpen && selectedNodeId) {
      loadPermissionDiagnostics();
    } else {
      setPermissionDiagnostics(null);
      setPermissionDiagnosticsError(null);
      setPermissionDiagnosticsLoading(false);
    }
  }, [permissionsDialogOpen, selectedNodeId, diagnosticPermissionType, diagnosticUsername, loadPermissionDiagnostics]);

  useEffect(() => {
    if (permissionsDialogOpen) {
      setDiagnosticUsername(user?.username ?? '');
    }
  }, [permissionsDialogOpen, user?.username]);

  const handleClose = () => {
    dispatch(setPermissionsDialogOpen(false));
    setTabValue(0);
    setPermissions([]);
    setNewPrincipal('');
    setNodePath('');
    setPermissionDiagnostics(null);
    setPermissionDiagnosticsError(null);
    setPermissionDiagnosticsLoading(false);
    setDiagnosticPermissionType('READ');
    setDiagnosticUsername('');
    setSelectedTemplateId('');
    setReplaceTemplate(false);
    setPermissionTemplatesError(null);
  };

  const handleCopyAcl = async () => {
    const lines = permissions.map((entry) => {
      const principal = entry.principal.replace('GROUP_', '');
      const type = entry.principalType.toUpperCase();
      const allowed = Object.keys(entry.permissions)
        .filter((perm) => entry.permissions[perm as PermissionType] === 'ALLOW')
        .join(', ');
      const denied = Object.keys(entry.permissions)
        .filter((perm) => entry.permissions[perm as PermissionType] === 'DENY')
        .join(', ');
      const summary = [
        allowed ? `ALLOW: ${allowed}` : null,
        denied ? `DENY: ${denied}` : null,
      ].filter(Boolean).join(' | ');
      return `${principal}\t${type}\t${summary || '-'}`;
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

  const handlePermissionToggle = async (
    principal: string,
    permission: PermissionType,
    currentState?: 'ALLOW' | 'DENY'
  ) => {
    if (!selectedNodeId) return;
    if (!canWrite) return;

    try {
      const authorityType = principal.startsWith('GROUP_') ? 'GROUP' : 'USER';
      const authority = principal.replace('GROUP_', '');
      let nextState: 'ALLOW' | 'DENY' | undefined;

      if (!currentState) {
        await nodeService.setPermission(selectedNodeId, authority, authorityType, permission, true);
        nextState = 'ALLOW';
      } else if (currentState === 'ALLOW') {
        await nodeService.setPermission(selectedNodeId, authority, authorityType, permission, false);
        nextState = 'DENY';
      } else {
        await nodeService.removePermission(selectedNodeId, authority, permission);
        nextState = undefined;
      }

      setPermissions((prev) =>
        prev.map((entry) =>
          entry.principal === principal
            ? (() => {
                const nextPermissions = { ...entry.permissions };
                if (nextState) {
                  nextPermissions[permission] = nextState;
                } else {
                  delete nextPermissions[permission];
                }
                return {
                  ...entry,
                  permissions: nextPermissions,
                };
              })()
            : entry
        )
      );
      
      if (!currentState) {
        toast.success('Permission allowed');
      } else if (currentState === 'ALLOW') {
        toast.success('Permission denied');
      } else {
        toast.success('Permission cleared');
      }
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
        acc[perm] = 'ALLOW';
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

  const handleApplyTemplate = async () => {
    if (!selectedNodeId || !selectedTemplateId || !canWrite) {
      return;
    }
    try {
      await permissionTemplateService.apply(selectedTemplateId, selectedNodeId, replaceTemplate);
      await loadPermissions();
      toast.success('Permission template applied');
    } catch {
      toast.error('Failed to apply permission template');
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
      permissions: { READ: 'ALLOW' },
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
                  {(() => {
                    const permissionKey = perm as PermissionType;
                    const origin = entry.inheritance?.[permissionKey];
                    const tooltipLabel = origin === 'INHERITED'
                      ? 'Inherited'
                      : origin === 'MIXED'
                        ? 'Mixed explicit/inherited'
                        : '';
                    const checkbox = (
                      <Checkbox
                        size="small"
                        checked={entry.permissions[permissionKey] === 'ALLOW'}
                        indeterminate={entry.permissions[permissionKey] === 'DENY'}
                        color={entry.permissions[permissionKey] === 'DENY' ? 'error' : 'primary'}
                        disabled={!canWrite}
                        onClick={() =>
                          handlePermissionToggle(
                            entry.principal,
                            permissionKey,
                            entry.permissions[permissionKey]
                          )
                        }
                        sx={origin === 'INHERITED' ? { opacity: 0.6 } : undefined}
                      />
                    );
                    if (!tooltipLabel) {
                      return checkbox;
                    }
                    return (
                      <Tooltip title={tooltipLabel} arrow>
                        <span>{checkbox}</span>
                      </Tooltip>
                    );
                  })()}
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
        <Alert severity="info" sx={{ mb: 2 }}>
          Click a permission to cycle Allow → Deny → Clear. Explicit denies override allows across inheritance.
        </Alert>
        <Box sx={{ mb: 2 }}>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
            <Typography variant="subtitle2">Permission templates</Typography>
            <Button
              size="small"
              onClick={() => loadPermissionTemplates()}
              disabled={permissionTemplatesLoading}
            >
              Refresh
            </Button>
          </Box>
          {permissionTemplatesError && (
            <Typography variant="caption" color="error" display="block" sx={{ mb: 1 }}>
              {permissionTemplatesError}
            </Typography>
          )}
          <Box display="flex" gap={1} alignItems="center" flexWrap="wrap">
            <FormControl size="small" sx={{ minWidth: 240 }}>
              <InputLabel id="permission-template-select-label">Template</InputLabel>
              <Select
                labelId="permission-template-select-label"
                label="Template"
                value={selectedTemplateId}
                onChange={(event) => setSelectedTemplateId(String(event.target.value))}
                disabled={!canWrite || permissionTemplates.length === 0}
              >
                <MenuItem value="" disabled>
                  Select
                </MenuItem>
                {permissionTemplates.map((template) => (
                  <MenuItem key={template.id} value={template.id}>
                    <Box display="flex" flexDirection="column">
                      <Typography variant="body2">{template.name}</Typography>
                      {template.description && (
                        <Typography variant="caption" color="text.secondary">
                          {template.description}
                        </Typography>
                      )}
                    </Box>
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControlLabel
              control={
                <Checkbox
                  checked={replaceTemplate}
                  onChange={(event) => setReplaceTemplate(event.target.checked)}
                  disabled={!canWrite}
                />
              }
              label="Replace permissions for listed principals"
            />
            <Button
              variant="outlined"
              size="small"
              onClick={handleApplyTemplate}
              disabled={!canWrite || !selectedTemplateId}
            >
              Apply Template
            </Button>
            {permissionTemplatesLoading && <CircularProgress size={18} />}
          </Box>
          {!user?.roles?.includes('ROLE_ADMIN') && (
            <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>
              Templates are available to admins only.
            </Typography>
          )}
        </Box>
        <Box sx={{ mb: 2 }}>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
            <Typography variant="subtitle2">Permission diagnostics</Typography>
            <Button
              size="small"
              onClick={() => loadPermissionDiagnostics()}
              disabled={permissionDiagnosticsLoading || !selectedNodeId}
            >
              Refresh
            </Button>
          </Box>
          <Box display="flex" gap={1} alignItems="center" flexWrap="wrap" sx={{ mb: 1 }}>
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel id="permission-diagnostic-type-label">Permission</InputLabel>
              <Select
                labelId="permission-diagnostic-type-label"
                label="Permission"
                value={diagnosticPermissionType}
                onChange={(event) => setDiagnosticPermissionType(event.target.value as PermissionType)}
              >
                {Object.entries(PERMISSION_LABELS).map(([key, label]) => (
                  <MenuItem key={key} value={key}>
                    {label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Autocomplete
              size="small"
              freeSolo
              disabled={!user?.roles?.includes('ROLE_ADMIN')}
              value={diagnosticUsername}
              onChange={(_, value) => setDiagnosticUsername(value ?? '')}
              onInputChange={(_, value) => setDiagnosticUsername(value)}
              options={availableUsers}
              sx={{ minWidth: 240 }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Diagnose as"
                  size="small"
                  helperText={!user?.roles?.includes('ROLE_ADMIN') ? 'Admin only' : 'Optional username'}
                />
              )}
            />
            {permissionDiagnosticsLoading && <CircularProgress size={18} />}
          </Box>
          {permissionDiagnosticsError && (
            <Typography variant="caption" color="error" display="block" sx={{ mb: 1 }}>
              {permissionDiagnosticsError}
            </Typography>
          )}
          {permissionDiagnostics && !permissionDiagnosticsError ? (
            <>
              <Box display="flex" flexWrap="wrap" gap={1} sx={{ mb: 1 }}>
                <Chip
                  size="small"
                  color={permissionDiagnostics.allowed ? 'success' : 'warning'}
                  label={permissionDiagnostics.allowed ? 'Allowed' : 'Denied'}
                />
                <Chip size="small" variant="outlined" label={`Reason ${permissionDiagnostics.reason}`} />
                {permissionDiagnostics.dynamicAuthority && (
                  <Chip
                    size="small"
                    variant="outlined"
                    label={`Dynamic ${permissionDiagnostics.dynamicAuthority}`}
                  />
                )}
                {permissionDiagnostics.username && (
                  <Chip size="small" variant="outlined" label={`User ${permissionDiagnostics.username}`} />
                )}
              </Box>
              {permissionDiagnostics.allowedAuthorities?.length > 0 && (
                <Box sx={{ mb: 1 }}>
                  <Typography variant="caption" color="text.secondary" display="block">
                    Allowed authorities
                  </Typography>
                  <Box display="flex" flexWrap="wrap" gap={1} mt={0.5}>
                    {permissionDiagnostics.allowedAuthorities.map((authority) => (
                      <Chip key={`allow-${authority}`} size="small" variant="outlined" label={authority} />
                    ))}
                  </Box>
                </Box>
              )}
              {permissionDiagnostics.deniedAuthorities?.length > 0 && (
                <Box>
                  <Typography variant="caption" color="text.secondary" display="block">
                    Denied authorities
                  </Typography>
                  <Box display="flex" flexWrap="wrap" gap={1} mt={0.5}>
                    {permissionDiagnostics.deniedAuthorities.map((authority) => (
                      <Chip key={`deny-${authority}`} size="small" variant="outlined" label={authority} />
                    ))}
                  </Box>
                </Box>
              )}
              {(permissionDiagnostics.reason === 'ACL_ALLOW' || permissionDiagnostics.reason === 'ACL_DENY') && (
                <Box sx={{ mt: 1 }}>
                  <Typography variant="caption" color="text.secondary" display="block">
                    Matched grants (effective for selected permission)
                  </Typography>
                  {diagnosticsMatchedGrants.length === 0 ? (
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                      No matching permission grants were found on this node or its inheritance chain.
                    </Typography>
                  ) : (
                    <TableContainer component={Paper} variant="outlined" sx={{ mt: 0.5 }}>
                      <Table size="small" aria-label="Matched permission grants">
                        <TableHead>
                          <TableRow>
                            <TableCell>Authority</TableCell>
                            <TableCell>Match</TableCell>
                            <TableCell>Source</TableCell>
                            <TableCell>Effective</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {diagnosticsMatchedGrants.map((grant) => (
                            <TableRow key={`diag-grant-${grant.authority}`}>
                              <TableCell>
                                <Typography variant="body2">{grant.resolvedKey || grant.authority}</Typography>
                              </TableCell>
                              <TableCell>
                                <Box display="flex" flexWrap="wrap" gap={0.5}>
                                  {grant.hasAllow && (
                                    <Chip size="small" color="success" label="Allow" />
                                  )}
                                  {grant.hasDeny && (
                                    <Chip size="small" color="warning" label="Deny" />
                                  )}
                                </Box>
                              </TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={
                                    grant.inheritance === 'INHERITED'
                                      ? 'Inherited'
                                      : grant.inheritance === 'MIXED'
                                        ? 'Mixed'
                                        : 'Explicit'
                                  }
                                />
                              </TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={grant.permissionState || 'Unknown'}
                                />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                    Explicit denies override allows across the inheritance chain.
                  </Typography>
                </Box>
              )}
            </>
          ) : (
            !permissionDiagnosticsError && !permissionDiagnosticsLoading && (
              <Typography variant="caption" color="text.secondary" display="block">
                Diagnostics unavailable.
              </Typography>
            )
          )}
        </Box>

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
