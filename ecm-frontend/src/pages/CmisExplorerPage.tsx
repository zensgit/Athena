import React, { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Paper,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { PlayArrow as RunIcon } from '@mui/icons-material';
import { toast } from 'react-toastify';
import cmisService, {
  CmisQueryResponse,
  CmisRepositoryInfo,
  CmisTypeDefinition,
} from 'services/cmisService';

const CmisExplorerPage: React.FC = () => {
  const [tab, setTab] = useState(0);
  const [repoInfo, setRepoInfo] = useState<CmisRepositoryInfo | null>(null);
  const [types, setTypes] = useState<CmisTypeDefinition[]>([]);
  const [queryStatement, setQueryStatement] = useState('SELECT * FROM cmis:document');
  const [queryResults, setQueryResults] = useState<CmisQueryResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const loadRepoInfo = useCallback(async () => {
    setLoading(true);
    try {
      const info = await cmisService.getRepositoryInfo();
      setRepoInfo(info);
    } catch {
      toast.error('Failed to load repository info');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadTypes = useCallback(async () => {
    setLoading(true);
    try {
      const response = await cmisService.getTypeChildren();
      setTypes(response.types);
    } catch {
      toast.error('Failed to load type definitions');
    } finally {
      setLoading(false);
    }
  }, []);

  const runQuery = useCallback(async () => {
    if (!queryStatement.trim()) {
      toast.warning('Enter a CMIS-QL statement');
      return;
    }
    setLoading(true);
    try {
      const response = await cmisService.query(queryStatement.trim());
      setQueryResults(response);
    } catch {
      toast.error('Query execution failed');
    } finally {
      setLoading(false);
    }
  }, [queryStatement]);

  useEffect(() => {
    loadRepoInfo();
  }, [loadRepoInfo]);

  useEffect(() => {
    if (tab === 1 && types.length === 0) {
      loadTypes();
    }
  }, [tab, types.length, loadTypes]);

  const renderRepoInfo = () => {
    if (loading && !repoInfo) {
      return (
        <Box display="flex" justifyContent="center" py={4}>
          <CircularProgress />
        </Box>
      );
    }
    if (!repoInfo) {
      return (
        <Typography color="text.secondary" py={2}>
          No repository info available.
        </Typography>
      );
    }
    return (
      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" gutterBottom>
            {repoInfo.repositoryName}
          </Typography>
          <Table size="small">
            <TableBody>
              <TableRow>
                <TableCell sx={{ fontWeight: 600, width: 200 }}>Repository ID</TableCell>
                <TableCell>{repoInfo.repositoryId}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Vendor</TableCell>
                <TableCell>{repoInfo.vendorName}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Product</TableCell>
                <TableCell>
                  {repoInfo.productName} {repoInfo.productVersion}
                </TableCell>
              </TableRow>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>CMIS Version</TableCell>
                <TableCell>{repoInfo.cmisVersionSupported}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Root Folder ID</TableCell>
                <TableCell>
                  <code>{repoInfo.rootFolderId}</code>
                </TableCell>
              </TableRow>
              {repoInfo.capabilities && repoInfo.capabilities.length > 0 && (
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, verticalAlign: 'top' }}>Capabilities</TableCell>
                  <TableCell>
                    <Box display="flex" flexWrap="wrap" gap={0.5}>
                      {repoInfo.capabilities.map((cap) => (
                        <Chip key={cap} label={cap} size="small" variant="outlined" />
                      ))}
                    </Box>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    );
  };

  const renderTypeBrowser = () => {
    if (loading && types.length === 0) {
      return (
        <Box display="flex" justifyContent="center" py={4}>
          <CircularProgress />
        </Box>
      );
    }
    if (types.length === 0) {
      return (
        <Typography color="text.secondary" py={2}>
          No type definitions found.
        </Typography>
      );
    }
    return (
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 600 }}>ID</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>Display Name</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>Base Type</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>Creatable</TableCell>
              <TableCell sx={{ fontWeight: 600 }}>Queryable</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {types.map((t) => (
              <TableRow key={t.id}>
                <TableCell>
                  <code>{t.id}</code>
                </TableCell>
                <TableCell>{t.displayName}</TableCell>
                <TableCell>
                  <Chip label={t.baseTypeId} size="small" variant="outlined" />
                </TableCell>
                <TableCell>
                  <Chip
                    label={t.creatable ? 'Yes' : 'No'}
                    size="small"
                    color={t.creatable ? 'success' : 'default'}
                  />
                </TableCell>
                <TableCell>
                  <Chip
                    label={t.queryable ? 'Yes' : 'No'}
                    size="small"
                    color={t.queryable ? 'success' : 'default'}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    );
  };

  const renderQueryConsole = () => {
    const objectKeys =
      queryResults?.objects && queryResults.objects.length > 0
        ? Object.keys(queryResults.objects[0])
        : [];
    return (
      <Box>
        <Box display="flex" gap={1} alignItems="flex-start" mb={2}>
          <TextField
            fullWidth
            multiline
            minRows={2}
            maxRows={6}
            label="CMIS-QL Statement"
            value={queryStatement}
            onChange={(e) => setQueryStatement(e.target.value)}
            placeholder="SELECT * FROM cmis:document"
            size="small"
          />
          <Button
            variant="contained"
            startIcon={loading ? <CircularProgress size={18} color="inherit" /> : <RunIcon />}
            onClick={runQuery}
            disabled={loading}
            sx={{ minWidth: 100, mt: 0.5 }}
          >
            Run
          </Button>
        </Box>
        {queryResults && (
          <Box>
            <Typography variant="body2" color="text.secondary" mb={1}>
              {queryResults.totalNumItems} result{queryResults.totalNumItems !== 1 ? 's' : ''}
              {queryResults.hasMoreItems ? ' (more available)' : ''}
            </Typography>
            {queryResults.objects.length > 0 ? (
              <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: 500 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      {objectKeys.map((key) => (
                        <TableCell key={key} sx={{ fontWeight: 600 }}>
                          {key}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {queryResults.objects.map((obj, idx) => (
                      <TableRow key={idx}>
                        {objectKeys.map((key) => (
                          <TableCell key={key} sx={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {typeof obj[key] === 'object' ? JSON.stringify(obj[key]) : String(obj[key] ?? '')}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Typography color="text.secondary">No results.</Typography>
            )}
          </Box>
        )}
      </Box>
    );
  };

  return (
    <Box sx={{ maxWidth: 1280, mx: 'auto', p: 2 }}>
      <Typography variant="h5" mb={2}>
        CMIS Explorer
      </Typography>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Repository Info" />
        <Tab label="Type Browser" />
        <Tab label="Query Console" />
      </Tabs>
      {tab === 0 && renderRepoInfo()}
      {tab === 1 && renderTypeBrowser()}
      {tab === 2 && renderQueryConsole()}
    </Box>
  );
};

export default CmisExplorerPage;
