import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material';
import {
  CheckCircle,
  Error as ErrorIcon,
  ManageAccounts,
  SyncAlt,
} from '@mui/icons-material';
import { toast } from 'react-toastify';
import ldapService, { LdapConnectionStatus, LdapSyncResult } from 'services/ldapService';

// ── helpers ───────────────────────────────────────────────────────────────────

const isLdapNotConfigured = (err: unknown): boolean => {
  if (err && typeof err === 'object' && 'response' in err) {
    const status = (err as { response?: { status?: number } }).response?.status;
    return status === 404 || status === 503;
  }
  return false;
};

const formatDateTime = (value: string | null | undefined): string => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

// ── Connection Status card ─────────────────────────────────────────────────────

interface ConnectionCardProps {
  onNotConfigured: () => void;
}

const ConnectionCard: React.FC<ConnectionCardProps> = ({ onNotConfigured }) => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<LdapConnectionStatus | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  const handleTest = async () => {
    setLoading(true);
    setResult(null);
    setTestError(null);
    try {
      const status = await ldapService.testConnection();
      setResult(status);
      if (status.reachable) {
        toast.success('LDAP connection successful.');
      } else {
        toast.error('LDAP connection failed. See details below.');
      }
    } catch (err) {
      if (isLdapNotConfigured(err)) {
        onNotConfigured();
      } else {
        const msg = (err instanceof Error) ? err.message : 'Connection test failed.';
        setTestError(msg);
        toast.error(`Connection test error: ${msg}`);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card>
      <CardHeader
        avatar={<ManageAccounts color="primary" />}
        title="LDAP Connection"
        subheader="Verify that the backend can reach the configured LDAP/AD server"
      />
      <CardContent>
        <Button
          variant="contained"
          onClick={() => void handleTest()}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          {loading ? 'Testing…' : 'Test Connection'}
        </Button>

        {testError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {testError}
          </Alert>
        )}

        {result && (
          <Box sx={{ mt: 2 }}>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
              {result.reachable ? (
                <CheckCircle color="success" />
              ) : (
                <ErrorIcon color="error" />
              )}
              <Chip
                label={result.reachable ? 'Reachable' : 'Unreachable'}
                color={result.reachable ? 'success' : 'error'}
                size="small"
              />
            </Stack>

            {result.userBaseDn && (
              <Typography variant="body2" sx={{ mb: 0.5 }}>
                <strong>User Base DN:</strong> {result.userBaseDn}
              </Typography>
            )}
            {result.groupBaseDn && (
              <Typography variant="body2" sx={{ mb: 0.5 }}>
                <strong>Group Base DN:</strong> {result.groupBaseDn}
              </Typography>
            )}
            {result.message && (
              <Alert severity={result.reachable ? 'info' : 'warning'} sx={{ mt: 1 }}>
                {result.message}
              </Alert>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

// ── Sync card ─────────────────────────────────────────────────────────────────

interface SyncCardProps {
  onNotConfigured: () => void;
}

const SyncCard: React.FC<SyncCardProps> = ({ onNotConfigured }) => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<LdapSyncResult | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);

  const handleSync = async () => {
    setLoading(true);
    setResult(null);
    setSyncError(null);
    try {
      const syncResult = await ldapService.syncNow();
      setResult(syncResult);
      toast.success('LDAP sync completed.');
    } catch (err) {
      if (isLdapNotConfigured(err)) {
        onNotConfigured();
      } else {
        const msg = (err instanceof Error) ? err.message : 'Sync failed.';
        setSyncError(msg);
        toast.error(`LDAP sync error: ${msg}`);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card>
      <CardHeader
        avatar={<SyncAlt color="primary" />}
        title="Directory Sync"
        subheader="Import users, groups, and memberships from LDAP/AD into the local mirror"
      />
      <CardContent>
        <Button
          variant="contained"
          onClick={() => void handleSync()}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          {loading ? 'Syncing…' : 'Sync Now'}
        </Button>

        {syncError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {syncError}
          </Alert>
        )}

        {result && (
          <Box sx={{ mt: 2 }}>
            <Stack direction="row" spacing={1} sx={{ mb: 1.5 }} flexWrap="wrap" useFlexGap>
              {result.trigger && (
                <Chip label={`Trigger: ${result.trigger}`} size="small" variant="outlined" />
              )}
              {result.syncedAt && (
                <Chip label={`Synced at: ${formatDateTime(result.syncedAt)}`} size="small" variant="outlined" />
              )}
            </Stack>

            <Divider sx={{ mb: 1.5 }} />

            <Typography variant="subtitle2" gutterBottom>Users</Typography>
            <Stack direction="row" spacing={1} sx={{ mb: 1.5 }} flexWrap="wrap" useFlexGap>
              <Chip label={`Created: ${result.usersCreated}`} size="small" color={result.usersCreated > 0 ? 'success' : 'default'} />
              <Chip label={`Updated: ${result.usersUpdated}`} size="small" color={result.usersUpdated > 0 ? 'info' : 'default'} />
              <Chip label={`Disabled: ${result.usersDisabled}`} size="small" color={result.usersDisabled > 0 ? 'warning' : 'default'} />
              <Chip label={`Skipped: ${result.usersSkipped}`} size="small" />
            </Stack>

            <Typography variant="subtitle2" gutterBottom>Groups</Typography>
            <Stack direction="row" spacing={1} sx={{ mb: 1.5 }} flexWrap="wrap" useFlexGap>
              <Chip label={`Created: ${result.groupsCreated}`} size="small" color={result.groupsCreated > 0 ? 'success' : 'default'} />
              <Chip label={`Updated: ${result.groupsUpdated}`} size="small" color={result.groupsUpdated > 0 ? 'info' : 'default'} />
              <Chip label={`Disabled: ${result.groupsDisabled}`} size="small" color={result.groupsDisabled > 0 ? 'warning' : 'default'} />
              <Chip label={`Skipped: ${result.groupsSkipped}`} size="small" />
            </Stack>

            <Typography variant="subtitle2" gutterBottom>Memberships</Typography>
            <Stack direction="row" spacing={1} sx={{ mb: 1.5 }} flexWrap="wrap" useFlexGap>
              <Chip label={`Changed: ${result.membershipsChanged}`} size="small" color={result.membershipsChanged > 0 ? 'info' : 'default'} />
              <Chip label={`Unresolved members: ${result.unresolvedMembers}`} size="small" color={result.unresolvedMembers > 0 ? 'warning' : 'default'} />
            </Stack>

            {result.warnings && result.warnings.length > 0 && (
              <>
                <Divider sx={{ mb: 1.5 }} />
                <Typography variant="subtitle2" gutterBottom>
                  Warnings ({result.warnings.length})
                </Typography>
                <List dense disablePadding>
                  {result.warnings.map((w, i) => (
                    <ListItem key={i} disablePadding sx={{ pl: 0 }}>
                      <ListItemText
                        primary={w}
                        primaryTypographyProps={{ variant: 'body2', color: 'warning.main' }}
                      />
                    </ListItem>
                  ))}
                </List>
              </>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

// ── Page ──────────────────────────────────────────────────────────────────────

const LdapSyncPage: React.FC = () => {
  const [ldapNotConfigured, setLdapNotConfigured] = useState(false);

  const handleNotConfigured = () => {
    setLdapNotConfigured(true);
  };

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', py: 2 }}>
      <Typography variant="h5" gutterBottom>
        LDAP / Active Directory Sync
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Manage connectivity and synchronization with the configured LDAP or Active Directory server.
      </Typography>

      {ldapNotConfigured ? (
        <Alert severity="info">
          LDAP integration is not enabled for this Athena instance. To enable it, set{' '}
          <code>ecm.identity.provider=ldap</code> in the backend configuration and restart the
          server.
        </Alert>
      ) : (
        <Stack spacing={3}>
          <ConnectionCard onNotConfigured={handleNotConfigured} />
          <SyncCard onNotConfigured={handleNotConfigured} />
        </Stack>
      )}
    </Box>
  );
};

export default LdapSyncPage;
