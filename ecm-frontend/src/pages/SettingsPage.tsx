import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  FormControlLabel,
  Stack,
  Switch,
  Typography,
  Alert,
} from '@mui/material';
import { Security as SecurityIcon, OpenInNew as OpenInNewIcon } from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useAppDispatch, useAppSelector } from 'store';
import { setCompactMode, setSidebarAutoCollapse } from 'store/slices/uiSlice';
import authService from 'services/authService';
import apiService from 'services/api';

type CollaboraCapabilities = {
  reachable: boolean;
  productName?: string;
  productVersion?: string;
  productVersionHash?: string;
  serverId?: string;
  hasWopiAccessCheck?: boolean;
  hasSettingIframeSupport?: boolean;
  error?: string;
};

type CollaboraDiscoveryStatus = {
  reachable: boolean;
  lastLoadedAtMs?: number;
  cacheTtlSeconds?: number;
  extensionCount?: number;
  sampleActionsByExtension?: Record<string, string[]>;
  lastError?: string;
};

type WopiHealthResponse = {
  enabled: boolean;
  wopiHostUrl?: string;
  discoveryUrl?: string;
  capabilitiesUrl?: string;
  publicUrl?: string;
  discovery?: CollaboraDiscoveryStatus;
  capabilities?: CollaboraCapabilities;
};

const SettingsPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const sidebarAutoCollapse = useAppSelector((state) => state.ui.sidebarAutoCollapse);
  const compactMode = useAppSelector((state) => state.ui.compactMode);
  const [sessionVersion, setSessionVersion] = useState(0);
  const [wopiHealth, setWopiHealth] = useState<WopiHealthResponse | null>(null);
  const [wopiHealthLoading, setWopiHealthLoading] = useState(false);

  const env = useMemo(() => {
    return {
      apiBaseUrl: process.env.REACT_APP_API_URL || '/api/v1',
      keycloakUrl: process.env.REACT_APP_KEYCLOAK_URL || '',
      keycloakRealm: process.env.REACT_APP_KEYCLOAK_REALM || '',
      keycloakClientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || '',
    };
  }, []);

  const tokenParsed = (authService.getTokenParsed() || {}) as {
    exp?: number;
    iat?: number;
    sid?: string;
    amr?: string[];
    acr?: string;
    otpConfigured?: boolean;
  };
  const accessToken = authService.getToken();
  const hasMfaConfigured = tokenParsed.amr?.includes('otp') || tokenParsed.otpConfigured === true;
  const tokenExpiresAt = tokenParsed.exp ? new Date(tokenParsed.exp * 1000) : null;
  const tokenIssuedAt = tokenParsed.iat ? new Date(tokenParsed.iat * 1000) : null;
  const maskedToken =
    accessToken && accessToken.length > 24
      ? `${accessToken.slice(0, 12)}…${accessToken.slice(-8)}`
      : accessToken || '';

  const handleCopyDebugInfo = async () => {
    try {
      const debugInfo = {
        user: user
          ? {
              id: user.id,
              username: user.username,
              email: user.email,
              roles: user.roles,
            }
          : null,
        env,
        client: {
          userAgent: navigator.userAgent,
          locale: navigator.language,
        },
        timestamp: new Date().toISOString(),
      };

      await navigator.clipboard.writeText(JSON.stringify(debugInfo, null, 2));
      toast.success('Debug info copied');
    } catch (e) {
      toast.error('Failed to copy debug info');
    }
  };

  const handleCopyWopiHealth = async () => {
    try {
      if (!wopiHealth) {
        toast.error('No WOPI status available');
        return;
      }
      await navigator.clipboard.writeText(JSON.stringify(wopiHealth, null, 2));
      toast.success('WOPI status copied');
    } catch {
      toast.error('Failed to copy WOPI status');
    }
  };

  const handleCopyToken = async () => {
    try {
      if (!accessToken) {
        toast.error('No access token available');
        return;
      }
      await navigator.clipboard.writeText(accessToken);
      toast.success('Access token copied');
    } catch {
      toast.error('Failed to copy access token');
    }
  };

  const handleCopyAuthHeader = async () => {
    try {
      if (!accessToken) {
        toast.error('No access token available');
        return;
      }
      await navigator.clipboard.writeText(`Authorization: Bearer ${accessToken}`);
      toast.success('Authorization header copied');
    } catch {
      toast.error('Failed to copy authorization header');
    }
  };

  const handleRefreshToken = async () => {
    try {
      const refreshed = await authService.refreshToken();
      if (!refreshed) {
        toast.error('Failed to refresh token');
        return;
      }
      setSessionVersion((v) => v + 1);
      toast.success('Token refreshed');
    } catch {
      toast.error('Failed to refresh token');
    }
  };

  useEffect(() => {
    let cancelled = false;

    const loadWopiHealth = async () => {
      if (!authService.isAuthenticated()) {
        setWopiHealth(null);
        return;
      }

      try {
        setWopiHealthLoading(true);
        const resp = await apiService.get<WopiHealthResponse>('/integration/wopi/health');
        if (!cancelled) {
          setWopiHealth(resp);
        }
      } catch {
        if (!cancelled) {
          setWopiHealth(null);
        }
      } finally {
        if (!cancelled) {
          setWopiHealthLoading(false);
        }
      }
    };

    loadWopiHealth();
    return () => {
      cancelled = true;
    };
  }, [sessionVersion]);

  const discoveryStatus = wopiHealth?.discovery;
  const capabilities = wopiHealth?.capabilities;
  const discoveryLoadedAt = discoveryStatus?.lastLoadedAtMs
    ? new Date(discoveryStatus.lastLoadedAtMs).toLocaleString()
    : '—';

  return (
    <Box maxWidth={900}>
      <Typography variant="h5" gutterBottom>
        Settings
      </Typography>

      <Stack spacing={2}>
        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Account
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={1}>
              <Typography variant="body2">
                <strong>Username:</strong> {user?.username || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Email:</strong> {user?.email || '—'}
              </Typography>
              <Box>
                <Typography variant="body2" gutterBottom>
                  <strong>Roles:</strong>
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap">
                  {(user?.roles || []).length ? (
                    user?.roles.map((role) => <Chip key={role} size="small" label={role} sx={{ mb: 1 }} />)
                  ) : (
                    <Typography variant="body2">—</Typography>
                  )}
                </Stack>
              </Box>
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Session
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={1}>
              <Typography variant="body2">
                <strong>Authenticated:</strong> {authService.isAuthenticated() ? 'Yes' : 'No'}
              </Typography>
              <Typography variant="body2">
                <strong>Session ID:</strong> {tokenParsed.sid || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Issued At:</strong> {tokenIssuedAt ? tokenIssuedAt.toLocaleString() : '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Expires At:</strong> {tokenExpiresAt ? tokenExpiresAt.toLocaleString() : '—'}
              </Typography>
              <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
                <strong>Access Token:</strong> {maskedToken || '—'}
              </Typography>

              <Box mt={1} display="flex" gap={1} flexWrap="wrap">
                <Button variant="outlined" onClick={handleCopyToken} disabled={!accessToken}>
                  Copy Access Token
                </Button>
                <Button variant="outlined" onClick={handleCopyAuthHeader} disabled={!accessToken}>
                  Copy Authorization Header
                </Button>
                <Button variant="outlined" onClick={handleRefreshToken} disabled={!authService.isAuthenticated()}>
                  Refresh Token
                </Button>
              </Box>

              <Typography variant="caption" color="text.secondary">
                For local testing only. Avoid sharing tokens.
              </Typography>
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <SecurityIcon color="primary" />
              <Typography variant="h6">
                Multi-Factor Authentication (MFA)
              </Typography>
            </Box>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={2}>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="body2">
                  <strong>OTP Status:</strong>
                </Typography>
                <Chip
                  size="small"
                  label={hasMfaConfigured ? 'Configured' : 'Not Configured'}
                  color={hasMfaConfigured ? 'success' : 'warning'}
                  variant="outlined"
                />
              </Box>

              {tokenParsed.acr && (
                <Typography variant="body2">
                  <strong>Authentication Context (acr):</strong> {tokenParsed.acr}
                </Typography>
              )}

              {tokenParsed.amr && tokenParsed.amr.length > 0 && (
                <Box>
                  <Typography variant="body2" gutterBottom>
                    <strong>Authentication Methods (amr):</strong>
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    {tokenParsed.amr.map((method) => (
                      <Chip key={method} size="small" label={method} sx={{ mb: 0.5 }} />
                    ))}
                  </Stack>
                </Box>
              )}

              <Alert severity="info" sx={{ mt: 1 }}>
                <Typography variant="body2">
                  To enhance account security, configure Time-based One-Time Password (TOTP) authentication
                  using an authenticator app like Google Authenticator, Microsoft Authenticator, or FreeOTP.
                </Typography>
              </Alert>

              <Box display="flex" gap={1} flexWrap="wrap">
                <Button
                  variant="outlined"
                  startIcon={<OpenInNewIcon />}
                  href={`${env.keycloakUrl}/realms/${env.keycloakRealm}/account/#/security/signingin`}
                  target="_blank"
                  rel="noopener noreferrer"
                  disabled={!env.keycloakUrl || !env.keycloakRealm}
                >
                  Manage MFA in Keycloak
                </Button>
              </Box>

              <Typography variant="caption" color="text.secondary">
                MFA adds an extra layer of security by requiring a verification code from your mobile device.
              </Typography>
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Environment
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={1}>
              <Typography variant="body2">
                <strong>API Base URL:</strong> {env.apiBaseUrl}
              </Typography>
              <Typography variant="body2">
                <strong>Keycloak URL:</strong> {env.keycloakUrl || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Keycloak Realm:</strong> {env.keycloakRealm || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Keycloak Client ID:</strong> {env.keycloakClientId || '—'}
              </Typography>
            </Stack>

            <Box mt={2}>
              <Button variant="outlined" onClick={handleCopyDebugInfo}>
                Copy Debug Info
              </Button>
            </Box>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Online Editor (WOPI / Collabora)
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={1}>
              <Typography variant="body2">
                <strong>Enabled:</strong> {wopiHealth ? (wopiHealth.enabled ? 'Yes' : 'No') : '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Collabora:</strong>{' '}
                {capabilities?.reachable ? `${capabilities.productName || 'OK'} ${capabilities.productVersion || ''}` : '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Public URL:</strong> {wopiHealth?.publicUrl || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Discovery URL:</strong> {wopiHealth?.discoveryUrl || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>WOPI Host URL:</strong> {wopiHealth?.wopiHostUrl || '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Discovery Loaded:</strong>{' '}
                {discoveryStatus ? (discoveryStatus.reachable ? `Yes (${discoveryStatus.extensionCount || 0} extensions)` : 'No') : '—'}
              </Typography>
              <Typography variant="body2">
                <strong>Discovery Loaded At:</strong> {discoveryLoadedAt}
              </Typography>

              {(capabilities?.error || discoveryStatus?.lastError) && (
                <Typography variant="caption" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
                  {capabilities?.error ? `Capabilities error: ${capabilities.error}` : ''}
                  {capabilities?.error && discoveryStatus?.lastError ? ' | ' : ''}
                  {discoveryStatus?.lastError ? `Discovery error: ${discoveryStatus.lastError}` : ''}
                </Typography>
              )}

              <Box mt={1} display="flex" gap={1} flexWrap="wrap">
                <Button variant="outlined" onClick={handleCopyWopiHealth} disabled={!wopiHealth || wopiHealthLoading}>
                  Copy WOPI Status
                </Button>
                <Button
                  variant="outlined"
                  href={(wopiHealth?.capabilitiesUrl || wopiHealth?.publicUrl || '').replace(/\/+$/, '')}
                  target="_blank"
                  rel="noreferrer"
                  disabled={!wopiHealth?.publicUrl}
                >
                  Open Collabora
                </Button>
              </Box>

              <Typography variant="caption" color="text.secondary">
                Collabora CODE is a development build; branding/limit dialogs are controlled by the editor, not Athena.
              </Typography>
            </Stack>
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Layout
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <FormControlLabel
              control={<Switch checked={compactMode} onChange={(e) => dispatch(setCompactMode(e.target.checked))} />}
              label="Compact spacing (reduce paddings)"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={sidebarAutoCollapse}
                  onChange={(e) => dispatch(setSidebarAutoCollapse(e.target.checked))}
                />
              }
              label="Auto-hide sidebar after selecting a folder"
            />
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
};

export default SettingsPage;
