import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
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
  Chip,
  Alert,
  Collapse,
  FormControlLabel,
  Switch,
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
  ContentCopy,
  NavigateBefore,
  NavigateNext,
  Edit,
  Visibility,
  AutoAwesome,
  Refresh,
  ChatBubbleOutline,
  Person,
  Share,
  Approval,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import { CheckoutInfo, LockInfo, Node, PdfAnnotation, PdfAnnotationState, RecordDeclaration } from 'types';
import { useAppSelector, useAppDispatch } from 'store';
import { setSelectedNodeId, setShareLinkManagerOpen } from 'store/slices/uiSlice';
import apiService from 'services/api';
import nodeService, {
  NodeCheckoutGraph,
  NodeRenditionDefinitionStatus,
  NodeRenditionRelationSummary,
  OcrQueueStatus,
  PreviewQueueStatus,
} from 'services/nodeService';
import recordsManagementService from 'services/recordsManagementService';
import { toast } from 'react-toastify';
import { getEffectivePreviewStatus, getFailedPreviewMeta, isRetryablePreviewFailure } from 'utils/previewStatusUtils';
import { getLockInfoAlertMessage, getLockInfoAlertSeverity, getLockInfoChipLabel } from 'utils/lockInfoUtils';
import { getCheckoutInfoAlertMessage, getCheckoutInfoAlertSeverity, getCheckoutInfoChipLabel } from 'utils/checkoutInfoUtils';
import CheckoutGraphDialog from 'components/dialogs/CheckoutGraphDialog';
import RenditionDefinitionDialog from 'components/dialogs/RenditionDefinitionDialog';
import RecordStatusChip from 'components/records/RecordStatusChip';
import DeclareRecordDialog from 'components/records/DeclareRecordDialog';
import UndeclareRecordDialog from 'components/records/UndeclareRecordDialog';
import { resolveDocumentPreviewQueueState } from './documentPreviewQueueState';
import { formatCheckoutGraphSummary } from 'utils/checkoutGraphUtils';
import { getRenditionDefinitionLines } from 'utils/renditionDefinitionUtils';
const CommentSection = React.lazy(() => import('../comments/CommentSection'));

interface DocumentPreviewProps {
  open: boolean;
  onClose: () => void;
  node: Node;
  initialAnnotateMode?: boolean;
  initialCommentsOpen?: boolean;
  initialCommentDraftText?: string | null;
  initialCommentId?: string | null;
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
  failureCategory?: string;
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
  initialCommentsOpen = false,
  initialCommentDraftText = null,
  initialCommentId = null,
}) => {
  const navigate = useNavigate();
  const previewDispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const isAdmin = Boolean(user?.roles?.includes('ROLE_ADMIN'));
  const currentUsername = user?.username ?? null;
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
  const [previewQueueStatus, setPreviewQueueStatus] = useState<PreviewQueueStatus | null>(null);
  const [previewRenditionSummary, setPreviewRenditionSummary] = useState<NodeRenditionRelationSummary | null>(null);
  const [previewRenditionDefinitions, setPreviewRenditionDefinitions] = useState<NodeRenditionDefinitionStatus[]>([]);
  const [previewStatusOverride, setPreviewStatusOverride] = useState<string | null>(null);
  const [previewFailureOverride, setPreviewFailureOverride] = useState<string | null>(null);
  const [queueingPreview, setQueueingPreview] = useState(false);
  const [nodeDetails, setNodeDetails] = useState<Node | null>(null);
  const [nodeDetailsLoading, setNodeDetailsLoading] = useState(false);
  const [recordDeclaration, setRecordDeclaration] = useState<RecordDeclaration | null>(null);
  const [recordDeclarationLoading, setRecordDeclarationLoading] = useState(false);
  const [declareRecordDialogOpen, setDeclareRecordDialogOpen] = useState(false);
  const [undeclareRecordDialogOpen, setUndeclareRecordDialogOpen] = useState(false);
  const [lockInfo, setLockInfo] = useState<LockInfo | null>(null);
  const [checkoutInfo, setCheckoutInfo] = useState<CheckoutInfo | null>(null);
  const [checkoutGraph, setCheckoutGraph] = useState<NodeCheckoutGraph | null>(null);
  const [checkoutActionLoading, setCheckoutActionLoading] = useState<'checkout' | 'cancel' | null>(null);
  const [checkoutGraphDialogOpen, setCheckoutGraphDialogOpen] = useState(false);
  const [renditionDefinitionDialogOpen, setRenditionDefinitionDialogOpen] = useState(false);
  const [checkInDialogOpen, setCheckInDialogOpen] = useState(false);
  const [checkInFile, setCheckInFile] = useState<File | null>(null);
  const [checkInComment, setCheckInComment] = useState('');
  const [checkInMajorVersion, setCheckInMajorVersion] = useState(false);
  const [checkInKeepCheckedOut, setCheckInKeepCheckedOut] = useState(false);
  const [checkInSubmitting, setCheckInSubmitting] = useState(false);
  const [ocrQueueStatus, setOcrQueueStatus] = useState<OcrQueueStatus | null>(null);
  const [ocrStatusOverride, setOcrStatusOverride] = useState<string | null>(null);
  const [ocrFailureOverride, setOcrFailureOverride] = useState<string | null>(null);
  const [queueingOcr, setQueueingOcr] = useState(false);
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
  const [commentsOpen, setCommentsOpen] = useState(false);
  const [commentDraftSeed, setCommentDraftSeed] = useState<string | null>(null);
  const [commentDraftVersion, setCommentDraftVersion] = useState(0);
  const pdfContainerRef = useRef<HTMLDivElement | null>(null);
  const commentsPanelRef = useRef<HTMLDivElement | null>(null);
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
  const effectiveAspects = useMemo(
    () => nodeDetails?.aspects ?? node?.aspects ?? [],
    [node?.aspects, nodeDetails?.aspects]
  );
  const isDeclaredRecord = effectiveAspects.includes('rm:record') || Boolean(recordDeclaration);
  const mentionTargets = [
    { label: 'Creator', username: node?.creator },
    { label: 'Modifier', username: node?.modifier },
    { label: 'Correspondent', username: node?.correspondent },
  ].filter((item, index, list) => (
    Boolean(item.username)
    && list.findIndex((candidate) => candidate.username === item.username) === index
  )) as Array<{ label: string; username: string }>;
  const fitModeLabel = fitModeLabels[fitMode];
  const fitScaleLabel = fitScale ? ` (${Math.round(fitScale * 100)}%)` : '';
  const previewQueueResolvedState = resolveDocumentPreviewQueueState(previewQueueStatus, {
    previewStatus: previewStatusOverride
      || previewRenditionSummary?.previewStatus
      || node?.previewStatus
      || serverPreview?.status
      || null,
    previewFailureReason: previewFailureOverride
      || previewRenditionSummary?.previewFailureReason
      || node?.previewFailureReason
      || serverPreview?.failureReason
      || null,
    previewFailureCategory: previewRenditionSummary?.previewFailureCategory
      || node?.previewFailureCategory
      || serverPreview?.failureCategory
      || null,
    previewLastUpdated: previewRenditionSummary?.previewLastUpdated
      || null,
  });
  const rawResolvedPreviewStatus = previewQueueResolvedState.previewStatus;
  const resolvedPreviewFailure = previewQueueResolvedState.previewFailureReason;
  const resolvedPreviewFailureCategory = previewQueueResolvedState.previewFailureCategory;
  const resolvedPreviewLastUpdated = previewQueueResolvedState.previewLastUpdated;
  const resolvedPreviewStatus = getEffectivePreviewStatus(
    rawResolvedPreviewStatus,
    resolvedPreviewFailureCategory,
    effectiveContentType,
    resolvedPreviewFailure
  );
  const failedPreviewMeta = getFailedPreviewMeta(
    effectiveContentType,
    resolvedPreviewFailureCategory,
    resolvedPreviewFailure
  );
  const previewFailureRetryable = resolvedPreviewStatus === 'FAILED' && isRetryablePreviewFailure(
    resolvedPreviewFailureCategory,
    effectiveContentType,
    resolvedPreviewFailure
  );
  const showPreviewFailureAlert = resolvedPreviewStatus === 'FAILED' || resolvedPreviewStatus === 'UNSUPPORTED';
  const previewFailureSeverity = previewFailureRetryable
    ? 'warning'
    : failedPreviewMeta.unsupported
      ? 'info'
      : 'error';
  const previewStatusLabel = resolvedPreviewStatus
    ? resolvedPreviewStatus === 'FAILED' || resolvedPreviewStatus === 'UNSUPPORTED'
      ? failedPreviewMeta.label
      : `Preview: ${resolvedPreviewStatus.charAt(0).toUpperCase()}${resolvedPreviewStatus.slice(1).toLowerCase()}`
    : null;
  const previewPollIntervalMs = 15000;
  const shouldPollPreview = resolvedPreviewStatus === 'PROCESSING' || resolvedPreviewStatus === 'QUEUED';
  const previewStatusTooltip = resolvedPreviewFailure
    ? resolvedPreviewFailure
    : shouldPollPreview
      ? `Status refreshes every ${previewPollIntervalMs / 1000}s while queued or processing.`
      : 'Preview status reflects the latest generation state.';
  const previewStatusColor = (() => {
    switch (resolvedPreviewStatus) {
      case 'READY':
        return 'success';
      case 'FAILED':
        return failedPreviewMeta.color;
      case 'QUEUED':
      case 'PROCESSING':
        return 'warning';
      case 'PENDING':
      case 'UNSUPPORTED':
      case 'STALE':
        return 'info';
      default:
        return 'default';
    }
  })();
  const effectiveNodeMetadata = (nodeDetails?.metadata ?? node?.metadata ?? {}) as Record<string, any>;
  const lockInfoChipLabel = getLockInfoChipLabel(lockInfo);
  const lockInfoAlertMessage = getLockInfoAlertMessage(lockInfo);
  const lockInfoAlertSeverity = getLockInfoAlertSeverity(lockInfo);
  const checkoutInfoChipLabel = getCheckoutInfoChipLabel(checkoutInfo);
  const checkoutInfoAlertMessage = getCheckoutInfoAlertMessage(checkoutInfo);
  const checkoutInfoAlertSeverity = getCheckoutInfoAlertSeverity(checkoutInfo);
  const resolvedOcrStatus = ocrStatusOverride
    || ocrQueueStatus?.ocrStatus
    || (effectiveNodeMetadata?.ocrStatus as string | undefined)
    || null;
  const resolvedOcrFailure = ocrFailureOverride
    || (effectiveNodeMetadata?.ocrFailureReason as string | undefined)
    || null;
  const ocrPollIntervalMs = 15000;
  const shouldPollOcr = resolvedOcrStatus === 'PROCESSING';
  const ocrStatusLabel = (() => {
    if (resolvedOcrStatus) {
      const normalized = String(resolvedOcrStatus).toUpperCase();
      const pretty = `${normalized.charAt(0)}${normalized.slice(1).toLowerCase()}`;
      return `OCR: ${pretty}`;
    }
    const message = ocrQueueStatus?.message?.trim();
    if (!message) {
      return null;
    }
    if (/disabled/i.test(message)) {
      return 'OCR: Disabled';
    }
    if (/not eligible/i.test(message)) {
      return 'OCR: Not eligible';
    }
    return 'OCR: Unavailable';
  })();
  const ocrStatusTooltip = resolvedOcrFailure
    ? resolvedOcrFailure
    : shouldPollOcr
      ? `Status refreshes every ${ocrPollIntervalMs / 1000}s while processing.`
      : ocrQueueStatus?.message
        || 'OCR status reflects the latest extraction state.';
  const ocrStatusColor = (() => {
    switch (resolvedOcrStatus) {
      case 'READY':
        return 'success';
      case 'FAILED':
        return 'error';
      case 'PROCESSING':
        return 'warning';
      default:
        return 'default';
    }
  })();
  const previewRenditionDefinitionLines = getRenditionDefinitionLines(previewRenditionDefinitions);
  const previewSource = (() => {
    if (officeDocument) {
      return canWrite ? 'Online editor' : 'Online viewer';
    }
    if (effectiveContentType === 'application/pdf') {
      return serverPreview ? 'Server preview' : 'PDF viewer';
    }
    if (effectiveContentType?.startsWith('image/')) {
      return 'Image preview';
    }
    if (effectiveContentType?.startsWith('text/')) {
      return 'Text preview';
    }
    if (fileUrl) {
      return 'File preview';
    }
    return 'Preview';
  })();
  const previewSourceDetail = (() => {
    if (officeDocument) {
      return canWrite ? 'WOPI edit session' : 'WOPI view session';
    }
    if (effectiveContentType === 'application/pdf' && serverPreview) {
      if (emptyPdf) {
        return 'Fallback: empty PDF';
      }
      if (pdfLoadFailed) {
        return 'Fallback: client PDF failed';
      }
      if (serverPreview.failureReason) {
        return `Fallback: ${serverPreview.failureReason}`;
      }
      if (serverPreview.message) {
        return `Fallback: ${serverPreview.message}`;
      }
      return 'Server-rendered preview';
    }
    if (effectiveContentType === 'application/pdf') {
      return 'Client-side PDF viewer';
    }
    return null;
  })();

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
      if (preview?.status) {
        setPreviewStatusOverride(preview.status);
      }
      if (preview?.failureReason) {
        setPreviewFailureOverride(preview.failureReason);
      }
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

  const loadNodeDetails = useCallback(async () => {
    if (!nodeId) {
      return;
    }
    setNodeDetailsLoading(true);
    try {
      const [details, nextLockInfo, nextCheckoutInfo, nextCheckoutGraph, nextPreviewRenditionSummary, nextPreviewRenditionDefinitions] = await Promise.all([
        nodeService.getNode(nodeId),
        nodeService.getLockInfo(nodeId).catch(() => null),
        nodeService.getCheckoutInfo(nodeId).catch(() => null),
        nodeService.getNodeRelationCheckoutGraph(nodeId).catch(() => null),
        nodeService.getNodeRenditionRelationSummary(nodeId).catch(() => null),
        nodeService.getNodeRenditionDefinitions(nodeId).catch(() => []),
      ]);
      setNodeDetails(details);
      setLockInfo(nextLockInfo);
      setCheckoutInfo(nextCheckoutInfo);
      setCheckoutGraph(nextCheckoutGraph);
      setPreviewRenditionSummary(nextPreviewRenditionSummary);
      setPreviewRenditionDefinitions(nextPreviewRenditionDefinitions || []);
    } catch {
      // Best effort only: OCR metadata should not block preview rendering.
    } finally {
      setNodeDetailsLoading(false);
    }
  }, [nodeId]);

  useEffect(() => {
    if (!open || !nodeId) {
      return;
    }
    void loadNodeDetails();
  }, [loadNodeDetails, nodeId, open]);

  useEffect(() => {
    if (!open || !nodeId || !isAdmin || !effectiveAspects.includes('rm:record')) {
      setRecordDeclaration(null);
      setRecordDeclarationLoading(false);
      return;
    }

    let cancelled = false;
    setRecordDeclarationLoading(true);
    void recordsManagementService.getRecord(nodeId)
      .then((declaration) => {
        if (!cancelled) {
          setRecordDeclaration(declaration);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRecordDeclaration(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setRecordDeclarationLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [effectiveAspects, isAdmin, nodeId, open]);

  const closeCheckInDialog = useCallback(() => {
    if (checkInSubmitting) {
      return;
    }
    setCheckInDialogOpen(false);
    setCheckInFile(null);
    setCheckInComment('');
    setCheckInMajorVersion(false);
    setCheckInKeepCheckedOut(false);
  }, [checkInSubmitting]);

  const handleCheckoutAction = useCallback(async () => {
    if (!nodeId) {
      return;
    }
    setCheckoutActionLoading('checkout');
    try {
      await nodeService.checkoutDocument(nodeId);
      await loadNodeDetails();
      toast.success('Document checked out');
    } catch {
      toast.error('Failed to check out document');
    } finally {
      setCheckoutActionLoading(null);
    }
  }, [loadNodeDetails, nodeId]);

  const handleCancelCheckoutAction = useCallback(async () => {
    if (!nodeId) {
      return;
    }
    setCheckoutActionLoading('cancel');
    try {
      await nodeService.cancelCheckoutDocument(nodeId);
      await loadNodeDetails();
      toast.success('Checkout cancelled');
    } catch {
      toast.error('Failed to cancel checkout');
    } finally {
      setCheckoutActionLoading(null);
    }
  }, [loadNodeDetails, nodeId]);

  const handleCheckInAction = useCallback(async () => {
    if (!nodeId) {
      return;
    }
    if (checkInKeepCheckedOut && !checkInFile) {
      toast.error('Keep checked out requires a new version file');
      return;
    }
    setCheckInSubmitting(true);
    try {
      await nodeService.checkinDocument(nodeId, {
        file: checkInFile,
        comment: checkInComment,
        majorVersion: checkInMajorVersion,
        keepCheckedOut: checkInKeepCheckedOut,
      });
      await loadNodeDetails();
      toast.success(checkInFile ? 'Document checked in with new version' : 'Document checked in');
      closeCheckInDialog();
    } catch {
      toast.error('Failed to check in document');
    } finally {
      setCheckInSubmitting(false);
    }
  }, [checkInComment, checkInFile, checkInKeepCheckedOut, checkInMajorVersion, closeCheckInDialog, loadNodeDetails, nodeId]);

  const handleQueuePreview = useCallback(async (force = false) => {
    if (!nodeId) {
      return;
    }
    setQueueingPreview(true);
    try {
      const status = await nodeService.queuePreview(nodeId, force);
      setPreviewQueueStatus(status);
      setPreviewStatusOverride(status?.previewStatus || (status?.queued ? 'PROCESSING' : null));
      setPreviewFailureOverride(status?.previewFailureReason ?? null);
      if (status?.queued) {
        toast.success(status?.message || 'Preview queued');
      } else {
        toast.info(status?.message || 'Preview already up to date');
      }
    } catch {
      toast.error('Failed to queue preview generation');
    } finally {
      setQueueingPreview(false);
    }
  }, [nodeId]);

  const handleQueueOcr = useCallback(async (force = false) => {
    if (!nodeId) {
      return;
    }
    setQueueingOcr(true);
    try {
      const status = await nodeService.queueOcr(nodeId, force);
      setOcrQueueStatus(status);
      setOcrFailureOverride(null);

      if (status?.queued) {
        setOcrStatusOverride('PROCESSING');
        toast.success('OCR queued');
      } else {
        if (status?.ocrStatus) {
          setOcrStatusOverride(status.ocrStatus);
        }
        toast.info(status?.message || 'OCR already up to date');
      }

      void loadNodeDetails();
    } catch {
      toast.error('Failed to queue OCR');
    } finally {
      setQueueingOcr(false);
    }
  }, [loadNodeDetails, nodeId]);

  useEffect(() => {
    if (!open || !nodeId || !shouldPollPreview) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      void loadServerPreview();
    }, previewPollIntervalMs);
    return () => window.clearInterval(interval);
  }, [loadServerPreview, nodeId, open, shouldPollPreview]);

  useEffect(() => {
    if (!open || !nodeId || !shouldPollOcr) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      void loadNodeDetails();
    }, ocrPollIntervalMs);
    return () => window.clearInterval(interval);
  }, [loadNodeDetails, nodeId, open, shouldPollOcr]);

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
    setPreviewQueueStatus(null);
    setPreviewRenditionSummary(null);
    setPreviewStatusOverride(null);
    setPreviewFailureOverride(null);
    setQueueingPreview(false);
    setNodeDetails(null);
    setNodeDetailsLoading(false);
    setRecordDeclaration(null);
    setRecordDeclarationLoading(false);
    setDeclareRecordDialogOpen(false);
    setUndeclareRecordDialogOpen(false);
    setLockInfo(null);
    setCheckoutInfo(null);
    setCheckoutGraph(null);
    setOcrQueueStatus(null);
    setOcrStatusOverride(null);
    setOcrFailureOverride(null);
    setQueueingOcr(false);
    setAnnotations([]);
    setAnnotationsUpdatedBy(null);
    setAnnotationsUpdatedAt(null);
    setAnnotationsLoading(false);
    setEmptyPdf(false);
    setAnnotateMode(initialAnnotateMode);
    setAnnotationDialogOpen(false);
    setAnnotationDraft(null);
    setCommentsOpen(false);
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

  useEffect(() => {
    if (!open) {
      return;
    }

    if (initialCommentDraftText !== undefined) {
      setCommentDraftSeed(initialCommentDraftText || null);
      setCommentDraftVersion((value) => value + 1);
    }

    if (initialCommentsOpen || initialCommentDraftText || initialCommentId) {
      setCommentsOpen(true);
      window.setTimeout(() => {
        commentsPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 0);
    }
  }, [initialCommentDraftText, initialCommentId, initialCommentsOpen, nodeId, open]);

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

  const handleOpenComments = (draftSeed?: string) => {
    if (draftSeed) {
      setCommentDraftSeed(draftSeed);
      setCommentDraftVersion((value) => value + 1);
    }
    setCommentsOpen((current) => {
      const next = !current;
      if (next) {
        window.setTimeout(() => {
          commentsPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 0);
      }
      return next;
    });
  };

  const handleQuickMention = async (username?: string) => {
    if (!username) {
      return;
    }

    const mentionText = `@${username} `;
    setCommentDraftSeed(mentionText);
    setCommentDraftVersion((value) => value + 1);
    setCommentsOpen(true);
    window.setTimeout(() => {
      commentsPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 0);

    try {
      await navigator.clipboard.writeText(mentionText);
      toast.success(`Copied ${mentionText.trim()} to clipboard`);
    } catch {
      // Clipboard access is best-effort only.
    }
  };

  const handleOpenPeopleDirectoryProfile = (username?: string) => {
    if (!username) {
      return;
    }
    navigate(`/people-directory?username=${encodeURIComponent(username)}`);
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
          <Button
            color="inherit"
            variant={commentsOpen ? 'outlined' : 'text'}
            onClick={() => handleOpenComments()}
            startIcon={<ChatBubbleOutline />}
            sx={{ mr: 2 }}
          >
            {commentsOpen ? 'Hide discussion' : 'Discuss'}
          </Button>
          {previewStatusLabel && (
            <Tooltip title={previewStatusTooltip} arrow>
              <Chip
                size="small"
                variant="outlined"
                label={previewStatusLabel}
                color={previewStatusColor}
                sx={{ mr: 2 }}
              />
            </Tooltip>
          )}
          {ocrStatusLabel && (
            <Tooltip title={ocrStatusTooltip} arrow>
              <Chip
                size="small"
                variant="outlined"
                label={ocrStatusLabel}
                color={ocrStatusColor}
                sx={{ mr: 2 }}
              />
            </Tooltip>
          )}
          {previewSource && (
            <Tooltip title={previewSourceDetail ?? ''} arrow disableHoverListener={!previewSourceDetail}>
              <Chip
                size="small"
                variant="outlined"
                label={`Source: ${previewSource}`}
                sx={{ mr: 2 }}
              />
            </Tooltip>
          )}
          {previewRenditionDefinitionLines.length > 0 && (
            <Tooltip
              title={(
                <Box>
                  {previewRenditionDefinitionLines.map((line) => (
                    <Typography key={line} variant="caption" display="block">
                      {line}
                    </Typography>
                  ))}
                </Box>
              )}
              arrow
            >
              <Chip
                size="small"
                variant="outlined"
                label={`Renditions ${previewRenditionDefinitionLines.length}`}
                sx={{ mr: 2 }}
                clickable
                onClick={() => setRenditionDefinitionDialogOpen(true)}
              />
            </Tooltip>
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

          {lockInfoChipLabel && (
            <Tooltip title={lockInfoAlertMessage || lockInfoChipLabel}>
              <Chip
                size="small"
                color={lockInfo?.status === 'LOCK_OWNER' ? 'success' : lockInfo?.status === 'LOCK_EXPIRED' ? 'info' : 'warning'}
                variant="outlined"
                label={lockInfoChipLabel}
                sx={{ mr: 2 }}
              />
            </Tooltip>
          )}

          {checkoutInfoChipLabel && (
            <Tooltip title={checkoutInfoAlertMessage || checkoutInfoChipLabel}>
              <Chip
                size="small"
                color={checkoutInfo?.status === 'CHECKED_OUT_BY_YOU' ? 'success' : 'warning'}
                variant="outlined"
                label={checkoutInfoChipLabel}
                sx={{ mr: 2 }}
              />
            </Tooltip>
          )}

          {isDeclaredRecord && (
            <RecordStatusChip
              declaration={recordDeclaration}
              sx={{ mr: 2 }}
            />
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
            {node?.nodeType === 'DOCUMENT' && (
              <MenuItem
                onClick={() => {
                  handleQueuePreview(false);
                  handleMenuClose();
                }}
                disabled={queueingPreview}
              >
                <ListItemIcon>
                  <Refresh fontSize="small" />
                </ListItemIcon>
                <ListItemText>Queue Preview</ListItemText>
              </MenuItem>
            )}
            {node?.nodeType === 'DOCUMENT' && (
              <MenuItem
                onClick={() => {
                  handleQueuePreview(true);
                  handleMenuClose();
                }}
                disabled={queueingPreview}
              >
                <ListItemIcon>
                  <Refresh fontSize="small" />
                </ListItemIcon>
                <ListItemText>Force Preview Rebuild</ListItemText>
              </MenuItem>
            )}
            {node?.nodeType === 'DOCUMENT' && (
              <MenuItem
                onClick={() => {
                  handleQueueOcr(false);
                  handleMenuClose();
                }}
                disabled={queueingOcr}
              >
                <ListItemIcon>
                  <Refresh fontSize="small" />
                </ListItemIcon>
                <ListItemText>Queue OCR</ListItemText>
              </MenuItem>
            )}
            {node?.nodeType === 'DOCUMENT' && (
              <MenuItem
                onClick={() => {
                  handleQueueOcr(true);
                  handleMenuClose();
                }}
                disabled={queueingOcr}
              >
                <ListItemIcon>
                  <Refresh fontSize="small" />
                </ListItemIcon>
                <ListItemText>Force OCR Rebuild</ListItemText>
              </MenuItem>
            )}
            <MenuItem onClick={() => { handleDownload(); handleMenuClose(); }}>
              <ListItemIcon>
                <Download fontSize="small" />
              </ListItemIcon>
              <ListItemText>Download</ListItemText>
            </MenuItem>
            {canWrite && node?.nodeType === 'DOCUMENT' && (
              <MenuItem onClick={() => {
                if (nodeId) {
                  previewDispatch(setSelectedNodeId(nodeId));
                  previewDispatch(setShareLinkManagerOpen(true));
                }
                handleMenuClose();
              }}>
                <ListItemIcon>
                  <Share fontSize="small" />
                </ListItemIcon>
                <ListItemText>Share</ListItemText>
              </MenuItem>
            )}
            {isAdmin && node?.nodeType === 'DOCUMENT' && !isDeclaredRecord && (
              <MenuItem
                onClick={() => {
                  setDeclareRecordDialogOpen(true);
                  handleMenuClose();
                }}
                disabled={recordDeclarationLoading || Boolean(nodeDetails?.checkedOut || node?.checkedOut)}
              >
                <ListItemIcon>
                  <Approval fontSize="small" />
                </ListItemIcon>
                <ListItemText>Declare Record</ListItemText>
              </MenuItem>
            )}
            {isAdmin && node?.nodeType === 'DOCUMENT' && isDeclaredRecord && (
              <MenuItem
                onClick={() => {
                  setUndeclareRecordDialogOpen(true);
                  handleMenuClose();
                }}
                disabled={recordDeclarationLoading}
              >
                <ListItemIcon>
                  <Approval fontSize="small" />
                </ListItemIcon>
                <ListItemText>Undeclare Record...</ListItemText>
              </MenuItem>
            )}
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
        {(shouldPollPreview
          || showPreviewFailureAlert
          || shouldPollOcr
          || resolvedOcrStatus === 'FAILED'
          || Boolean(lockInfoAlertMessage)
          || Boolean(checkoutInfoAlertMessage)
          || Boolean(recordDeclaration)) && (
          <Box sx={{ p: 2, bgcolor: 'background.paper', borderBottom: '1px solid', borderColor: 'divider' }}>
            {recordDeclaration && (
              <Alert
                severity="warning"
                sx={{
                  mb: checkoutInfoAlertMessage || lockInfoAlertMessage || shouldPollPreview || showPreviewFailureAlert || shouldPollOcr || resolvedOcrStatus === 'FAILED'
                    ? 1
                    : 0,
                }}
              >
                {`Declared as a record${recordDeclaration.declaredBy ? ` by ${recordDeclaration.declaredBy}` : ''}${recordDeclaration.declaredAt ? ` on ${new Date(recordDeclaration.declaredAt).toLocaleString()}` : ''}.`}
                {recordDeclaration.declarationComment ? ` Comment: ${recordDeclaration.declarationComment}` : ''}
              </Alert>
            )}
            {checkoutInfoAlertMessage && checkoutInfoAlertSeverity && (
              <Alert
                severity={checkoutInfoAlertSeverity}
                sx={{ mb: lockInfoAlertMessage || shouldPollPreview || showPreviewFailureAlert || shouldPollOcr || resolvedOcrStatus === 'FAILED' ? 1 : 0 }}
                action={(
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {checkoutInfo?.canCheckout && canWrite && (
                      <Button
                        color="inherit"
                        size="small"
                        disabled={checkoutActionLoading === 'checkout'}
                        onClick={() => { void handleCheckoutAction(); }}
                      >
                        {checkoutActionLoading === 'checkout' ? 'Checking out...' : 'Check Out'}
                      </Button>
                    )}
                    {checkoutInfo?.canCheckIn && canWrite && (
                      <Button
                        color="inherit"
                        size="small"
                        disabled={checkInSubmitting}
                        onClick={() => setCheckInDialogOpen(true)}
                      >
                        {checkInSubmitting ? 'Checking in...' : 'Check In'}
                      </Button>
                    )}
                    {checkoutInfo?.canCancelCheckout && canWrite && (
                      <Button
                        color="inherit"
                        size="small"
                        disabled={checkoutActionLoading === 'cancel'}
                        onClick={() => { void handleCancelCheckoutAction(); }}
                      >
                        {checkoutActionLoading === 'cancel' ? 'Cancelling...' : 'Cancel Checkout'}
                      </Button>
                    )}
                    {checkoutGraph?.destinationNode?.id && (
                      <Button
                        color="inherit"
                        size="small"
                        onClick={() => navigate(`/browse/${checkoutGraph.destinationNode?.id}`)}
                      >
                        Open check-in target
                      </Button>
                    )}
                    {checkoutGraph?.checkedOut && (
                      <Button
                        color="inherit"
                        size="small"
                        onClick={() => setCheckoutGraphDialogOpen(true)}
                      >
                        View checkout graph
                      </Button>
                    )}
                  </Box>
                )}
              >
                {checkoutInfoAlertMessage}
                {checkoutGraph && (
                  <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
                    Graph: {formatCheckoutGraphSummary(checkoutGraph)}
                  </Typography>
                )}
              </Alert>
            )}
            {lockInfoAlertMessage && lockInfoAlertSeverity && (
              <Alert severity={lockInfoAlertSeverity} sx={{ mb: shouldPollPreview || showPreviewFailureAlert || shouldPollOcr || resolvedOcrStatus === 'FAILED' ? 1 : 0 }}>
                {lockInfoAlertMessage}
              </Alert>
            )}
            {shouldPollPreview && (
              <Alert severity="info" sx={{ mb: 1 }}>
                Preview generation is in progress. Status updates every {previewPollIntervalMs / 1000}s.
              </Alert>
            )}
            {showPreviewFailureAlert && (
              <Alert
                severity={previewFailureSeverity}
                action={previewFailureRetryable ? (
                  <Box display="flex" gap={1} flexWrap="wrap">
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleQueuePreview(false)}
                      disabled={queueingPreview}
                    >
                      Retry preview
                    </Button>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleQueuePreview(true)}
                      disabled={queueingPreview}
                    >
                      Force rebuild
                    </Button>
                  </Box>
                ) : undefined}
              >
                {resolvedPreviewFailure
                  ? failedPreviewMeta.unsupported
                    ? `Preview unsupported: ${resolvedPreviewFailure}`
                    : previewFailureRetryable
                      ? `Preview failed: ${resolvedPreviewFailure}`
                      : `Preview failed (permanent): ${resolvedPreviewFailure}`
                  : previewFailureRetryable
                    ? 'Preview failed. Retry generation or force a rebuild if the file recently changed.'
                    : failedPreviewMeta.unsupported
                      ? 'Preview is not supported for this file type.'
                      : 'Preview failed permanently. Retry is disabled; download the file or upload a corrected version.'}
              </Alert>
            )}
            {shouldPollOcr && (
              <Alert severity="info" sx={{ mt: shouldPollPreview || showPreviewFailureAlert ? 1 : 0, mb: 1 }}>
                OCR extraction is in progress. Status updates every {ocrPollIntervalMs / 1000}s.
              </Alert>
            )}
            {resolvedOcrStatus === 'FAILED' && (
              <Alert
                severity="warning"
                action={(
                  <Box display="flex" gap={1} flexWrap="wrap">
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleQueueOcr(false)}
                      disabled={queueingOcr}
                    >
                      Retry OCR
                    </Button>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => handleQueueOcr(true)}
                      disabled={queueingOcr}
                    >
                      Force OCR
                    </Button>
                  </Box>
                )}
              >
                {resolvedOcrFailure
                  ? `OCR failed: ${resolvedOcrFailure}`
                  : 'OCR failed. Retry extraction or force a rebuild if the file recently changed.'}
              </Alert>
            )}
            {(previewQueueStatus?.attempts
              || previewQueueStatus?.nextAttemptAt
              || serverPreviewError
              || ocrQueueStatus?.attempts
              || ocrQueueStatus?.nextAttemptAt) && (
              <Box mt={1}>
                {previewQueueStatus?.attempts !== undefined && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    Preview attempts: {previewQueueStatus.attempts}
                  </Typography>
                )}
                {previewQueueStatus?.nextAttemptAt && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    Preview next retry: {format(new Date(previewQueueStatus.nextAttemptAt), 'PPp')}
                  </Typography>
                )}
                {resolvedPreviewLastUpdated && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    Preview updated: {format(new Date(resolvedPreviewLastUpdated), 'PPp')}
                  </Typography>
                )}
                {serverPreviewError && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    Server preview error: {serverPreviewError}
                  </Typography>
                )}
                {ocrQueueStatus?.attempts !== undefined && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    OCR attempts: {ocrQueueStatus.attempts}
                  </Typography>
                )}
                {ocrQueueStatus?.nextAttemptAt && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    OCR next retry: {format(new Date(ocrQueueStatus.nextAttemptAt), 'PPp')}
                  </Typography>
                )}
                {nodeDetailsLoading && shouldPollOcr && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    Refreshing OCR status...
                  </Typography>
                )}
              </Box>
            )}
          </Box>
        )}
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ flex: 1, minHeight: 0 }}>
            {renderPreview()}
          </Box>
          <Collapse in={commentsOpen} timeout={250} unmountOnExit>
            <Box
              ref={commentsPanelRef}
              sx={{
                borderTop: '1px solid',
                borderColor: 'divider',
                bgcolor: 'background.paper',
                height: { xs: 360, md: 420 },
                overflow: 'auto',
              }}
            >
              <Box sx={{ px: 2, py: 1.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                <Box display="flex" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap">
                  <Box>
                    <Typography variant="subtitle2">Document Comments</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Visible discussion thread for this document
                    </Typography>
                  </Box>
                  {mentionTargets.length > 0 && (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={() => {
                        commentsPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                      }}
                    >
                      Jump to discussion
                    </Button>
                  )}
                </Box>
              </Box>
              <Box sx={{ p: 2 }}>
                {mentionTargets.length > 0 && (
                  <Box sx={{ mb: 2 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>
                      Quick mention
                    </Typography>
                    <Box display="flex" gap={1} flexWrap="wrap">
                      {mentionTargets.map((target) => (
                        <Box key={target.username} display="flex" alignItems="center" gap={0.5}>
                          <Chip
                            size="small"
                            icon={<ContentCopy fontSize="small" />}
                            variant="outlined"
                            label={`@${target.username}`}
                            onClick={() => void handleQuickMention(target.username)}
                          />
                          <Tooltip title={`Open ${target.label.toLowerCase()} profile`}>
                            <IconButton
                              size="small"
                              aria-label={`Open ${target.username} profile`}
                              onClick={() => handleOpenPeopleDirectoryProfile(target.username)}
                              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}
                            >
                              <Person fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </Box>
                      ))}
                    </Box>
                  </Box>
                )}
                <React.Suspense
                  fallback={
                    <Box display="flex" justifyContent="center" alignItems="center" py={4}>
                      <CircularProgress />
                    </Box>
                  }
                >
                  <CommentSection
                    nodeId={node.id}
                    initialDraftText={commentDraftSeed}
                    draftVersion={commentDraftVersion}
                    initialCommentId={initialCommentId}
                  />
                </React.Suspense>
              </Box>
            </Box>
          </Collapse>
        </Box>
      </DialogContent>

      <Dialog
        open={checkInDialogOpen}
        onClose={closeCheckInDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Check In</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Check in "{node.name}" with an optional new version file.
            </Typography>
            <Alert severity="info">
              Leave the file empty to release checkout without uploading a new version. Enable keep checked out only when uploading a new file.
            </Alert>
            <Button component="label" variant="outlined" sx={{ alignSelf: 'flex-start' }}>
              {checkInFile ? `Selected: ${checkInFile.name}` : 'Choose New Version File'}
              <input
                hidden
                type="file"
                onChange={(event) => {
                  const file = event.target.files?.[0] ?? null;
                  setCheckInFile(file);
                  if (!file) {
                    setCheckInMajorVersion(false);
                    setCheckInKeepCheckedOut(false);
                  }
                  event.currentTarget.value = '';
                }}
              />
            </Button>
            {checkInFile && (
              <Button
                color="inherit"
                onClick={() => {
                  setCheckInFile(null);
                  setCheckInMajorVersion(false);
                  setCheckInKeepCheckedOut(false);
                }}
                sx={{ alignSelf: 'flex-start' }}
              >
                Clear File
              </Button>
            )}
            <TextField
              label="Version Comment"
              value={checkInComment}
              onChange={(event) => setCheckInComment(event.target.value)}
              fullWidth
              multiline
              minRows={2}
            />
            <FormControlLabel
              control={(
                <Switch
                  checked={checkInMajorVersion}
                  onChange={(event) => setCheckInMajorVersion(event.target.checked)}
                  disabled={!checkInFile}
                />
              )}
              label="Create major version"
            />
            <FormControlLabel
              control={(
                <Switch
                  checked={checkInKeepCheckedOut}
                  onChange={(event) => setCheckInKeepCheckedOut(event.target.checked)}
                  disabled={!checkInFile || (Boolean(node.checkoutUser) && node.checkoutUser !== currentUsername && !isAdmin)}
                />
              )}
              label="Keep checked out after check-in"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeCheckInDialog} disabled={checkInSubmitting}>
            Cancel
          </Button>
          <Button onClick={() => void handleCheckInAction()} disabled={checkInSubmitting} variant="contained">
            {checkInSubmitting ? 'Checking in...' : 'Check In'}
          </Button>
        </DialogActions>
      </Dialog>

      <DeclareRecordDialog
        open={declareRecordDialogOpen}
        nodeId={nodeId}
        nodeName={node.name}
        onClose={() => setDeclareRecordDialogOpen(false)}
        onDeclared={(declaration) => {
          setRecordDeclaration(declaration);
          void loadNodeDetails();
        }}
      />

      <UndeclareRecordDialog
        open={undeclareRecordDialogOpen}
        nodeId={nodeId}
        nodeName={node.name}
        onClose={() => setUndeclareRecordDialogOpen(false)}
        onUndeclared={() => {
          setRecordDeclaration(null);
          void loadNodeDetails();
        }}
      />

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
      <CheckoutGraphDialog
        open={checkoutGraphDialogOpen}
        nodeId={node.id}
        nodeName={node.name}
        onClose={() => setCheckoutGraphDialogOpen(false)}
      />
      <RenditionDefinitionDialog
        open={renditionDefinitionDialogOpen}
        nodeId={node.id}
        nodeName={node.name}
        onClose={() => setRenditionDefinitionDialogOpen(false)}
      />
    </Dialog>
  );
};

export default DocumentPreview;
