import React, { useEffect, useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  CircularProgress,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { RestoreFromTrash, DeleteForever, DeleteSweep } from '@mui/icons-material';
import trashService, { TrashItem } from 'services/trashService';
import { format } from 'date-fns';
import { toast } from 'react-toastify';

const TrashPage: React.FC = () => {
  const [items, setItems] = useState<TrashItem[]>([]);
  const [loading, setLoading] = useState(false);

  const loadTrash = async () => {
    setLoading(true);
    try {
      const data = await trashService.getTrashItems();
      setItems(data);
    } catch {
      toast.error('Failed to load trash');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTrash();
  }, []);

  const handleRestore = async (item: TrashItem) => {
    try {
      await trashService.restore(item.id);
      toast.success('Item restored');
      loadTrash();
    } catch {
      toast.error('Failed to restore item');
    }
  };

  const handlePermanentDelete = async (item: TrashItem) => {
    if (!window.confirm(`Permanently delete "${item.name}"?`)) return;
    try {
      await trashService.permanentDelete(item.id);
      toast.success('Item permanently deleted');
      loadTrash();
    } catch {
      toast.error('Failed to permanently delete item');
    }
  };

  const handleEmptyTrash = async () => {
    if (!window.confirm('Empty trash permanently?')) return;
    try {
      const res = await trashService.emptyTrash();
      toast.success(`Deleted ${res.deletedCount} items`);
      loadTrash();
    } catch {
      toast.error('Failed to empty trash');
    }
  };

  const columns: GridColDef[] = [
    { field: 'name', headerName: 'Name', flex: 2 },
    { field: 'nodeType', headerName: 'Type', width: 120 },
    {
      field: 'deletedAt',
      headerName: 'Deleted At',
      width: 180,
      valueFormatter: (params) =>
        params.value ? format(new Date(params.value as string), 'PPp') : '-',
    },
    { field: 'deletedBy', headerName: 'Deleted By', width: 180 },
    {
      field: 'size',
      headerName: 'Size',
      width: 110,
      valueFormatter: (params) => {
        const bytes = params.value as number | undefined;
        if (!bytes) return '-';
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(1024));
        return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
      },
    },
    {
      field: 'actions',
      headerName: '',
      width: 120,
      sortable: false,
      renderCell: (params) => (
        <Box display="flex" gap={1}>
          <IconButton
            size="small"
            aria-label={`Restore ${String((params.row as TrashItem).name)}`}
            onClick={() => handleRestore(params.row as TrashItem)}
          >
            <RestoreFromTrash fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            color="error"
            aria-label={`Permanently delete ${String((params.row as TrashItem).name)}`}
            onClick={() => handlePermanentDelete(params.row as TrashItem)}
          >
            <DeleteForever fontSize="small" />
          </IconButton>
        </Box>
      ),
    },
  ];

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography variant="h6">Trash</Typography>
          <Button
            variant="outlined"
            color="error"
            startIcon={<DeleteSweep />}
            onClick={handleEmptyTrash}
            disabled={items.length === 0}
          >
            Empty Trash
          </Button>
        </Box>
      </Paper>

      <Paper sx={{ height: 600 }}>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <DataGrid
            rows={items}
            columns={columns}
            autoHeight
            disableRowSelectionOnClick
            getRowId={(row) => row.id}
          />
        )}
      </Paper>
    </Box>
  );
};

export default TrashPage;
