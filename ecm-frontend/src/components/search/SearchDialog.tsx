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
import savedSearchService from 'services/savedSearchService';
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
  const [minSize, setMinSize] = useState<number | undefined>();
  const [maxSize, setMaxSize] = useState<number | undefined>();
  const [pathPrefix, setPathPrefix] = useState<string>('');
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [saveName, setSaveName] = useState('');
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
    if (!searchOpen || !searchPrefill) {
      return;
    }

    // Start from a clean slate before applying prefill values.
    setSearchCriteria({
      name: searchPrefill.name || '',
      properties: {},
      aspects: [],
      contentType: searchPrefill.contentType || '',
      createdBy: searchPrefill.createdBy || '',
      createdFrom: searchPrefill.createdFrom,
      createdTo: searchPrefill.createdTo,
      modifiedFrom: searchPrefill.modifiedFrom,
      modifiedTo: searchPrefill.modifiedTo,
    });
    setCustomProperties([]);
    setNewPropertyKey('');
    setNewPropertyValue('');
    setExpandedSection('basic');
    setTags(searchPrefill.tags || []);
    setCategories(searchPrefill.categories || []);
    setCorrespondents(searchPrefill.correspondents || []);
    setMinSize(searchPrefill.minSize);
    setMaxSize(searchPrefill.maxSize);
    setPathPrefix(searchPrefill.pathPrefix || '');
    setSaveDialogOpen(false);
    setSaveName('');

    dispatch(setSearchPrefill(null));
  }, [searchOpen, searchPrefill, dispatch]);

  const handleClose = () => {
    dispatch(setSearchOpen(false));
    resetForm();
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
    setMinSize(undefined);
    setMaxSize(undefined);
    setPathPrefix('');
    setSaveDialogOpen(false);
    setSaveName('');
  };

  const buildSavedSearchQueryParams = () => {
    const query = (searchCriteria.name || '').trim();
    const filters: Record<string, any> = {};
    const normalizedTags = normalizeList(tags);
    const normalizedCategories = normalizeList(categories);
    const normalizedCorrespondents = normalizeList(correspondents);

    if (searchCriteria.contentType) {
      filters.mimeTypes = [searchCriteria.contentType];
    }
    if (searchCriteria.createdBy) {
      filters.createdBy = searchCriteria.createdBy;
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
    if (pathPrefix.length > 0) {
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

    try {
      await savedSearchService.save(trimmed, buildSavedSearchQueryParams());
      toast.success('Saved search created');
      setSaveDialogOpen(false);
      setSaveName('');
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to save search';
      toast.error(message);
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
      minSize,
      maxSize,
      path: pathPrefix || undefined,
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
      minSize !== undefined ||
      maxSize !== undefined ||
      pathPrefix.length > 0
    );
  };

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
                    <TextField
                      fullWidth
                      label="Name contains"
                      value={searchCriteria.name}
                      onChange={(e) =>
                        setSearchCriteria({ ...searchCriteria, name: e.target.value })
                      }
                      placeholder="Enter partial or full name"
                    />
                  </Grid>
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
                      {facetOptions.mimeTypes.length > 0
                        ? facetOptions.mimeTypes.map((mt) => (
                            <MenuItem key={mt} value={mt}>
                              {mt}
                            </MenuItem>
                          ))
                        : CONTENT_TYPES.map((type) => (
                            <MenuItem key={type.value} value={type.value}>
                              {type.label}
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
                      {facetOptions.createdBy.map((u) => (
                        <MenuItem key={u} value={u}>
                          {u}
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
                  />
                </Grid>
              </Grid>
            </AccordionDetails>
          </Accordion>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={resetForm}>Clear All</Button>
          <Button onClick={handleClose}>Cancel</Button>
          <Button
            onClick={() => setSaveDialogOpen(true)}
            startIcon={<Save />}
            disabled={!isSearchValid()}
          >
            Save Search
          </Button>
          <Button
            onClick={handleSearch}
            variant="contained"
            startIcon={<Search />}
            disabled={!isSearchValid()}
          >
            Search
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={saveDialogOpen} onClose={() => setSaveDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Save Search</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 1 }}>
            <TextField
              fullWidth
              label="Name"
              value={saveName}
              onChange={(e) => setSaveName(e.target.value)}
              placeholder="e.g. Recent PDFs"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSaveDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveSearch} disabled={!saveName.trim()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default SearchDialog;
