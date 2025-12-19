import React, { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { Box, CircularProgress, Alert } from '@mui/material';
import apiService from 'services/api';

/**
 * Editor Page Component
 * 
 * Handles loading external editors (WPS, Office Online) in an iframe.
 * Supports:
 * - WPS Web Office (via /api/v1/integration/wps/url)
 * - WOPI Clients (via future WOPI discovery URL)
 */
const EditorPage: React.FC = () => {
  const { documentId } = useParams<{ documentId: string }>();
  const [searchParams] = useSearchParams();
  const rawProvider = searchParams.get('provider');
  // WPS integration is optional and may be disabled in this deployment. Treat "wps" as a
  // compatibility alias for WOPI to avoid hard failures (501 from backend).
  const provider = rawProvider === 'wps' ? 'wopi' : rawProvider || 'wopi'; // Default to WOPI (Collabora)
  const permission = searchParams.get('permission') || 'read';

  const [editorUrl, setEditorUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchEditorUrl = async () => {
      try {
        setLoading(true);
        setError(null);

        let url = '';
        if (provider === 'wopi') {
          const response = await apiService.get<{ wopiUrl: string }>(`/integration/wopi/url/${documentId}`, {
            params: { permission },
          });
          url = response.wopiUrl;
        } else {
          setError(`Unsupported editor provider: ${provider}`);
        }

        if (url) {
          setEditorUrl(url);
        } else {
          setError("Failed to generate editor URL.");
        }
      } catch (err) {
        console.error("Error loading editor:", err);
        const backendMessage =
          (err as any)?.response?.data?.message ||
          (err as any)?.message ||
          "Failed to load editor. Please check permissions or try again.";
        setError(backendMessage);
      } finally {
        setLoading(false);
      }
    };

    if (documentId) {
      fetchEditorUrl();
    }
  }, [documentId, provider, permission]);

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="100vh">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box p={3}>
        <Alert severity="error">{error}</Alert>
      </Box>
    );
  }

  return (
    <Box height="100vh" width="100vw" overflow="hidden">
      {editorUrl && (
        <iframe
          src={editorUrl}
          title="Online Editor"
          style={{ width: '100%', height: '100%', border: 'none' }}
          allow="clipboard-read; clipboard-write; fullscreen"
        />
      )}
    </Box>
  );
};

export default EditorPage;
