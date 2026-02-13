import React, { useState, useCallback, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  LinearProgress,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  IconButton,
  Chip,
  Alert,
  Switch,
  FormControlLabel,
} from '@mui/material';
import { useDropzone } from 'react-dropzone';
import {
  CloudUpload,
  InsertDriveFile,
  Close,
  CheckCircle,
  Error,
  Refresh,
  DeleteSweep,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'store';
import { setUploadDialogOpen } from 'store/slices/uiSlice';
import { uploadDocument } from 'store/slices/nodeSlice';
import { toast } from 'react-toastify';
import authService from 'services/authService';
import nodeService from 'services/nodeService';
import apiService from 'services/api';
import { Node } from 'types';
import { getEffectivePreviewStatus, getFailedPreviewMeta } from 'utils/previewStatusUtils';

interface UploadFile {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error';
  error?: string;
}

interface PreviewQueueStatus {
  queued?: boolean;
  previewStatus?: string;
  message?: string;
}

const uploadAutoRefreshKey = 'ecmUploadAutoRefresh';
const uploadAutoRefreshIntervalMs = 15000;

const UploadDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { uploadDialogOpen } = useAppSelector((state) => state.ui);
  const { currentNode } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const isAdmin = Boolean(effectiveUser?.roles?.includes('ROLE_ADMIN'));
  const canWrite = Boolean(
    effectiveUser?.roles?.includes('ROLE_ADMIN') || effectiveUser?.roles?.includes('ROLE_EDITOR')
  );
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const hasUploadableFiles = files.some((file) => file.status !== 'success');
  const [uploadSummary, setUploadSummary] = useState<{ success: number; failed: number } | null>(null);
  const [uploadedItems, setUploadedItems] = useState<Node[]>([]);
  const [refreshingUploads, setRefreshingUploads] = useState(false);
  const [queueingPreviewIds, setQueueingPreviewIds] = useState<string[]>([]);
  const [lastStatusRefreshAt, setLastStatusRefreshAt] = useState<Date | null>(null);
  const [autoRefreshUploads, setAutoRefreshUploads] = useState(() => {
    if (typeof window === 'undefined') {
      return true;
    }
    const saved = window.localStorage.getItem(uploadAutoRefreshKey);
    if (saved === null) {
      return true;
    }
    return saved === 'true';
  });

  const resetDialog = () => {
    dispatch(setUploadDialogOpen(false));
    setFiles([]);
    setUploadSummary(null);
    setUploadedItems([]);
    setRefreshingUploads(false);
  };

  const onDrop = useCallback((acceptedFiles: File[]) => {
    if (!canWrite) {
      return;
    }
    setUploadSummary(null);
    const newFiles = acceptedFiles.map((file) => ({
      file,
      progress: 0,
      status: 'pending' as const,
    }));
    setFiles((prev) => [...prev, ...newFiles]);
  }, [canWrite]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
    disabled: uploading || !canWrite,
  });

  const handleClose = () => {
    if (!uploading) {
      resetDialog();
    }
  };

  const handleRemoveFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
    setUploadSummary(null);
  };

  const handleRetryFile = (index: number) => {
    setUploadSummary(null);
    setFiles((prev) =>
      prev.map((file, idx) =>
        idx === index
          ? {
              ...file,
              status: 'pending' as const,
              progress: 0,
              error: undefined,
            }
          : file
      )
    );
  };

  const upsertUploadedItem = useCallback((node: Node) => {
    setUploadedItems((prev) => {
      const existingIndex = prev.findIndex((item) => item.id === node.id);
      if (existingIndex === -1) {
        return [node, ...prev];
      }
      const next = [...prev];
      next[existingIndex] = { ...prev[existingIndex], ...node };
      return next;
    });
    setLastStatusRefreshAt(new Date());
  }, []);

  const handleRefreshUploadedItems = useCallback(async () => {
    if (uploadedItems.length === 0 || refreshingUploads) {
      return;
    }
    setRefreshingUploads(true);
    try {
      const refreshed = await Promise.all(
        uploadedItems.map(async (item) => {
          try {
            return await nodeService.getNode(item.id);
          } catch (err) {
            return item;
          }
        })
      );
      setUploadedItems(refreshed);
    } finally {
      setRefreshingUploads(false);
      setLastStatusRefreshAt(new Date());
    }
  }, [refreshingUploads, uploadedItems]);

  const handleToggleAutoRefresh = (event: React.ChangeEvent<HTMLInputElement>) => {
    const nextValue = event.target.checked;
    setAutoRefreshUploads(nextValue);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(uploadAutoRefreshKey, String(nextValue));
    }
  };

  useEffect(() => {
    if (!uploadDialogOpen || !autoRefreshUploads || uploadedItems.length === 0) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      void handleRefreshUploadedItems();
    }, uploadAutoRefreshIntervalMs);

    return () => {
      window.clearInterval(interval);
    };
  }, [autoRefreshUploads, handleRefreshUploadedItems, uploadDialogOpen, uploadedItems.length]);

  const handleDismissUploadedItem = (nodeId: string) => {
    setUploadedItems((prev) => prev.filter((item) => item.id !== nodeId));
  };

  const handleClearUploadedItems = () => {
    setUploadedItems([]);
    setQueueingPreviewIds([]);
    setLastStatusRefreshAt(null);
  };

  const handleQueuePreview = useCallback(async (nodeId: string, force: boolean) => {
    if (queueingPreviewIds.includes(nodeId)) {
      return;
    }
    setQueueingPreviewIds((prev) => [...prev, nodeId]);
    try {
      const status = await apiService.post<PreviewQueueStatus>(
        `/documents/${nodeId}/preview/queue`,
        { force }
      );
      const nextStatus = status?.queued ? 'PROCESSING' : status?.previewStatus;
      if (nextStatus) {
        setUploadedItems((prev) =>
          prev.map((item) => (item.id === nodeId ? { ...item, previewStatus: nextStatus } : item))
        );
      }
      toast.success(status?.queued ? 'Preview queued' : 'Preview already up to date');
      void handleRefreshUploadedItems();
    } catch (error) {
      toast.error('Failed to queue preview');
    } finally {
      setQueueingPreviewIds((prev) => prev.filter((id) => id !== nodeId));
    }
  }, [handleRefreshUploadedItems, queueingPreviewIds]);

  const getUploadErrorMessage = (error: any) => {
    const responseData = error?.response?.data;
    const responseMessage = responseData?.message;
    if (typeof responseMessage === 'string' && responseMessage.trim()) {
      return responseMessage;
    }

    const errorsMap = responseData?.errors;
    if (errorsMap && typeof errorsMap === 'object') {
      const entries = Array.isArray(errorsMap)
        ? errorsMap.map((value, index) => [String(index), value])
        : Object.entries(errorsMap as Record<string, unknown>);
      const messages = entries
        .map(([key, value]) => {
          if (typeof value === 'string' && value.trim()) {
            return value;
          }
          if (value !== null && value !== undefined) {
            return `${key}: ${String(value)}`;
          }
          return key;
        })
        .filter((value) => value && value.trim());
      if (messages.length > 0) {
        return messages.join(' • ');
      }
    }

    if (typeof error?.message === 'string' && error.message.trim()) {
      return error.message;
    }
    return 'Upload failed';
  };

  const handleUpload = async () => {
    if (files.length === 0 || !hasUploadableFiles) return;
    if (!canWrite) {
      toast.error('Requires write permission');
      return;
    }
    setUploadSummary(null);
    const parentId = currentNode?.id || 'root';

    setUploading(true);
    let successCount = 0;
    let errorCount = 0;

    for (let i = 0; i < files.length; i++) {
      const uploadFile = files[i];
      if (uploadFile.status === 'success') {
        continue;
      }

      setFiles((prev) =>
        prev.map((f, idx) =>
          idx === i ? { ...f, status: 'uploading' as const, progress: 0, error: undefined } : f
        )
      );

      try {
        const uploadedNode = await dispatch(
          uploadDocument({
            parentId,
            file: uploadFile.file,
            onProgress: (progress) => {
              setFiles((prev) =>
                prev.map((f, idx) => (idx === i ? { ...f, progress } : f))
              );
            },
          })
        ).unwrap();

        upsertUploadedItem(uploadedNode);

        successCount += 1;
        setFiles((prev) =>
          prev.map((f, idx) =>
            idx === i ? { ...f, status: 'success' as const, progress: 100 } : f
          )
        );
      } catch (error) {
        errorCount += 1;
        const message = getUploadErrorMessage(error);
        setFiles((prev) =>
          prev.map((f, idx) =>
            idx === i
              ? {
                  ...f,
                  status: 'error' as const,
                  error: message,
                }
              : f
          )
        );
      }
    }

    setUploading(false);
    setUploadSummary({ success: successCount, failed: errorCount });
    if (errorCount === 0) {
      toast.success(`${successCount} file(s) uploaded successfully`);
    } else if (successCount > 0) {
      toast.warn(`Uploaded ${successCount} file(s); ${errorCount} failed`);
    } else {
      toast.error('All uploads failed');
    }
  };

  const handleOpenFolder = () => {
    const targetId = currentNode?.id || 'root';
    navigate(`/browse/${targetId}`);
    resetDialog();
  };

  const handleOpenStatus = () => {
    navigate('/status');
    resetDialog();
  };

  const formatFileSize = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const getPreviewStatusMeta = (node: Node) => {
    const mimeType = node.contentType || node.properties?.mimeType || node.properties?.contentType;
    const status = getEffectivePreviewStatus(
      node.previewStatus,
      node.previewFailureCategory,
      mimeType,
      node.previewFailureReason
    );
    if (!status || status === 'PENDING') {
      return { label: 'Preview pending', color: 'default' as const };
    }
    if (status === 'READY') {
      return { label: 'Preview ready', color: 'success' as const };
    }
    if (status === 'FAILED' || status === 'UNSUPPORTED') {
      return getFailedPreviewMeta(mimeType, node.previewFailureCategory, node.previewFailureReason);
    }
    if (status === 'PROCESSING') {
      return { label: 'Preview processing', color: 'warning' as const };
    }
    if (status === 'QUEUED') {
      return { label: 'Preview queued', color: 'info' as const };
    }
    return { label: `Preview ${status.toLowerCase()}`, color: 'default' as const };
  };

  const showUploadedItems = Boolean(uploadSummary || uploadedItems.length > 0);
  const statusSummary = uploadedItems.reduce(
    (acc, item) => {
      const mimeType = item.contentType || item.properties?.mimeType || item.properties?.contentType;
      const status = getEffectivePreviewStatus(
        item.previewStatus,
        item.previewFailureCategory,
        mimeType,
        item.previewFailureReason
      );
      if (!status || status === 'PENDING') {
        acc.pending += 1;
        return acc;
      }
      if (status === 'READY') {
        acc.ready += 1;
        return acc;
      }
      if (status === 'UNSUPPORTED') {
        acc.unsupported += 1;
        return acc;
      }
      if (status === 'FAILED') {
        acc.failed += 1;
        return acc;
      }
      if (status === 'PROCESSING') {
        acc.processing += 1;
        return acc;
      }
      if (status === 'QUEUED') {
        acc.queued += 1;
        return acc;
      }
      acc.other += 1;
      return acc;
    },
    {
      ready: 0,
      pending: 0,
      processing: 0,
      queued: 0,
      failed: 0,
      unsupported: 0,
      other: 0,
    }
  );

  return (
    <Dialog
      open={uploadDialogOpen}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>
        Upload Files
        <IconButton
          aria-label="close"
          onClick={handleClose}
          sx={{ position: 'absolute', right: 8, top: 8 }}
          disabled={uploading}
        >
          <Close />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box
          {...getRootProps()}
          sx={{
            border: '2px dashed',
            borderColor: isDragActive ? 'primary.main' : 'divider',
            borderRadius: 2,
            p: 3,
            textAlign: 'center',
            cursor: 'pointer',
            bgcolor: isDragActive ? 'action.hover' : 'background.paper',
            mb: 2,
          }}
        >
          <input {...getInputProps()} />
          <CloudUpload sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
          <Typography variant="body1" gutterBottom>
            {isDragActive
              ? 'Drop the files here...'
              : 'Drag & drop files here, or click to select files'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {canWrite ? 'You can upload multiple files at once' : 'Read-only: you do not have permission to upload'}
          </Typography>
        </Box>

        <Box mb={2}>
          <Typography variant="caption" color="text.secondary" display="block">
            After upload, indexing and preview generation may take a few moments. New files might not appear in search immediately.
          </Typography>
        </Box>

        {uploadSummary && (
          <Alert
            severity={uploadSummary.failed === 0 ? 'success' : uploadSummary.success > 0 ? 'warning' : 'error'}
            sx={{ mb: 2 }}
            action={
              uploadSummary.success > 0 ? (
                <Box display="flex" alignItems="center" gap={1}>
                  <Button color="inherit" size="small" onClick={handleOpenFolder}>
                    Open folder
                  </Button>
                  {isAdmin && (
                    <Button color="inherit" size="small" onClick={handleOpenStatus}>
                      System status
                    </Button>
                  )}
                </Box>
              ) : undefined
            }
          >
            {uploadSummary.failed === 0
              ? `${uploadSummary.success} file(s) uploaded. Indexing and previews may take a moment.`
              : uploadSummary.success > 0
                ? `${uploadSummary.success} file(s) uploaded, ${uploadSummary.failed} failed. Indexing and previews may take a moment.`
                : `Upload failed for ${uploadSummary.failed} file(s).`}
          </Alert>
        )}

        {showUploadedItems && (
            <Box mb={2}>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                <Typography variant="subtitle2">Uploaded items</Typography>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                <FormControlLabel
                  control={
                    <Switch
                      size="small"
                      checked={autoRefreshUploads}
                      onChange={handleToggleAutoRefresh}
                    />
                  }
                  label="Auto refresh"
                />
                <Button
                  size="small"
                  startIcon={<Refresh />}
                  onClick={handleRefreshUploadedItems}
                  disabled={uploadedItems.length === 0 || refreshingUploads}
                >
                  Refresh status
                </Button>
                <Button
                  size="small"
                  startIcon={<DeleteSweep />}
                  onClick={handleClearUploadedItems}
                  disabled={uploadedItems.length === 0}
                >
                  Clear list
                </Button>
              </Box>
            </Box>
              {uploadedItems.length > 0 && (
                <Box mb={1} display="flex" flexWrap="wrap" gap={1}>
                  <Chip label={`Total ${uploadedItems.length}`} size="small" />
                  <Chip label={`Ready ${statusSummary.ready}`} size="small" color="success" />
                  <Chip label={`Pending ${statusSummary.pending}`} size="small" />
                  <Chip label={`Processing ${statusSummary.processing}`} size="small" color="warning" />
                  <Chip label={`Queued ${statusSummary.queued}`} size="small" color="info" />
                  <Chip label={`Unsupported ${statusSummary.unsupported}`} size="small" />
                  <Chip label={`Failed ${statusSummary.failed}`} size="small" color="error" />
                </Box>
              )}
              <Typography variant="caption" color="text.secondary" display="block" mb={1}>
                Last updated: {lastStatusRefreshAt ? lastStatusRefreshAt.toLocaleTimeString() : 'Not yet'}
              </Typography>
              {uploadedItems.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Uploaded files will appear here once processing begins.
              </Typography>
            ) : (
              <List dense>
                {uploadedItems.map((item) => {
                  const previewMeta = getPreviewStatusMeta(item);
                  const mimeType = item.contentType || item.properties?.mimeType || item.properties?.contentType;
                  const effectiveStatus = getEffectivePreviewStatus(
                    item.previewStatus,
                    item.previewFailureCategory,
                    mimeType,
                    item.previewFailureReason
                  );
                  const showForceRebuild = item.nodeType === 'DOCUMENT' && effectiveStatus !== 'READY';
                  const showQueuePreview = showForceRebuild && effectiveStatus !== 'UNSUPPORTED';
                  const isQueueing = queueingPreviewIds.includes(item.id);
                  return (
                    <ListItem
                      key={item.id}
                      secondaryAction={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Chip
                            label={previewMeta.label}
                            size="small"
                            color={previewMeta.color}
                          />
                          {showForceRebuild && (
                            <>
                              {showQueuePreview && (
                                <Button
                                  size="small"
                                  onClick={() => handleQueuePreview(item.id, false)}
                                  disabled={isQueueing}
                                >
                                  Queue preview
                                </Button>
                              )}
                              <Button
                                size="small"
                                onClick={() => handleQueuePreview(item.id, true)}
                                disabled={isQueueing}
                              >
                                Force rebuild
                              </Button>
                            </>
                          )}
                          <IconButton
                            edge="end"
                            size="small"
                            onClick={() => handleDismissUploadedItem(item.id)}
                            aria-label={`Dismiss ${item.name}`}
                          >
                            <Close fontSize="small" />
                          </IconButton>
                        </Box>
                      }
                    >
                      <ListItemIcon>
                        <InsertDriveFile />
                      </ListItemIcon>
                      <ListItemText
                        primary={item.name}
                        secondary={item.previewFailureReason || item.previewStatus || 'Preview pending'}
                      />
                    </ListItem>
                  );
                })}
              </List>
            )}
          </Box>
        )}

        {files.length > 0 && (
          <List>
            {files.map((uploadFile, index) => (
              <ListItem
                key={index}
                secondaryAction={
                  !uploading ? (
                    <Box display="flex" alignItems="center" gap={1}>
                      {uploadFile.status === 'error' && (
                        <Button size="small" onClick={() => handleRetryFile(index)}>
                          Retry
                        </Button>
                      )}
                      {(uploadFile.status === 'pending' || uploadFile.status === 'error') && (
                        <IconButton edge="end" onClick={() => handleRemoveFile(index)}>
                          <Close />
                        </IconButton>
                      )}
                    </Box>
                  ) : null
                }
              >
                <ListItemIcon>
                  {uploadFile.status === 'success' ? (
                    <CheckCircle color="success" />
                  ) : uploadFile.status === 'error' ? (
                    <Error color="error" />
                  ) : (
                    <InsertDriveFile />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={uploadFile.file.name}
                  secondary={
                    <Box>
                      <Typography variant="caption" component="span">
                        {formatFileSize(uploadFile.file.size)}
                      </Typography>
                      {uploadFile.status === 'uploading' && (
                        <LinearProgress
                          variant="determinate"
                          value={uploadFile.progress}
                          sx={{ mt: 1 }}
                        />
                      )}
                      {uploadFile.error && (
                        <Typography variant="caption" color="error" display="block">
                          {uploadFile.error}
                        </Typography>
                      )}
                    </Box>
                  }
                />
                {uploadFile.status !== 'pending' && (
                  <Chip
                    label={uploadFile.status}
                    size="small"
                    color={
                      uploadFile.status === 'success'
                        ? 'success'
                        : uploadFile.status === 'error'
                        ? 'error'
                        : 'default'
                    }
                  />
                )}
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={uploading}>
          Cancel
        </Button>
        <Button
          onClick={handleUpload}
          variant="contained"
          disabled={files.length === 0 || uploading || !canWrite || !hasUploadableFiles}
        >
          Upload {files.length > 0 && `(${files.length})`}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UploadDialog;
