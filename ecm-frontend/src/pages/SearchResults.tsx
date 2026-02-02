import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  Edit,
  AutoAwesome,
  FilterList,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppSelector, useAppDispatch } from 'store';
import { fetchSearchFacets, searchNodes } from 'store/slices/nodeSlice';
import { setSearchOpen, setSidebarOpen } from 'store/slices/uiSlice';
import nodeService from 'services/nodeService';
import { Node, SearchCriteria } from 'types';
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
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { nodes, nodesTotal, loading, error, searchFacets, lastSearchCriteria } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const { sidebarAutoCollapse } = useAppSelector((state) => state.ui);
  const [quickSearch, setQuickSearch] = useState('');
  const [sortBy, setSortBy] = useState('relevance');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [selectedMimeTypes, setSelectedMimeTypes] = useState<string[]>([]);
  const [selectedCreators, setSelectedCreators] = useState<string[]>([]);
  const [selectedCorrespondents, setSelectedCorrespondents] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [selectedPreviewStatuses, setSelectedPreviewStatuses] = useState<string[]>([]);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewAnnotate, setPreviewAnnotate] = useState(false);
  const [hiddenNodeIds, setHiddenNodeIds] = useState<string[]>([]);
  const [fallbackNodes, setFallbackNodes] = useState<Node[]>([]);
  const [fallbackLabel, setFallbackLabel] = useState('');
  const [similarResults, setSimilarResults] = useState<Node[] | null>(null);
  const [similarSource, setSimilarSource] = useState<{ id: string; name?: string } | null>(null);
  const [similarLoadingId, setSimilarLoadingId] = useState<string | null>(null);
  const [similarError, setSimilarError] = useState<string | null>(null);
  const [suggestedFilters, setSuggestedFilters] = useState<
    Array<{ field: string; label: string; value: string; count?: number }>
  >([]);
  const [suggestedFiltersLoading, setSuggestedFiltersLoading] = useState(false);
  const [suggestedFiltersError, setSuggestedFiltersError] = useState<string | null>(null);
  const [spellcheckSuggestions, setSpellcheckSuggestions] = useState<string[]>([]);
  const [spellcheckLoading, setSpellcheckLoading] = useState(false);
  const [spellcheckError, setSpellcheckError] = useState<string | null>(null);
  const lastSpellcheckQueryRef = useRef('');
  const previewOpen = Boolean(previewNode);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const suppressFacetSearch = useRef(false);
  const quickSearchDebounceRef = useRef<number | null>(null);
  const isSimilarMode = similarResults !== null;

  const clearSimilarResults = useCallback(() => {
    setSimilarResults(null);
    setSimilarSource(null);
    setSimilarLoadingId(null);
    setSimilarError(null);
  }, []);

  const runSearch = useCallback(
    (criteria: SearchCriteria) => {
      clearSimilarResults();
      return dispatch(searchNodes(criteria));
    },
    [clearSimilarResults, dispatch]
  );

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

  const handleQuickSearch = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (quickSearchDebounceRef.current) {
      window.clearTimeout(quickSearchDebounceRef.current);
    }
    const formData = new FormData(e.currentTarget);
    const rawQuery = formData.get('quickSearch');
    const query = (typeof rawQuery === 'string' ? rawQuery : quickSearch).trim();
    if (!query) {
      return;
    }
    if (query !== quickSearch) {
      setQuickSearch(query);
    }
    setPage(1);
    const sortParams = getSortParams(sortBy);
    await runSearch({ name: query, page: 0, size: pageSize, ...sortParams });
  };

  const handleClearSearch = () => {
    setQuickSearch('');
  };

  const handleAdvancedSearch = () => {
    dispatch(setSearchOpen(true));
  };

  const handleRetrySearch = () => {
    if (!lastSearchCriteria) {
      return;
    }
    const sortParams = getSortParams(sortBy);
    runSearch({ ...lastSearchCriteria, page: 0, size: pageSize, ...sortParams });
  };

  const handleSpellcheckSuggestion = (suggestion: string) => {
    const nextQuery = suggestion.trim();
    if (!nextQuery) {
      return;
    }
    setQuickSearch(nextQuery);
    setPage(1);
    const sortParams = getSortParams(sortBy);
    runSearch({ name: nextQuery, page: 0, size: pageSize, ...sortParams });
  };

  const clearFacetFilters = () => {
    setSelectedMimeTypes([]);
    setSelectedCreators([]);
    setSelectedCorrespondents([]);
    setSelectedTags([]);
    setSelectedCategories([]);
    setSelectedPreviewStatuses([]);
  };

  const removeFacetValue = (
    setter: React.Dispatch<React.SetStateAction<string[]>>,
    value: string
  ) => {
    setter((prev) => prev.filter((item) => item !== value));
  };

  const togglePreviewStatus = (status: string) => {
    setSelectedPreviewStatuses((prev) =>
      prev.includes(status) ? prev.filter((item) => item !== status) : [...prev, status]
    );
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
    const query = (lastSearchCriteria?.name || '').trim();
    if (!query || isSimilarMode) {
      setSuggestedFilters([]);
      setSuggestedFiltersError(null);
      return;
    }

    let active = true;
    setSuggestedFiltersLoading(true);
    nodeService.getSuggestedFilters(query)
      .then((filters) => {
        if (!active) return;
        setSuggestedFilters(Array.isArray(filters) ? filters : []);
        setSuggestedFiltersError(null);
      })
      .catch(() => {
        if (!active) return;
        setSuggestedFilters([]);
        setSuggestedFiltersError('Failed to load suggested filters');
      })
      .finally(() => {
        if (active) {
          setSuggestedFiltersLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [lastSearchCriteria, isSimilarMode]);

  useEffect(() => {
    const query = (lastSearchCriteria?.name || '').trim();
    if (!query || isSimilarMode) {
      lastSpellcheckQueryRef.current = '';
      setSpellcheckSuggestions([]);
      setSpellcheckError(null);
      setSpellcheckLoading(false);
      return;
    }

    if (lastSpellcheckQueryRef.current === query) {
      return;
    }

    lastSpellcheckQueryRef.current = query;
    let active = true;
    setSpellcheckLoading(true);
    nodeService.getSpellcheckSuggestions(query)
      .then((suggestions) => {
        if (!active) return;
        setSpellcheckSuggestions(Array.isArray(suggestions) ? suggestions : []);
        setSpellcheckError(null);
      })
      .catch(() => {
        if (!active) return;
        setSpellcheckSuggestions([]);
        setSpellcheckError('Failed to load spellcheck suggestions');
      })
      .finally(() => {
        if (active) {
          setSpellcheckLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [lastSearchCriteria, isSimilarMode]);

  useEffect(() => {
    if (lastSearchCriteria?.name !== undefined) {
      setQuickSearch(lastSearchCriteria.name || '');
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    const state = location.state as { similarSourceId?: string; similarSourceName?: string } | null;
    const sourceId = state?.similarSourceId;
    if (!sourceId) {
      return;
    }

    let active = true;
    setSimilarLoadingId(sourceId);
    setSimilarError(null);
    nodeService.findSimilar(sourceId, pageSize)
      .then((results) => {
        if (!active) return;
        setSimilarResults(results);
        setSimilarSource({ id: sourceId, name: state.similarSourceName });
        setPage(1);
        if (!results || results.length === 0) {
          toast.info('No similar documents found');
        }
      })
      .catch((error: any) => {
        if (!active) return;
        const message = error?.response?.data?.message || 'Failed to load similar documents';
        setSimilarError(message);
        toast.error(message);
      })
      .finally(() => {
        if (active) {
          setSimilarLoadingId(null);
        }
      });

    navigate(location.pathname, { replace: true, state: null });

    return () => {
      active = false;
    };
  }, [location.pathname, location.state, navigate, pageSize]);

  useEffect(() => {
    const query = quickSearch.trim();
    if (!query) {
      return;
    }
    if (query === (lastSearchCriteria?.name || '')) {
      return;
    }
    if (quickSearchDebounceRef.current) {
      window.clearTimeout(quickSearchDebounceRef.current);
    }
    quickSearchDebounceRef.current = window.setTimeout(() => {
      const sortParams = getSortParams(sortBy);
      runSearch({ name: query, page: 0, size: pageSize, ...sortParams });
    }, 400);

    return () => {
      if (quickSearchDebounceRef.current) {
        window.clearTimeout(quickSearchDebounceRef.current);
      }
    };
  }, [quickSearch, lastSearchCriteria, pageSize, runSearch, sortBy]);

  useEffect(() => {
    if (lastSearchCriteria?.page !== undefined) {
      setPage(lastSearchCriteria.page + 1);
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    if (lastSearchCriteria) {
      setHiddenNodeIds([]);
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    if (nodes.length > 0) {
      setFallbackNodes(nodes);
      setFallbackLabel((lastSearchCriteria?.name || '').trim());
    }
  }, [nodes, lastSearchCriteria]);

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
    runSearch({
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
    });
  }, [
    selectedMimeTypes,
    selectedCreators,
    selectedCorrespondents,
    selectedTags,
    selectedCategories,
    lastSearchCriteria,
    pageSize,
    runSearch,
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
    runSearch({ ...lastSearchCriteria, page: 0, size: pageSize, ...sortParams });
  }, [sortBy, lastSearchCriteria, pageSize, runSearch]);

  const isDocumentNode = (node: Node) => {
    const normalizedName = node.name?.trim().toLowerCase() || '';
    const hasExtension = FILE_EXTENSIONS.some((ext) => normalizedName.endsWith(ext));
    const normalizedPath = node.path?.trim().toLowerCase() || '';
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

  const isPdfDocument = (node: Node) => {
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const normalizedType = contentTypeHint?.toLowerCase();
    if (normalizedType && normalizedType.includes('pdf')) {
      return true;
    }
    const normalizedName = node.name?.trim().toLowerCase() || '';
    return normalizedName.endsWith('.pdf');
  };

  const isOfficeDocument = (node: Node) => {
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const normalizedType = contentTypeHint?.toLowerCase();
    if (normalizedType) {
      const officeTypes = new Set([
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
      if (officeTypes.has(normalizedType)) {
        return true;
      }
    }
    const normalizedName = node.name?.trim().toLowerCase() || '';
    return [
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
    ].some((ext) => normalizedName.endsWith(ext));
  };

  const isFolderNode = (node: Node) => node.nodeType === 'FOLDER' && !isDocumentNode(node);

  const hideNodeFromResults = (nodeId: string) => {
    setHiddenNodeIds((prev) => (prev.includes(nodeId) ? prev : [...prev, nodeId]));
  };

  const handlePreviewNode = async (node: Node, options?: { annotate?: boolean }) => {
    try {
      setPreviewAnnotate(Boolean(options?.annotate));
      const freshNode = await nodeService.getNode(node.id);
      if (isFolderNode(freshNode)) {
        navigate(`/browse/${freshNode.id}`);
        if (sidebarAutoCollapse) {
          dispatch(setSidebarOpen(false));
        }
      } else {
        setPreviewNode(freshNode);
      }
    } catch (error: any) {
      const status = error?.response?.status;
      if (status === 404 || status === 410) {
        hideNodeFromResults(node.id);
        toast.info('This item is no longer available and was removed from search results.');
      }
    }
  };

  const handleClosePreview = () => {
    setPreviewNode(null);
    setPreviewAnnotate(false);
  };

  const handleEditOnline = (node: Node, permission: 'read' | 'write') => {
    navigate(`/editor/${node.id}?provider=wopi&permission=${permission}`);
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

  const handleFindSimilar = async (node: Node) => {
    if (!isDocumentNode(node)) {
      return;
    }
    setSimilarLoadingId(node.id);
    setSimilarError(null);
    try {
      const results = await nodeService.findSimilar(node.id, pageSize);
      setSimilarResults(results);
      setSimilarSource({ id: node.id, name: node.name });
      setPage(1);
      if (!results || results.length === 0) {
        toast.info('No similar documents found');
      }
    } catch (error: any) {
      const message = error?.response?.data?.message || 'Failed to load similar documents';
      setSimilarError(message);
      toast.error(message);
    } finally {
      setSimilarLoadingId(null);
    }
  };

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const applySuggestedFilter = (filter: { field: string; value: string }) => {
    if (!lastSearchCriteria) {
      return;
    }
    if (isSimilarMode) {
      clearSimilarResults();
    }
    const sortParams = getSortParams(sortBy);
    const baseCriteria: SearchCriteria = {
      ...lastSearchCriteria,
      page: 0,
      size: pageSize,
      ...sortParams,
    };

    if (filter.field === 'mimeType') {
      const nextMimeTypes = Array.from(
        new Set([...(baseCriteria.mimeTypes || []), filter.value])
      );
      runSearch({
        ...baseCriteria,
        mimeTypes: nextMimeTypes,
        contentType: undefined,
      });
      return;
    }

    if (filter.field === 'createdBy') {
      const nextCreators = Array.from(
        new Set([...(baseCriteria.createdByList || []), filter.value])
      );
      runSearch({
        ...baseCriteria,
        createdByList: nextCreators,
        createdBy: undefined,
      });
      return;
    }

    if (filter.field === 'dateRange') {
      const now = new Date();
      let fromDate: Date | null = null;
      if (filter.value === '7d') {
        fromDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      } else if (filter.value === '30d') {
        fromDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      } else if (filter.value === '1y') {
        fromDate = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
      }
      runSearch({
        ...baseCriteria,
        createdFrom: fromDate ? fromDate.toISOString() : baseCriteria.createdFrom,
      });
    }
  };

  const getVisualLength = (value?: string) => {
    if (!value) return 0;
    let total = 0;
    for (const char of value) {
      total += char.charCodeAt(0) > 0xff ? 2 : 1;
    }
    return total;
  };

  const getFileIcon = (node: Node) => {
    if (isFolderNode(node)) {
      return <Folder sx={{ fontSize: 48, color: 'primary.main' }} />;
    }
    return <InsertDriveFile sx={{ fontSize: 48, color: 'text.secondary' }} />;
  };

  const getNameTypographySx = (name: string) => {
    const length = getVisualLength(name);
    const isLong = length > 32;
    const isExtraLong = length > 56;
    const isVeryLong = length > 80;
    const lineClamp = isLong ? 3 : 2;
    const fontSize = lineClamp === 3
      ? (isVeryLong ? '0.8rem' : isExtraLong ? '0.86rem' : '0.9rem')
      : undefined;
    const lineHeight = lineClamp === 3 ? (isVeryLong ? 1.05 : isExtraLong ? 1.1 : 1.16) : 1.3;

    return {
      display: '-webkit-box',
      WebkitLineClamp: lineClamp,
      WebkitBoxOrient: 'vertical',
      overflow: 'hidden',
      wordBreak: 'break-word',
      overflowWrap: 'anywhere',
      lineHeight,
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

  const getPreviewStatusChip = (node: Node) => {
    if (node.nodeType === 'FOLDER') {
      return null;
    }
    const status = node.previewStatus?.toUpperCase();
    const normalized = status || 'PENDING';
    const label = normalized === 'READY'
      ? 'Preview ready'
      : normalized === 'FAILED'
        ? 'Preview failed'
        : normalized === 'PROCESSING'
          ? 'Preview processing'
          : normalized === 'QUEUED'
            ? 'Preview queued'
            : 'Preview pending';
    const color = normalized === 'READY'
      ? 'success'
      : normalized === 'FAILED'
        ? 'error'
        : normalized === 'PROCESSING'
          ? 'warning'
          : normalized === 'QUEUED'
            ? 'info'
            : 'default';
    return (
      <Tooltip
        title={node.previewFailureReason || ''}
        placement="top-start"
        arrow
        disableHoverListener={!node.previewFailureReason}
      >
        <Chip label={label} size="small" variant="outlined" color={color} />
      </Tooltip>
    );
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
    selectedCategories.length > 0 ||
    selectedPreviewStatuses.length > 0;

  const hasActiveCriteria = isSimilarMode
    || Boolean((lastSearchCriteria?.name || '').trim())
    || filtersApplied;
  const shouldShowFallback = !isSimilarMode && !loading && nodes.length === 0 && fallbackNodes.length > 0 && hasActiveCriteria;
  const displayNodes = isSimilarMode ? (similarResults || []) : (shouldShowFallback ? fallbackNodes : nodes);
  const previewStatusCounts = displayNodes.reduce(
    (acc, node) => {
      if (node.nodeType === 'FOLDER') {
        acc.folders += 1;
        return acc;
      }
      const status = node.previewStatus?.toUpperCase() || 'PENDING';
      if (status in acc) {
        acc[status as keyof typeof acc] += 1;
      } else {
        acc.other += 1;
      }
      return acc;
    },
    {
      READY: 0,
      PROCESSING: 0,
      QUEUED: 0,
      FAILED: 0,
      PENDING: 0,
      other: 0,
      folders: 0,
    }
  );
  const previewStatusFilterApplied = selectedPreviewStatuses.length > 0;
  const statusFilteredNodes = previewStatusFilterApplied
    ? displayNodes.filter((node) => {
        if (node.nodeType === 'FOLDER') {
          return false;
        }
        const status = node.previewStatus?.toUpperCase() || 'PENDING';
        return selectedPreviewStatuses.includes(status);
      })
    : displayNodes;
  const paginatedNodes = statusFilteredNodes.filter((node) => !hiddenNodeIds.includes(node.id));
  const displayTotal = isSimilarMode
    ? paginatedNodes.length
    : previewStatusFilterApplied
      ? statusFilteredNodes.length
      : nodesTotal;
  const totalPages = Math.max(1, Math.ceil(displayTotal / pageSize));
  const spellcheckQuery = (lastSearchCriteria?.name || '').trim();
  const normalizedSpellcheckQuery = spellcheckQuery.toLowerCase();
  const filteredSpellcheckSuggestions = useMemo(() => {
    const unique = new Set<string>();
    for (const suggestion of spellcheckSuggestions) {
      const trimmed = suggestion.trim();
      if (!trimmed) continue;
      if (trimmed.toLowerCase() === normalizedSpellcheckQuery) continue;
      if (!unique.has(trimmed)) {
        unique.add(trimmed);
      }
    }
    return Array.from(unique);
  }, [spellcheckSuggestions, normalizedSpellcheckQuery]);
  const primarySpellcheckSuggestion = filteredSpellcheckSuggestions[0];
  const secondarySpellcheckSuggestions = filteredSpellcheckSuggestions.slice(1);
  const showSpellcheckSuggestions = !spellcheckLoading && filteredSpellcheckSuggestions.length > 0;

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 3 }}>
        <form onSubmit={handleQuickSearch}>
          <Box display="flex" alignItems="center" gap={2}>
            <TextField
              fullWidth
              placeholder="Quick search by name..."
              name="quickSearch"
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

      {spellcheckLoading && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Checking spelling suggestions…
        </Alert>
      )}
      {spellcheckError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {spellcheckError}
        </Alert>
      )}
      {showSpellcheckSuggestions && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <Box display="flex" alignItems="center" flexWrap="wrap" gap={0.5}>
            {spellcheckQuery && (
              <Typography variant="body2" color="text.secondary">
                {displayTotal > 0
                  ? `Showing results for "${spellcheckQuery}".`
                  : `No results for "${spellcheckQuery}".`}
              </Typography>
            )}
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              Did you mean
            </Typography>
            {primarySpellcheckSuggestion && (
              <Button
                size="small"
                onClick={() => handleSpellcheckSuggestion(primarySpellcheckSuggestion)}
                sx={{ textTransform: 'none', ml: 0.5 }}
              >
                {primarySpellcheckSuggestion}
              </Button>
            )}
            {secondarySpellcheckSuggestions.length > 0 && (
              <>
                <Typography variant="body2" color="text.secondary">
                  or
                </Typography>
                {secondarySpellcheckSuggestions.map((suggestion) => (
                  <Button
                    key={suggestion}
                    size="small"
                    onClick={() => handleSpellcheckSuggestion(suggestion)}
                    sx={{ textTransform: 'none', ml: 0.5 }}
                  >
                    {suggestion}
                  </Button>
                ))}
              </>
            )}
          </Box>
        </Alert>
      )}

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
              Preview Status
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
              Filter results by preview generation state.
            </Typography>
            <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
              {[
                { value: 'READY', label: 'Ready', color: 'success' as const, count: previewStatusCounts.READY },
                { value: 'PROCESSING', label: 'Processing', color: 'warning' as const, count: previewStatusCounts.PROCESSING },
                { value: 'QUEUED', label: 'Queued', color: 'info' as const, count: previewStatusCounts.QUEUED },
                { value: 'FAILED', label: 'Failed', color: 'error' as const, count: previewStatusCounts.FAILED },
                { value: 'PENDING', label: 'Pending', color: 'default' as const, count: previewStatusCounts.PENDING },
              ].map((status) => (
                <Chip
                  key={status.value}
                  label={`${status.label} (${status.count})`}
                  size="small"
                  color={selectedPreviewStatuses.includes(status.value) ? status.color : 'default'}
                  variant={selectedPreviewStatuses.includes(status.value) ? 'filled' : 'outlined'}
                  onClick={() => togglePreviewStatus(status.value)}
                />
              ))}
              <Button size="small" disabled={selectedPreviewStatuses.length === 0} onClick={() => setSelectedPreviewStatuses([])}>
                Clear
              </Button>
            </Box>

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
          {similarError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {similarError}
            </Alert>
          )}
          {isSimilarMode && (
            <Alert
              severity="info"
              sx={{ mb: 2 }}
              action={(
                <Button color="inherit" size="small" onClick={clearSimilarResults}>
                  Back to results
                </Button>
              )}
            >
              {similarSource?.name
                ? `Showing documents similar to "${similarSource.name}".`
                : 'Showing similar documents.'}
            </Alert>
          )}
          {shouldShowFallback && (
            <Alert
              severity="info"
              sx={{ mb: 2 }}
              action={(
                <Button color="inherit" size="small" onClick={handleRetrySearch}>
                  Retry
                </Button>
              )}
            >
              {fallbackLabel
                ? `Search results may still be indexing. Showing previous results for "${fallbackLabel}".`
                : 'Search results may still be indexing. Showing previous results.'}
            </Alert>
          )}
          {suggestedFiltersError && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {suggestedFiltersError}
            </Alert>
          )}
          {suggestedFiltersLoading && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Loading suggested filters…
            </Alert>
          )}
          {!suggestedFiltersLoading && suggestedFilters.length > 0 && (
            <Paper sx={{ p: 1.5, mb: 2 }}>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                <Typography variant="subtitle2" color="text.secondary">
                  Suggested:
                </Typography>
                {suggestedFilters.map((filter, index) => (
                  <Chip
                    key={`${filter.field}-${filter.value}-${index}`}
                    label={filter.count !== undefined ? `${filter.label} (${filter.count})` : filter.label}
                    size="small"
                    onClick={() => applySuggestedFilter(filter)}
                    variant="outlined"
                  />
                ))}
              </Box>
            </Paper>
          )}
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">
              {loading
                ? 'Searching...'
                : isSimilarMode
                  ? (paginatedNodes.length > 0
                    ? `Showing ${paginatedNodes.length} similar result${paginatedNodes.length === 1 ? '' : 's'}`
                    : '0 similar results found')
                  : shouldShowFallback
                    ? `Showing previous results (${paginatedNodes.length}) while the index refreshes`
                    : displayTotal > 0
                      ? `Showing ${paginatedNodes.length} of ${displayTotal} results`
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
              {selectedPreviewStatuses.map((value) => (
                <Chip
                  key={`preview-${value}`}
                  label={`Preview: ${value.toLowerCase()}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedPreviewStatuses, value)}
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
          ) : paginatedNodes.length === 0 ? (
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
                  <Grid item xs={12} sm={6} md={4} lg={3} xl={2} key={node.id}>
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
                              {getPreviewStatusChip(node)}
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
                        <Button size="small" startIcon={<Visibility />} onClick={() => void handlePreviewNode(node)}>
                          View
                        </Button>
                        {isDocumentNode(node) && isPdfDocument(node) && canWrite && (
                          <Button size="small" startIcon={<Edit />} onClick={() => void handlePreviewNode(node, { annotate: true })}>
                            Annotate (PDF)
                          </Button>
                        )}
                        {isDocumentNode(node) && isOfficeDocument(node) && !isPdfDocument(node) && (
                          <Button
                            size="small"
                            startIcon={canWrite ? <Edit /> : <Visibility />}
                            onClick={() => handleEditOnline(node, canWrite ? 'write' : 'read')}
                          >
                            {canWrite ? 'Edit Online' : 'View Online'}
                          </Button>
                        )}
                        {isDocumentNode(node) && (
                          <Button size="small" startIcon={<Download />} onClick={() => handleDownload(node)}>
                            Download
                          </Button>
                        )}
                        {isDocumentNode(node) && (
                          <Button
                            size="small"
                            startIcon={<AutoAwesome />}
                            onClick={() => handleFindSimilar(node)}
                            disabled={similarLoadingId === node.id}
                          >
                            {similarLoadingId === node.id ? 'Finding...' : 'More like this'}
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
                        runSearch({ ...lastSearchCriteria, page: value - 1, size: pageSize, ...sortParams });
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
            initialAnnotateMode={previewAnnotate}
          />
        </React.Suspense>
      )}
    </Box>
  );
};

export default SearchResults;
