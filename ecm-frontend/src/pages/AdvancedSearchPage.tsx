import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  IconButton,
} from '@mui/material';
import {
  Search as SearchIcon,
  FilterList as FilterIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { format } from 'date-fns';
import apiService from '../services/api';
import { toast } from 'react-toastify';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'store';
import { setSidebarOpen } from 'store/slices/uiSlice';
import nodeService from 'services/nodeService';
import {
  formatPreviewFailureReasonLabel,
  getEffectivePreviewStatus,
  getFailedPreviewMeta,
  isUnsupportedPreviewFailure,
  normalizePreviewFailureReason,
  summarizeFailedPreviews,
} from 'utils/previewStatusUtils';

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

const PREVIEW_STATUS_VALUES = ['READY', 'PROCESSING', 'QUEUED', 'FAILED', 'UNSUPPORTED', 'PENDING'] as const;
const DATE_RANGE_VALUES = ['all', 'today', 'week', 'month'] as const;

const parsePreviewStatuses = (rawValue: string | null): string[] => {
  if (!rawValue) {
    return [];
  }
  const allowed = new Set(PREVIEW_STATUS_VALUES);
  const parsed = rawValue
    .split(',')
    .map((value) => value.trim().toUpperCase())
    .filter((value) => value && allowed.has(value as (typeof PREVIEW_STATUS_VALUES)[number]));
  return Array.from(new Set(parsed));
};

const parseCsvValues = (rawValue: string | null): string[] => {
  if (!rawValue) {
    return [];
  }
  return Array.from(new Set(rawValue.split(',').map((value) => value.trim()).filter(Boolean)));
};

const parseDateRange = (rawValue: string | null): 'all' | 'today' | 'week' | 'month' => {
  if (!rawValue) {
    return 'all';
  }
  return DATE_RANGE_VALUES.includes(rawValue as (typeof DATE_RANGE_VALUES)[number])
    ? (rawValue as 'all' | 'today' | 'week' | 'month')
    : 'all';
};

const parseOptionalNumber = (rawValue: string | null): number | undefined => {
  if (!rawValue) {
    return undefined;
  }
  const value = Number(rawValue);
  if (!Number.isFinite(value) || value < 0) {
    return undefined;
  }
  return value;
};

type AdvancedSearchUrlState = {
  query: string;
  page: number;
  previewStatuses: string[];
  dateRange: 'all' | 'today' | 'week' | 'month';
  mimeTypes: string[];
  creators: string[];
  tags: string[];
  categories: string[];
  minSize?: number;
  maxSize?: number;
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
  previewFailureCategory?: string;
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
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { sidebarAutoCollapse } = useAppSelector((state) => state.ui);
  const initializedFromUrlRef = useRef(false);
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
  const [selectedPreviewStatuses, setSelectedPreviewStatuses] = useState<string[]>([]);
  const [queueingPreviewId, setQueueingPreviewId] = useState<string | null>(null);
  const [batchRetrying, setBatchRetrying] = useState(false);
  const [previewQueueStatusById, setPreviewQueueStatusById] = useState<Record<string, {
    attempts?: number;
    nextAttemptAt?: string;
  }>>({});

  const syncSearchStateToUrl = useCallback((state: AdvancedSearchUrlState) => {
    const params = new URLSearchParams();
    const normalizedQuery = state.query.trim();
    if (normalizedQuery) {
      params.set('q', normalizedQuery);
    }
    if (state.page > 1) {
      params.set('page', String(state.page));
    }
    if (state.previewStatuses.length > 0) {
      params.set('previewStatus', state.previewStatuses.join(','));
    }
    if (state.dateRange !== 'all') {
      params.set('dateRange', state.dateRange);
    }
    if (state.mimeTypes.length > 0) params.set('mimeTypes', state.mimeTypes.join(','));
    if (state.creators.length > 0) params.set('creators', state.creators.join(','));
    if (state.tags.length > 0) params.set('tags', state.tags.join(','));
    if (state.categories.length > 0) params.set('categories', state.categories.join(','));
    if (state.minSize !== undefined) params.set('minSize', String(state.minSize));
    if (state.maxSize !== undefined) params.set('maxSize', String(state.maxSize));

    const nextSearch = params.toString();
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : '',
      },
      { replace: true }
    );
  }, [location.pathname, navigate]);

  const getPreviewStatusMeta = (
    status?: string,
    mimeType?: string,
    failureCategory?: string,
    failureReason?: string
  ) => {
    const normalized = status?.toUpperCase();
    if (!normalized || normalized === 'READY') {
      return null;
    }
    if (normalized === 'FAILED') {
      return getFailedPreviewMeta(mimeType, failureCategory, failureReason);
    }
    if (normalized === 'UNSUPPORTED') {
      return { label: 'Preview unsupported', color: 'default' as const, unsupported: true };
    }
    if (normalized === 'PROCESSING') {
      return { label: 'Preview processing', color: 'warning' as const, unsupported: false };
    }
    if (normalized === 'QUEUED') {
      return { label: 'Preview queued', color: 'info' as const, unsupported: false };
    }
    return { label: `Preview ${normalized.toLowerCase()}`, color: 'default' as const, unsupported: false };
  };

  const formatScore = (score?: number) => {
    if (score === undefined || score === null) {
      return null;
    }
    const rounded = Math.round(score * 100) / 100;
    return `Score ${rounded}`;
  };

  const handleSearch = useCallback(async (
    newPage = 1,
    options?: {
      queryOverride?: string;
      previewStatuses?: string[];
      dateRangeOverride?: 'all' | 'today' | 'week' | 'month';
      mimeTypesOverride?: string[];
      creatorsOverride?: string[];
      tagsOverride?: string[];
      categoriesOverride?: string[];
      minSizeOverride?: number;
      maxSizeOverride?: number;
    }
  ) => {
    try {
      setLoading(true);
      const effectiveQuery = options?.queryOverride ?? query;
      const effectivePreviewStatuses = options?.previewStatuses ?? selectedPreviewStatuses;
      const effectiveDateRange = options?.dateRangeOverride ?? dateRange;
      const effectiveMimeTypes = options?.mimeTypesOverride ?? selectedMimeTypes;
      const effectiveCreators = options?.creatorsOverride ?? selectedCreators;
      const effectiveTags = options?.tagsOverride ?? selectedTags;
      const effectiveCategories = options?.categoriesOverride ?? selectedCategories;
      const effectiveMinSize = options?.minSizeOverride ?? minSize;
      const effectiveMaxSize = options?.maxSizeOverride ?? maxSize;
      
      // Calculate date filters
      let dateFrom = null;
      const now = new Date();
      if (effectiveDateRange === 'today') dateFrom = new Date(now.setHours(0,0,0,0)).toISOString();
      if (effectiveDateRange === 'week') dateFrom = new Date(now.setDate(now.getDate() - 7)).toISOString();
      if (effectiveDateRange === 'month') dateFrom = new Date(now.setMonth(now.getMonth() - 1)).toISOString();

      const payload = {
        query: effectiveQuery,
        filters: {
          mimeTypes: effectiveMimeTypes.length > 0 ? effectiveMimeTypes : undefined,
          createdBy: effectiveCreators.length > 0 ? effectiveCreators : undefined,
          tags: effectiveTags.length > 0 ? effectiveTags : undefined,
          categories: effectiveCategories.length > 0 ? effectiveCategories : undefined,
          previewStatuses: effectivePreviewStatuses.length > 0 ? effectivePreviewStatuses : undefined,
          modifiedFrom: dateFrom,
          minSize: effectiveMinSize,
          maxSize: effectiveMaxSize,
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
      syncSearchStateToUrl({
        query: effectiveQuery,
        page: newPage,
        previewStatuses: effectivePreviewStatuses,
        dateRange: effectiveDateRange,
        mimeTypes: effectiveMimeTypes,
        creators: effectiveCreators,
        tags: effectiveTags,
        categories: effectiveCategories,
        minSize: effectiveMinSize,
        maxSize: effectiveMaxSize,
      });
      setPreviewQueueStatusById((prev) => {
        const next: Record<string, { attempts?: number; nextAttemptAt?: string }> = {};
        response.results.content.forEach((result) => {
          if (prev[result.id]) {
            next[result.id] = prev[result.id];
          }
        });
        return next;
      });

    } catch (error) {
      toast.error('Search failed');
    } finally {
      setLoading(false);
    }
  }, [
    dateRange,
    maxSize,
    minSize,
    query,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedPreviewStatuses,
    selectedTags,
    syncSearchStateToUrl,
  ]);

  useEffect(() => {
    if (initializedFromUrlRef.current) {
      return;
    }
    initializedFromUrlRef.current = true;
    const params = new URLSearchParams(location.search);
    const queryFromUrl = (params.get('q') || '').trim();
    const parsedPage = Number(params.get('page') || '1');
    const pageFromUrl = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 1;
    const previewStatusesFromUrl = parsePreviewStatuses(params.get('previewStatus'));
    const dateRangeFromUrl = parseDateRange(params.get('dateRange'));
    const mimeTypesFromUrl = parseCsvValues(params.get('mimeTypes'));
    const creatorsFromUrl = parseCsvValues(params.get('creators'));
    const tagsFromUrl = parseCsvValues(params.get('tags'));
    const categoriesFromUrl = parseCsvValues(params.get('categories'));
    const minSizeFromUrl = parseOptionalNumber(params.get('minSize'));
    const maxSizeFromUrl = parseOptionalNumber(params.get('maxSize'));

    const hasRestorableState = Boolean(
      queryFromUrl
      || previewStatusesFromUrl.length > 0
      || dateRangeFromUrl !== 'all'
      || mimeTypesFromUrl.length > 0
      || creatorsFromUrl.length > 0
      || tagsFromUrl.length > 0
      || categoriesFromUrl.length > 0
      || minSizeFromUrl !== undefined
      || maxSizeFromUrl !== undefined
      || pageFromUrl > 1
    );

    setQuery(queryFromUrl);
    setSelectedPreviewStatuses(previewStatusesFromUrl);
    setDateRange(dateRangeFromUrl);
    setSelectedMimeTypes(mimeTypesFromUrl);
    setSelectedCreators(creatorsFromUrl);
    setSelectedTags(tagsFromUrl);
    setSelectedCategories(categoriesFromUrl);
    setMinSize(minSizeFromUrl);
    setMaxSize(maxSizeFromUrl);

    if (hasRestorableState) {
      void handleSearch(pageFromUrl, {
        queryOverride: queryFromUrl,
        previewStatuses: previewStatusesFromUrl,
        dateRangeOverride: dateRangeFromUrl,
        mimeTypesOverride: mimeTypesFromUrl,
        creatorsOverride: creatorsFromUrl,
        tagsOverride: tagsFromUrl,
        categoriesOverride: categoriesFromUrl,
        minSizeOverride: minSizeFromUrl,
        maxSizeOverride: maxSizeFromUrl,
      });
    }
  }, [handleSearch, location.search]);

  const handlePageChange = (event: React.ChangeEvent<unknown>, value: number) => {
    handleSearch(value);
  };

  const previewStatusCounts = useMemo(() => results.reduce(
    (acc, result) => {
      if (result.nodeType === 'FOLDER') {
        return acc;
      }
      const status = getEffectivePreviewStatus(
        result.previewStatus,
        result.previewFailureCategory,
        result.mimeType,
        result.previewFailureReason
      );
      if (status in acc) {
        acc[status as keyof typeof acc] += 1;
      } else {
        acc.PENDING += 1;
      }
      return acc;
    },
    {
      READY: 0,
      PROCESSING: 0,
      QUEUED: 0,
      FAILED: 0,
      UNSUPPORTED: 0,
      PENDING: 0,
    }
  ), [results]);

  const failedPreviewResults = useMemo(
    () => results.filter((result) => result.nodeType !== 'FOLDER'
      && (result.previewStatus || '').toUpperCase() === 'FAILED'
      && !isUnsupportedPreviewFailure(result.previewFailureCategory, result.mimeType, result.previewFailureReason)),
    [results]
  );
  const failedPreviewSummary = useMemo(
    () => summarizeFailedPreviews(
      results
        .filter((result) => result.nodeType !== 'FOLDER')
        .map((result) => ({
          previewStatus: result.previewStatus,
          previewFailureCategory: result.previewFailureCategory,
          previewFailureReason: result.previewFailureReason,
          mimeType: result.mimeType,
        }))
    ),
    [results]
  );

  const failedPreviewReasonSummary = useMemo(() => {
    return failedPreviewSummary.retryableReasons.slice(0, 4);
  }, [failedPreviewSummary]);

  const previewRetrySummary = useMemo(() => {
    const entries = Object.values(previewQueueStatusById);
    if (entries.length === 0) {
      return null;
    }
    const times = entries
      .map((entry) => entry.nextAttemptAt)
      .filter((value): value is string => Boolean(value))
      .map((value) => new Date(value))
      .filter((value) => !Number.isNaN(value.getTime()));
    if (times.length === 0) {
      return null;
    }
    const nextAt = times.sort((a, b) => a.getTime() - b.getTime())[0];
    return {
      count: entries.length,
      nextAt,
    };
  }, [previewQueueStatusById]);

  const getQueueDetail = useCallback((resultId: string) => {
    const queueStatus = previewQueueStatusById[resultId];
    if (!queueStatus) {
      return null;
    }
    const attemptsLabel = queueStatus.attempts !== undefined
      ? `Attempts: ${queueStatus.attempts}`
      : null;
    const nextLabel = queueStatus.nextAttemptAt
      ? `Next retry: ${format(new Date(queueStatus.nextAttemptAt), 'PPp')}`
      : null;
    const labels = [attemptsLabel, nextLabel].filter(Boolean);
    return labels.length > 0 ? labels.join(' • ') : null;
  }, [previewQueueStatusById]);

  const togglePreviewStatus = useCallback((status: string) => {
    const next = selectedPreviewStatuses.includes(status)
      ? selectedPreviewStatuses.filter((value) => value !== status)
      : [...selectedPreviewStatuses, status];
    setSelectedPreviewStatuses(next);
    syncSearchStateToUrl({
      query,
      page: 1,
      previewStatuses: next,
      dateRange,
      mimeTypes: selectedMimeTypes,
      creators: selectedCreators,
      tags: selectedTags,
      categories: selectedCategories,
      minSize,
      maxSize,
    });
    void handleSearch(1, { previewStatuses: next });
  }, [dateRange, handleSearch, maxSize, minSize, query, selectedCategories, selectedCreators, selectedMimeTypes, selectedPreviewStatuses, selectedTags, syncSearchStateToUrl]);

  const handleRetryPreview = useCallback(async (result: SearchResult, force = false) => {
    if (!result?.id || result.nodeType === 'FOLDER') {
      return;
    }
    setQueueingPreviewId(result.id);
    try {
      const status = await nodeService.queuePreview(result.id, force);
      setPreviewQueueStatusById((prev) => ({
        ...prev,
        [result.id]: {
          attempts: status?.attempts,
          nextAttemptAt: status?.nextAttemptAt,
        },
      }));
      if (status?.queued) {
        toast.success('Preview queued');
      } else {
        toast.info('Preview already up to date');
      }
    } catch {
      toast.error('Failed to queue preview');
    } finally {
      setQueueingPreviewId(null);
    }
  }, []);

  const handleRetryFailedPreviews = useCallback(async (force = false) => {
    if (failedPreviewResults.length === 0) {
      return;
    }
    setBatchRetrying(true);
    let queued = 0;
    let skipped = 0;
    let failed = 0;
    for (const result of failedPreviewResults) {
      try {
        const status = await nodeService.queuePreview(result.id, force);
        setPreviewQueueStatusById((prev) => ({
          ...prev,
          [result.id]: {
            attempts: status?.attempts,
            nextAttemptAt: status?.nextAttemptAt,
          },
        }));
        if (status?.queued) {
          queued += 1;
        } else {
          skipped += 1;
        }
      } catch {
        failed += 1;
      }
    }
    const parts = [`queued ${queued}`];
    if (skipped > 0) parts.push(`skipped ${skipped}`);
    if (failed > 0) parts.push(`failed ${failed}`);
    toast.success(`Preview ${force ? 'rebuilds' : 'retries'}: ${parts.join(', ')}`);
    setBatchRetrying(false);
  }, [failedPreviewResults]);

  const handleRetryFailedReason = useCallback(async (reason: string, force = false) => {
    const targets = failedPreviewResults.filter((result) => {
      const nodeReason = normalizePreviewFailureReason(result.previewFailureReason);
      return nodeReason === reason;
    });
    if (targets.length === 0) {
      return;
    }
    setBatchRetrying(true);
    let queued = 0;
    let skipped = 0;
    let failed = 0;
    for (const result of targets) {
      try {
        const status = await nodeService.queuePreview(result.id, force);
        setPreviewQueueStatusById((prev) => ({
          ...prev,
          [result.id]: {
            attempts: status?.attempts,
            nextAttemptAt: status?.nextAttemptAt,
          },
        }));
        if (status?.queued) {
          queued += 1;
        } else {
          skipped += 1;
        }
      } catch {
        failed += 1;
      }
    }
    const reasonLabel = formatPreviewFailureReasonLabel(reason);
    const parts = [`queued ${queued}`];
    if (skipped > 0) parts.push(`skipped ${skipped}`);
    if (failed > 0) parts.push(`failed ${failed}`);
    toast.success(`Preview ${force ? 'rebuilds' : 'retries'} (${reasonLabel}): ${parts.join(', ')}`);
    setBatchRetrying(false);
  }, [failedPreviewResults]);

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
                <Paper variant="outlined" sx={{ p: 1.5 }}>
                  <Typography variant="subtitle2" gutterBottom>
                    Preview Status
                  </Typography>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                    Filter results by preview generation state.
                  </Typography>
                  <Box display="flex" flexWrap="wrap" gap={1}>
                    {[
                      { value: 'READY', label: 'Ready', color: 'success' as const, count: previewStatusCounts.READY },
                      { value: 'PROCESSING', label: 'Processing', color: 'warning' as const, count: previewStatusCounts.PROCESSING },
                      { value: 'QUEUED', label: 'Queued', color: 'info' as const, count: previewStatusCounts.QUEUED },
                      { value: 'FAILED', label: 'Failed', color: 'error' as const, count: previewStatusCounts.FAILED },
                      { value: 'UNSUPPORTED', label: 'Unsupported', color: 'default' as const, count: previewStatusCounts.UNSUPPORTED },
                      { value: 'PENDING', label: 'Pending', color: 'default' as const, count: previewStatusCounts.PENDING },
                    ].map((status) => (
                      <Chip
                        key={`advanced-preview-status-${status.value}`}
                        label={`${status.label} (${status.count})`}
                        size="small"
                        color={selectedPreviewStatuses.includes(status.value) ? status.color : 'default'}
                        variant={selectedPreviewStatuses.includes(status.value) ? 'filled' : 'outlined'}
                        onClick={() => togglePreviewStatus(status.value)}
                      />
                    ))}
                    <Button
                      size="small"
                      disabled={selectedPreviewStatuses.length === 0}
                      onClick={() => {
                        setSelectedPreviewStatuses([]);
                        syncSearchStateToUrl({
                          query,
                          page: 1,
                          previewStatuses: [],
                          dateRange,
                          mimeTypes: selectedMimeTypes,
                          creators: selectedCreators,
                          tags: selectedTags,
                          categories: selectedCategories,
                          minSize,
                          maxSize,
                        });
                        void handleSearch(1, { previewStatuses: [] });
                      }}
                    >
                      Clear
                    </Button>
                  </Box>
                </Paper>
                {failedPreviewSummary.totalFailed > 0 && (
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={1}>
                      <Typography variant="caption" color="text.secondary">
                        Preview issues on current page: {failedPreviewSummary.totalFailed}
                        {' • '}Retryable {failedPreviewSummary.retryableFailed}
                        {' • '}Unsupported {failedPreviewSummary.unsupportedFailed}
                      </Typography>
                      {failedPreviewSummary.retryableFailed > 0 && (
                        <Box display="flex" flexWrap="wrap" gap={1}>
                          <Button
                            size="small"
                            variant="outlined"
                            onClick={() => { void handleRetryFailedPreviews(false); }}
                            disabled={batchRetrying}
                          >
                            Retry failed previews
                          </Button>
                          <Button
                            size="small"
                            variant="text"
                            onClick={() => { void handleRetryFailedPreviews(true); }}
                            disabled={batchRetrying}
                          >
                            Force rebuild failed previews
                          </Button>
                        </Box>
                      )}
                      {failedPreviewSummary.totalFailed > 0 && failedPreviewSummary.retryableFailed === 0 && (
                        <Typography variant="caption" color="text.secondary">
                          All preview issues on this page are unsupported; retry actions are hidden.
                        </Typography>
                      )}
                      {failedPreviewSummary.retryableFailed > 0 && failedPreviewReasonSummary.length > 0 && (
                        <Box display="flex" flexWrap="wrap" gap={1}>
                          {failedPreviewReasonSummary.map((item) => (
                            <Button
                              key={`retry-reason-${item.reason}`}
                              size="small"
                              variant="text"
                              onClick={() => { void handleRetryFailedReason(item.reason); }}
                              disabled={batchRetrying}
                            >
                              Retry reason "{formatPreviewFailureReasonLabel(item.reason)}" ({item.count})
                            </Button>
                          ))}
                        </Box>
                      )}
                      {failedPreviewSummary.retryableFailed > 0 && previewRetrySummary && (
                        <Typography variant="caption" color="text.secondary">
                          Preview queue: {previewRetrySummary.count} item(s) • Next retry {format(previewRetrySummary.nextAt, 'PPp')}
                        </Typography>
                      )}
                    </Stack>
                  </Paper>
                )}
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
                        {formatScore(result.score) && (
                          <Chip label={formatScore(result.score)} size="small" variant="outlined" />
                        )}
                        {result.nodeType !== 'FOLDER' && getPreviewStatusMeta(
                          result.previewStatus,
                          result.mimeType,
                          result.previewFailureCategory,
                          result.previewFailureReason
                        ) && (() => {
                          const previewMeta = getPreviewStatusMeta(
                            result.previewStatus,
                            result.mimeType,
                            result.previewFailureCategory,
                            result.previewFailureReason
                          );
                          if (!previewMeta) {
                            return null;
                          }
                          const queueDetail = getQueueDetail(result.id);
                          const tooltipTitle = [result.previewFailureReason || '', queueDetail].filter(Boolean).join(' • ');
                          const isFailed = (result.previewStatus || '').toUpperCase() === 'FAILED';
                          const canRetry = isFailed && !previewMeta.unsupported;
                          return (
                            <Box display="flex" flexDirection="column" alignItems="flex-start" gap={0.5}>
                              <Box display="flex" alignItems="center" gap={0.5}>
                                <Tooltip
                                  title={tooltipTitle}
                                  placement="top-start"
                                  arrow
                                  disableHoverListener={!tooltipTitle}
                                >
                                  <Chip
                                    label={previewMeta.label}
                                    color={previewMeta.color}
                                    size="small"
                                    variant="outlined"
                                  />
                                </Tooltip>
                                {canRetry && (
                                  <Tooltip title="Retry preview" placement="top-start" arrow>
                                    <span>
                                      <IconButton
                                        size="small"
                                        aria-label="Retry preview"
                                        disabled={queueingPreviewId === result.id}
                                        onClick={(event) => {
                                          event.stopPropagation();
                                          void handleRetryPreview(result);
                                        }}
                                      >
                                        <RefreshIcon fontSize="small" />
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )}
                              </Box>
                              {queueDetail && (
                                <Typography variant="caption" color="text.secondary">
                                  {queueDetail}
                                </Typography>
                              )}
                            </Box>
                          );
                        })()}
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
