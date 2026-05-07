import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { Key, Refresh, VpnKey } from '@mui/icons-material';
import oauthCredentialAdminService, {
  OAuthCredentialInventoryItem,
} from 'services/oauthCredentialAdminService';

const PROVIDERS = ['', 'GOOGLE', 'MICROSOFT', 'CUSTOM'];

const formatDateTime = (value: string | null | undefined): string => {
  if (!value) {
    return '-';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
};

const statusChip = (label: string, active: boolean) => (
  <Chip
    label={label}
    color={active ? 'success' : 'default'}
    variant={active ? 'filled' : 'outlined'}
    size="small"
  />
);

const resolveErrorMessage = (err: unknown, fallback: string): string => {
  const responseMessage = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage.trim();
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
};

const OAuthCredentialAdminPage: React.FC = () => {
  const [credentials, setCredentials] = useState<OAuthCredentialInventoryItem[]>([]);
  const [ownerType, setOwnerType] = useState('');
  const [provider, setProvider] = useState('');
  const [loading, setLoading] = useState(false);
  const [reauthCredentialId, setReauthCredentialId] = useState<string | null>(null);
  const [refreshCredentialId, setRefreshCredentialId] = useState<string | null>(null);
  const [revokeCredentialId, setRevokeCredentialId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadCredentials = async (
    nextOwnerType = ownerType,
    nextProvider = provider,
    options: { preserveError?: boolean } = {}
  ) => {
    setLoading(true);
    if (!options.preserveError) {
      setError(null);
    }
    try {
      const result = await oauthCredentialAdminService.listCredentials({
        ownerType: nextOwnerType,
        provider: nextProvider,
      });
      setCredentials(result);
    } catch (err) {
      setError(resolveErrorMessage(err, 'Failed to load OAuth credential inventory.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    oauthCredentialAdminService.listCredentials()
      .then((result) => {
        if (active) {
          setCredentials(result);
        }
      })
      .catch((err) => {
        if (active) {
          setError(resolveErrorMessage(err, 'Failed to load OAuth credential inventory.'));
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
  }, []);

  const connectedCount = credentials.filter((credential) => credential.connected).length;
  const refreshTokenCount = credentials.filter((credential) => credential.refreshTokenStored).length;
  const credentialKeyCount = credentials.filter((credential) => credential.credentialKeyConfigured).length;

  const handleRequireReauth = async (credential: OAuthCredentialInventoryItem) => {
    const confirmed = window.confirm(
      `Clear stored OAuth tokens for ${credential.ownerType} ${credential.ownerId}? The owner must reconnect before token-based access can resume.`
    );
    if (!confirmed) {
      return;
    }
    setReauthCredentialId(credential.id);
    setError(null);
    try {
      const updated = await oauthCredentialAdminService.requireReauth(credential.id);
      setCredentials((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (err) {
      setError(resolveErrorMessage(err, 'Failed to require OAuth reauthorization.'));
    } finally {
      setReauthCredentialId(null);
    }
  };

  const handleRefreshNow = async (credential: OAuthCredentialInventoryItem) => {
    const confirmed = window.confirm(
      `Refresh OAuth token now for ${credential.ownerType} ${credential.ownerId}? If the provider rejects the refresh token, Athena may require the owner to reconnect.`
    );
    if (!confirmed) {
      return;
    }
    setRefreshCredentialId(credential.id);
    setError(null);
    try {
      const updated = await oauthCredentialAdminService.refreshNow(credential.id);
      setCredentials((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (err) {
      setError(resolveErrorMessage(err, 'Failed to refresh OAuth credential.'));
      await loadCredentials(ownerType, provider, { preserveError: true });
    } finally {
      setRefreshCredentialId(null);
    }
  };

  const handleRevoke = async (credential: OAuthCredentialInventoryItem) => {
    const confirmed = window.confirm(
      `Revoke OAuth token at the provider for ${credential.ownerType} ${credential.ownerId}? This contacts Google to invalidate the token. The owner must reconnect afterwards. This is different from Require Reauth, which only clears Athena's local copy.`
    );
    if (!confirmed) {
      return;
    }
    setRevokeCredentialId(credential.id);
    setError(null);
    try {
      const updated = await oauthCredentialAdminService.revoke(credential.id);
      setCredentials((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (err) {
      setError(resolveErrorMessage(err, 'Failed to revoke OAuth token at the provider.'));
      await loadCredentials(ownerType, provider, { preserveError: true });
    } finally {
      setRevokeCredentialId(null);
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            OAuth Credential Store
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Admin inventory and local reauthorization controls for generic OAuth credentials.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : <Refresh />}
          onClick={() => void loadCredentials()}
          disabled={loading}
        >
          Refresh
        </Button>
      </Stack>

      <Alert severity="info" sx={{ mb: 3 }}>
        Token values are never returned by this admin surface. Only storage and configuration flags are shown.
      </Alert>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 3 }}>
        <Card sx={{ flex: 1 }}>
          <CardHeader avatar={<VpnKey color="primary" />} title="Total Credentials" />
          <CardContent>
            <Typography variant="h4">{credentials.length}</Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: 1 }}>
          <CardHeader avatar={<Key color="success" />} title="Connected" />
          <CardContent>
            <Typography variant="h4">{connectedCount}</Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: 1 }}>
          <CardHeader title="Refresh Tokens" />
          <CardContent>
            <Typography variant="h4">{refreshTokenCount}</Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: 1 }}>
          <CardHeader title="Credential Keys" />
          <CardContent>
            <Typography variant="h4">{credentialKeyCount}</Typography>
          </CardContent>
        </Card>
      </Stack>

      <Card sx={{ mb: 3 }}>
        <CardHeader title="Filters" subheader="Filters are exact-match because owner types and providers are operational identifiers." />
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <TextField
              label="Owner type"
              value={ownerType}
              onChange={(event) => setOwnerType(event.target.value)}
              placeholder="MAIL_ACCOUNT"
              fullWidth
            />
            <TextField
              select
              SelectProps={{ native: true }}
              label="Provider"
              value={provider}
              onChange={(event) => setProvider(event.target.value)}
              fullWidth
            >
              {PROVIDERS.map((option) => (
                <option key={option || 'ALL'} value={option}>
                  {option || 'All providers'}
                </option>
              ))}
            </TextField>
            <Button
              variant="outlined"
              onClick={() => void loadCredentials()}
              disabled={loading}
              sx={{ minWidth: 140 }}
            >
              Apply Filters
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Credential Inventory" />
        <CardContent>
          {loading && credentials.length === 0 ? (
            <Stack alignItems="center" sx={{ py: 4 }}>
              <CircularProgress />
            </Stack>
          ) : credentials.length === 0 ? (
            <Alert severity="info">No OAuth credentials match the current filters.</Alert>
          ) : (
            <TableContainer>
              <Table aria-label="OAuth credential inventory" size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Owner</TableCell>
                    <TableCell>Provider</TableCell>
                    <TableCell>Connection</TableCell>
                    <TableCell>Configuration</TableCell>
                    <TableCell>Expires</TableCell>
                    <TableCell>Updated</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {credentials.map((credential) => (
                    <TableRow key={credential.id}>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {credential.ownerType}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {credential.ownerId}
                        </Typography>
                      </TableCell>
                      <TableCell>{credential.provider || '-'}</TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {statusChip('Connected', credential.connected)}
                          {statusChip('Access token', credential.accessTokenStored)}
                          {statusChip('Refresh token', credential.refreshTokenStored)}
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          {statusChip('Credential key', credential.credentialKeyConfigured)}
                          {statusChip('Endpoint', credential.tokenEndpointConfigured)}
                          {statusChip('Scope', credential.scopeConfigured)}
                          {statusChip('Tenant', credential.tenantIdConfigured)}
                        </Stack>
                      </TableCell>
                      <TableCell>{formatDateTime(credential.tokenExpiresAt)}</TableCell>
                      <TableCell>{formatDateTime(credential.updatedAt || credential.createdAt)}</TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={() => void handleRefreshNow(credential)}
                            disabled={
                              refreshCredentialId === credential.id
                              || reauthCredentialId === credential.id
                              || revokeCredentialId === credential.id
                              || (!credential.refreshTokenStored && !credential.credentialKeyConfigured)
                            }
                          >
                            {refreshCredentialId === credential.id ? 'Refreshing...' : 'Refresh Now'}
                          </Button>
                          <Button
                            variant="outlined"
                            color="warning"
                            size="small"
                            onClick={() => void handleRequireReauth(credential)}
                            disabled={
                              reauthCredentialId === credential.id
                              || refreshCredentialId === credential.id
                              || revokeCredentialId === credential.id
                              || (!credential.connected && !credential.accessTokenStored && !credential.refreshTokenStored)
                            }
                          >
                            {reauthCredentialId === credential.id ? 'Clearing...' : 'Require Reauth'}
                          </Button>
                          <Button
                            variant="outlined"
                            color="error"
                            size="small"
                            onClick={() => void handleRevoke(credential)}
                            disabled={
                              revokeCredentialId === credential.id
                              || refreshCredentialId === credential.id
                              || reauthCredentialId === credential.id
                              || credential.provider !== 'GOOGLE'
                              || (!credential.accessTokenStored && !credential.refreshTokenStored)
                            }
                          >
                            {revokeCredentialId === credential.id ? 'Revoking...' : 'Provider Revoke'}
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

export default OAuthCredentialAdminPage;
