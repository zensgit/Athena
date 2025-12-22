import React, { useEffect, useMemo, useRef, useState } from 'react';
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
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Divider,
  Checkbox,
  ListItemText,
  Tooltip,
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
import { useAppSelector, useAppDispatch } from 'store';
import { fetchSearchFacets, searchNodes } from 'store/slices/nodeSlice';
import { setSearchOpen } from 'store/slices/uiSlice';
import nodeService from 'services/nodeService';
import { Node } from 'types';
import { toast } from 'react-toastify';
import Highlight from 'components/search/Highlight';
const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));

type FacetValue = { value: string; count: number };

const FILE_EXTENSIONS = [
  '.pdf',
  '.txt',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.csv',
  '.rtf',
  '.png',
  '.jpg',
  '.jpeg',
  '.gif',
  '.webp',
];

const SearchResults: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { nodes, nodesTotal, loading, error, searchFacets, lastSearchCriteria } = useAppSelector((state) => state.node);
  const [quickSearch, setQuickSearch] = useState('');
  const [sortBy, setSortBy] = useState('relevance');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [selectedMimeTypes, setSelectedMimeTypes] = useState<string[]>([]);
  const [selectedCreators, setSelectedCreators] = useState<string[]>([]);
  const [selectedCorrespondents, setSelectedCorrespondents] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const previewOpen = Boolean(previewNode);
  const suppressFacetSearch = useRef(false);

  const getSortParams = (value: string) => {
    switch (value) {
      case 'name':
        return { sortBy: 'name', sortDirection: 'asc' as const };
      case 'modified':
        return { sortBy: 'modified', sortDirection: 'desc' as const };
      case 'size':
        return { sortBy: 'size', sortDirection: 'desc' as const };
      default:
        return { sortBy: undefined, sortDirection: undefined };
    }
  };

  const handleQuickSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    const query = quickSearch.trim();
    if (!query) {
      return;
    }
    setPage(1);
    const sortParams = getSortParams(sortBy);
    await dispatch(searchNodes({ name: query, page: 0, size: pageSize, ...sortParams }));
  };

  const handleClearSearch = () => {
    setQuickSearch('');
  };

  const handleAdvancedSearch = () => {
    dispatch(setSearchOpen(true));
  };

  const clearFacetFilters = () => {
    setSelectedMimeTypes([]);
    setSelectedCreators([]);
    setSelectedCorrespondents([]);
    setSelectedTags([]);
    setSelectedCategories([]);
  };

  const removeFacetValue = (
    setter: React.Dispatch<React.SetStateAction<string[]>>,
    value: string
  ) => {
    setter((prev) => prev.filter((item) => item !== value));
  };

  useEffect(() => {
    if (!lastSearchCriteria) {
      return;
    }
    suppressFacetSearch.current = true;
    const baseMimeTypes = lastSearchCriteria.mimeTypes
      ?? (lastSearchCriteria.contentType ? [lastSearchCriteria.contentType] : []);
    const baseCreators = lastSearchCriteria.createdByList
      ?? (lastSearchCriteria.createdBy ? [lastSearchCriteria.createdBy] : []);

    setSelectedMimeTypes(baseMimeTypes);
    setSelectedCreators(baseCreators);
    setSelectedCorrespondents(lastSearchCriteria.correspondents || []);
    setSelectedTags(lastSearchCriteria.tags || []);
    setSelectedCategories(lastSearchCriteria.categories || []);
    setPage(1);

    const timer = window.setTimeout(() => {
      suppressFacetSearch.current = false;
    }, 0);

    return () => window.clearTimeout(timer);
  }, [lastSearchCriteria]);

  useEffect(() => {
    const query = (lastSearchCriteria?.name || '').trim();
    dispatch(fetchSearchFacets(query));
  }, [lastSearchCriteria, dispatch]);

  useEffect(() => {
    if (lastSearchCriteria?.name !== undefined) {
      setQuickSearch(lastSearchCriteria.name || '');
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    if (lastSearchCriteria?.page !== undefined) {
      setPage(lastSearchCriteria.page + 1);
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    if (!lastSearchCriteria || suppressFacetSearch.current) {
      return;
    }

    const normalize = (values: string[]) =>
      values.map((value) => value.trim()).filter((value) => value.length > 0).sort();
    const arraysEqual = (left: string[], right: string[]) => {
      if (left.length !== right.length) return false;
      return left.every((value, index) => value === right[index]);
    };

    const baseMimeTypes = normalize(
      lastSearchCriteria.mimeTypes
        ?? (lastSearchCriteria.contentType ? [lastSearchCriteria.contentType] : [])
    );
    const baseCreators = normalize(
      lastSearchCriteria.createdByList
        ?? (lastSearchCriteria.createdBy ? [lastSearchCriteria.createdBy] : [])
    );
    const baseTags = normalize(lastSearchCriteria.tags || []);
    const baseCategories = normalize(lastSearchCriteria.categories || []);
    const baseCorrespondents = normalize(lastSearchCriteria.correspondents || []);

    const nextMimeTypes = normalize(selectedMimeTypes);
    const nextCreators = normalize(selectedCreators);
    const nextTags = normalize(selectedTags);
    const nextCategories = normalize(selectedCategories);
    const nextCorrespondents = normalize(selectedCorrespondents);

    if (
      arraysEqual(baseMimeTypes, nextMimeTypes)
      && arraysEqual(baseCreators, nextCreators)
      && arraysEqual(baseTags, nextTags)
      && arraysEqual(baseCategories, nextCategories)
      && arraysEqual(baseCorrespondents, nextCorrespondents)
    ) {
      return;
    }

    const sortParams = getSortParams(sortBy);
    dispatch(
      searchNodes({
        ...lastSearchCriteria,
        mimeTypes: nextMimeTypes.length ? nextMimeTypes : undefined,
        contentType: undefined,
        createdByList: nextCreators.length ? nextCreators : undefined,
        createdBy: undefined,
        tags: nextTags.length ? nextTags : undefined,
        categories: nextCategories.length ? nextCategories : undefined,
        correspondents: nextCorrespondents.length ? nextCorrespondents : undefined,
        page: 0,
        size: pageSize,
        ...sortParams,
      })
    );
  }, [
    selectedMimeTypes,
    selectedCreators,
    selectedCorrespondents,
    selectedTags,
    selectedCategories,
    lastSearchCriteria,
    dispatch,
    pageSize,
    sortBy,
  ]);

  useEffect(() => {
    setPage(1);
  }, [selectedMimeTypes, selectedCreators, selectedCorrespondents, selectedTags, selectedCategories]);

  useEffect(() => {
    if (!lastSearchCriteria) {
      return;
    }
    const sortParams = getSortParams(sortBy);
    if (
      lastSearchCriteria.sortBy === sortParams.sortBy
      && lastSearchCriteria.sortDirection === sortParams.sortDirection
    ) {
      return;
    }
    setPage(1);
    dispatch(searchNodes({ ...lastSearchCriteria, page: 0, size: pageSize, ...sortParams }));
  }, [sortBy, lastSearchCriteria, dispatch, pageSize]);

  const isDocumentNode = (node: Node) => {
    const normalizedName = node.name?.toLowerCase() || '';
    const hasExtension = FILE_EXTENSIONS.some((ext) => normalizedName.endsWith(ext));
    const normalizedPath = node.path?.toLowerCase() || '';
    const hasPathExtension = FILE_EXTENSIONS.some((ext) => normalizedPath.endsWith(ext));
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const sizeHint = node.size
      || node.properties?.fileSize
      || node.properties?.size;
    return node.nodeType === 'DOCUMENT'
      || Boolean(contentTypeHint || sizeHint || node.currentVersionLabel)
      || hasExtension
      || hasPathExtension;
  };

  const isFolderNode = (node: Node) => node.nodeType === 'FOLDER' && !isDocumentNode(node);

  const handleViewNode = (node: Node) => {
    if (isFolderNode(node)) {
      navigate(`/browse/${node.id}`);
    } else {
      setPreviewNode(node);
    }
  };

  const handleClosePreview = () => {
    setPreviewNode(null);
  };

  const handleDownload = async (node: Node) => {
    if (isDocumentNode(node)) {
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
    if (isFolderNode(node)) {
      return <Folder sx={{ fontSize: 48, color: 'primary.main' }} />;
    }
    return <InsertDriveFile sx={{ fontSize: 48, color: 'text.secondary' }} />;
  };

  const getNameTypographySx = (name: string) => {
    const length = name?.length ?? 0;
    const isLong = length > 28;
    const isExtraLong = length > 40;
    const fontSize = isExtraLong ? '1rem' : isLong ? '1.1rem' : undefined;

    return {
      display: '-webkit-box',
      WebkitLineClamp: isLong ? 3 : 2,
      WebkitBoxOrient: 'vertical',
      overflow: 'hidden',
      wordBreak: 'break-word',
      overflowWrap: 'anywhere',
      lineHeight: isExtraLong ? 1.2 : isLong ? 1.25 : 1.3,
      ...(fontSize ? { fontSize } : {}),
    };
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

  const renderTagsCategories = (node: Node) => {
    return (
      <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
        {node.correspondent && (
          <Chip key={`corr-${node.id}`} label={node.correspondent} size="small" color="secondary" variant="outlined" />
        )}
        {node.tags?.map((tag) => (
          <Chip key={tag} label={tag} size="small" variant="outlined" />
        ))}
        {node.categories?.map((cat) => (
          <Chip key={cat} label={cat} size="small" color="info" variant="outlined" />
        ))}
      </Box>
    );
  };

  const facets = useMemo(() => {
    if (searchFacets && Object.keys(searchFacets).length > 0) {
      const toSorted = (values?: FacetValue[]) =>
        (values || []).slice().sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));

      return {
        mimeTypes: toSorted(searchFacets.mimeType),
        creators: toSorted(searchFacets.createdBy),
        correspondents: toSorted(searchFacets.correspondent),
        tags: toSorted(searchFacets.tags),
        categories: toSorted(searchFacets.categories),
      };
    }

    const mimeTypeCounts = new Map<string, number>();
    const creatorCounts = new Map<string, number>();
    const correspondentCounts = new Map<string, number>();
    const tagCounts = new Map<string, number>();
    const categoryCounts = new Map<string, number>();

    const inc = (map: Map<string, number>, key: string) => {
      map.set(key, (map.get(key) || 0) + 1);
    };

    for (const node of nodes) {
      if (node.contentType) {
        inc(mimeTypeCounts, node.contentType);
      }
      if (node.creator) {
        inc(creatorCounts, node.creator);
      }
      if (node.correspondent) {
        inc(correspondentCounts, node.correspondent);
      }
      for (const tag of node.tags || []) {
        if (tag) {
          inc(tagCounts, tag);
        }
      }
      for (const category of node.categories || []) {
        if (category) {
          inc(categoryCounts, category);
        }
      }
    }

    const mapToSortedArray = (map: Map<string, number>): FacetValue[] => {
      return Array.from(map.entries())
        .map(([value, count]) => ({ value, count }))
        .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));
    };

    return {
      mimeTypes: mapToSortedArray(mimeTypeCounts),
      creators: mapToSortedArray(creatorCounts),
      correspondents: mapToSortedArray(correspondentCounts),
      tags: mapToSortedArray(tagCounts),
      categories: mapToSortedArray(categoryCounts),
    };
  }, [nodes, searchFacets]);

  const filtersApplied =
    selectedMimeTypes.length > 0 ||
    selectedCreators.length > 0 ||
    selectedCorrespondents.length > 0 ||
    selectedTags.length > 0 ||
    selectedCategories.length > 0;

  const paginatedNodes = nodes;
  const totalPages = Math.ceil(nodesTotal / pageSize);

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

      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
              <Typography variant="h6">Facets</Typography>
              <Button size="small" disabled={!filtersApplied} onClick={clearFacetFilters}>
                Clear
              </Button>
            </Box>
            <Divider sx={{ mb: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              File Type
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel>Mime Type</InputLabel>
              <Select
                multiple
                value={selectedMimeTypes}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedMimeTypes(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Mime Type"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value.split('/')[1] || value} size="small" />
                    ))}
                  </Box>
                )}
              >
                {facets.mimeTypes.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedMimeTypes.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Created By
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel>Creator</InputLabel>
              <Select
                multiple
                value={selectedCreators}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedCreators(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Creator"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" />
                    ))}
                  </Box>
                )}
              >
                {facets.creators.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedCreators.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Correspondent
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel id="facet-correspondent-label">Correspondent</InputLabel>
              <Select
                id="facet-correspondent-select"
                labelId="facet-correspondent-label"
                multiple
                value={selectedCorrespondents}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedCorrespondents(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Correspondent"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" />
                    ))}
                  </Box>
                )}
              >
                {facets.correspondents.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedCorrespondents.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Tags
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel>Tags</InputLabel>
              <Select
                multiple
                value={selectedTags}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedTags(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Tags"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" variant="outlined" />
                    ))}
                  </Box>
                )}
              >
                {facets.tags.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedTags.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Categories
            </Typography>
            <FormControl fullWidth size="small">
              <InputLabel>Categories</InputLabel>
              <Select
                multiple
                value={selectedCategories}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedCategories(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Categories"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" color="info" variant="outlined" />
                    ))}
                  </Box>
                )}
              >
                {facets.categories.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedCategories.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Paper>
        </Grid>

        <Grid item xs={12} md={9}>
          {error && (
            <Alert
              severity="error"
              sx={{ mb: 2 }}
              action={
                <Button color="inherit" size="small" onClick={handleAdvancedSearch}>
                  Advanced
                </Button>
              }
            >
              {error}
            </Alert>
          )}
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">
              {loading
                ? 'Searching...'
                : nodesTotal > 0
                  ? `Showing ${nodes.length} of ${nodesTotal} results`
                  : '0 results found'}
            </Typography>
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel>Sort by</InputLabel>
              <Select value={sortBy} onChange={(e) => setSortBy(e.target.value)} label="Sort by">
                <MenuItem value="relevance">Relevance</MenuItem>
                <MenuItem value="name">Name</MenuItem>
                <MenuItem value="modified">Modified Date</MenuItem>
                <MenuItem value="size">Size</MenuItem>
              </Select>
            </FormControl>
          </Box>
          {filtersApplied && (
            <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
              {selectedMimeTypes.map((value) => (
                <Chip
                  key={`mime-${value}`}
                  label={`Type: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedMimeTypes, value)}
                />
              ))}
              {selectedCreators.map((value) => (
                <Chip
                  key={`creator-${value}`}
                  label={`Creator: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedCreators, value)}
                />
              ))}
              {selectedCorrespondents.map((value) => (
                <Chip
                  key={`corr-${value}`}
                  label={`Correspondent: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedCorrespondents, value)}
                />
              ))}
              {selectedTags.map((value) => (
                <Chip
                  key={`tag-${value}`}
                  label={`Tag: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedTags, value)}
                />
              ))}
              {selectedCategories.map((value) => (
                <Chip
                  key={`cat-${value}`}
                  label={`Category: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedCategories, value)}
                />
              ))}
              <Button size="small" onClick={clearFacetFilters}>
                Clear all
              </Button>
            </Box>
          )}

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
                          <Box ml={2} flex={1} sx={{ minWidth: 0 }}>
                            <Tooltip title={node.name} placement="top-start" arrow>
                              <Typography
                                variant="h6"
                                sx={getNameTypographySx(node.name)}
                              >
                                {node.name}
                              </Typography>
                            </Tooltip>
                            <Box display="flex" gap={1} mt={0.5}>
                              {getFileTypeChip(node.contentType)}
                              {node.currentVersionLabel && (
                                <Chip label={`v${node.currentVersionLabel}`} size="small" variant="outlined" />
                              )}
                            </Box>
                          </Box>
                        </Box>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                          {node.path}
                        </Typography>
                        <Highlight
                          text={node.description}
                          highlights={node.highlights?.description || node.highlights?.content}
                        />
                        {renderTagsCategories(node)}
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
                        <Button size="small" startIcon={<Visibility />} onClick={() => handleViewNode(node)}>
                          View
                        </Button>
                        {isDocumentNode(node) && (
                          <Button size="small" startIcon={<Download />} onClick={() => handleDownload(node)}>
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
                    onChange={(_, value) => {
                      setPage(value);
                      if (lastSearchCriteria) {
                        const sortParams = getSortParams(sortBy);
                        dispatch(searchNodes({ ...lastSearchCriteria, page: value - 1, size: pageSize, ...sortParams }));
                      }
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }}
                    color="primary"
                  />
                </Box>
              )}
            </>
          )}
        </Grid>
      </Grid>

      {previewOpen && previewNode && (
        <React.Suspense
          fallback={(
            <Box
              sx={{
                position: 'fixed',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'rgba(0,0,0,0.35)',
                zIndex: (theme) => theme.zIndex.modal + 1,
              }}
            >
              <CircularProgress />
            </Box>
          )}
        >
          <DocumentPreview
            open={previewOpen}
            onClose={handleClosePreview}
            node={previewNode}
          />
        </React.Suspense>
      )}
    </Box>
  );
};

export default SearchResults;
