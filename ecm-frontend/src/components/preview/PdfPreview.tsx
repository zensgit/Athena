import React from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Document, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/Page/AnnotationLayer.css';
import 'react-pdf/dist/Page/TextLayer.css';

pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.js`;

type PdfPreviewProps = {
  fileUrl: string | null;
  pageNumber: number;
  scale: number;
  rotation: number;
  onLoadSuccess: (payload: { numPages: number }) => void;
};

const PdfPreview: React.FC<PdfPreviewProps> = ({
  fileUrl,
  pageNumber,
  scale,
  rotation,
  onLoadSuccess,
}) => {
  if (!fileUrl) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="60vh">
        <Typography color="text.secondary">PDF not available</Typography>
      </Box>
    );
  }

  return (
    <Document
      file={fileUrl}
      onLoadSuccess={onLoadSuccess}
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
  );
};

export default PdfPreview;
