import React, { useState, useCallback } from 'react';
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
} from '@mui/material';
import { useDropzone } from 'react-dropzone';
import {
  CloudUpload,
  InsertDriveFile,
  Close,
  CheckCircle,
  Error,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from 'store';
import { setUploadDialogOpen } from 'store/slices/uiSlice';
import { uploadDocument } from 'store/slices/nodeSlice';
import { toast } from 'react-toastify';

interface UploadFile {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error';
  error?: string;
}

const UploadDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const { uploadDialogOpen } = useAppSelector((state) => state.ui);
  const { currentNode } = useAppSelector((state) => state.node);
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const newFiles = acceptedFiles.map((file) => ({
      file,
      progress: 0,
      status: 'pending' as const,
    }));
    setFiles((prev) => [...prev, ...newFiles]);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    multiple: true,
  });

  const handleClose = () => {
    if (!uploading) {
      dispatch(setUploadDialogOpen(false));
      setFiles([]);
    }
  };

  const handleRemoveFile = (index: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleUpload = async () => {
    if (!currentNode || files.length === 0) return;

    setUploading(true);

    for (let i = 0; i < files.length; i++) {
      const uploadFile = files[i];
      
      setFiles((prev) =>
        prev.map((f, idx) =>
          idx === i ? { ...f, status: 'uploading' as const } : f
        )
      );

      try {
        await dispatch(
          uploadDocument({
            parentId: currentNode.id,
            file: uploadFile.file,
          })
        ).unwrap();

        setFiles((prev) =>
          prev.map((f, idx) =>
            idx === i ? { ...f, status: 'success' as const, progress: 100 } : f
          )
        );
      } catch (error) {
        setFiles((prev) =>
          prev.map((f, idx) =>
            idx === i
              ? {
                  ...f,
                  status: 'error' as const,
                  error: 'Upload failed',
                }
              : f
          )
        );
      }
    }

    setUploading(false);
    toast.success(`${files.length} file(s) uploaded successfully`);

    setTimeout(() => {
      handleClose();
    }, 1000);
  };

  const formatFileSize = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

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
            You can upload multiple files at once
          </Typography>
        </Box>

        {files.length > 0 && (
          <List>
            {files.map((uploadFile, index) => (
              <ListItem
                key={index}
                secondaryAction={
                  !uploading && uploadFile.status === 'pending' ? (
                    <IconButton
                      edge="end"
                      onClick={() => handleRemoveFile(index)}
                    >
                      <Close />
                    </IconButton>
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
          disabled={files.length === 0 || uploading}
        >
          Upload {files.length > 0 && `(${files.length})`}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default UploadDialog;