import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import { Add, Edit } from '@mui/icons-material';
import { toast } from 'react-toastify';
import correspondentService, { Correspondent, MatchAlgorithm } from 'services/correspondentService';

type EditForm = {
  name: string;
  matchAlgorithm: MatchAlgorithm;
  matchPattern: string;
  insensitive: boolean;
  email: string;
  phone: string;
};

const algorithmOptions: MatchAlgorithm[] = ['AUTO', 'ANY', 'ALL', 'EXACT', 'REGEX', 'FUZZY'];

const emptyForm: EditForm = {
  name: '',
  matchAlgorithm: 'AUTO',
  matchPattern: '',
  insensitive: true,
  email: '',
  phone: '',
};

const toForm = (c?: Correspondent | null): EditForm => ({
  name: c?.name || '',
  matchAlgorithm: (c?.matchAlgorithm || 'AUTO') as MatchAlgorithm,
  matchPattern: c?.matchPattern || '',
  insensitive: c?.insensitive ?? true,
  email: c?.email || '',
  phone: c?.phone || '',
});

const CorrespondentsPage: React.FC = () => {
  const [rows, setRows] = useState<Correspondent[]>([]);
  const [loading, setLoading] = useState(false);
  const [filterText, setFilterText] = useState('');

  const [dialogOpen, setDialogOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<Correspondent | null>(null);
  const [form, setForm] = useState<EditForm>(emptyForm);

  const filteredRows = useMemo(() => {
    const query = filterText.trim().toLowerCase();
    if (!query) {
      return rows;
    }

    const matches = (value?: string | null) => (value || '').toLowerCase().includes(query);

    return rows.filter((c) => matches(c.name) || matches(c.matchPattern) || matches(c.email) || matches(c.phone));
  }, [filterText, rows]);

  const load = async () => {
    try {
      setLoading(true);
      const data = await correspondentService.list();
      setRows(data);
    } catch {
      toast.error('Failed to load correspondents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setDialogOpen(true);
  };

  const openEdit = (c: Correspondent) => {
    setEditing(c);
    setForm(toForm(c));
    setDialogOpen(true);
  };

  const closeDialog = () => {
    if (saving) {
      return;
    }
    setDialogOpen(false);
  };

  const handleSave = async () => {
    const name = form.name.trim();
    if (!name) {
      toast.error('Name is required');
      return;
    }

    try {
      setSaving(true);
      const payload = {
        name,
        matchAlgorithm: form.matchAlgorithm,
        matchPattern: form.matchPattern.trim() || null,
        insensitive: form.insensitive,
        email: form.email.trim() || null,
        phone: form.phone.trim() || null,
      };

      if (editing) {
        await correspondentService.update(editing.id, payload);
        toast.success('Correspondent updated');
      } else {
        await correspondentService.create(payload);
        toast.success('Correspondent created');
      }

      setDialogOpen(false);
      await load();
    } catch (error: any) {
      const message = error?.response?.data?.message || 'Failed to save correspondent';
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const columns: GridColDef[] = useMemo(
    () => [
      { field: 'name', headerName: 'Name', flex: 1.5, minWidth: 200 },
      { field: 'matchAlgorithm', headerName: 'Algorithm', width: 140 },
      {
        field: 'matchPattern',
        headerName: 'Pattern',
        flex: 2,
        minWidth: 260,
        valueGetter: (params) => String((params.row as Correspondent).matchPattern || ''),
      },
      {
        field: 'insensitive',
        headerName: 'Insensitive',
        width: 120,
        valueGetter: (params) => ((params.row as Correspondent).insensitive ? 'Yes' : 'No'),
      },
      {
        field: 'email',
        headerName: 'Email',
        flex: 1,
        minWidth: 180,
        valueGetter: (params) => String((params.row as Correspondent).email || ''),
      },
      {
        field: 'phone',
        headerName: 'Phone',
        width: 160,
        valueGetter: (params) => String((params.row as Correspondent).phone || ''),
      },
      {
        field: 'actions',
        headerName: '',
        width: 90,
        sortable: false,
        renderCell: (params) => (
          <IconButton
            size="small"
            aria-label={`Edit ${String((params.row as Correspondent).name)}`}
            onClick={() => openEdit(params.row as Correspondent)}
          >
            <Edit fontSize="small" />
          </IconButton>
        ),
      },
    ],
    []
  );

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 2 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1}>
          <Box>
            <Typography variant="h6">Correspondents</Typography>
            <Typography variant="body2" color="text.secondary">
              Auto-match document sources (e.g. vendors, banks) using extracted text.
            </Typography>
          </Box>
          <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
            <TextField
              size="small"
              label="Search correspondents"
              value={filterText}
              onChange={(e) => setFilterText(e.target.value)}
            />
            <Button variant="contained" startIcon={<Add />} onClick={openCreate}>
              New Correspondent
            </Button>
          </Box>
        </Box>
      </Paper>

      <Paper sx={{ p: 0.5 }}>
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : (
          <DataGrid
            rows={filteredRows}
            columns={columns}
            autoHeight
            disableRowSelectionOnClick
            getRowId={(row) => row.id}
            pageSizeOptions={[25, 50, 100]}
            initialState={{
              pagination: { paginationModel: { pageSize: 25, page: 0 } },
            }}
          />
        )}
      </Paper>

      <Dialog open={dialogOpen} onClose={closeDialog} maxWidth="sm" fullWidth>
        <DialogTitle>{editing ? 'Edit Correspondent' : 'New Correspondent'}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Name"
            fullWidth
            value={form.name}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
          />

          <FormControl fullWidth margin="dense">
            <InputLabel id="correspondent-algo">Match Algorithm</InputLabel>
            <Select
              labelId="correspondent-algo"
              label="Match Algorithm"
              value={form.matchAlgorithm}
              onChange={(e) => setForm((prev) => ({ ...prev, matchAlgorithm: e.target.value as MatchAlgorithm }))}
            >
              {algorithmOptions.map((algo) => (
                <MenuItem key={algo} value={algo}>
                  {algo}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            margin="dense"
            label="Match Pattern"
            helperText="Keywords or regex used for auto-matching. Leave empty to disable matching."
            fullWidth
            multiline
            minRows={3}
            value={form.matchPattern}
            onChange={(e) => setForm((prev) => ({ ...prev, matchPattern: e.target.value }))}
          />

          <FormControlLabel
            control={
              <Switch
                checked={form.insensitive}
                onChange={(e) => setForm((prev) => ({ ...prev, insensitive: e.target.checked }))}
              />
            }
            label="Case-insensitive match"
          />

          <Box display="flex" gap={1} flexWrap="wrap">
            <TextField
              margin="dense"
              label="Email (optional)"
              value={form.email}
              onChange={(e) => setForm((prev) => ({ ...prev, email: e.target.value }))}
              sx={{ flex: 1, minWidth: 220 }}
            />
            <TextField
              margin="dense"
              label="Phone (optional)"
              value={form.phone}
              onChange={(e) => setForm((prev) => ({ ...prev, phone: e.target.value }))}
              sx={{ flex: 1, minWidth: 220 }}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog} disabled={saving}>
            Cancel
          </Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CorrespondentsPage;
