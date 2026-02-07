import React, { useEffect, useState } from 'react';
import { Alert, Box, Card, CardContent, Button, Typography } from '@mui/material';
import authService from 'services/authService';
import { LOGIN_IN_PROGRESS_KEY, LOGIN_IN_PROGRESS_STARTED_AT_KEY } from 'constants/auth';

const Login: React.FC = () => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
    sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
  }, []);

  const handleLogin = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await authService.login({ redirectUri: window.location.origin + '/' });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'Unable to start sign-in.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
      }}
    >
      <Card sx={{ maxWidth: 400, width: '100%', mx: 2 }}>
        <CardContent sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h4" component="h1" gutterBottom>
            Athena ECM
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Sign in with your organization account
          </Typography>
          {error && (
            <Alert severity="error" sx={{ mb: 2, textAlign: 'left' }}>
              Sign-in failed. {error}
            </Alert>
          )}
          <Button variant="contained" fullWidth onClick={handleLogin} disabled={submitting}>
            Sign in with Keycloak
          </Button>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Login;
