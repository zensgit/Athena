import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Add, CheckCircleOutline, Delete, Edit, Refresh } from '@mui/icons-material';
import { toast } from 'react-toastify';
import tenantService, { DEFAULT_TENANT_DOMAIN, TenantDto, TenantMutationRequest } from 'services/tenantService';

const EMPTY_FORM: TenantMutationRequest = {
  tenantDomain: '',
  tenantName: '',
  enabled: true,
  rootNodeId: '',
  quotaBytes: null,
};

const TenantAdminPage: React.FC = () => {
  const [tenants, setTenants] = useState<TenantDto[]>([]);
  const [currentTenant, setCurrentTenant] = useState<TenantDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingTenant, setEditingTenant] = useState<TenantDto | null>(null);
  const [form, setForm] = useState<TenantMutationRequest>(EMPTY_FORM);

  const loadTenants = async () => {
    setLoading(true);
    try {
      const [tenantList, current] = await Promise.all([
        tenantService.listTenants(),
        tenantService.getCurrentTenant(),
      ]);
      setTenants(tenantList);
      setCurrentTenant(current);
    } catch {
      toast.error('Failed to load tenants');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadTenants();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const openCreateDialog = () => {
    setEditingTenant(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  };

  const openEditDialog = (tenant: TenantDto) => {
    setEditingTenant(tenant);
    setForm({
      tenantDomain: tenant.tenantDomain,
      tenantName: tenant.tenantName,
      enabled: tenant.enabled,
      rootNodeId: tenant.rootNodeId || '',
      quotaBytes: tenant.quotaBytes ?? null,
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    const payload: TenantMutationRequest = {
      tenantDomain: form.tenantDomain.trim(),
      tenantName: form.tenantName.trim(),
      enabled: form.enabled,
      rootNodeId: form.rootNodeId?.trim() ? form.rootNodeId.trim() : null,
      quotaBytes: form.quotaBytes == null || Number.isNaN(Number(form.quotaBytes)) ? null : Number(form.quotaBytes),
    };
    try {
      if (editingTenant) {
        await tenantService.updateTenant(editingTenant.tenantDomain, payload);
        toast.success('Tenant updated');
      } else {
        await tenantService.createTenant(payload);
        toast.success('Tenant created');
      }
      setDialogOpen(false);
      await loadTenants();
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to save tenant');
    }
  };

  const handleDelete = async (tenant: TenantDto) => {
    if (!window.confirm(`Delete tenant ${tenant.tenantDomain}?`)) {
      return;
    }
    try {
      await tenantService.deleteTenant(tenant.tenantDomain);
      if (tenantService.getActiveTenantDomain() === tenant.tenantDomain) {
        tenantService.setActiveTenantDomain(DEFAULT_TENANT_DOMAIN);
      }
      toast.success('Tenant deleted');
      await loadTenants();
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to delete tenant');
    }
  };

  const handleSetActiveTenant = async (tenant: TenantDto) => {
    tenantService.setActiveTenantDomain(tenant.tenantDomain);
    toast.success(`Active tenant set to ${tenant.tenantDomain}`);
    await loadTenants();
  };

  const handleToggleEnabled = async (tenant: TenantDto) => {
    try {
      await tenantService.updateTenant(tenant.tenantDomain, {
        tenantDomain: tenant.tenantDomain,
        tenantName: tenant.tenantName,
        enabled: !tenant.enabled,
        rootNodeId: tenant.rootNodeId || null,
        quotaBytes: tenant.quotaBytes ?? null,
      });
      toast.success(tenant.enabled ? 'Tenant disabled' : 'Tenant enabled');
      await loadTenants();
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to update tenant');
    }
  };

  const activeTenantDomain = tenantService.getActiveTenantDomain();

  return (
    <Box maxWidth={1280}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Tenant Admin</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage tenant registry, request context routing, and the active tenant header used by this browser session.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void loadTenants()} disabled={loading}>
            Refresh
          </Button>
          <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
            New Tenant
          </Button>
        </Stack>
      </Box>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
          <Box>
            <Typography variant="subtitle1">Current Request Tenant</Typography>
            <Typography variant="body2" color="text.secondary">
              Server-resolved tenant for this session&apos;s current `X-Tenant-ID` header.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label={`Client active: ${activeTenantDomain}`} color="primary" variant="outlined" />
            {currentTenant && <Chip label={`Server current: ${currentTenant.tenantDomain}`} color="success" />}
          </Stack>
        </Stack>
      </Paper>

      {loading ? (
        <Box display="flex" justifyContent="center" p={6}>
          <CircularProgress />
        </Box>
      ) : (
        <Stack spacing={2}>
          {tenants.map((tenant) => {
            const isActive = activeTenantDomain === tenant.tenantDomain;
            return (
              <Card key={tenant.id} variant="outlined">
                <CardContent>
                  <Stack spacing={1.5}>
                    <Box display="flex" justifyContent="space-between" alignItems="flex-start" gap={2}>
                      <Box>
                        <Typography variant="h6">{tenant.tenantName}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {tenant.tenantDomain}
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap justifyContent="flex-end">
                        <Chip size="small" label={tenant.enabled ? 'Enabled' : 'Disabled'} color={tenant.enabled ? 'success' : 'default'} />
                        {tenant.systemDefault && <Chip size="small" label="Default" color="secondary" />}
                        {isActive && <Chip size="small" label="Active Client Tenant" color="primary" />}
                      </Stack>
                    </Box>
                    <Divider />
                    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                      <Typography variant="body2" color="text.secondary">
                        Quota: {tenant.quotaBytes != null ? `${tenant.quotaBytes.toLocaleString()} bytes` : 'Not set'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Root Node: {tenant.rootNodeId || 'Not set'}
                      </Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <Button
                        size="small"
                        variant={isActive ? 'contained' : 'outlined'}
                        startIcon={<CheckCircleOutline />}
                        onClick={() => void handleSetActiveTenant(tenant)}
                        disabled={!tenant.enabled}
                      >
                        {isActive ? 'Active' : 'Use Tenant'}
                      </Button>
                      <Button size="small" startIcon={<Edit />} onClick={() => openEditDialog(tenant)}>
                        Edit
                      </Button>
                      <Button size="small" onClick={() => void handleToggleEnabled(tenant)} disabled={tenant.systemDefault}>
                        {tenant.enabled ? 'Disable' : 'Enable'}
                      </Button>
                      <Button
                        size="small"
                        color="error"
                        startIcon={<Delete />}
                        onClick={() => void handleDelete(tenant)}
                        disabled={tenant.systemDefault}
                      >
                        Delete
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            );
          })}
          {tenants.length === 0 && (
            <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
              <Typography color="text.secondary">No tenants defined.</Typography>
            </Paper>
          )}
        </Stack>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingTenant ? 'Edit Tenant' : 'Create Tenant'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label="Tenant Domain"
              value={form.tenantDomain}
              disabled={Boolean(editingTenant)}
              onChange={(event) => setForm((current) => ({ ...current, tenantDomain: event.target.value }))}
              helperText="Used in the X-Tenant-ID header"
              fullWidth
            />
            <TextField
              label="Tenant Name"
              value={form.tenantName}
              onChange={(event) => setForm((current) => ({ ...current, tenantName: event.target.value }))}
              fullWidth
            />
            <TextField
              label="Quota Bytes"
              type="number"
              value={form.quotaBytes ?? ''}
              onChange={(event) => setForm((current) => ({
                ...current,
                quotaBytes: event.target.value ? Number(event.target.value) : null,
              }))}
              fullWidth
            />
            <TextField
              label="Root Node ID"
              value={form.rootNodeId || ''}
              onChange={(event) => setForm((current) => ({ ...current, rootNodeId: event.target.value }))}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleSave()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TenantAdminPage;
