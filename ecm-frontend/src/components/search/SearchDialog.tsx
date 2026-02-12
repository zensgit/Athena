import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Grid,
  Autocomplete,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  IconButton,
  Typography,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  FormControlLabel,
  Checkbox,
} from '@mui/material';
import {
  Close,
  Search,
  ExpandMore,
  Save,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { SearchCriteria } from 'types';
import { useAppDispatch, useAppSelector } from 'store';
import { setSearchOpen, setSearchPrefill } from 'store/slices/uiSlice';
import { searchNodes } from 'store/slices/nodeSlice';
import { useNavigate } from 'react-router-dom';
import apiService from 'services/api';
import savedSearchService, { SavedSearch } from 'services/savedSearchService';
import { toast } from 'react-toastify';

const CONTENT_TYPES = [
  { value: 'application/pdf', label: 'PDF' },
  { value: 'application/msword', label: 'Word' },
  { value: 'application/vnd.ms-excel', label: 'Excel' },
  { value: 'application/vnd.ms-powerpoint', label: 'PowerPoint' },
  { value: 'text/plain', label: 'Text' },
  { value: 'image/jpeg', label: 'JPEG' },
  { value: 'image/png', label: 'PNG' },
];

const ASPECTS = [
  { value: 'cm:versionable', label: 'Versionable' },
  { value: 'cm:auditable', label: 'Auditable' },
  { value: 'cm:taggable', label: 'Taggable' },
  { value: 'cm:classifiable', label: 'Classifiable' },
];

const PREVIEW_STATUS_OPTIONS = [
  { value: 'READY', label: 'Ready' },
  { value: 'PROCESSING', label: 'Processing' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'UNSUPPORTED', label: 'Unsupported' },
  { value: 'PENDING', label: 'Pending' },
];

const SearchDialog: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { searchOpen, searchPrefill } = useAppSelector((state) => state.ui);
  
  const [searchCriteria, setSearchCriteria] = useState<SearchCriteria>({
    name: '',
    properties: {},
    aspects: [],
    contentType: '',
    createdBy: '',
  });
  
  const [customProperties, setCustomProperties] = useState<{ key: string; value: string }[]>([]);
  const [newPropertyKey, setNewPropertyKey] = useState('');
  const [newPropertyValue, setNewPropertyValue] = useState('');
  const [expandedSection, setExpandedSection] = useState<string | false>('basic');

  const [tags, setTags] = useState<string[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [correspondents, setCorrespondents] = useState<string[]>([]);
  const [selectedPreviewStatuses, setSelectedPreviewStatuses] = useState<string[]>([]);
  const [minSize, setMinSize] = useState<number | undefined>();
  const [maxSize, setMaxSize] = useState<number | undefined>();
  const [pathPrefix, setPathPrefix] = useState<string>('');
  const [scopeFolderId, setScopeFolderId] = useState<string>('');
  const [scopeIncludeChildren, setScopeIncludeChildren] = useState(true);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [saveName, setSaveName] = useState('');
  const [saveMode, setSaveMode] = useState<'create' | 'update'>('create');
  const [saveExistingId, setSaveExistingId] = useState('');
  const [saveDialogLoading, setSaveDialogLoading] = useState(false);
  const [saveDialogSubmitting, setSaveDialogSubmitting] = useState(false);
  const [savedSearchOptions, setSavedSearchOptions] = useState<SavedSearch[]>([]);
  const [nameSuggestions, setNameSuggestions] = useState<string[]>([]);
  const [nameSuggestionsLoading, setNameSuggestionsLoading] = useState(false);
  const [facetOptions, setFacetOptions] = useState<{
    tags: string[];
    categories: string[];
    correspondents: string[];
    mimeTypes: string[];
    createdBy: string[];
  }>({
    tags: [],
    categories: [],
    correspondents: [],
    mimeTypes: [],
    createdBy: [],
  });

  const normalizeList = (items: string[]) =>
    items.map((item) => item.trim()).filter((item) => item.length > 0);

  const loadFacets = async () => {
    try {
      const res = await apiService.get<any>('/search/facets', { params: { q: '' } });
      setFacetOptions({
        tags: res.tags?.map((f: any) => f.value) || [],
        categories: res.categories?.map((f: any) => f.value) || [],
        correspondents: res.correspondent?.map((f: any) => f.value) || [],
        mimeTypes: res.mimeType?.map((f: any) => f.value) || [],
        createdBy: res.createdBy?.map((f: any) => f.value) || [],
      });
    } catch {
      // ignore facet load failures
    }
  };

  React.useEffect(() => {
    if (searchOpen) {
      loadFacets();
    }
  }, [searchOpen]);

  React.useEffect(() => {
    if (!searchOpen) {
      return;
    }
    const prefix = (searchCriteria.name || '').trim();
    if (prefix.length < 2) {
      setNameSuggestions([]);
      setNameSuggestionsLoading(false);
      return;
    }

    let active = true;
    const timer = window.setTimeout(async () => {
      setNameSuggestionsLoading(true);
      try {
        const suggestions = await apiService.get<string[]>('/search/suggestions', {
          params: { prefix, limit: 8 },
        });
        if (active) {
          setNameSuggestions(Array.isArray(suggestions) ? suggestions : []);
        }
      } catch {
        if (active) {
          setNameSuggestions([]);
        }
      } finally {
        if (active) {
          setNameSuggestionsLoading(false);
        }
      }
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [searchCriteria.name, searchOpen]);

  React.useEffect(() => {
    if (!searchOpen || !searchPrefill) {
      return;
    }

    const prefilledProperties = Object.entries(searchPrefill.properties || {})
      .filter(([key, value]) => key.trim().length > 0 && value !== undefined && value !== null)
      .map(([key, value]) => ({ key, value: String(value) }));
    const hasBasicPrefill = Boolean(
      searchPrefill.name
      || searchPrefill.contentType
      || searchPrefill.createdBy
      || (searchPrefill.previewStatuses && searchPrefill.previewStatuses.length > 0)
      || (searchPrefill.folderId && searchPrefill.folderId.trim().length > 0)
    );
    const hasDatePrefill = Boolean(
      searchPrefill.createdFrom
      || searchPrefill.createdTo
      || searchPrefill.modifiedFrom
      || searchPrefill.modifiedTo
    );
    const hasAspectsPrefill = Boolean(searchPrefill.aspects && searchPrefill.aspects.length > 0);
    const hasPropertiesPrefill = prefilledProperties.length > 0;
    const hasMetaPrefill = Boolean(
      (searchPrefill.tags && searchPrefill.tags.length > 0)
      || (searchPrefill.categories && searchPrefill.categories.length > 0)
      || (searchPrefill.correspondents && searchPrefill.correspondents.length > 0)
      || searchPrefill.minSize !== undefined
      || searchPrefill.maxSize !== undefined
      || (searchPrefill.pathPrefix && searchPrefill.pathPrefix.length > 0)
    );

    let nextExpandedSection: string | false = 'basic';
    if (!hasBasicPrefill) {
      if (hasDatePrefill) {
        nextExpandedSection = 'dates';
      } else if (hasAspectsPrefill) {
        nextExpandedSection = 'aspects';
      } else if (hasPropertiesPrefill) {
        nextExpandedSection = 'properties';
      } else if (hasMetaPrefill) {
        nextExpandedSection = 'meta';
      }
    }

    // Start from a clean slate before applying prefill values.
    setSearchCriteria({
      name: searchPrefill.name || '',
      properties: searchPrefill.properties || {},
      aspects: searchPrefill.aspects || [],
      contentType: searchPrefill.contentType || '',
      createdBy: searchPrefill.createdBy || '',
      createdFrom: searchPrefill.createdFrom,
      createdTo: searchPrefill.createdTo,
      modifiedFrom: searchPrefill.modifiedFrom,
      modifiedTo: searchPrefill.modifiedTo,
    });
    setCustomProperties(prefilledProperties);
    setNewPropertyKey('');
    setNewPropertyValue('');
    setExpandedSection(nextExpandedSection);
    setTags(searchPrefill.tags || []);
    setCategories(searchPrefill.categories || []);
    setCorrespondents(searchPrefill.correspondents || []);
    setSelectedPreviewStatuses(searchPrefill.previewStatuses || []);
    setMinSize(searchPrefill.minSize);
    setMaxSize(searchPrefill.maxSize);
    setPathPrefix(searchPrefill.pathPrefix || '');
    setScopeFolderId(searchPrefill.folderId || '');
    setScopeIncludeChildren(searchPrefill.includeChildren ?? true);
    setSaveDialogOpen(false);
    setSaveName('');

    dispatch(setSearchPrefill(null));
  }, [searchOpen, searchPrefill, dispatch]);

  const handleClose = () => {
    dispatch(setSearchOpen(false));
    resetForm();
  };

  const closeSaveDialog = (options?: { force?: boolean }) => {
    if (saveDialogSubmitting && !options?.force) {
      return;
    }
    setSaveDialogOpen(false);
    setSaveName('');
    setSaveMode('create');
    setSaveExistingId('');
    setSaveDialogLoading(false);
    setSaveDialogSubmitting(false);
  };

  const handleCloseSaveDialog = () => closeSaveDialog();

  const openSaveDialog = async () => {
    setSaveDialogOpen(true);
    setSaveDialogLoading(true);
    setSaveMode('create');
    setSaveExistingId('');
    try {
      const searches = await savedSearchService.list();
      setSavedSearchOptions(searches);
    } catch {
      setSavedSearchOptions([]);
      toast.error('Failed to load saved searches');
    } finally {
      setSaveDialogLoading(false);
    }
  };

  const handleSaveModeChange = (nextMode: 'create' | 'update') => {
    setSaveMode(nextMode);
    if (nextMode === 'update') {
      const first = savedSearchOptions[0];
      if (first) {
        setSaveExistingId(first.id);
        setSaveName(first.name || '');
      } else {
        setSaveExistingId('');
      }
      return;
    }
    setSaveExistingId('');
    setSaveName('');
  };

  const handleUpdateTargetChange = (id: string) => {
    setSaveExistingId(id);
    const selected = savedSearchOptions.find((item) => item.id === id);
    if (selected) {
      setSaveName(selected.name || '');
    }
  };

  const resetForm = () => {
    setSearchCriteria({
      name: '',
      properties: {},
      aspects: [],
      contentType: '',
      createdBy: '',
    });
    setCustomProperties([]);
    setNewPropertyKey('');
    setNewPropertyValue('');
    setExpandedSection('basic');
    setTags([]);
    setCategories([]);
    setCorrespondents([]);
    setSelectedPreviewStatuses([]);
    setMinSize(undefined);
    setMaxSize(undefined);
    setPathPrefix('');
    setScopeFolderId('');
    setScopeIncludeChildren(true);
    closeSaveDialog({ force: true });
    setNameSuggestions([]);
    setNameSuggestionsLoading(false);
  };

  const buildSavedSearchQueryParams = () => {
    const query = (searchCriteria.name || '').trim();
    const filters: Record<string, any> = {};
    const normalizedTags = normalizeList(tags);
    const normalizedCategories = normalizeList(categories);
    const normalizedCorrespondents = normalizeList(correspondents);
    const normalizedProperties = customProperties.reduce((acc, prop) => {
      const key = prop.key.trim();
      if (!key || prop.value === undefined || prop.value === null || prop.value === '') {
        return acc;
      }
      acc[key] = prop.value;
      return acc;
    }, {} as Record<string, string>);

    if (searchCriteria.contentType) {
      filters.mimeTypes = [searchCriteria.contentType];
    }
    if (searchCriteria.aspects && searchCriteria.aspects.length > 0) {
      filters.aspects = searchCriteria.aspects;
    }
    if (searchCriteria.createdBy) {
      filters.createdBy = searchCriteria.createdBy;
    }
    if (Object.keys(normalizedProperties).length > 0) {
      filters.properties = normalizedProperties;
    }
    if (normalizedTags.length) {
      filters.tags = normalizedTags;
    }
    if (normalizedCategories.length) {
      filters.categories = normalizedCategories;
    }
    if (normalizedCorrespondents.length) {
      filters.correspondents = normalizedCorrespondents;
    }
    if (selectedPreviewStatuses.length > 0) {
      filters.previewStatuses = selectedPreviewStatuses;
    }
    if (minSize !== undefined) {
      filters.minSize = minSize;
    }
    if (maxSize !== undefined) {
      filters.maxSize = maxSize;
    }
    if (searchCriteria.createdFrom) {
      filters.dateFrom = searchCriteria.createdFrom;
    }
    if (searchCriteria.createdTo) {
      filters.dateTo = searchCriteria.createdTo;
    }
    if (searchCriteria.modifiedFrom) {
      filters.modifiedFrom = searchCriteria.modifiedFrom;
    }
    if (searchCriteria.modifiedTo) {
      filters.modifiedTo = searchCriteria.modifiedTo;
    }
    if (scopeFolderId.trim()) {
      filters.folderId = scopeFolderId.trim();
      filters.includeChildren = scopeIncludeChildren;
    } else if (pathPrefix.length > 0) {
      filters.path = pathPrefix;
    }

    return {
      query,
      filters,
      highlightEnabled: true,
      facetFields: ['mimeType', 'createdBy', 'tags', 'categories', 'correspondent'],
      pageable: { page: 0, size: 50 },
    };
  };

  const handleSaveSearch = async () => {
    const trimmed = saveName.trim();
    if (!trimmed) {
      toast.error('Please enter a saved search name');
      return;
    }
    if (saveMode === 'update' && !saveExistingId) {
      toast.error('Please choose a saved search to update');
      return;
    }

    setSaveDialogSubmitting(true);
    try {
      const queryParams = buildSavedSearchQueryParams();
      if (saveMode === 'update') {
        await savedSearchService.update(saveExistingId, {
          name: trimmed,
          queryParams,
        });
        toast.success('Saved search updated');
      } else {
        await savedSearchService.save(trimmed, queryParams);
        toast.success('Saved search created');
      }
      closeSaveDialog({ force: true });
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to save search';
      toast.error(message);
    } finally {
      setSaveDialogSubmitting(false);
    }
  };

  const handleSearch = async () => {
    const normalizedTags = normalizeList(tags);
    const normalizedCategories = normalizeList(categories);
    const normalizedCorrespondents = normalizeList(correspondents);

    const criteria: SearchCriteria = {
      ...searchCriteria,
      properties: customProperties.reduce((acc, prop) => {
        acc[prop.key] = prop.value;
        return acc;
      }, {} as Record<string, any>),
      tags: normalizedTags,
      categories: normalizedCategories,
      correspondents: normalizedCorrespondents,
      previewStatuses: selectedPreviewStatuses,
      minSize,
      maxSize,
      path: scopeFolderId.trim() ? undefined : (pathPrefix || undefined),
      folderId: scopeFolderId.trim() || undefined,
      includeChildren: scopeIncludeChildren,
      page: 0,
      size: 20,
    };

    try {
      await dispatch(searchNodes(criteria)).unwrap();
      navigate('/search-results');
      handleClose();
    } catch (err: any) {
      const message = err?.message || err?.response?.data?.message || 'Search failed';
      toast.error(message);
    }
  };

  const handleAddProperty = () => {
    if (newPropertyKey && newPropertyValue) {
      setCustomProperties([...customProperties, { key: newPropertyKey, value: newPropertyValue }]);
      setNewPropertyKey('');
      setNewPropertyValue('');
    }
  };

  const handleRemoveProperty = (index: number) => {
    setCustomProperties(customProperties.filter((_, i) => i !== index));
  };

  const handleAspectToggle = (aspect: string) => {
    setSearchCriteria((prev) => ({
      ...prev,
      aspects: prev.aspects?.includes(aspect)
        ? prev.aspects.filter((a) => a !== aspect)
        : [...(prev.aspects || []), aspect],
    }));
  };

  const activeCriteriaChips = React.useMemo(() => {
    const chips: Array<{ key: string; label: string }> = [];
    const aspectLabelByValue = new Map(ASPECTS.map((item) => [item.value, item.label]));
    const previewLabelByValue = new Map(PREVIEW_STATUS_OPTIONS.map((item) => [item.value, item.label]));
    const asDateLabel = (value?: string) => (value ? value.slice(0, 10) : '');

    if (searchCriteria.name?.trim()) {
      chips.push({ key: 'name', label: `Name: ${searchCriteria.name.trim()}` });
    }
    if (searchCriteria.contentType?.trim()) {
      chips.push({ key: 'contentType', label: `Type: ${searchCriteria.contentType.trim()}` });
    }
    if (searchCriteria.createdBy?.trim()) {
      chips.push({ key: 'createdBy', label: `Creator: ${searchCriteria.createdBy.trim()}` });
    }
    if (searchCriteria.createdFrom || searchCriteria.createdTo) {
      chips.push({
        key: 'createdDate',
        label: `Created: ${asDateLabel(searchCriteria.createdFrom) || '...'} -> ${asDateLabel(searchCriteria.createdTo) || '...'}`,
      });
    }
    if (searchCriteria.modifiedFrom || searchCriteria.modifiedTo) {
      chips.push({
        key: 'modifiedDate',
        label: `Modified: ${asDateLabel(searchCriteria.modifiedFrom) || '...'} -> ${asDateLabel(searchCriteria.modifiedTo) || '...'}`,
      });
    }
    (searchCriteria.aspects || []).forEach((aspect) => {
      chips.push({ key: `aspect-${aspect}`, label: `Aspect: ${aspectLabelByValue.get(aspect) || aspect}` });
    });
    customProperties.forEach((item, index) => {
      chips.push({
        key: `property-${index}`,
        label: `Property: ${item.key}=${item.value}`,
      });
    });
    tags.forEach((tag) => chips.push({ key: `tag-${tag}`, label: `Tag: ${tag}` }));
    categories.forEach((category) => chips.push({ key: `category-${category}`, label: `Category: ${category}` }));
    correspondents.forEach((correspondent) =>
      chips.push({ key: `correspondent-${correspondent}`, label: `Correspondent: ${correspondent}` })
    );
    selectedPreviewStatuses.forEach((status) =>
      chips.push({ key: `preview-${status}`, label: `Preview: ${previewLabelByValue.get(status) || status}` })
    );
    if (minSize !== undefined || maxSize !== undefined) {
      chips.push({
        key: 'sizeRange',
        label: `Size: ${minSize !== undefined ? minSize : '...'} - ${maxSize !== undefined ? maxSize : '...'} bytes`,
      });
    }
    if (scopeFolderId.trim().length > 0) {
      chips.push({
        key: 'scopeFolder',
        label: `Scope: folder (${scopeIncludeChildren ? 'include children' : 'this folder only'})`,
      });
    } else if (pathPrefix.trim().length > 0) {
      chips.push({ key: 'pathPrefix', label: `Path: ${pathPrefix.trim()}` });
    }

    return chips;
  }, [
    searchCriteria.name,
    searchCriteria.contentType,
    searchCriteria.createdBy,
    searchCriteria.createdFrom,
    searchCriteria.createdTo,
    searchCriteria.modifiedFrom,
    searchCriteria.modifiedTo,
    searchCriteria.aspects,
    customProperties,
    tags,
    categories,
    correspondents,
    selectedPreviewStatuses,
    minSize,
    maxSize,
    scopeFolderId,
    scopeIncludeChildren,
    pathPrefix,
  ]);

  const contentTypeOptions = React.useMemo(() => {
    const baseValues = facetOptions.mimeTypes.length > 0
      ? facetOptions.mimeTypes
      : CONTENT_TYPES.map((item) => item.value);
    const selected = (searchCriteria.contentType || '').trim();
    return selected && !baseValues.includes(selected)
      ? [...baseValues, selected]
      : baseValues;
  }, [facetOptions.mimeTypes, searchCriteria.contentType]);

  const createdByOptions = React.useMemo(() => {
    const baseValues = facetOptions.createdBy;
    const selected = (searchCriteria.createdBy || '').trim();
    return selected && !baseValues.includes(selected)
      ? [selected, ...baseValues]
      : baseValues;
  }, [facetOptions.createdBy, searchCriteria.createdBy]);

  const contentTypeLabelByValue = React.useMemo(
    () => new Map(CONTENT_TYPES.map((item) => [item.value, item.label])),
    []
  );

  const isSearchValid = () => {
    return (
      searchCriteria.name ||
      searchCriteria.contentType ||
      searchCriteria.createdBy ||
      (searchCriteria.aspects && searchCriteria.aspects.length > 0) ||
      customProperties.length > 0 ||
      searchCriteria.createdFrom ||
      searchCriteria.createdTo ||
      searchCriteria.modifiedFrom ||
      searchCriteria.modifiedTo ||
      tags.length > 0 ||
      categories.length > 0 ||
      correspondents.length > 0 ||
      selectedPreviewStatuses.length > 0 ||
      minSize !== undefined ||
      maxSize !== undefined ||
      pathPrefix.length > 0 ||
      scopeFolderId.trim().length > 0
    );
  };

  const canSubmitSaveDialog = saveName.trim().length > 0
    && (saveMode === 'create' || saveExistingId.length > 0)
    && !saveDialogSubmitting;
  const canSubmitSearch = isSearchValid();

  return (
    <>
      <Dialog
        open={searchOpen}
        onClose={handleClose}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          Advanced Search
          <IconButton
            aria-label="close"
            onClick={handleClose}
            sx={{ position: 'absolute', right: 8, top: 8 }}
          >
            <Close />
          </IconButton>
        </DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 1 }}>
            <Accordion
              expanded={expandedSection === 'basic'}
              onChange={(_, isExpanded) => setExpandedSection(isExpanded ? 'basic' : false)}
            >
              <AccordionSummary expandIcon={<ExpandMore />}>
                <Typography>Basic Search</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <Grid container spacing={2}>
                  <Grid item xs={12}>
                    <Autocomplete
                      freeSolo
                      options={nameSuggestions}
                      value={searchCriteria.name || ''}
                      inputValue={searchCriteria.name || ''}
                      onChange={(_, value) => {
                        setSearchCriteria({ ...searchCriteria, name: value || '' });
                      }}
                      onInputChange={(_, value) => {
                        setSearchCriteria({ ...searchCriteria, name: value });
                      }}
                      loading={nameSuggestionsLoading}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          fullWidth
                          label="Name contains"
                          placeholder="Enter partial or full name"
                          InputProps={{
                            ...params.InputProps,
                            endAdornment: (
                              <>
                                {nameSuggestionsLoading ? (
                                  <CircularProgress color="inherit" size={18} />
                                ) : null}
                                {params.InputProps.endAdornment}
                              </>
                            ),
                          }}
                        />
                      )}
                    />
                  </Grid>
                  {scopeFolderId.trim().length > 0 && (
                    <Grid item xs={12}>
                      <Box display="flex" flexWrap="wrap" alignItems="center" gap={1}>
                        <Chip
                          label="Scope: This folder"
                          onDelete={() => setScopeFolderId('')}
                          size="small"
                        />
                        <FormControlLabel
                          control={(
                            <Checkbox
                              checked={scopeIncludeChildren}
                              onChange={(event) => setScopeIncludeChildren(event.target.checked)}
                            />
                          )}
                          label="Include subfolders"
                        />
                      </Box>
                    </Grid>
                  )}
                  <Grid item xs={12}>
                    <FormControl fullWidth>
                      <InputLabel>Content Type</InputLabel>
                      <Select
                        value={searchCriteria.contentType || ''}
                        onChange={(e) => {
                          setSearchCriteria({ ...searchCriteria, contentType: e.target.value as string });
                        }}
                        label="Content Type"
                      >
                        <MenuItem value="">All Types</MenuItem>
                        {contentTypeOptions.map((mimeType) => (
                          <MenuItem key={mimeType} value={mimeType}>
                            {facetOptions.mimeTypes.length > 0
                              ? mimeType
                              : (contentTypeLabelByValue.get(mimeType) || mimeType)}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12}>
                    <FormControl fullWidth>
                      <InputLabel>Created By</InputLabel>
                      <Select
                        value={searchCriteria.createdBy || ''}
                        label="Created By"
                        onChange={(e) =>
                          setSearchCriteria({
                            ...searchCriteria,
                            createdBy: e.target.value as string,
                          })
                        }
                      >
                        <MenuItem value="">Any creator</MenuItem>
                        {createdByOptions.map((u) => (
                          <MenuItem key={u} value={u}>
                            {u}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={12}>
                    <FormControl fullWidth>
                      <InputLabel id="preview-status-label">Preview Status</InputLabel>
                      <Select
                        labelId="preview-status-label"
                        multiple
                        value={selectedPreviewStatuses}
                        label="Preview Status"
                        onChange={(e) => {
                          const value = e.target.value;
                          setSelectedPreviewStatuses(typeof value === 'string' ? value.split(',') : value);
                        }}
                        renderValue={(selected) => (
                          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                            {(selected as string[]).map((value) => (
                              <Chip
                                key={value}
                                size="small"
                                label={PREVIEW_STATUS_OPTIONS.find((item) => item.value === value)?.label || value}
                              />
                            ))}
                          </Box>
                        )}
                      >
                        {PREVIEW_STATUS_OPTIONS.map((status) => (
                          <MenuItem key={status.value} value={status.value}>
                            {status.label}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
              </Grid>
            </AccordionDetails>
          </Accordion>

          <Accordion
            expanded={expandedSection === 'dates'}
            onChange={(_, isExpanded) => setExpandedSection(isExpanded ? 'dates' : false)}
          >
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Typography>Date Filters</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <LocalizationProvider dateAdapter={AdapterDateFns}>
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <DatePicker
                      label="Created From"
                      value={searchCriteria.createdFrom ? new Date(searchCriteria.createdFrom) : null}
                      onChange={(date) =>
                        setSearchCriteria({
                          ...searchCriteria,
                          createdFrom: date?.toISOString(),
                        })
                      }
                      slotProps={{ textField: { fullWidth: true } }}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <DatePicker
                      label="Created To"
                      value={searchCriteria.createdTo ? new Date(searchCriteria.createdTo) : null}
                      onChange={(date) =>
                        setSearchCriteria({
                          ...searchCriteria,
                          createdTo: date?.toISOString(),
                        })
                      }
                      slotProps={{ textField: { fullWidth: true } }}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <DatePicker
                      label="Modified From"
                      value={searchCriteria.modifiedFrom ? new Date(searchCriteria.modifiedFrom) : null}
                      onChange={(date) =>
                        setSearchCriteria({
                          ...searchCriteria,
                          modifiedFrom: date?.toISOString(),
                        })
                      }
                      slotProps={{ textField: { fullWidth: true } }}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <DatePicker
                      label="Modified To"
                      value={searchCriteria.modifiedTo ? new Date(searchCriteria.modifiedTo) : null}
                      onChange={(date) =>
                        setSearchCriteria({
                          ...searchCriteria,
                          modifiedTo: date?.toISOString(),
                        })
                      }
                      slotProps={{ textField: { fullWidth: true } }}
                    />
                  </Grid>
                </Grid>
              </LocalizationProvider>
            </AccordionDetails>
          </Accordion>

          <Accordion
            expanded={expandedSection === 'aspects'}
            onChange={(_, isExpanded) => setExpandedSection(isExpanded ? 'aspects' : false)}
          >
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Typography>Aspects</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <Grid container spacing={1}>
                {ASPECTS.map((aspect) => (
                  <Grid item xs={6} key={aspect.value}>
                    <FormControlLabel
                      control={
                        <Checkbox
                          checked={searchCriteria.aspects?.includes(aspect.value) || false}
                          onChange={() => handleAspectToggle(aspect.value)}
                        />
                      }
                      label={aspect.label}
                    />
                  </Grid>
                ))}
              </Grid>
            </AccordionDetails>
          </Accordion>

          <Accordion
            expanded={expandedSection === 'properties'}
            onChange={(_, isExpanded) => setExpandedSection(isExpanded ? 'properties' : false)}
          >
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Typography>Custom Properties</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <Box>
                <Grid container spacing={1} alignItems="center" sx={{ mb: 2 }}>
                  <Grid item xs={5}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Property Key"
                      value={newPropertyKey}
                      onChange={(e) => setNewPropertyKey(e.target.value)}
                    />
                  </Grid>
                  <Grid item xs={5}>
                    <TextField
                      fullWidth
                      size="small"
                      label="Property Value"
                      value={newPropertyValue}
                      onChange={(e) => setNewPropertyValue(e.target.value)}
                    />
                  </Grid>
                  <Grid item xs={2}>
                    <Button
                      fullWidth
                      variant="outlined"
                      onClick={handleAddProperty}
                      disabled={!newPropertyKey || !newPropertyValue}
                    >
                      Add
                    </Button>
                  </Grid>
                </Grid>

                <Box display="flex" flexWrap="wrap" gap={1}>
                  {customProperties.map((prop, index) => (
                    <Chip
                      key={index}
                      label={`${prop.key}: ${prop.value}`}
                      onDelete={() => handleRemoveProperty(index)}
                    />
                  ))}
                </Box>
              </Box>
            </AccordionDetails>
          </Accordion>

          <Accordion
            expanded={expandedSection === 'meta'}
            onChange={(_, isExpanded) => setExpandedSection(isExpanded ? 'meta' : false)}
          >
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Typography>Tags / Categories / Size / Path</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={facetOptions.tags}
                    value={tags}
                    onChange={(_, value) => setTags(value.map((item) => item.toString()))}
                    renderTags={(value, getTagProps) =>
                      value.map((option, index) => (
                        <Chip label={option} size="small" {...getTagProps({ index })} />
                      ))
                    }
                    renderInput={(params) => <TextField {...params} label="Tags" placeholder="Add tags" />}
                  />
                </Grid>
                <Grid item xs={12}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={facetOptions.categories}
                    value={categories}
                    onChange={(_, value) => setCategories(value.map((item) => item.toString()))}
                    renderTags={(value, getTagProps) =>
                      value.map((option, index) => (
                        <Chip label={option} size="small" {...getTagProps({ index })} />
                      ))
                    }
                    renderInput={(params) => <TextField {...params} label="Categories" placeholder="Add categories" />}
                  />
                </Grid>
                <Grid item xs={12}>
                  <Autocomplete
                    multiple
                    freeSolo
                    options={facetOptions.correspondents}
                    value={correspondents}
                    onChange={(_, value) => setCorrespondents(value.map((item) => item.toString()))}
                    renderTags={(value, getTagProps) =>
                      value.map((option, index) => (
                        <Chip label={option} size="small" {...getTagProps({ index })} />
                      ))
                    }
                    renderInput={(params) => (
                      <TextField {...params} label="Correspondents" placeholder="Add correspondents" />
                    )}
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    fullWidth
                    type="number"
                    label="Min size (bytes)"
                    value={minSize ?? ''}
                    onChange={(e) => setMinSize(e.target.value ? Number(e.target.value) : undefined)}
                  />
                </Grid>
                <Grid item xs={6}>
                  <TextField
                    fullWidth
                    type="number"
                    label="Max size (bytes)"
                    value={maxSize ?? ''}
                    onChange={(e) => setMaxSize(e.target.value ? Number(e.target.value) : undefined)}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Path starts with"
                    value={pathPrefix}
                    onChange={(e) => setPathPrefix(e.target.value)}
                    placeholder="/Documents/Projects"
                    disabled={scopeFolderId.trim().length > 0}
                  />
                </Grid>
              </Grid>
            </AccordionDetails>
          </Accordion>

          {activeCriteriaChips.length > 0 && (
            <Box
              data-testid="active-criteria-summary"
              aria-label="Active criteria summary"
              sx={{
                mt: 2,
                p: 1.5,
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                backgroundColor: 'background.paper',
              }}
            >
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Active Criteria ({activeCriteriaChips.length})
              </Typography>
              <Box display="flex" flexWrap="wrap" gap={0.75}>
                {activeCriteriaChips.map((item) => (
                  <Chip key={item.key} size="small" label={item.label} />
                ))}
              </Box>
            </Box>
          )}
          </Box>
        </DialogContent>
        <DialogActions>
          {!canSubmitSearch && (
            <Typography variant="caption" color="text.secondary" sx={{ mr: 'auto' }}>
              Add at least one search criterion to enable Save Search and Search.
            </Typography>
          )}
          <Button onClick={resetForm}>Clear All</Button>
          <Button onClick={handleClose}>Cancel</Button>
          <Button
            onClick={openSaveDialog}
            startIcon={<Save />}
            disabled={!canSubmitSearch}
          >
            Save Search
          </Button>
          <Button
            onClick={handleSearch}
            variant="contained"
            startIcon={<Search />}
            disabled={!canSubmitSearch}
          >
            Search
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={saveDialogOpen} onClose={handleCloseSaveDialog} maxWidth="xs" fullWidth>
        <DialogTitle>Save Search</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 1 }}>
            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel id="save-search-mode-label">Mode</InputLabel>
              <Select
                labelId="save-search-mode-label"
                label="Mode"
                value={saveMode}
                disabled={saveDialogSubmitting || saveDialogLoading}
                onChange={(e) => handleSaveModeChange(e.target.value as 'create' | 'update')}
              >
                <MenuItem value="create">Create new</MenuItem>
                <MenuItem value="update">Update existing</MenuItem>
              </Select>
            </FormControl>
            {saveMode === 'update' && (
              <FormControl fullWidth sx={{ mb: 2 }}>
                <InputLabel id="save-search-target-label">Saved Search</InputLabel>
                <Select
                  labelId="save-search-target-label"
                  label="Saved Search"
                  value={saveExistingId}
                  disabled={saveDialogSubmitting || saveDialogLoading || savedSearchOptions.length === 0}
                  onChange={(e) => handleUpdateTargetChange(e.target.value)}
                >
                  {savedSearchOptions.length === 0 ? (
                    <MenuItem value="" disabled>
                      No saved searches
                    </MenuItem>
                  ) : (
                    savedSearchOptions.map((item) => (
                      <MenuItem key={item.id} value={item.id}>
                        {item.name}
                      </MenuItem>
                    ))
                  )}
                </Select>
              </FormControl>
            )}
            <TextField
              fullWidth
              label="Name"
              value={saveName}
              onChange={(e) => setSaveName(e.target.value)}
              placeholder="e.g. Recent PDFs"
              disabled={saveDialogSubmitting || saveDialogLoading}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseSaveDialog} disabled={saveDialogSubmitting}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveSearch} disabled={!canSubmitSaveDialog}>
            {saveDialogSubmitting ? 'Saving…' : (saveMode === 'update' ? 'Update' : 'Save')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default SearchDialog;
