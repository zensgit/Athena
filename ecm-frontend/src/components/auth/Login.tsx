import React from 'react';
import { Box, Card, CardContent, Button, Typography } from '@mui/material';
import { keycloak } from 'services/authService';

const Login: React.FC = () => {
  const handleLogin = () => {
    keycloak.login({ redirectUri: window.location.origin + '/' });
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
          <Button variant="contained" fullWidth onClick={handleLogin}>
            Sign in with Keycloak
          </Button>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Login;
