import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Box,
  Button,
  CircularProgress,
  TextField,
  Typography,
  AppBar,
  Toolbar,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Skeleton,
} from '@mui/material';
import {
  Close,
  Download,
  Print,
  ZoomIn,
  ZoomOut,
  RotateLeft,
  RotateRight,
  FitScreen,
  ArrowDropDown,
  MoreVert,
  NavigateBefore,
  NavigateNext,
  Edit,
  Visibility,
  AutoAwesome,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { Node, PdfAnnotation, PdfAnnotationState } from 'types';
import { useAppSelector } from 'store';
import apiService from 'services/api';
import nodeService from 'services/nodeService';
import { toast } from 'react-toastify';

interface DocumentPreviewProps {
  open: boolean;
  onClose: () => void;
  node: Node;
  initialAnnotateMode?: boolean;
}

const PdfPreview = React.lazy(() => import('./PdfPreview'));

const DEFAULT_ANNOTATION_COLOR = '#1976d2';
const FIT_MODE_STORAGE_KEY = 'ecm_pdf_fit_mode';
const FIT_MODE_VERSION_KEY = 'ecm_pdf_fit_mode_version';
const FIT_MODE_VERSION = '2025-12-31';

type FitMode = 'screen' | 'height' | 'width' | 'actual';

const parseFitMode = (value: string | null): FitMode => {
  if (value === 'height' || value === 'width' || value === 'actual') {
    return value;
  }
  return 'height';
};

const fitModeLabels: Record<FitMode, string> = {
  screen: 'Fit to screen (F)',
  height: 'Fit to height (H)',
  width: 'Fit to width (W)',
  actual: 'Actual size (0)',
};

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
  status?: string;
  message?: string;
  failureReason?: string;
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
  const normalizedName = name?.trim().toLowerCase() || '';
  return OFFICE_EXTENSIONS.some((ext) => normalizedName.endsWith(ext));
};

const isPdfDocument = (contentType?: string, name?: string) => {
  const normalizedType = contentType?.toLowerCase();
  if (normalizedType && normalizedType.includes('pdf')) {
    return true;
  }
  const normalizedName = name?.trim().toLowerCase() || '';
  return normalizedName.endsWith('.pdf');
};

const inferContentTypeFromName = (name?: string) => {
  const normalizedName = name?.trim().toLowerCase() || '';
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

const clampToUnit = (value: number) => Math.min(Math.max(value, 0), 1);
const getPreviewErrorMessage = (error: unknown, fallback: string) => {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status === 404) {
    return 'Preview unavailable. The file was not found.';
  }
  if (status === 401 || status === 403) {
    return 'Preview unavailable. Access denied.';
  }
  if (status && status >= 500) {
    return 'Preview unavailable. The server returned an error.';
  }
  return fallback;
};

const DocumentPreview: React.FC<DocumentPreviewProps> = ({
  open,
  onClose,
  node,
  initialAnnotateMode = false,
}) => {
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
  const [fitScale, setFitScale] = useState<number | null>(null);
  const [autoScaleEnabled, setAutoScaleEnabled] = useState(true);
  const [fitMode, setFitMode] = useState<FitMode>(() => {
    if (typeof window === 'undefined') {
      return 'height';
    }
    const storedVersion = window.localStorage.getItem(FIT_MODE_VERSION_KEY);
    if (storedVersion !== FIT_MODE_VERSION) {
      window.localStorage.setItem(FIT_MODE_VERSION_KEY, FIT_MODE_VERSION);
      window.localStorage.setItem(FIT_MODE_STORAGE_KEY, 'height');
      return 'height';
    }
    return parseFitMode(window.localStorage.getItem(FIT_MODE_STORAGE_KEY));
  });
  const [rotation, setRotation] = useState(0);
  const [pdfLoadFailed, setPdfLoadFailed] = useState(false);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [fitMenuAnchor, setFitMenuAnchor] = useState<null | HTMLElement>(null);
  const [serverPreview, setServerPreview] = useState<PreviewResult | null>(null);
  const [serverPreviewLoading, setServerPreviewLoading] = useState(false);
  const [serverPreviewError, setServerPreviewError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [pageSize, setPageSize] = useState<{ width: number; height: number } | null>(null);
  const [annotations, setAnnotations] = useState<PdfAnnotation[]>([]);
  const [annotationsUpdatedBy, setAnnotationsUpdatedBy] = useState<string | null>(null);
  const [annotationsUpdatedAt, setAnnotationsUpdatedAt] = useState<string | null>(null);
  const [annotationsLoading, setAnnotationsLoading] = useState(false);
  const [emptyPdf, setEmptyPdf] = useState(false);
  const [annotateMode, setAnnotateMode] = useState(false);
  const [annotationDialogOpen, setAnnotationDialogOpen] = useState(false);
  const [annotationDraft, setAnnotationDraft] = useState<PdfAnnotation | null>(null);
  const [annotationSaving, setAnnotationSaving] = useState(false);
  const pdfContainerRef = useRef<HTMLDivElement | null>(null);
  const previewHeight = '100%';
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
  const pdfDocument = isPdfDocument(resolvedContentType, nodeName);
  const rotationNormalized = ((rotation % 360) + 360) % 360;
  const annotationsDisabledForRotation = rotationNormalized !== 0;
  const canAnnotate = canWrite && !annotationsDisabledForRotation;
  const fitModeLabel = fitModeLabels[fitMode];
  const fitScaleLabel = fitScale ? ` (${Math.round(fitScale * 100)}%)` : '';

  useEffect(() => {
    if (!open) {
      return;
    }
    if (initialAnnotateMode && canAnnotate) {
      setAnnotateMode(true);
    } else {
      setAnnotateMode(false);
    }
  }, [open, nodeId, initialAnnotateMode, canAnnotate]);

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
      const message = getPreviewErrorMessage(err, 'Failed to load server preview');
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
    setScale(1.0);
    setFitScale(null);
    setAutoScaleEnabled(true);
    setPdfLoadFailed(false);
    setServerPreview(null);
    setServerPreviewLoading(false);
    setServerPreviewError(null);
    setAnnotations([]);
    setAnnotationsUpdatedBy(null);
    setAnnotationsUpdatedAt(null);
    setAnnotationsLoading(false);
    setEmptyPdf(false);
    setAnnotateMode(initialAnnotateMode);
    setAnnotationDialogOpen(false);
    setAnnotationDraft(null);
    setPageSize(null);

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
            setEmptyPdf(true);
            setPdfLoadFailed(true);
            void loadServerPreview();
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(getPreviewErrorMessage(err, 'Failed to load document'));
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
          setError(getPreviewErrorMessage(err, 'Failed to load document'));
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
  }, [open, nodeId, officeDocument, reloadKey, resolvedContentType, loadServerPreview, initialAnnotateMode]);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    window.localStorage.setItem(FIT_MODE_STORAGE_KEY, fitMode);
  }, [fitMode]);

  useEffect(() => {
    if (!open || !nodeId || !pdfDocument) {
      return;
    }

    let cancelled = false;
    setAnnotationsLoading(true);

    nodeService.getPdfAnnotations(nodeId)
      .then((state: PdfAnnotationState) => {
        if (cancelled) {
          return;
        }
        setAnnotations(state.annotations || []);
        setAnnotationsUpdatedBy(state.updatedBy ?? null);
        setAnnotationsUpdatedAt(state.updatedAt ?? null);
      })
      .catch(() => {
        if (!cancelled) {
          toast.error('Failed to load annotations');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setAnnotationsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [open, nodeId, pdfDocument]);

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

  const handlePdfPageLoadSuccess = useCallback((page: {
    getViewport: (options: { scale: number; rotation?: number }) => { width: number; height: number };
  }) => {
    const viewport = page.getViewport({ scale: 1 });
    setPageSize({ width: viewport.width, height: viewport.height });
  }, []);

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

  const handleOpenInEditor = () => {
    const permission = canWrite ? 'write' : 'read';
    navigate(`/editor/${node.id}?provider=wopi&permission=${permission}`);
    onClose();
  };

  const handleOpenFile = () => {
    if (!fileUrl) {
      return;
    }
    window.open(fileUrl, '_blank', 'noopener,noreferrer');
  };

  const handlePrint = () => {
    window.print();
  };

  const handleZoomIn = useCallback(() => {
    setAutoScaleEnabled(false);
    setScale((prev) => Math.min(prev + 0.25, 3));
  }, []);

  const handleZoomOut = useCallback(() => {
    setAutoScaleEnabled(false);
    setScale((prev) => Math.max(prev - 0.25, 0.5));
  }, []);

  const computeFitScale = useCallback((mode: FitMode = fitMode) => {
    if (!pdfContainerRef.current || !pageSize) {
      return null;
    }
    const containerHeight = pdfContainerRef.current.clientHeight;
    const containerWidth = pdfContainerRef.current.clientWidth;
    if (!containerHeight || !containerWidth) {
      return null;
    }
    const rotated = rotationNormalized % 180 !== 0;
    const pageWidth = rotated ? pageSize.height : pageSize.width;
    const pageHeight = rotated ? pageSize.width : pageSize.height;
    const heightScale = containerHeight / pageHeight;
    const widthScale = containerWidth / pageWidth;
    let nextScale = 1;

    switch (mode) {
      case 'height':
        nextScale = heightScale;
        break;
      case 'width':
        nextScale = widthScale;
        break;
      case 'actual':
        nextScale = 1;
        break;
      case 'screen':
      default: {
        const useHeightScale = pageWidth * heightScale <= containerWidth;
        nextScale = useHeightScale ? heightScale : widthScale;
        break;
      }
    }
    const clampedScale = Math.min(Math.max(nextScale, 0.5), 3);
    setFitScale(clampedScale);
    return clampedScale;
  }, [fitMode, pageSize, rotationNormalized]);

  const applyFitScale = useCallback((mode?: FitMode) => {
    const nextScale = computeFitScale(mode);
    if (nextScale !== null) {
      setScale(nextScale);
    }
  }, [computeFitScale]);

  const handleApplyFitMode = useCallback((mode?: FitMode) => {
    setAutoScaleEnabled(true);
    applyFitScale(mode);
  }, [applyFitScale]);

  const handleFitMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setFitMenuAnchor(event.currentTarget);
  };

  const handleFitMenuClose = useCallback(() => {
    setFitMenuAnchor(null);
  }, []);

  const handleSelectFitMode = useCallback((mode: FitMode) => {
    setFitMode(mode);
    handleApplyFitMode(mode);
    handleFitMenuClose();
  }, [handleApplyFitMode, handleFitMenuClose]);

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
    setEmptyPdf(false);
    setError(null);
    setServerPreview(null);
    setServerPreviewError(null);
    setReloadKey((prev) => prev + 1);
  };

  const handleLoadServerPreview = () => {
    setServerPreviewError(null);
    void loadServerPreview();
  };

  useEffect(() => {
    if (!open || !pdfDocument || !autoScaleEnabled) {
      return;
    }
    applyFitScale();
  }, [open, pdfDocument, autoScaleEnabled, applyFitScale]);

  useEffect(() => {
    if (!open || !pdfDocument) {
      return;
    }
    const handleResize = () => {
      if (!autoScaleEnabled) {
        return;
      }
      applyFitScale();
    };
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, [open, pdfDocument, autoScaleEnabled, applyFitScale]);

  useEffect(() => {
    if (!open || !pdfDocument || !autoScaleEnabled) {
      return;
    }
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const element = pdfContainerRef.current;
    if (!element) {
      return;
    }
    const observer = new ResizeObserver(() => {
      if (!autoScaleEnabled) {
        return;
      }
      applyFitScale();
    });
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [open, pdfDocument, autoScaleEnabled, applyFitScale]);

  useEffect(() => {
    if (!open || !pdfDocument) {
      return;
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      const target = event.target as HTMLElement | null;
      if (target) {
        const tagName = target.tagName?.toLowerCase();
        if (tagName === 'input' || tagName === 'textarea' || tagName === 'select' || target.isContentEditable) {
          return;
        }
      }
      switch (event.key) {
        case 'f':
        case 'F':
          event.preventDefault();
          handleApplyFitMode('screen');
          break;
        case 'h':
        case 'H':
          event.preventDefault();
          handleSelectFitMode('height');
          break;
        case 'w':
        case 'W':
          event.preventDefault();
          handleSelectFitMode('width');
          break;
        case '0':
          event.preventDefault();
          handleSelectFitMode('actual');
          break;
        case '+':
        case '=':
          event.preventDefault();
          handleZoomIn();
          break;
        case '-':
        case '_':
          event.preventDefault();
          handleZoomOut();
          break;
        default:
          break;
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, pdfDocument, handleApplyFitMode, handleSelectFitMode, handleZoomIn, handleZoomOut]);

  const handleToggleAnnotate = () => {
    if (!canWrite) {
      return;
    }
    if (annotationsDisabledForRotation) {
      toast.info('Rotate to 0° to add annotations');
      return;
    }
    setAnnotateMode((prev) => !prev);
  };

  const handleAnnotationOverlayClick = (event: React.MouseEvent<HTMLDivElement>) => {
    if (!annotateMode || !canAnnotate) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      return;
    }
    const x = clampToUnit((event.clientX - rect.left) / rect.width);
    const y = clampToUnit((event.clientY - rect.top) / rect.height);
    setAnnotationDraft({
      page: pageNumber,
      x,
      y,
      text: '',
      color: DEFAULT_ANNOTATION_COLOR,
    });
    setAnnotationDialogOpen(true);
  };

  const handleAnnotationEdit = (annotation: PdfAnnotation) => {
    setAnnotationDraft(annotation);
    setAnnotationDialogOpen(true);
  };

  const handleAnnotationDialogClose = () => {
    setAnnotationDialogOpen(false);
    setAnnotationDraft(null);
  };

  useEffect(() => {
    if (!annotationsDisabledForRotation) {
      return;
    }
    if (annotateMode) {
      setAnnotateMode(false);
    }
    if (annotationDialogOpen) {
      setAnnotationDialogOpen(false);
      setAnnotationDraft(null);
    }
  }, [annotationsDisabledForRotation, annotateMode, annotationDialogOpen]);

  const persistAnnotations = async (next: PdfAnnotation[]) => {
    if (!nodeId) {
      return;
    }
    setAnnotationSaving(true);
    try {
      const state = await nodeService.savePdfAnnotations(nodeId, next);
      setAnnotations(state.annotations || []);
      setAnnotationsUpdatedBy(state.updatedBy ?? null);
      setAnnotationsUpdatedAt(state.updatedAt ?? null);
    } catch (error) {
      toast.error('Failed to save annotations');
    } finally {
      setAnnotationSaving(false);
    }
  };

  const handleAnnotationSave = async () => {
    if (!annotationDraft) {
      return;
    }
    const text = annotationDraft.text?.trim();
    if (!text) {
      toast.error('Annotation text is required');
      return;
    }
    const nextAnnotation: PdfAnnotation = {
      ...annotationDraft,
      text,
      color: annotationDraft.color || DEFAULT_ANNOTATION_COLOR,
    };
    const next = annotationDraft.id
      ? annotations.map((annotation) => (annotation.id === annotationDraft.id ? nextAnnotation : annotation))
      : [...annotations, nextAnnotation];
    await persistAnnotations(next);
    handleAnnotationDialogClose();
  };

  const handleAnnotationDelete = async () => {
    if (!annotationDraft?.id) {
      handleAnnotationDialogClose();
      return;
    }
    const next = annotations.filter((annotation) => annotation.id !== annotationDraft.id);
    await persistAnnotations(next);
    handleAnnotationDialogClose();
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleFindSimilar = () => {
    navigate('/search-results', {
      state: { similarSourceId: node.id, similarSourceName: node.name },
    });
    handleMenuClose();
    onClose();
  };

  const renderLoadingState = (message: string, detail?: string) => (
    <Box
      display="flex"
      flexDirection="column"
      alignItems="center"
      justifyContent="center"
      height={previewHeight}
      gap={2}
      textAlign="center"
      px={2}
      role="status"
      aria-live="polite"
      aria-busy={true}
    >
      <CircularProgress size={32} />
      <Typography variant="body2" color="text.secondary">
        {message}
      </Typography>
      {detail && (
        <Typography variant="caption" color="text.secondary">
          {detail}
        </Typography>
      )}
      <Box sx={{ width: '90%', maxWidth: 720 }}>
        <Skeleton variant="rectangular" height={260} sx={{ borderRadius: 1 }} />
        <Skeleton variant="text" sx={{ mt: 1 }} />
      </Box>
    </Box>
  );

  const renderPdfLoadingOverlay = () => (
    <Box
      role="status"
      aria-live="polite"
      aria-busy={true}
      sx={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'rgba(255, 255, 255, 0.8)',
        zIndex: 1,
        pointerEvents: 'none',
      }}
    >
      <Box sx={{ width: '90%', maxWidth: 520, textAlign: 'center' }}>
        <CircularProgress size={28} />
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Rendering PDF preview...
        </Typography>
        <Skeleton variant="rectangular" height={200} sx={{ mt: 2, borderRadius: 1 }} />
      </Box>
    </Box>
  );

  const renderPreviewError = (
    message: string,
    options?: {
      showRetry?: boolean;
      showServerPreview?: boolean;
      showOpenFile?: boolean;
      showOpenInEditor?: boolean;
    }
  ) => {
    const showRetry = options?.showRetry ?? true;
    return (
      <Box
        display="flex"
        flexDirection="column"
        alignItems="center"
        justifyContent="center"
        height={previewHeight}
        gap={2}
        textAlign="center"
      >
        <Typography color="error">{message}</Typography>
        <Box display="flex" gap={1} flexWrap="wrap" justifyContent="center">
          {showRetry && (
            <Button variant="contained" size="small" onClick={handleRetry}>
              Retry
            </Button>
          )}
          {options?.showServerPreview && (
            <Button variant="outlined" size="small" onClick={handleLoadServerPreview}>
              Switch to server preview
            </Button>
          )}
          {options?.showOpenInEditor && (
            <Button variant="outlined" size="small" onClick={handleOpenInEditor}>
              Open in editor
            </Button>
          )}
          {options?.showOpenFile && (
            <Button variant="outlined" size="small" onClick={handleOpenFile}>
              Open original
            </Button>
          )}
          <Button variant="outlined" size="small" onClick={handleDownload}>
            Download original
          </Button>
        </Box>
      </Box>
    );
  };

  const currentPageAnnotations = annotations.filter((annotation) => annotation.page === pageNumber);

  const renderAnnotationMarkers = () => {
    if (annotationsDisabledForRotation) {
      return null;
    }
    return currentPageAnnotations.map((annotation, index) => {
      const key = annotation.id || `${annotation.page}-${index}`;
      const label = annotation.text?.trim() || 'Annotation';
      return (
        <Tooltip key={key} title={label} arrow>
          <Box
            onClick={(event) => {
              event.stopPropagation();
              handleAnnotationEdit(annotation);
            }}
            sx={{
              position: 'absolute',
              left: `${annotation.x * 100}%`,
              top: `${annotation.y * 100}%`,
              transform: 'translate(-50%, -50%)',
              width: 14,
              height: 14,
              borderRadius: '50%',
              backgroundColor: annotation.color || DEFAULT_ANNOTATION_COLOR,
              border: '2px solid #fff',
              boxShadow: 2,
              cursor: 'pointer',
              zIndex: 2,
            }}
          />
        </Tooltip>
      );
    });
  };

  const renderAnnotationOverlay = () => {
    if (!annotateMode || !canAnnotate) {
      return null;
    }
    return (
      <Box
        onClick={handleAnnotationOverlayClick}
        sx={{
          position: 'absolute',
          inset: 0,
          cursor: 'crosshair',
          zIndex: 1,
        }}
      />
    );
  };

  const renderPreview = () => {
    if (loading) {
      return renderLoadingState('Preparing preview...', 'Fetching document content');
    }

    if (error) {
      return renderPreviewError(error, {
        showServerPreview: pdfDocument && !serverPreviewLoading,
        showOpenInEditor: officeDocument,
        showOpenFile: Boolean(fileUrl),
      });
    }

    if (officeDocument) {
      return (
        <Box height={previewHeight} sx={{ overflow: 'hidden', flex: 1, minHeight: 0 }}>
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
          sx={{ overflow: 'auto', flex: 1, minHeight: 0 }}
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
        return renderLoadingState('Loading server preview...', 'Rendering pages on the server');
      }

      if (serverPreview) {
        const previewPageCount = serverPreview.pageCount ?? serverPreview.pages?.length ?? 0;
        if (serverPreview.supported === false) {
          return renderPreviewError(
            serverPreview.message || 'Preview not available for this document',
            {
              showServerPreview: true,
              showOpenFile: Boolean(fileUrl),
            }
          );
        }
        if (previewPageCount === 0) {
          return renderPreviewError(
            serverPreview.message || 'No preview pages available for this document',
            {
              showServerPreview: true,
              showOpenFile: Boolean(fileUrl),
            }
          );
        }

        const serverPreviewNotice = emptyPdf
          ? 'PDF is empty; showing server-rendered preview.'
          : pdfLoadFailed
            ? 'Client preview failed; showing server-rendered preview.'
            : 'Server-rendered preview.';
        const pages = serverPreview.pages || [];
        const currentPage = pages.find((page) => page.pageNumber === pageNumber) || pages[0];
        if (!currentPage || !currentPage.content) {
          return renderPreviewError(
            serverPreviewError || 'Preview not available for this document',
            {
              showServerPreview: true,
              showOpenFile: Boolean(fileUrl),
            }
          );
        }

        const imageSrc = `data:image/${currentPage.format || 'png'};base64,${currentPage.content}`;
        return (
          <Box
            display="flex"
            flexDirection="column"
            alignItems="center"
            height={previewHeight}
            sx={{ overflow: 'auto', flex: 1, minHeight: 0 }}
            data-testid="pdf-preview-fallback"
          >
            <Box
              display="flex"
              alignItems="center"
              justifyContent="space-between"
              flexWrap="wrap"
              width="100%"
              px={2}
              py={1}
              gap={1}
              sx={{ bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}
              data-testid="pdf-preview-fallback-banner"
            >
              <Typography
                variant="caption"
                color="text.secondary"
                data-testid="pdf-preview-fallback-message"
              >
                {serverPreviewNotice}
              </Typography>
              <Box display="flex" gap={1} flexWrap="wrap">
                <Button
                  size="small"
                  variant="outlined"
                  onClick={handleRetry}
                  data-testid="pdf-preview-fallback-retry"
                >
                  Try PDF viewer
                </Button>
                <Button
                  size="small"
                  variant="text"
                  onClick={handleDownload}
                  data-testid="pdf-preview-fallback-download"
                >
                  Download original
                </Button>
              </Box>
            </Box>
            <Box
              sx={{
                position: 'relative',
                display: 'inline-block',
                transform: `scale(${scale}) rotate(${rotation}deg)`,
                transformOrigin: 'top center',
                transition: 'transform 0.3s',
              }}
            >
              <img
                src={imageSrc}
                alt={`${node.name} page ${currentPage.pageNumber}`}
                style={{
                  maxWidth: '100%',
                  display: 'block',
                }}
              />
              {renderAnnotationOverlay()}
              {renderAnnotationMarkers()}
            </Box>
          </Box>
        );
      }

      if (emptyPdf) {
        return renderPreviewError('This PDF has no pages to preview.', {
          showServerPreview: true,
          showOpenFile: Boolean(fileUrl),
        });
      }

      if (pdfLoadFailed) {
        return renderPreviewError('Failed to load PDF', {
          showServerPreview: true,
          showOpenFile: Boolean(fileUrl),
        });
      }

      const showPdfLoadingOverlay = Boolean(fileUrl) && !pageSize && !pdfLoadFailed;
      return (
        <Box
          ref={pdfContainerRef}
          display="flex"
          flexDirection="column"
          alignItems="center"
          height={previewHeight}
          sx={{ overflow: 'auto', flex: 1, minHeight: 0, position: 'relative' }}
        >
          {showPdfLoadingOverlay && renderPdfLoadingOverlay()}
          <React.Suspense fallback={<CircularProgress />}>
            <PdfPreview
              fileUrl={fileUrl}
              pageNumber={pageNumber}
              scale={scale}
              rotation={rotation}
              onLoadSuccess={handleDocumentLoadSuccess}
              onLoadError={handlePdfLoadError}
              onPageLoadSuccess={handlePdfPageLoadSuccess}
              overlay={renderAnnotationOverlay()}
              markers={<>{renderAnnotationMarkers()}</>}
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
            flex: 1,
            minHeight: 0,
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
    return renderPreviewError('Preview not available for this file type', {
      showRetry: false,
      showOpenInEditor: officeDocument,
      showOpenFile: Boolean(fileUrl),
    });
  };

  const annotationDialogTitle = annotationDraft?.id ? 'Edit annotation' : 'Add annotation';
  const annotationText = annotationDraft?.text ?? '';
  const canEditAnnotations = canWrite;
  const annotationUpdatedLabel = annotationsUpdatedAt
    ? (() => {
      const date = new Date(annotationsUpdatedAt);
      return Number.isNaN(date.getTime()) ? annotationsUpdatedAt : date.toLocaleString();
    })()
    : null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth={false}
      fullScreen
      PaperProps={{ sx: { display: 'flex', flexDirection: 'column' } }}
    >
      <AppBar position="relative">
        <Toolbar>
          <IconButton edge="start" color="inherit" onClick={onClose} aria-label="close">
            <Close />
          </IconButton>
          <Typography sx={{ ml: 2, flex: 1 }} variant="h6" component="div">
            {node.name}
          </Typography>
          {pdfDocument && (
            <Typography variant="body2" sx={{ mr: 2, opacity: 0.8 }}>
              PDF preview is read-only{canWrite ? ', annotations available' : ''}
            </Typography>
          )}

          {pdfDocument && (
            <Tooltip
              title={
                !canWrite
                  ? 'You do not have permission to annotate'
                  : annotationsDisabledForRotation
                    ? 'Rotate to 0° to annotate'
                    : annotateMode
                      ? 'Exit annotate mode'
                      : 'Add annotations'
              }
            >
              <span>
                <Button
                  color="inherit"
                  variant={annotateMode ? 'outlined' : 'text'}
                  onClick={handleToggleAnnotate}
                  disabled={!canAnnotate}
                  sx={{ mr: 2 }}
                >
                  {annotateMode ? 'Annotating' : 'Annotate'}
                </Button>
              </span>
            </Tooltip>
          )}

          {pdfDocument && annotationUpdatedLabel && (
            <Tooltip title={`Updated by ${annotationsUpdatedBy || 'unknown'}`}>
              <Typography variant="caption" sx={{ mr: 2, opacity: 0.8 }}>
                Notes updated {annotationUpdatedLabel}
              </Typography>
            </Tooltip>
          )}

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

          {pdfDocument && (
            <Box display="flex" alignItems="center">
              <Tooltip title={`${fitModeLabel}${fitScaleLabel}`}>
                <IconButton color="inherit" onClick={() => handleApplyFitMode()}>
                  <FitScreen />
                </IconButton>
              </Tooltip>
              <IconButton
                color="inherit"
                aria-label="Change fit mode"
                onClick={handleFitMenuOpen}
                size="small"
                sx={{ ml: -0.5 }}
              >
                <ArrowDropDown />
              </IconButton>
              <Menu
                anchorEl={fitMenuAnchor}
                open={Boolean(fitMenuAnchor)}
                onClose={handleFitMenuClose}
              >
                <MenuItem selected={fitMode === 'screen'} onClick={() => handleSelectFitMode('screen')}>
                  <ListItemText>Fit to screen (F)</ListItemText>
                </MenuItem>
                <MenuItem selected={fitMode === 'height'} onClick={() => handleSelectFitMode('height')}>
                  <ListItemText>Fit to height (H)</ListItemText>
                </MenuItem>
                <MenuItem selected={fitMode === 'width'} onClick={() => handleSelectFitMode('width')}>
                  <ListItemText>Fit to width (W)</ListItemText>
                </MenuItem>
                <MenuItem selected={fitMode === 'actual'} onClick={() => handleSelectFitMode('actual')}>
                  <ListItemText>Actual size (100%) (0)</ListItemText>
                </MenuItem>
              </Menu>
            </Box>
          )}

          {/* Rotation controls */}
          <IconButton color="inherit" onClick={handleRotateLeft}>
            <RotateLeft />
          </IconButton>
          <IconButton color="inherit" onClick={handleRotateRight}>
            <RotateRight />
          </IconButton>

          {/* Action menu */}
          <IconButton color="inherit" onClick={handleMenuOpen} aria-label="More actions">
            <MoreVert />
          </IconButton>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
          >
            {pdfDocument && (
              <MenuItem
                onClick={() => {
                  handleToggleAnnotate();
                  handleMenuClose();
                }}
                disabled={!canAnnotate}
              >
                <ListItemIcon>
                  <Edit fontSize="small" />
                </ListItemIcon>
                <ListItemText>{annotateMode ? 'Stop Annotating' : 'Annotate'}</ListItemText>
              </MenuItem>
            )}
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
            <MenuItem onClick={handleFindSimilar}>
              <ListItemIcon>
                <AutoAwesome fontSize="small" />
              </ListItemIcon>
              <ListItemText>More like this</ListItemText>
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <DialogContent
        sx={{
          p: 0,
          bgcolor: 'grey.100',
          flex: 1,
          minHeight: 0,
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {renderPreview()}
      </DialogContent>

      <Dialog
        open={annotationDialogOpen}
        onClose={handleAnnotationDialogClose}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>{annotationDialogTitle}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Annotation"
            type="text"
            fullWidth
            multiline
            minRows={3}
            value={annotationText}
            disabled={!canEditAnnotations}
            onChange={(event) => {
              if (!annotationDraft) {
                return;
              }
              setAnnotationDraft({ ...annotationDraft, text: event.target.value });
            }}
          />
        </DialogContent>
        <DialogActions>
          {canEditAnnotations && annotationDraft?.id && (
            <Button
              color="error"
              onClick={handleAnnotationDelete}
              disabled={annotationSaving}
            >
              Delete
            </Button>
          )}
          <Button onClick={handleAnnotationDialogClose}>Cancel</Button>
          {canEditAnnotations && (
            <Button
              onClick={handleAnnotationSave}
              disabled={annotationSaving || annotationsLoading}
              variant="contained"
            >
              Save
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Dialog>
  );
};

export default DocumentPreview;
