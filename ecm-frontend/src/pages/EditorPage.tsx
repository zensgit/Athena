import React, { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import axios from 'axios';
import { Box, CircularProgress, Alert } from '@mui/material';

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
  const provider = searchParams.get('provider') || 'wps'; // Default to WPS
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
        if (provider === 'wps') {
          // Fetch WPS Web Office URL
          const response = await axios.get<{ wpsUrl: string }>(
            `/api/v1/integration/wps/url/${documentId}?permission=${permission}`
          );
          url = response.data.wpsUrl;
        } else if (provider === 'wopi') {
          // Future: Fetch WOPI Client URL (e.g. Collabora / Office Online)
          // const response = await axios.get(...)
          setError("WOPI client integration not yet configured in frontend.");
          return;
        }

        if (url) {
          setEditorUrl(url);
        } else {
          setError("Failed to generate editor URL.");
        }
      } catch (err) {
        console.error("Error loading editor:", err);
        setError("Failed to load editor. Please check permissions or try again.");
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
