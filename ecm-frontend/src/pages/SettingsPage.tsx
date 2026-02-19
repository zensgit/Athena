import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Typography,
  Alert,
} from '@mui/material';
import { Security as SecurityIcon, OpenInNew as OpenInNewIcon } from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useAppDispatch, useAppSelector } from 'store';
import { setCompactMode, setSidebarAutoCollapse } from 'store/slices/uiSlice';
import authService from 'services/authService';
import apiService from 'services/api';
import {
  isAuthRecoveryDebugEnabled,
  isAuthRecoveryDebugLocalEnabled,
  setAuthRecoveryDebugLocalEnabled as persistAuthRecoveryDebugLocalEnabled,
} from 'utils/authRecoveryDebug';

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

type MfaStatus = {
  username: string;
  configured: boolean;
  enabled: boolean;
  lastVerifiedAt?: string | null;
  recoveryCodesGeneratedAt?: string | null;
};

type MfaEnrollment = {
  username: string;
  secret: string;
  otpauthUri: string;
  recoveryCodes: string[];
};

type MfaVerification = {
  verified: boolean;
  enabled: boolean;
  lastVerifiedAt?: string | null;
};

type MfaDisableResult = {
  disabled: boolean;
  enabled: boolean;
  remainingRecoveryCodes: number;
};

const SettingsPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const user = useAppSelector((state) => state.auth.user);
  const sidebarAutoCollapse = useAppSelector((state) => state.ui.sidebarAutoCollapse);
  const compactMode = useAppSelector((state) => state.ui.compactMode);
  const [sessionVersion, setSessionVersion] = useState(0);
  const [wopiHealth, setWopiHealth] = useState<WopiHealthResponse | null>(null);
  const [wopiHealthLoading, setWopiHealthLoading] = useState(false);
  const [mfaStatus, setMfaStatus] = useState<MfaStatus | null>(null);
  const [mfaLoading, setMfaLoading] = useState(false);
  const [mfaEnrollment, setMfaEnrollment] = useState<MfaEnrollment | null>(null);
  const [mfaDialogOpen, setMfaDialogOpen] = useState(false);
  const [mfaDisableDialogOpen, setMfaDisableDialogOpen] = useState(false);
  const [mfaCodeInput, setMfaCodeInput] = useState('');
  const [mfaDisableCode, setMfaDisableCode] = useState('');
  const [authRecoveryDebugLocalEnabled, setAuthRecoveryDebugLocalEnabled] = useState(false);
  const [authRecoveryDebugEffectiveEnabled, setAuthRecoveryDebugEffectiveEnabled] = useState(false);

  const env = useMemo(() => {
    return {
      apiBaseUrl: process.env.REACT_APP_API_URL
        || process.env.REACT_APP_API_BASE_URL
        || '/api/v1',
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
        diagnostics: {
          authRecoveryDebugLocalEnabled,
          authRecoveryDebugEffectiveEnabled,
        },
        timestamp: new Date().toISOString(),
      };

      await navigator.clipboard.writeText(JSON.stringify(debugInfo, null, 2));
      toast.success('Debug info copied');
    } catch (e) {
      toast.error('Failed to copy debug info');
    }
  };

  const refreshAuthRecoveryDebugState = useCallback(() => {
    setAuthRecoveryDebugLocalEnabled(isAuthRecoveryDebugLocalEnabled());
    setAuthRecoveryDebugEffectiveEnabled(isAuthRecoveryDebugEnabled());
  }, []);

  const handleToggleAuthRecoveryDebug = useCallback((enabled: boolean) => {
    persistAuthRecoveryDebugLocalEnabled(enabled);
    refreshAuthRecoveryDebugState();
    toast.success(
      enabled
        ? 'Auth recovery debug logs enabled for this browser.'
        : 'Auth recovery debug logs disabled for this browser.'
    );
  }, [refreshAuthRecoveryDebugState]);

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

  const loadMfaStatus = async () => {
    if (!authService.isAuthenticated()) {
      setMfaStatus(null);
      return;
    }
    try {
      setMfaLoading(true);
      const resp = await apiService.get<MfaStatus>('/mfa/status');
      setMfaStatus(resp);
    } catch {
      setMfaStatus(null);
    } finally {
      setMfaLoading(false);
    }
  };

  const handleStartMfaEnrollment = async () => {
    try {
      setMfaLoading(true);
      const resp = await apiService.post<MfaEnrollment>('/mfa/enroll', {});
      setMfaEnrollment(resp);
      setMfaCodeInput('');
      setMfaDialogOpen(true);
      toast.success('MFA enrollment started');
    } catch {
      toast.error('Failed to start MFA enrollment');
    } finally {
      setMfaLoading(false);
    }
  };

  const handleVerifyMfa = async () => {
    if (!mfaCodeInput.trim()) {
      toast.error('Enter a verification code');
      return;
    }
    try {
      setMfaLoading(true);
      const resp = await apiService.post<MfaVerification>('/mfa/verify', { code: mfaCodeInput.trim() });
      if (!resp.verified) {
        toast.error('Invalid verification code');
        return;
      }
      toast.success('MFA enabled');
      setMfaDialogOpen(false);
      await loadMfaStatus();
    } catch {
      toast.error('Failed to verify MFA');
    } finally {
      setMfaLoading(false);
    }
  };

  const handleDisableMfa = async () => {
    if (!mfaDisableCode.trim()) {
      toast.error('Enter a verification or recovery code');
      return;
    }
    try {
      setMfaLoading(true);
      const resp = await apiService.post<MfaDisableResult>('/mfa/disable', { code: mfaDisableCode.trim() });
      if (!resp.disabled) {
        toast.error('Failed to disable MFA');
        return;
      }
      toast.success('MFA disabled');
      setMfaDisableDialogOpen(false);
      setMfaDisableCode('');
      await loadMfaStatus();
    } catch {
      toast.error('Failed to disable MFA');
    } finally {
      setMfaLoading(false);
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

  useEffect(() => {
    loadMfaStatus();
  }, [sessionVersion]);

  useEffect(() => {
    refreshAuthRecoveryDebugState();
  }, [refreshAuthRecoveryDebugState]);

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
            <Typography variant="h6" gutterBottom>
              Diagnostics
            </Typography>
            <Divider sx={{ mb: 2 }} />

            <Stack spacing={1}>
              <FormControlLabel
                control={(
                  <Switch
                    checked={authRecoveryDebugLocalEnabled}
                    onChange={(event) => handleToggleAuthRecoveryDebug(event.target.checked)}
                  />
                )}
                label="Enable auth recovery debug logs"
              />
              <Typography variant="body2">
                <strong>Effective status:</strong> {authRecoveryDebugEffectiveEnabled ? 'Enabled' : 'Disabled'}
              </Typography>
              {authRecoveryDebugEffectiveEnabled && !authRecoveryDebugLocalEnabled && (
                <Alert severity="info" sx={{ mt: 1 }}>
                  Debug logs are enabled by environment or URL query override.
                </Alert>
              )}
              <Typography variant="caption" color="text.secondary">
                When enabled, auth recovery events are logged to the browser console with prefix
                {' '}<code>[auth-recovery]</code>. Sensitive fields are redacted automatically.
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

              <Divider />

              <Typography variant="subtitle2">Local MFA (TOTP)</Typography>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                <Typography variant="body2">
                  <strong>Local MFA:</strong>
                </Typography>
                <Chip
                  size="small"
                  label={mfaStatus?.enabled ? 'Enabled' : 'Not Enabled'}
                  color={mfaStatus?.enabled ? 'success' : 'warning'}
                  variant="outlined"
                />
                {mfaStatus?.lastVerifiedAt && (
                  <Typography variant="caption" color="text.secondary">
                    Last verified {new Date(mfaStatus.lastVerifiedAt).toLocaleString()}
                  </Typography>
                )}
              </Box>

              <Box display="flex" gap={1} flexWrap="wrap">
                <Button
                  variant="outlined"
                  onClick={handleStartMfaEnrollment}
                  disabled={mfaLoading}
                >
                  Set up Local MFA
                </Button>
                <Button
                  variant="outlined"
                  color="warning"
                  onClick={() => setMfaDisableDialogOpen(true)}
                  disabled={!mfaStatus?.enabled || mfaLoading}
                >
                  Disable Local MFA
                </Button>
              </Box>

              <Typography variant="caption" color="text.secondary">
                Local MFA protects sensitive actions within Athena. For organization-wide MFA, use Keycloak.
              </Typography>

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

      <Dialog
        open={mfaDialogOpen}
        onClose={() => setMfaDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Set up Local MFA</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {mfaEnrollment ? (
              <>
                <Alert severity="info">
                  Scan the QR code URL in your authenticator app, then enter the verification code below.
                </Alert>
                <TextField
                  label="TOTP Secret"
                  value={mfaEnrollment.secret}
                  fullWidth
                  InputProps={{ readOnly: true }}
                />
                <Button
                  variant="outlined"
                  onClick={async () => {
                    await navigator.clipboard.writeText(mfaEnrollment.secret);
                    toast.success('Secret copied');
                  }}
                >
                  Copy Secret
                </Button>
                <TextField
                  label="OTPAuth URI"
                  value={mfaEnrollment.otpauthUri}
                  fullWidth
                  multiline
                  minRows={2}
                  InputProps={{ readOnly: true }}
                />
                <Button
                  variant="outlined"
                  onClick={async () => {
                    await navigator.clipboard.writeText(mfaEnrollment.otpauthUri);
                    toast.success('OTPAuth URI copied');
                  }}
                >
                  Copy OTPAuth URI
                </Button>
                <Box>
                  <Typography variant="subtitle2">Recovery Codes</Typography>
                  <Typography variant="caption" color="text.secondary">
                    Store these codes somewhere safe. Each code can be used once.
                  </Typography>
                  <Box mt={1} display="flex" flexWrap="wrap" gap={1}>
                    {mfaEnrollment.recoveryCodes.map((code) => (
                      <Chip key={code} label={code} size="small" variant="outlined" />
                    ))}
                  </Box>
                </Box>
              </>
            ) : (
              <Typography variant="body2">Generating MFA enrollment...</Typography>
            )}

            <TextField
              label="Verification code"
              value={mfaCodeInput}
              onChange={(event) => setMfaCodeInput(event.target.value)}
              fullWidth
              placeholder="123456"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMfaDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleVerifyMfa} disabled={mfaLoading}>
            Verify & Enable
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={mfaDisableDialogOpen}
        onClose={() => setMfaDisableDialogOpen(false)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>Disable Local MFA</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="warning">
              Disabling MFA will remove local protection for sensitive actions.
            </Alert>
            <TextField
              label="Verification or recovery code"
              value={mfaDisableCode}
              onChange={(event) => setMfaDisableCode(event.target.value)}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMfaDisableDialogOpen(false)}>Cancel</Button>
          <Button color="warning" onClick={handleDisableMfa} disabled={mfaLoading}>
            Disable
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SettingsPage;
