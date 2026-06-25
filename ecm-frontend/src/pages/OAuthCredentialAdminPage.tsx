import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { Cancel, Key, Refresh, VpnKey } from '@mui/icons-material';
import oauthCredentialAdminService, {
  OAuthCredentialInventoryItem,
  OAuthCredentialRevokeEndpointDetails,
} from 'services/oauthCredentialAdminService';

const PROVIDERS = ['', 'GOOGLE', 'MICROSOFT', 'CUSTOM'];
const OWNER_TYPE_FILTER_QUERY_PARAM = 'ownerType';
const PROVIDER_FILTER_QUERY_PARAM = 'provider';
const REVOKE_CAPABILITY_FILTER_QUERY_PARAM = 'revokeCapability';
const REVOKE_CAPABILITY_FILTER_QUERY_VALUES = {
  READY: 'ready',
  BLOCKED: 'blocked',
  CUSTOM_ENDPOINT_GAP: 'custom-endpoint-gap',
  ENV_MANAGED_ONLY: 'env-managed-only',
} as const;

type RevokeCapabilityFilter = 'ALL' | 'READY' | 'BLOCKED' | 'CUSTOM_ENDPOINT_GAP' | 'ENV_MANAGED_ONLY';

const resolveProviderFilter = (value: string | null): string => (
  PROVIDERS.includes(value ?? '') ? (value ?? '') : ''
);

const resolveRevokeCapabilityFilter = (value: string | null): RevokeCapabilityFilter => {
  switch ((value ?? '').toLowerCase()) {
    case REVOKE_CAPABILITY_FILTER_QUERY_VALUES.READY:
      return 'READY';
    case REVOKE_CAPABILITY_FILTER_QUERY_VALUES.BLOCKED:
      return 'BLOCKED';
    case REVOKE_CAPABILITY_FILTER_QUERY_VALUES.CUSTOM_ENDPOINT_GAP:
      return 'CUSTOM_ENDPOINT_GAP';
    case REVOKE_CAPABILITY_FILTER_QUERY_VALUES.ENV_MANAGED_ONLY:
      return 'ENV_MANAGED_ONLY';
    default:
      return 'ALL';
  }
};

const revokeCapabilityFilterQueryValue = (filter: RevokeCapabilityFilter): string | null => {
  if (filter === 'ALL') {
    return null;
  }
  return REVOKE_CAPABILITY_FILTER_QUERY_VALUES[filter];
};

const buildCredentialFilters = (ownerType: string, provider: string) => {
  const filters: { ownerType?: string; provider?: string } = {};
  if (ownerType) {
    filters.ownerType = ownerType;
  }
  if (provider) {
    filters.provider = provider;
  }
  return Object.keys(filters).length > 0 ? filters : undefined;
};

const isCustomRevokeEndpointGap = (credential: OAuthCredentialInventoryItem): boolean => (
  credential.provider === 'CUSTOM'
  && !credential.providerRevokeSupported
  && (credential.providerRevokeUnsupportedReason ?? '').toLowerCase().includes('revoke endpoint')
);

const isEnvManagedOnlyCredential = (credential: OAuthCredentialInventoryItem): boolean => (
  credential.credentialKeyConfigured
  && !credential.accessTokenStored
  && !credential.refreshTokenStored
);

const matchesRevokeCapabilityFilter = (
  credential: OAuthCredentialInventoryItem,
  filter: RevokeCapabilityFilter
): boolean => {
  switch (filter) {
    case 'READY':
      return credential.providerRevokeSupported;
    case 'BLOCKED':
      return !credential.providerRevokeSupported;
    case 'CUSTOM_ENDPOINT_GAP':
      return isCustomRevokeEndpointGap(credential);
    case 'ENV_MANAGED_ONLY':
      return isEnvManagedOnlyCredential(credential);
    case 'ALL':
    default:
      return true;
  }
};

const revokeCapabilityFilterLabel = (filter: RevokeCapabilityFilter): string => {
  switch (filter) {
    case 'READY':
      return 'Showing credentials where OAuth revoke/local-clear is currently actionable.';
    case 'BLOCKED':
      return 'Showing credentials where OAuth revoke/local-clear is blocked by backend capability metadata.';
    case 'CUSTOM_ENDPOINT_GAP':
      return 'Showing CUSTOM credentials where provider-side revoke is blocked by a missing revoke endpoint.';
    case 'ENV_MANAGED_ONLY':
      return 'Showing credentials that only reference env-managed secrets and have no local token to revoke.';
    case 'ALL':
    default:
      return '';
  }
};

const revokeCapabilityFilterChipLabel = (filter: RevokeCapabilityFilter): string => {
  switch (filter) {
    case 'READY':
      return 'Revoke action ready';
    case 'BLOCKED':
      return 'Revoke action blocked';
    case 'CUSTOM_ENDPOINT_GAP':
      return 'CUSTOM revoke gaps';
    case 'ENV_MANAGED_ONLY':
      return 'Env-managed only';
    case 'ALL':
    default:
      return '';
  }
};

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

const isLocalClearRevoke = (credential: OAuthCredentialInventoryItem): boolean => (
  credential.providerRevokeMode === 'LOCAL_CLEAR'
);

const revokeActionLabel = (credential: OAuthCredentialInventoryItem): string => (
  isLocalClearRevoke(credential) ? 'Local Clear' : 'Provider Revoke'
);

const revokeActionTooltip = (credential: OAuthCredentialInventoryItem): string => {
  if (!credential.providerRevokeSupported) {
    return credential.providerRevokeUnsupportedReason
      ?? 'OAuth revoke/local-clear is not supported for this credential.';
  }
  if (isLocalClearRevoke(credential)) {
    return 'Clears Athena-local Microsoft OAuth tokens only. Entra sessions remain until expiry or a separate Graph revokeSignInSessions action.';
  }
  return '';
};

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
  const [searchParams, setSearchParams] = useSearchParams();
  const queryOwnerType = (searchParams.get(OWNER_TYPE_FILTER_QUERY_PARAM) ?? '').trim();
  const queryProvider = resolveProviderFilter(searchParams.get(PROVIDER_FILTER_QUERY_PARAM));
  const [credentials, setCredentials] = useState<OAuthCredentialInventoryItem[]>([]);
  const [ownerType, setOwnerType] = useState(queryOwnerType);
  const [provider, setProvider] = useState(queryProvider);
  const [loading, setLoading] = useState(false);
  const [reauthCredentialId, setReauthCredentialId] = useState<string | null>(null);
  const [refreshCredentialId, setRefreshCredentialId] = useState<string | null>(null);
  const [revokeCredentialId, setRevokeCredentialId] = useState<string | null>(null);
  const [revokeEndpointCredential, setRevokeEndpointCredential] = useState<OAuthCredentialInventoryItem | null>(null);
  const [revokeEndpointDetails, setRevokeEndpointDetails] =
    useState<OAuthCredentialRevokeEndpointDetails | null>(null);
  const [revokeEndpointValue, setRevokeEndpointValue] = useState('');
  const [revokeEndpointDetailsLoading, setRevokeEndpointDetailsLoading] = useState(false);
  const [revokeEndpointDetailsError, setRevokeEndpointDetailsError] = useState<string | null>(null);
  const [revokeEndpointSubmitting, setRevokeEndpointSubmitting] = useState(false);
  const [revokeEndpointAction, setRevokeEndpointAction] = useState<'SAVE' | 'CLEAR' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const revokeCapabilityFilter = resolveRevokeCapabilityFilter(
    searchParams.get(REVOKE_CAPABILITY_FILTER_QUERY_PARAM)
  );

  const handleServerFiltersApply = () => {
    const nextOwnerType = ownerType.trim();
    const nextProvider = provider.trim();
    const filtersChanged = nextOwnerType !== queryOwnerType || nextProvider !== queryProvider;
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (nextOwnerType) {
        next.set(OWNER_TYPE_FILTER_QUERY_PARAM, nextOwnerType);
      } else {
        next.delete(OWNER_TYPE_FILTER_QUERY_PARAM);
      }
      if (nextProvider) {
        next.set(PROVIDER_FILTER_QUERY_PARAM, nextProvider);
      } else {
        next.delete(PROVIDER_FILTER_QUERY_PARAM);
      }
      return next;
    }, { replace: true });
    if (!filtersChanged) {
      void loadCredentials(nextOwnerType, nextProvider);
    }
  };

  const handleRevokeCapabilityFilterChange = (nextFilter: RevokeCapabilityFilter) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      const queryValue = revokeCapabilityFilterQueryValue(nextFilter);
      if (queryValue) {
        next.set(REVOKE_CAPABILITY_FILTER_QUERY_PARAM, queryValue);
      } else {
        next.delete(REVOKE_CAPABILITY_FILTER_QUERY_PARAM);
      }
      return next;
    }, { replace: true });
  };

  const handleQueryFilterClear = (queryParam: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete(queryParam);
      return next;
    }, { replace: true });
  };

  const handleAllQueryFiltersClear = () => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete(OWNER_TYPE_FILTER_QUERY_PARAM);
      next.delete(PROVIDER_FILTER_QUERY_PARAM);
      next.delete(REVOKE_CAPABILITY_FILTER_QUERY_PARAM);
      return next;
    }, { replace: true });
  };

  const loadCredentials = async (
    nextOwnerType = queryOwnerType,
    nextProvider = queryProvider,
    options: { preserveError?: boolean } = {}
  ) => {
    setLoading(true);
    if (!options.preserveError) {
      setError(null);
    }
    try {
      const filters = buildCredentialFilters(nextOwnerType, nextProvider);
      const result = filters
        ? await oauthCredentialAdminService.listCredentials(filters)
        : await oauthCredentialAdminService.listCredentials();
      setCredentials(result);
    } catch (err) {
      setError(resolveErrorMessage(err, 'Failed to load OAuth credential inventory.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setOwnerType(queryOwnerType);
    setProvider(queryProvider);
  }, [queryOwnerType, queryProvider]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    const filters = buildCredentialFilters(queryOwnerType, queryProvider);
    const request = filters
      ? oauthCredentialAdminService.listCredentials(filters)
      : oauthCredentialAdminService.listCredentials();
    request
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
  }, [queryOwnerType, queryProvider]);

  const revokeEndpointCredentialId = revokeEndpointCredential?.id ?? null;

  useEffect(() => {
    if (!revokeEndpointCredentialId) {
      setRevokeEndpointDetails(null);
      setRevokeEndpointDetailsLoading(false);
      setRevokeEndpointDetailsError(null);
      return undefined;
    }
    let active = true;
    setRevokeEndpointDetails(null);
    setRevokeEndpointDetailsLoading(true);
    setRevokeEndpointDetailsError(null);
    setRevokeEndpointValue('');
    oauthCredentialAdminService.getRevokeEndpointDetails(revokeEndpointCredentialId)
      .then((details) => {
        if (active) {
          setRevokeEndpointDetails(details);
          setRevokeEndpointValue(details.revokeEndpoint ?? '');
        }
      })
      .catch((err) => {
        if (active) {
          setRevokeEndpointDetailsError(resolveErrorMessage(
            err,
            'Failed to load the stored CUSTOM revoke endpoint.'
          ));
        }
      })
      .finally(() => {
        if (active) {
          setRevokeEndpointDetailsLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [revokeEndpointCredentialId]);

  const connectedCount = credentials.filter((credential) => credential.connected).length;
  const refreshTokenCount = credentials.filter((credential) => credential.refreshTokenStored).length;
  const credentialKeyCount = credentials.filter((credential) => credential.credentialKeyConfigured).length;
  const providerRevokeReadyCount = credentials.filter((credential) => credential.providerRevokeSupported).length;
  const providerRevokeBlockedCount = credentials.filter((credential) => !credential.providerRevokeSupported).length;
  const customRevokeEndpointGapCount = credentials.filter(isCustomRevokeEndpointGap).length;
  const envManagedOnlyCount = credentials.filter(isEnvManagedOnlyCredential).length;
  const visibleCredentials = credentials.filter((credential) => (
    matchesRevokeCapabilityFilter(credential, revokeCapabilityFilter)
  ));
  const emptyInventoryMessage = (() => {
    switch (revokeCapabilityFilter) {
      case 'READY':
        return 'No OAuth credentials are currently ready for revoke/local-clear action.';
      case 'BLOCKED':
        return 'No OAuth credentials are currently blocked from revoke/local-clear action.';
      case 'CUSTOM_ENDPOINT_GAP':
        return 'No CUSTOM credentials currently need a revoke endpoint.';
      case 'ENV_MANAGED_ONLY':
        return 'No OAuth credentials currently depend only on env-managed secrets.';
      case 'ALL':
      default:
        return 'No OAuth credentials match the current filters.';
    }
  })();
  const activeRevokeCapabilityFilterDescription = revokeCapabilityFilterLabel(revokeCapabilityFilter);
  const activeRevokeCapabilityFilterChipLabel = revokeCapabilityFilterChipLabel(revokeCapabilityFilter);
  const hasActiveQueryFilters = Boolean(
    queryOwnerType || queryProvider || activeRevokeCapabilityFilterChipLabel
  );

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
    const confirmed = window.confirm(isLocalClearRevoke(credential)
      ? `Clear Athena-local Microsoft OAuth tokens for ${credential.ownerType} ${credential.ownerId}? Athena will stop using this credential immediately. This does not revoke the user's Entra sign-in sessions; those remain until expiry or a separate Microsoft Graph revokeSignInSessions action.`
      : `Revoke OAuth token at the provider for ${credential.ownerType} ${credential.ownerId}? This contacts the provider to invalidate the token. The owner must reconnect afterwards. This is different from Require Reauth, which only clears Athena's local copy.`
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
      setError(resolveErrorMessage(err, isLocalClearRevoke(credential)
        ? 'Failed to clear local Microsoft OAuth tokens.'
        : 'Failed to revoke OAuth token at the provider.'
      ));
      await loadCredentials(ownerType, provider, { preserveError: true });
    } finally {
      setRevokeCredentialId(null);
    }
  };

  const openRevokeEndpointDialog = (credential: OAuthCredentialInventoryItem) => {
    setRevokeEndpointCredential(credential);
    setRevokeEndpointValue('');
    setError(null);
  };

  const closeRevokeEndpointDialog = () => {
    if (revokeEndpointSubmitting) {
      return;
    }
    setRevokeEndpointCredential(null);
    setRevokeEndpointDetails(null);
    setRevokeEndpointDetailsError(null);
    setRevokeEndpointValue('');
  };

  const submitRevokeEndpoint = async (
    nextRevokeEndpoint: string,
    action: 'SAVE' | 'CLEAR'
  ) => {
    if (!revokeEndpointCredential) {
      return;
    }
    setRevokeEndpointSubmitting(true);
    setRevokeEndpointAction(action);
    setError(null);
    try {
      const updated = await oauthCredentialAdminService.updateRevokeEndpoint(
        revokeEndpointCredential.id,
        nextRevokeEndpoint
      );
      setCredentials((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setRevokeEndpointCredential(null);
      setRevokeEndpointDetails(null);
      setRevokeEndpointDetailsError(null);
      setRevokeEndpointValue('');
    } catch (err) {
      setError(resolveErrorMessage(err, 'Failed to update OAuth revoke endpoint.'));
    } finally {
      setRevokeEndpointSubmitting(false);
      setRevokeEndpointAction(null);
    }
  };

  const handleSaveRevokeEndpoint = async () => {
    await submitRevokeEndpoint(revokeEndpointValue, 'SAVE');
  };

  const handleClearRevokeEndpoint = async () => {
    await submitRevokeEndpoint('', 'CLEAR');
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
          onClick={() => void loadCredentials(queryOwnerType, queryProvider)}
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
        <Card sx={{ flex: 1 }}>
          <CardHeader title="CUSTOM Revoke Gaps" />
          <CardContent>
            <Typography variant="h4">{customRevokeEndpointGapCount}</Typography>
          </CardContent>
        </Card>
        <Card sx={{ flex: 1 }}>
          <CardHeader title="Env-managed Only" />
          <CardContent>
            <Typography variant="h4">{envManagedOnlyCount}</Typography>
          </CardContent>
        </Card>
      </Stack>

      <Card sx={{ mb: 3 }}>
        <CardHeader title="Filters" subheader="Filters are exact-match because owner types and providers are operational identifiers." />
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ xs: 'stretch', md: 'center' }}>
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
              onClick={handleServerFiltersApply}
              disabled={loading}
              sx={{ minWidth: 140 }}
            >
              Apply Filters
            </Button>
            <Button
              variant={revokeCapabilityFilter === 'ALL' ? 'contained' : 'outlined'}
              onClick={() => handleRevokeCapabilityFilterChange('ALL')}
              aria-pressed={revokeCapabilityFilter === 'ALL'}
              sx={{ minWidth: 120 }}
            >
              All ({credentials.length})
            </Button>
            <Button
              variant={revokeCapabilityFilter === 'READY' ? 'contained' : 'outlined'}
              color="success"
              onClick={() => handleRevokeCapabilityFilterChange('READY')}
              aria-pressed={revokeCapabilityFilter === 'READY'}
              sx={{ minWidth: 210 }}
            >
              Revoke action ready ({providerRevokeReadyCount})
            </Button>
            <Button
              variant={revokeCapabilityFilter === 'BLOCKED' ? 'contained' : 'outlined'}
              color="warning"
              onClick={() => handleRevokeCapabilityFilterChange('BLOCKED')}
              aria-pressed={revokeCapabilityFilter === 'BLOCKED'}
              sx={{ minWidth: 220 }}
            >
              Revoke action blocked ({providerRevokeBlockedCount})
            </Button>
            <Button
              variant={revokeCapabilityFilter === 'CUSTOM_ENDPOINT_GAP' ? 'contained' : 'outlined'}
              color="warning"
              onClick={() => handleRevokeCapabilityFilterChange('CUSTOM_ENDPOINT_GAP')}
              aria-pressed={revokeCapabilityFilter === 'CUSTOM_ENDPOINT_GAP'}
              sx={{ minWidth: 220 }}
            >
              CUSTOM revoke gaps ({customRevokeEndpointGapCount})
            </Button>
            <Button
              variant={revokeCapabilityFilter === 'ENV_MANAGED_ONLY' ? 'contained' : 'outlined'}
              color="warning"
              onClick={() => handleRevokeCapabilityFilterChange('ENV_MANAGED_ONLY')}
              aria-pressed={revokeCapabilityFilter === 'ENV_MANAGED_ONLY'}
              sx={{ minWidth: 210 }}
            >
              Env-managed only ({envManagedOnlyCount})
            </Button>
          </Stack>
          {activeRevokeCapabilityFilterDescription && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              {activeRevokeCapabilityFilterDescription}
            </Typography>
          )}
          {hasActiveQueryFilters && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                Active filters
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                {queryOwnerType && (
                  <Chip
                    label={`Owner type: ${queryOwnerType}`}
                    onDelete={() => handleQueryFilterClear(OWNER_TYPE_FILTER_QUERY_PARAM)}
                    deleteIcon={<Cancel aria-label="Clear owner type filter" />}
                    size="small"
                    variant="outlined"
                  />
                )}
                {queryProvider && (
                  <Chip
                    label={`Provider: ${queryProvider}`}
                    onDelete={() => handleQueryFilterClear(PROVIDER_FILTER_QUERY_PARAM)}
                    deleteIcon={<Cancel aria-label="Clear provider filter" />}
                    size="small"
                    variant="outlined"
                  />
                )}
                {activeRevokeCapabilityFilterChipLabel && (
                  <Chip
                    label={`Revoke capability: ${activeRevokeCapabilityFilterChipLabel}`}
                    onDelete={() => handleQueryFilterClear(REVOKE_CAPABILITY_FILTER_QUERY_PARAM)}
                    deleteIcon={<Cancel aria-label="Clear revoke capability filter" />}
                    size="small"
                    variant="outlined"
                  />
                )}
                <Button size="small" onClick={handleAllQueryFiltersClear}>
                  Clear all filters
                </Button>
              </Stack>
            </Box>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Credential Inventory" />
        <CardContent>
          {loading && credentials.length === 0 ? (
            <Stack alignItems="center" sx={{ py: 4 }}>
              <CircularProgress />
            </Stack>
          ) : visibleCredentials.length === 0 ? (
            <Alert severity="info">{emptyInventoryMessage}</Alert>
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
                  {visibleCredentials.map((credential) => (
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
                          {statusChip('Revoke endpoint', credential.revokeEndpointConfigured)}
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
                          {credential.provider === 'CUSTOM' && (
                            <Button
                              variant="outlined"
                              size="small"
                              onClick={() => openRevokeEndpointDialog(credential)}
                              disabled={
                                revokeEndpointSubmitting
                                || revokeCredentialId === credential.id
                                || refreshCredentialId === credential.id
                                || reauthCredentialId === credential.id
                              }
                            >
                              Configure Revoke Endpoint
                            </Button>
                          )}
                          <Tooltip
                            title={revokeActionTooltip(credential)}
                          >
                            <span
                              data-testid={`provider-revoke-wrapper-${credential.id}`}
                              aria-label={
                                !credential.providerRevokeSupported
                                  ? (credential.providerRevokeUnsupportedReason
                                    ?? 'OAuth revoke/local-clear is not supported for this credential.')
                                  : undefined
                              }
                            >
                              <Button
                                variant="outlined"
                                color="error"
                                size="small"
                                onClick={() => void handleRevoke(credential)}
                                disabled={
                                  revokeCredentialId === credential.id
                                  || refreshCredentialId === credential.id
                                  || reauthCredentialId === credential.id
                                  || !credential.providerRevokeSupported
                                }
                              >
                                {revokeCredentialId === credential.id
                                  ? (isLocalClearRevoke(credential) ? 'Clearing...' : 'Revoking...')
                                  : revokeActionLabel(credential)}
                              </Button>
                            </span>
                          </Tooltip>
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

      <Dialog
        open={Boolean(revokeEndpointCredential)}
        onClose={closeRevokeEndpointDialog}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Configure CUSTOM Revoke Endpoint</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Store a HTTPS RFC 7009-style revoke endpoint for this CUSTOM credential. Inventory still returns only a
              boolean flag; this dialog loads the persisted URL through an explicit admin detail request.
            </Typography>
            {revokeEndpointCredential && (
              <Stack spacing={1}>
                <Typography variant="caption" color="text.secondary">
                  {revokeEndpointCredential.ownerType} {revokeEndpointCredential.ownerId}
                </Typography>
                {revokeEndpointDetailsLoading ? (
                  <Alert severity="info">Loading stored revoke endpoint...</Alert>
                ) : revokeEndpointDetailsError ? (
                  <Alert severity="error">{revokeEndpointDetailsError}</Alert>
                ) : (
                  <Alert severity={revokeEndpointDetails?.revokeEndpointConfigured ? 'info' : 'warning'}>
                    {revokeEndpointDetails?.revokeEndpointConfigured
                      ? 'The persisted revoke endpoint is loaded below. Review, replace, or clear it explicitly.'
                      : 'No persisted revoke endpoint is currently configured for this CUSTOM credential.'}
                  </Alert>
                )}
                <Typography variant="caption" color="text.secondary">
                  Env fallback revoke endpoints are not displayed here; only the persisted per-credential URL is shown.
                </Typography>
              </Stack>
            )}
            <TextField
              label="Revoke endpoint"
              value={revokeEndpointValue}
              onChange={(event) => setRevokeEndpointValue(event.target.value)}
              placeholder="https://provider.example/oauth/revoke"
              fullWidth
              autoFocus
              disabled={revokeEndpointDetailsLoading}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeRevokeEndpointDialog} disabled={revokeEndpointSubmitting}>
            Cancel
          </Button>
          {revokeEndpointCredential?.revokeEndpointConfigured && (
            <Button
              onClick={() => void handleClearRevokeEndpoint()}
              disabled={revokeEndpointSubmitting || revokeEndpointDetailsLoading}
              color="warning"
            >
              {revokeEndpointAction === 'CLEAR' ? 'Clearing...' : 'Clear Endpoint'}
            </Button>
          )}
          <Button
            onClick={() => void handleSaveRevokeEndpoint()}
            disabled={revokeEndpointSubmitting || revokeEndpointDetailsLoading}
            variant="contained"
          >
            {revokeEndpointAction === 'SAVE' ? 'Saving...' : 'Save Endpoint'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default OAuthCredentialAdminPage;
