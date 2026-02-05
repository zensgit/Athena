import React, { useState } from 'react';
import {
  Box,
  TextField,
  Button,
  Grid,
  Paper,
  Typography,
  Chip,
  FormControl,
  Select,
  MenuItem,
  Checkbox,
  ListItemText,
  Pagination,
  Stack,
  Divider,
  Tooltip,
} from '@mui/material';
import { Search as SearchIcon, FilterList as FilterIcon } from '@mui/icons-material';
import { format } from 'date-fns';
import apiService from '../services/api';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'store';
import { setSidebarOpen } from 'store/slices/uiSlice';

const MATCH_FIELD_LABELS: Record<string, string> = {
  name: 'Name',
  title: 'Title',
  description: 'Description',
  content: 'Content',
  textContent: 'Text',
  extractedText: 'Extracted text',
  tags: 'Tags',
  categories: 'Categories',
  correspondent: 'Correspondent',
};

interface SearchResult {
  id: string;
  name: string;
  mimeType: string;
  fileSize: number;
  highlights?: Record<string, string[]>;
  matchFields?: string[];
  highlightSummary?: string;
  score: number;
  createdDate: string;
  path: string;
  nodeType?: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  previewStatus?: string;
  previewFailureReason?: string;
}


interface FacetValue {
  value: string;
  count: number;
}

interface Facets {
  mimeType: FacetValue[];
  createdBy: FacetValue[];
  tags: FacetValue[];
  categories: FacetValue[];
}

interface SearchResponse {
  results: {
    content: SearchResult[];
    totalElements: number;
    totalPages: number;
  };
  facets: Facets;
}

const AdvancedSearchPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { sidebarAutoCollapse } = useAppSelector((state) => state.ui);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [facets, setFacets] = useState<Facets | null>(null);
  const [totalResults, setTotalResults] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  // Filters
  const [selectedMimeTypes, setSelectedMimeTypes] = useState<string[]>([]);
  const [selectedCreators, setSelectedCreators] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [dateRange, setDateRange] = useState<'all' | 'today' | 'week' | 'month'>('all');
  const [minSize, setMinSize] = useState<number | undefined>();
  const [maxSize, setMaxSize] = useState<number | undefined>();

  const getPreviewStatusMeta = (status?: string) => {
    const normalized = status?.toUpperCase();
    if (!normalized || normalized === 'READY') {
      return null;
    }
    if (normalized === 'FAILED') {
      return { label: 'Preview failed', color: 'error' as const };
    }
    if (normalized === 'PROCESSING') {
      return { label: 'Preview processing', color: 'warning' as const };
    }
    if (normalized === 'QUEUED') {
      return { label: 'Preview queued', color: 'info' as const };
    }
    return { label: `Preview ${normalized.toLowerCase()}`, color: 'default' as const };
  };

  const handleSearch = async (newPage = 1) => {
    try {
      setLoading(true);
      
      // Calculate date filters
      let dateFrom = null;
      const now = new Date();
      if (dateRange === 'today') dateFrom = new Date(now.setHours(0,0,0,0)).toISOString();
      if (dateRange === 'week') dateFrom = new Date(now.setDate(now.getDate() - 7)).toISOString();
      if (dateRange === 'month') dateFrom = new Date(now.setMonth(now.getMonth() - 1)).toISOString();

      const payload = {
        query: query,
        filters: {
          mimeTypes: selectedMimeTypes.length > 0 ? selectedMimeTypes : undefined,
          createdBy: selectedCreators.length > 0 ? selectedCreators : undefined,
          tags: selectedTags.length > 0 ? selectedTags : undefined,
          categories: selectedCategories.length > 0 ? selectedCategories : undefined,
          modifiedFrom: dateFrom,
          minSize,
          maxSize,
        },
        pageable: {
          page: newPage - 1, // API is 0-based
          size: 10
        },
        facetFields: ['mimeType', 'createdBy', 'tags', 'categories']
      };

      const response = await apiService.post<SearchResponse>('/search/faceted', payload);
      
      setResults(response.results.content);
      setTotalResults(response.results.totalElements);
      setTotalPages(response.results.totalPages);
      setFacets(response.facets);
      setPage(newPage);

    } catch (error) {
      toast.error('Search failed');
    } finally {
      setLoading(false);
    }
  };

  const handlePageChange = (event: React.ChangeEvent<unknown>, value: number) => {
    handleSearch(value);
  };

  return (
    <Box p={3} sx={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h4" gutterBottom>
        Advanced Search
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={8}>
            <TextField
              fullWidth
              label="Search query"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch(1)}
              InputProps={{
                endAdornment: <SearchIcon color="action" />
              }}
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={() => handleSearch(1)}
              startIcon={<SearchIcon />}
            >
              Search
            </Button>
          </Grid>
        </Grid>
      </Paper>

      <Grid container spacing={3} sx={{ flex: 1, overflow: 'hidden' }}>
        {/* Sidebar Filters */}
        <Grid item xs={12} md={3} sx={{ height: '100%', overflow: 'auto' }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Box display="flex" alignItems="center" mb={2}>
              <FilterIcon sx={{ mr: 1 }} color="primary" />
              <Typography variant="h6">Filters</Typography>
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* Date Filter */}
            <Typography variant="subtitle2" gutterBottom>Date Modified</Typography>
            <FormControl fullWidth size="small" sx={{ mb: 3 }}>
              <Select
                value={dateRange}
                onChange={(e) => setDateRange(e.target.value as any)}
              >
                <MenuItem value="all">Any time</MenuItem>
                <MenuItem value="today">Past 24 hours</MenuItem>
                <MenuItem value="week">Past week</MenuItem>
                <MenuItem value="month">Past month</MenuItem>
              </Select>
            </FormControl>

            {/* Mime Type Facet */}
            {facets?.mimeType && facets.mimeType.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>File Type</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedMimeTypes}
                    onChange={(e) => {
                        const val = e.target.value;
                        setSelectedMimeTypes(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                            <Chip key={value} label={value.split('/')[1]} size="small" />
                        ))}
                        </Box>
                    )}
                  >
                    {facets.mimeType.map((facet) => (
                      <MenuItem key={facet.value} value={facet.value}>
                        <Checkbox checked={selectedMimeTypes.indexOf(facet.value) > -1} />
                        <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </>
            )}

            <Typography variant="subtitle2" gutterBottom>File Size (bytes)</Typography>
            <TextField
              fullWidth
              type="number"
              label="Min size"
              size="small"
              sx={{ mb: 2 }}
              value={minSize ?? ''}
              onChange={(e) => setMinSize(e.target.value ? Number(e.target.value) : undefined)}
            />
            <TextField
              fullWidth
              type="number"
              label="Max size"
              size="small"
              sx={{ mb: 3 }}
              value={maxSize ?? ''}
              onChange={(e) => setMaxSize(e.target.value ? Number(e.target.value) : undefined)}
            />

            {/* Tags Facet */}
            {facets?.tags && facets.tags.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Tags</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedTags}
                    onChange={(e) => {
                      const val = e.target.value;
                      setSelectedTags(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                          <Chip key={value} label={value} size="small" />
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
              </>
            )}

            {/* Categories Facet */}
            {facets?.categories && facets.categories.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Categories</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedCategories}
                    onChange={(e) => {
                      const val = e.target.value;
                      setSelectedCategories(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                          <Chip key={value} label={value} size="small" />
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
              </>
            )}

            {/* Creator Facet */}
            {facets?.createdBy && facets.createdBy.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Created By</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedCreators}
                    onChange={(e) => {
                        const val = e.target.value;
                        setSelectedCreators(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                            <Chip key={value} label={value} size="small" />
                        ))}
                        </Box>
                    )}
                  >
                    {facets.createdBy.map((facet) => (
                      <MenuItem key={facet.value} value={facet.value}>
                        <Checkbox checked={selectedCreators.indexOf(facet.value) > -1} />
                        <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </>
            )}
            
            <Button 
                variant="outlined" 
                fullWidth 
                onClick={() => handleSearch(1)}
                disabled={loading}
            >
                Apply Filters
            </Button>
          </Paper>
        </Grid>

        {/* Results Area */}
        <Grid item xs={12} md={9} sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <Box flex={1} overflow="auto">
            {loading ? (
                <Typography sx={{ p: 2 }}>Searching...</Typography>
            ) : results.length > 0 ? (
              <Stack spacing={2}>
                <Typography variant="body2" color="textSecondary">
                  Found {totalResults} results
                </Typography>
                {results.map((result) => (
                  <Paper 
                    key={result.id} 
                    sx={{ p: 2, cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
                    onClick={() => {
                      if (result.nodeType === 'FOLDER') {
                        navigate(`/browse/${result.id}`);
                      } else {
                        navigate(`/browse/${result.parentId || 'root'}`);
                      }
                      if (sidebarAutoCollapse) {
                        dispatch(setSidebarOpen(false));
                      }
                    }}
                  >
                    <Box display="flex" justifyContent="space-between">
                      <Typography variant="h6" color="primary" sx={{ textDecoration: 'underline' }}>
                        {result.name}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {format(new Date(result.createdDate), 'PPP')}
                      </Typography>
                    </Box>
                    
                    {/* Snippets / Highlights */}
                    {(() => {
                      const snippet = result.highlightSummary
                        || result.highlights?.description?.[0]
                        || result.highlights?.content?.[0]
                        || result.highlights?.textContent?.[0]
                        || result.highlights?.extractedText?.[0]
                        || result.highlights?.title?.[0]
                        || result.highlights?.name?.[0];
                      if (!snippet) {
                        return null;
                      }
                      return (
                        <Typography
                          variant="body2"
                          color="textSecondary"
                          sx={{ mt: 1 }}
                          dangerouslySetInnerHTML={{ __html: `...${snippet}...` }}
                        />
                      );
                    })()}

                    {(() => {
                      const matchFields = (result.matchFields && result.matchFields.length > 0)
                        ? result.matchFields
                        : Object.entries(result.highlights || {})
                            .filter(([, values]) => Array.isArray(values) && values.length > 0)
                            .map(([key]) => key);
                      if (matchFields.length === 0) {
                        return null;
                      }
                      const displayFields = matchFields.slice(0, 4);
                      const remaining = matchFields.length - displayFields.length;
                      const formatField = (field: string) =>
                        MATCH_FIELD_LABELS[field]
                        || field.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ');
                      return (
                        <Box mt={1} display="flex" gap={1} flexWrap="wrap">
                          <Chip label="Matched in" size="small" variant="outlined" />
                          {displayFields.map((field) => (
                            <Chip key={`${result.id}-match-${field}`} label={formatField(field)} size="small" />
                          ))}
                          {remaining > 0 && (
                            <Chip label={`+${remaining}`} size="small" variant="outlined" />
                          )}
                        </Box>
                      );
                    })()}
                    
                    <Box mt={1} display="flex" gap={1}>
                        <Chip label={result.mimeType} size="small" variant="outlined" />
                        <Chip
                          label={`${((result.fileSize || 0) / 1024).toFixed(1)} KB`}
                          size="small"
                          variant="outlined"
                        />
                        {result.nodeType !== 'FOLDER' && getPreviewStatusMeta(result.previewStatus) && (
                          <Tooltip
                            title={result.previewFailureReason || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!result.previewFailureReason}
                          >
                            <Chip
                              label={getPreviewStatusMeta(result.previewStatus)?.label}
                              color={getPreviewStatusMeta(result.previewStatus)?.color}
                              size="small"
                              variant="outlined"
                            />
                          </Tooltip>
                        )}
                    </Box>
                  </Paper>
                ))}
              </Stack>
            ) : (
              <Box p={4} textAlign="center">
                <Typography color="textSecondary">
                    {totalResults === 0 && query ? 'No results found.' : 'Enter a query to start searching.'}
                </Typography>
              </Box>
            )}
          </Box>

          {/* Pagination */}
          {totalPages > 1 && (
            <Box py={2} display="flex" justifyContent="center">
              <Pagination
                count={totalPages}
                page={page}
                onChange={handlePageChange}
                color="primary"
              />
            </Box>
          )}
        </Grid>
      </Grid>
    </Box>
  );
};

export default AdvancedSearchPage;
