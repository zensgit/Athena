import React, { useCallback, useEffect, useState } from 'react';
import { Box, Paper, Typography, IconButton, CircularProgress, Button, Tooltip } from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import {
  ContentPasteSearch,
  Delete,
  PlayArrow,
  Refresh,
  SavedSearch as SavedSearchIcon,
  Star,
  StarBorder,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';
import savedSearchService, { SavedSearch } from 'services/savedSearchService';
import { useAppDispatch } from 'store';
import { executeSavedSearch, setLastSearchCriteria } from 'store/slices/nodeSlice';
import { setSearchOpen, setSearchPrefill } from 'store/slices/uiSlice';
import { buildSearchCriteriaFromSavedSearch } from 'utils/savedSearchUtils';

const SavedSearchesPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [items, setItems] = useState<SavedSearch[]>([]);
  const [loading, setLoading] = useState(false);

  const sortSavedSearches = useCallback((data: SavedSearch[]) => {
    const toTime = (value?: string) => {
      if (!value) return 0;
      const time = new Date(value).getTime();
      return Number.isNaN(time) ? 0 : time;
    };
    return [...data].sort((a, b) => {
      const pinDiff = Number(Boolean(b.pinned)) - Number(Boolean(a.pinned));
      if (pinDiff !== 0) return pinDiff;
      return toTime(b.createdAt) - toTime(a.createdAt);
    });
  }, []);

  const loadSavedSearches = useCallback(async () => {
    setLoading(true);
    try {
      const data = await savedSearchService.list();
      setItems(sortSavedSearches(data));
    } catch {
      toast.error('Failed to load saved searches');
    } finally {
      setLoading(false);
    }
  }, [sortSavedSearches]);

  useEffect(() => {
    loadSavedSearches();
  }, [loadSavedSearches]);

  const normalizeList = (input: unknown) =>
    Array.isArray(input)
      ? input.map((value) => String(value).trim()).filter((value) => value.length > 0)
      : [];

  const handleRun = async (item: SavedSearch) => {
    try {
      await dispatch(executeSavedSearch(item.id)).unwrap();
      dispatch(setLastSearchCriteria(buildSearchCriteriaFromSavedSearch(item)));
      navigate('/search-results');
    } catch {
      toast.error('Failed to execute saved search');
    }
  };

  const handleTogglePin = (item: SavedSearch) => {
    const nextPinned = !item.pinned;
    savedSearchService
      .setPinned(item.id, nextPinned)
      .then((updated) => {
        setItems((prev) => sortSavedSearches(
          prev.map((current) => (current.id === updated.id ? updated : current))
        ));
        toast.success(updated.pinned ? 'Pinned saved search' : 'Unpinned saved search');
      })
      .catch(() => toast.error('Failed to update pin'));
  };

  const handleDelete = async (item: SavedSearch) => {
    if (!window.confirm(`Delete saved search "${item.name}"?`)) return;
    try {
      await savedSearchService.delete(item.id);
      toast.success('Saved search deleted');
      await loadSavedSearches();
    } catch {
      toast.error('Failed to delete saved search');
    }
  };

  const handleLoadToSearch = (item: SavedSearch) => {
    const queryParams = item.queryParams || {};
    const filters = (queryParams.filters || {}) as Record<string, any>;
    const mimeTypes = Array.isArray(filters.mimeTypes) ? filters.mimeTypes : [];

    dispatch(
      setSearchPrefill({
        name: typeof queryParams.query === 'string' ? queryParams.query : '',
        contentType: typeof mimeTypes[0] === 'string' ? mimeTypes[0] : '',
        createdBy: typeof filters.createdBy === 'string' ? filters.createdBy : '',
        createdFrom: typeof filters.dateFrom === 'string' ? filters.dateFrom : undefined,
        createdTo: typeof filters.dateTo === 'string' ? filters.dateTo : undefined,
        modifiedFrom: typeof filters.modifiedFrom === 'string' ? filters.modifiedFrom : undefined,
        modifiedTo: typeof filters.modifiedTo === 'string' ? filters.modifiedTo : undefined,
        tags: normalizeList(filters.tags),
        categories: normalizeList(filters.categories),
        correspondents: normalizeList(filters.correspondents),
        minSize: typeof filters.minSize === 'number' ? filters.minSize : undefined,
        maxSize: typeof filters.maxSize === 'number' ? filters.maxSize : undefined,
        pathPrefix: typeof filters.path === 'string' ? filters.path : '',
      })
    );
    dispatch(setSearchOpen(true));
    toast.success('Loaded saved search into Advanced Search');
  };

  const columns: GridColDef[] = [
    {
      field: 'pinned',
      headerName: '',
      width: 60,
      sortable: false,
      renderCell: (params) => {
        const item = params.row as SavedSearch;
        const isPinned = Boolean(item.pinned);
        return (
          <Tooltip title={isPinned ? 'Unpin' : 'Pin'}>
            <IconButton
              size="small"
              aria-label={`${isPinned ? 'Unpin' : 'Pin'} saved search ${String(item.name)}`}
              onClick={() => handleTogglePin(item)}
            >
              {isPinned ? <Star fontSize="small" color="warning" /> : <StarBorder fontSize="small" />}
            </IconButton>
          </Tooltip>
        );
      },
    },
    { field: 'name', headerName: 'Name', flex: 2 },
    {
      field: 'createdAt',
      headerName: 'Created At',
      width: 180,
      valueFormatter: (params) => {
        if (!params.value) return '-';
        try {
          return format(new Date(params.value as string), 'PPp');
        } catch {
          return String(params.value);
        }
      },
    },
    {
      field: 'actions',
      headerName: '',
      width: 170,
      sortable: false,
      renderCell: (params) => (
        <Box display="flex" gap={1}>
          <IconButton
            size="small"
            aria-label={`Load saved search ${String((params.row as SavedSearch).name)}`}
            onClick={() => handleLoadToSearch(params.row as SavedSearch)}
          >
            <ContentPasteSearch fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            aria-label={`Run saved search ${String((params.row as SavedSearch).name)}`}
            onClick={() => handleRun(params.row as SavedSearch)}
          >
            <PlayArrow fontSize="small" />
          </IconButton>
          <IconButton
            size="small"
            aria-label={`Delete saved search ${String((params.row as SavedSearch).name)}`}
            onClick={() => handleDelete(params.row as SavedSearch)}
          >
            <Delete fontSize="small" />
          </IconButton>
        </Box>
      ),
    },
  ];

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box display="flex" alignItems="center" gap={1}>
            <SavedSearchIcon fontSize="small" color="primary" />
            <Typography variant="h6">Saved Searches</Typography>
          </Box>
          <Button variant="outlined" startIcon={<Refresh />} onClick={loadSavedSearches}>
            Refresh
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

export default SavedSearchesPage;
