import React from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

const publicUrl = process.env.PUBLIC_URL || '';
pdfjs.GlobalWorkerOptions.workerSrc = `${publicUrl}/pdf.worker.min.mjs`;

type PdfPreviewProps = {
  fileUrl: string | null;
  pageNumber: number;
  scale: number;
  rotation: number;
  onLoadSuccess: (payload: { numPages: number }) => void;
  onLoadError?: () => void;
  onPageLoadSuccess?: (page: { getViewport: (options: { scale: number; rotation?: number }) => { width: number; height: number } }) => void;
  overlay?: React.ReactNode;
  markers?: React.ReactNode;
};

const PdfPreview: React.FC<PdfPreviewProps> = ({
  fileUrl,
  pageNumber,
  scale,
  rotation,
  onLoadSuccess,
  onLoadError,
  onPageLoadSuccess,
  overlay,
  markers,
}) => {
  if (!fileUrl) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="60vh">
        <Typography color="text.secondary">PDF not available</Typography>
      </Box>
    );
  }

  const renderError = () => {
    if (onLoadError) {
      onLoadError();
    }
    return (
      <Box data-testid="pdf-preview-error">
        <Typography color="error">Failed to load PDF</Typography>
      </Box>
    );
  };

  return (
    <Document
      file={fileUrl}
      onLoadSuccess={onLoadSuccess}
      onLoadError={onLoadError}
      onSourceError={onLoadError}
      loading={<CircularProgress />}
      error={renderError}
    >
      <Box sx={{ position: 'relative', display: 'inline-block' }}>
        <Page
          pageNumber={pageNumber}
          scale={scale}
          rotate={rotation}
          renderTextLayer={true}
          renderAnnotationLayer={true}
          onLoadSuccess={onPageLoadSuccess}
          onLoadError={onLoadError}
          onRenderError={onLoadError}
          error={renderError}
        />
        {overlay}
        {markers}
      </Box>
    </Document>
  );
};

export default PdfPreview;
