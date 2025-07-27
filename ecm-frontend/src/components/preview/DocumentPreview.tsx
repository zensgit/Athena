import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Box,
  CircularProgress,
  Typography,
  AppBar,
  Toolbar,
  Button,
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
  Fullscreen,
  MoreVert,
  NavigateBefore,
  NavigateNext,
} from '@mui/icons-material';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/esm/Page/AnnotationLayer.css';
import 'react-pdf/dist/esm/Page/TextLayer.css';
import { Node } from '@/types';
import nodeService from '@/services/nodeService';
import { toast } from 'react-toastify';

// Configure PDF.js worker
pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.js`;

interface DocumentPreviewProps {
  open: boolean;
  onClose: () => void;
  node: Node;
}

const DocumentPreview: React.FC<DocumentPreviewProps> = ({ open, onClose, node }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [numPages, setNumPages] = useState<number | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [scale, setScale] = useState(1.0);
  const [rotation, setRotation] = useState(0);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  useEffect(() => {
    if (open && node) {
      loadDocument();
    }

    return () => {
      if (fileUrl) {
        URL.revokeObjectURL(fileUrl);
      }
    };
  }, [open, node]);

  const loadDocument = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`/api/nodes/${node.id}/content`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
      });

      if (!response.ok) {
        throw new Error('Failed to load document');
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      setFileUrl(url);
    } catch (err) {
      setError('Failed to load document');
      toast.error('Failed to load document preview');
    } finally {
      setLoading(false);
    }
  };

  const handleDocumentLoadSuccess = ({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setLoading(false);
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

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const renderPreview = () => {
    if (loading) {
      return (
        <Box display="flex" justifyContent="center" alignItems="center" height="60vh">
          <CircularProgress />
        </Box>
      );
    }

    if (error) {
      return (
        <Box display="flex" justifyContent="center" alignItems="center" height="60vh">
          <Typography color="error">{error}</Typography>
        </Box>
      );
    }

    // Image preview
    if (node.contentType?.startsWith('image/')) {
      return (
        <Box
          display="flex"
          justifyContent="center"
          alignItems="center"
          height="calc(100vh - 200px)"
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
    if (node.contentType === 'application/pdf') {
      return (
        <Box
          display="flex"
          flexDirection="column"
          alignItems="center"
          height="calc(100vh - 200px)"
          sx={{ overflow: 'auto' }}
        >
          <Document
            file={fileUrl}
            onLoadSuccess={handleDocumentLoadSuccess}
            loading={<CircularProgress />}
            error={<Typography color="error">Failed to load PDF</Typography>}
          >
            <Page
              pageNumber={pageNumber}
              scale={scale}
              rotate={rotation}
              renderTextLayer={true}
              renderAnnotationLayer={true}
            />
          </Document>
        </Box>
      );
    }

    // Text preview
    if (node.contentType?.startsWith('text/')) {
      return (
        <Box
          sx={{
            height: 'calc(100vh - 200px)',
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
          {node.contentType === 'application/pdf' && numPages && (
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