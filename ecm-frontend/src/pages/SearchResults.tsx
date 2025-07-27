import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  TextField,
  InputAdornment,
  IconButton,
  Chip,
  Grid,
  Card,
  CardContent,
  CardActions,
  Button,
  Pagination,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import {
  Search,
  Clear,
  Folder,
  InsertDriveFile,
  Download,
  Visibility,
  FilterList,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppSelector, useAppDispatch } from '@/store';
import { searchNodes } from '@/store/slices/nodeSlice';
import { setSearchOpen } from '@/store/slices/uiSlice';
import nodeService from '@/services/nodeService';
import { Node } from '@/types';
import { toast } from 'react-toastify';

const SearchResults: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { nodes, loading } = useAppSelector((state) => state.node);
  const [quickSearch, setQuickSearch] = useState('');
  const [sortBy, setSortBy] = useState('relevance');
  const [page, setPage] = useState(1);
  const itemsPerPage = 20;

  const handleQuickSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (quickSearch.trim()) {
      await dispatch(searchNodes({ name: quickSearch }));
    }
  };

  const handleClearSearch = () => {
    setQuickSearch('');
  };

  const handleAdvancedSearch = () => {
    dispatch(setSearchOpen(true));
  };

  const handleViewNode = (node: Node) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
    } else {
      // For documents, show in properties dialog
      navigate(`/browse/${node.parentId || 'root'}`);
    }
  };

  const handleDownload = async (node: Node) => {
    if (node.nodeType === 'DOCUMENT') {
      try {
        await nodeService.downloadDocument(node.id);
      } catch (error) {
        toast.error('Failed to download file');
      }
    }
  };

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const getFileIcon = (node: Node) => {
    if (node.nodeType === 'FOLDER') {
      return <Folder sx={{ fontSize: 48, color: 'primary.main' }} />;
    }
    return <InsertDriveFile sx={{ fontSize: 48, color: 'text.secondary' }} />;
  };

  const getFileTypeChip = (contentType?: string) => {
    if (!contentType) return null;

    const typeMap: Record<string, { label: string; color: any }> = {
      'application/pdf': { label: 'PDF', color: 'error' },
      'application/msword': { label: 'Word', color: 'primary' },
      'application/vnd.ms-excel': { label: 'Excel', color: 'success' },
      'image/jpeg': { label: 'Image', color: 'warning' },
      'image/png': { label: 'Image', color: 'warning' },
      'text/plain': { label: 'Text', color: 'default' },
    };

    const type = typeMap[contentType];
    if (type) {
      return <Chip label={type.label} size="small" color={type.color} />;
    }

    return <Chip label="File" size="small" />;
  };

  const sortedNodes = [...nodes].sort((a, b) => {
    switch (sortBy) {
      case 'name':
        return a.name.localeCompare(b.name);
      case 'modified':
        return new Date(b.modified).getTime() - new Date(a.modified).getTime();
      case 'size':
        return (b.size || 0) - (a.size || 0);
      default:
        return 0;
    }
  });

  const paginatedNodes = sortedNodes.slice(
    (page - 1) * itemsPerPage,
    page * itemsPerPage
  );

  const totalPages = Math.ceil(sortedNodes.length / itemsPerPage);

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 3 }}>
        <form onSubmit={handleQuickSearch}>
          <Box display="flex" alignItems="center" gap={2}>
            <TextField
              fullWidth
              placeholder="Quick search by name..."
              value={quickSearch}
              onChange={(e) => setQuickSearch(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Search />
                  </InputAdornment>
                ),
                endAdornment: quickSearch && (
                  <InputAdornment position="end">
                    <IconButton onClick={handleClearSearch} edge="end">
                      <Clear />
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
            <Button
              variant="outlined"
              startIcon={<FilterList />}
              onClick={handleAdvancedSearch}
            >
              Advanced
            </Button>
          </Box>
        </form>
      </Paper>

      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">
          {loading ? 'Searching...' : `${nodes.length} results found`}
        </Typography>
        <FormControl size="small" sx={{ minWidth: 150 }}>
          <InputLabel>Sort by</InputLabel>
          <Select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            label="Sort by"
          >
            <MenuItem value="relevance">Relevance</MenuItem>
            <MenuItem value="name">Name</MenuItem>
            <MenuItem value="modified">Modified Date</MenuItem>
            <MenuItem value="size">Size</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}>
          <CircularProgress />
        </Box>
      ) : nodes.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h6" color="text.secondary" gutterBottom>
            No results found
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Try adjusting your search criteria or use the advanced search for more options
          </Typography>
          <Button
            variant="contained"
            startIcon={<FilterList />}
            onClick={handleAdvancedSearch}
            sx={{ mt: 2 }}
          >
            Advanced Search
          </Button>
        </Paper>
      ) : (
        <>
          <Grid container spacing={2}>
            {paginatedNodes.map((node) => (
              <Grid item xs={12} sm={6} md={4} key={node.id}>
                <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                  <CardContent sx={{ flex: 1 }}>
                    <Box display="flex" alignItems="center" mb={2}>
                      {getFileIcon(node)}
                      <Box ml={2} flex={1}>
                        <Typography variant="h6" noWrap>
                          {node.name}
                        </Typography>
                        <Box display="flex" gap={1} mt={0.5}>
                          {getFileTypeChip(node.contentType)}
                          {node.currentVersionLabel && (
                            <Chip
                              label={`v${node.currentVersionLabel}`}
                              size="small"
                              variant="outlined"
                            />
                          )}
                        </Box>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary" gutterBottom>
                      {node.path}
                    </Typography>
                    <Box mt={2}>
                      <Typography variant="caption" color="text.secondary">
                        Modified: {format(new Date(node.modified), 'PPp')}
                      </Typography>
                      <br />
                      <Typography variant="caption" color="text.secondary">
                        Size: {formatFileSize(node.size)}
                      </Typography>
                    </Box>
                  </CardContent>
                  <CardActions>
                    <Button
                      size="small"
                      startIcon={<Visibility />}
                      onClick={() => handleViewNode(node)}
                    >
                      View
                    </Button>
                    {node.nodeType === 'DOCUMENT' && (
                      <Button
                        size="small"
                        startIcon={<Download />}
                        onClick={() => handleDownload(node)}
                      >
                        Download
                      </Button>
                    )}
                  </CardActions>
                </Card>
              </Grid>
            ))}
          </Grid>

          {totalPages > 1 && (
            <Box display="flex" justifyContent="center" mt={4}>
              <Pagination
                count={totalPages}
                page={page}
                onChange={(_, value) => setPage(value)}
                color="primary"
              />
            </Box>
          )}
        </>
      )}
    </Box>
  );
};

export default SearchResults;