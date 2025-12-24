import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Dialog,
  DialogContent,
  IconButton,
  Box,
  Button,
  CircularProgress,
  Typography,
  AppBar,
  Toolbar,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import {
  Close,
  Download,
  Print,
  ZoomIn,
  ZoomOut,
  RotateLeft,
  RotateRight,
  MoreVert,
  NavigateBefore,
  NavigateNext,
  Edit,
  Visibility,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { Node } from 'types';
import { useAppSelector } from 'store';
import apiService from 'services/api';
import nodeService from 'services/nodeService';
import { toast } from 'react-toastify';

interface DocumentPreviewProps {
  open: boolean;
  onClose: () => void;
  node: Node;
}

const PdfPreview = React.lazy(() => import('./PdfPreview'));

type PreviewPage = {
  pageNumber: number;
  width?: number;
  height?: number;
  format?: string;
  content?: string;
  textContent?: string;
};

type PreviewResult = {
  documentId?: string;
  mimeType?: string;
  supported?: boolean;
  message?: string;
  pages?: PreviewPage[];
  pageCount?: number;
};

const OFFICE_MIME_TYPES = new Set([
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'application/vnd.oasis.opendocument.text',
  'application/vnd.oasis.opendocument.spreadsheet',
  'application/vnd.oasis.opendocument.presentation',
  'application/rtf',
  'text/rtf',
]);

const OFFICE_EXTENSIONS = [
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.odt',
  '.ods',
  '.odp',
  '.rtf',
];

const isOfficeDocument = (contentType?: string, name?: string) => {
  const normalizedType = contentType?.toLowerCase();
  if (normalizedType && OFFICE_MIME_TYPES.has(normalizedType)) {
    return true;
  }
  const normalizedName = name?.toLowerCase() || '';
  return OFFICE_EXTENSIONS.some((ext) => normalizedName.endsWith(ext));
};

const inferContentTypeFromName = (name?: string) => {
  const normalizedName = name?.toLowerCase() || '';
  if (normalizedName.endsWith('.pdf')) return 'application/pdf';
  if (normalizedName.endsWith('.png')) return 'image/png';
  if (normalizedName.endsWith('.jpg') || normalizedName.endsWith('.jpeg')) return 'image/jpeg';
  if (normalizedName.endsWith('.gif')) return 'image/gif';
  if (normalizedName.endsWith('.txt') || normalizedName.endsWith('.md') || normalizedName.endsWith('.csv')) {
    return 'text/plain';
  }
  return undefined;
};

const normalizeContentType = (value?: string) => {
  if (!value) {
    return undefined;
  }
  return value.split(';')[0]?.trim().toLowerCase();
};

const isGenericContentType = (value?: string) => {
  if (!value) {
    return true;
  }
  return value === 'application/octet-stream'
    || value === 'binary/octet-stream'
    || value === 'application/x-empty';
};

const DocumentPreview: React.FC<DocumentPreviewProps> = ({ open, onClose, node }) => {
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const nodeId = node?.id;
  const nodeName = node?.name;
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [blobContentType, setBlobContentType] = useState<string | null>(null);
  const [wopiUrl, setWopiUrl] = useState<string | null>(null);
  const [numPages, setNumPages] = useState<number | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [scale, setScale] = useState(1.0);
  const [rotation, setRotation] = useState(0);
  const [pdfLoadFailed, setPdfLoadFailed] = useState(false);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [serverPreview, setServerPreview] = useState<PreviewResult | null>(null);
  const [serverPreviewLoading, setServerPreviewLoading] = useState(false);
  const [serverPreviewError, setServerPreviewError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const pdfContainerRef = useRef<HTMLDivElement | null>(null);
  const previewHeight = 'calc(100vh - 64px)';
  const resolvedContentType = (() => {
    const candidates = [
      normalizeContentType(node?.contentType),
      normalizeContentType(node?.properties?.mimeType),
      normalizeContentType(node?.properties?.contentType),
    ];
    const specific = candidates.find((candidate) => candidate && !isGenericContentType(candidate));
    return specific || inferContentTypeFromName(nodeName);
  })();
  const effectiveContentType = blobContentType || resolvedContentType;
  const officeDocument = isOfficeDocument(resolvedContentType, nodeName);

  const loadServerPreview = useCallback(async () => {
    if (!nodeId) {
      return;
    }
    setServerPreviewLoading(true);
    setServerPreviewError(null);
    try {
      const preview = await apiService.get<PreviewResult>(`/documents/${nodeId}/preview`);
      setServerPreview(preview);
      if (preview?.pageCount) {
        setNumPages(preview.pageCount);
      } else if (preview?.pages?.length) {
        setNumPages(preview.pages.length);
      }
    } catch (err) {
      const message = 'Failed to load server preview';
      setServerPreviewError(message);
      setServerPreview({ supported: false, message });
    } finally {
      setServerPreviewLoading(false);
    }
  }, [nodeId]);

  useEffect(() => {
    if (!open || !nodeId) {
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setFileUrl(null);
    setBlobContentType(null);
    setWopiUrl(null);
    setNumPages(null);
    setPageNumber(1);
    setPdfLoadFailed(false);
    setServerPreview(null);
    setServerPreviewLoading(false);
    setServerPreviewError(null);

    const loadDocument = async () => {
      try {
        const blob = await apiService.getBlob(`/nodes/${nodeId}/content`);
        const url = URL.createObjectURL(blob);
        const detectedType = normalizeContentType(blob.type);
        if (!cancelled) {
          setFileUrl(url);
          if (detectedType) {
            setBlobContentType(detectedType);
          }
          if ((detectedType === 'application/pdf' || resolvedContentType === 'application/pdf') && blob.size === 0) {
            setPdfLoadFailed(true);
            void loadServerPreview();
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError('Failed to load document');
          toast.error('Failed to load document preview');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    const loadOfficeDocument = async () => {
      try {
        const response = await apiService.get<{ wopiUrl: string }>(`/integration/wopi/url/${nodeId}`, {
          params: { permission: 'read' },
        });
        if (!cancelled) {
          setWopiUrl(response.wopiUrl);
        }
      } catch (err) {
        if (!cancelled) {
          setError('Failed to load document');
          toast.error('Failed to load document preview');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    if (officeDocument) {
      void loadOfficeDocument();
    } else {
      void loadDocument();
    }

    return () => {
      cancelled = true;
    };
  }, [open, nodeId, officeDocument, reloadKey, resolvedContentType, loadServerPreview]);

  useEffect(() => {
    return () => {
      if (fileUrl) {
        URL.revokeObjectURL(fileUrl);
      }
    };
  }, [fileUrl]);

  useEffect(() => {
    if (!open || effectiveContentType !== 'application/pdf') {
      return;
    }
    if (!fileUrl) {
      return;
    }
    if (serverPreview || serverPreviewLoading) {
      return;
    }
    const timeoutId = window.setTimeout(() => {
      const hasCanvas = Boolean(
        pdfContainerRef.current?.querySelector('.react-pdf__Page__canvas')
      );
      const hasError = Boolean(
        pdfContainerRef.current?.querySelector('[data-testid="pdf-preview-error"]')
      );
      if ((!hasCanvas || hasError) && !serverPreview && !serverPreviewLoading) {
        void loadServerPreview();
      }
    }, 8000);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [open, effectiveContentType, fileUrl, serverPreview, serverPreviewLoading, loadServerPreview]);

  const handleDocumentLoadSuccess = ({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setLoading(false);
  };

  const handlePdfLoadError = () => {
    setPdfLoadFailed(true);
    if (serverPreview || serverPreviewLoading) {
      return;
    }
    void loadServerPreview();
  };

  const handleDownload = async () => {
    try {
      await nodeService.downloadDocument(node.id);
    } catch (error) {
      toast.error('Failed to download document');
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const handleZoomIn = () => {
    setScale((prev) => Math.min(prev + 0.25, 3));
  };

  const handleZoomOut = () => {
    setScale((prev) => Math.max(prev - 0.25, 0.5));
  };

  const handleRotateLeft = () => {
    setRotation((prev) => prev - 90);
  };

  const handleRotateRight = () => {
    setRotation((prev) => prev + 90);
  };

  const handlePreviousPage = () => {
    setPageNumber((prev) => Math.max(prev - 1, 1));
  };

  const handleNextPage = () => {
    setPageNumber((prev) => Math.min(prev + 1, numPages || 1));
  };

  const handleRetry = () => {
    setPdfLoadFailed(false);
    setReloadKey((prev) => prev + 1);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const renderPreviewError = (message: string) => (
    <Box
      display="flex"
      flexDirection="column"
      alignItems="center"
      justifyContent="center"
      height="60vh"
      gap={2}
      textAlign="center"
    >
      <Typography color="error">{message}</Typography>
      <Box display="flex" gap={1} flexWrap="wrap" justifyContent="center">
        <Button variant="contained" size="small" onClick={handleRetry}>
          Retry
        </Button>
        <Button variant="outlined" size="small" onClick={handleDownload}>
          Download
        </Button>
      </Box>
    </Box>
  );

  const renderPreview = () => {
    if (loading) {
      return (
        <Box display="flex" justifyContent="center" alignItems="center" height="60vh">
          <CircularProgress />
        </Box>
      );
    }

    if (error) {
      return renderPreviewError(error);
    }

    if (officeDocument) {
      return (
        <Box height={previewHeight} sx={{ overflow: 'hidden' }}>
          <iframe
            src={wopiUrl || undefined}
            title={node.name}
            style={{ width: '100%', height: '100%', border: 'none' }}
            allow="clipboard-read; clipboard-write; fullscreen"
          />
        </Box>
      );
    }

    // Image preview
    if (effectiveContentType?.startsWith('image/')) {
      return (
        <Box
          display="flex"
          justifyContent="center"
          alignItems="center"
          height={previewHeight}
          sx={{ overflow: 'auto' }}
        >
          <img
            src={fileUrl!}
            alt={node.name}
            style={{
              maxWidth: '100%',
              maxHeight: '100%',
              transform: `scale(${scale}) rotate(${rotation}deg)`,
              transition: 'transform 0.3s',
            }}
          />
        </Box>
      );
    }

    // PDF preview
    if (effectiveContentType === 'application/pdf') {
      if (serverPreviewLoading) {
        return (
          <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" height="60vh">
            <CircularProgress />
            <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
              Loading server preview...
            </Typography>
          </Box>
        );
      }

      if (serverPreview) {
        if (serverPreview.supported === false) {
          return renderPreviewError(
            serverPreview.message || 'Preview not available for this document'
          );
        }

        const pages = serverPreview.pages || [];
        const currentPage = pages.find((page) => page.pageNumber === pageNumber) || pages[0];
        if (!currentPage || !currentPage.content) {
          return renderPreviewError(
            serverPreviewError || 'Preview not available for this document'
          );
        }

        const imageSrc = `data:image/${currentPage.format || 'png'};base64,${currentPage.content}`;
        return (
          <Box
            display="flex"
            flexDirection="column"
            alignItems="center"
            height={previewHeight}
            sx={{ overflow: 'auto' }}
            data-testid="pdf-preview-fallback"
          >
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, mb: 2 }}>
              Using server-rendered preview
            </Typography>
            <img
              src={imageSrc}
              alt={`${node.name} page ${currentPage.pageNumber}`}
              style={{
                maxWidth: '100%',
                transform: `scale(${scale}) rotate(${rotation}deg)`,
                transformOrigin: 'top center',
                transition: 'transform 0.3s',
              }}
            />
          </Box>
        );
      }

      if (pdfLoadFailed) {
        return renderPreviewError('Failed to load PDF');
      }

      return (
        <Box
          ref={pdfContainerRef}
          display="flex"
          flexDirection="column"
          alignItems="center"
          height={previewHeight}
          sx={{ overflow: 'auto' }}
        >
          <React.Suspense fallback={<CircularProgress />}>
            <PdfPreview
              fileUrl={fileUrl}
              pageNumber={pageNumber}
              scale={scale}
              rotation={rotation}
              onLoadSuccess={handleDocumentLoadSuccess}
              onLoadError={handlePdfLoadError}
            />
          </React.Suspense>
        </Box>
      );
    }

    // Text preview
    if (effectiveContentType?.startsWith('text/')) {
      return (
        <Box
          sx={{
            height: previewHeight,
            overflow: 'auto',
            p: 2,
            bgcolor: 'background.paper',
          }}
        >
          <iframe
            src={fileUrl!}
            style={{
              width: '100%',
              height: '100%',
              border: 'none',
              transform: `scale(${scale})`,
              transformOrigin: 'top left',
            }}
            title={node.name}
          />
        </Box>
      );
    }

    // Unsupported format
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="60vh">
        <Typography variant="h6" color="text.secondary">
          Preview not available for this file type
        </Typography>
      </Box>
    );
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth={false} fullScreen>
      <AppBar position="relative">
        <Toolbar>
          <IconButton edge="start" color="inherit" onClick={onClose} aria-label="close">
            <Close />
          </IconButton>
          <Typography sx={{ ml: 2, flex: 1 }} variant="h6" component="div">
            {node.name}
          </Typography>

          {/* Navigation controls for PDF */}
          {effectiveContentType === 'application/pdf' && numPages && (
            <Box display="flex" alignItems="center" mr={2}>
              <IconButton
                color="inherit"
                onClick={handlePreviousPage}
                disabled={pageNumber <= 1}
              >
                <NavigateBefore />
              </IconButton>
              <Typography variant="body2" sx={{ mx: 1 }}>
                {pageNumber} / {numPages}
              </Typography>
              <IconButton
                color="inherit"
                onClick={handleNextPage}
                disabled={pageNumber >= numPages}
              >
                <NavigateNext />
              </IconButton>
            </Box>
          )}

          {/* Zoom controls */}
          <IconButton color="inherit" onClick={handleZoomOut}>
            <ZoomOut />
          </IconButton>
          <Typography variant="body2" sx={{ mx: 1 }}>
            {Math.round(scale * 100)}%
          </Typography>
          <IconButton color="inherit" onClick={handleZoomIn}>
            <ZoomIn />
          </IconButton>

          {/* Rotation controls */}
          <IconButton color="inherit" onClick={handleRotateLeft}>
            <RotateLeft />
          </IconButton>
          <IconButton color="inherit" onClick={handleRotateRight}>
            <RotateRight />
          </IconButton>

          {/* Action menu */}
          <IconButton color="inherit" onClick={handleMenuOpen}>
            <MoreVert />
          </IconButton>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
          >
            {officeDocument && (
              <MenuItem onClick={() => {
                const permission = canWrite ? 'write' : 'read';
                navigate(`/editor/${node.id}?provider=wopi&permission=${permission}`);
                handleMenuClose();
                onClose();
              }}>
                <ListItemIcon>
                  {canWrite ? <Edit fontSize="small" /> : <Visibility fontSize="small" />}
                </ListItemIcon>
                <ListItemText>{canWrite ? 'Edit Online' : 'View Online'}</ListItemText>
              </MenuItem>
            )}
            <MenuItem onClick={() => { handleDownload(); handleMenuClose(); }}>
              <ListItemIcon>
                <Download fontSize="small" />
              </ListItemIcon>
              <ListItemText>Download</ListItemText>
            </MenuItem>
            <MenuItem onClick={() => { handlePrint(); handleMenuClose(); }}>
              <ListItemIcon>
                <Print fontSize="small" />
              </ListItemIcon>
              <ListItemText>Print</ListItemText>
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <DialogContent sx={{ p: 0, bgcolor: 'grey.100' }}>
        {renderPreview()}
      </DialogContent>
    </Dialog>
  );
};

export default DocumentPreview;
