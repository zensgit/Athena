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
  FilterList,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { SearchCriteria } from '@/types';
import { useAppDispatch, useAppSelector } from '@/store';
import { setSearchOpen } from '@/store/slices/uiSlice';
import { searchNodes } from '@/store/slices/nodeSlice';
import { useNavigate } from 'react-router-dom';

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
  const { searchOpen } = useAppSelector((state) => state.ui);
  
  const [searchCriteria, setSearchCriteria] = useState<SearchCriteria>({
    name: '',
    properties: {},
    aspects: [],
    contentType: '',
  });
  
  const [customProperties, setCustomProperties] = useState<{ key: string; value: string }[]>([]);
  const [newPropertyKey, setNewPropertyKey] = useState('');
  const [newPropertyValue, setNewPropertyValue] = useState('');
  const [expandedSection, setExpandedSection] = useState<string | false>('basic');

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
    });
    setCustomProperties([]);
    setNewPropertyKey('');
    setNewPropertyValue('');
    setExpandedSection('basic');
  };

  const handleSearch = async () => {
    const criteria: SearchCriteria = {
      ...searchCriteria,
      properties: customProperties.reduce((acc, prop) => {
        acc[prop.key] = prop.value;
        return acc;
      }, {} as Record<string, any>),
    };

    await dispatch(searchNodes(criteria));
    navigate('/search-results');
    handleClose();
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
      (searchCriteria.aspects && searchCriteria.aspects.length > 0) ||
      customProperties.length > 0 ||
      searchCriteria.createdFrom ||
      searchCriteria.createdTo ||
      searchCriteria.modifiedFrom ||
      searchCriteria.modifiedTo
    );
  };

  return (
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
                      value={searchCriteria.contentType}
                      onChange={(e) =>
                        setSearchCriteria({ ...searchCriteria, contentType: e.target.value })
                      }
                      label="Content Type"
                    >
                      <MenuItem value="">All Types</MenuItem>
                      {CONTENT_TYPES.map((type) => (
                        <MenuItem key={type.value} value={type.value}>
                          {type.label}
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
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={resetForm}>Clear All</Button>
        <Button onClick={handleClose}>Cancel</Button>
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
  );
};

export default SearchDialog;