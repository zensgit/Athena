import React, { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { Add, Delete, OpenInNew, Refresh, Archive, NotificationsActive, NotificationsOff } from '@mui/icons-material';
import { toast } from 'react-toastify';
import activityService, { ActivityDto } from 'services/activityService';
import followingService from 'services/followingService';
import siteService, {
  CreateSiteRequest,
  MembershipRequestDto,
  SiteDto,
  SiteMemberDto,
  SiteMemberRole,
  SiteVisibility,
} from 'services/siteService';
import { useAppSelector } from 'store';
import authService from 'services/authService';
import {
  formatActivityLabel,
  formatActivitySummary,
  getActivityLinkTargets,
} from 'utils/siteActivityUtils';

const SITE_MEMBER_ROLE_OPTIONS: SiteMemberRole[] = ['MANAGER', 'COLLABORATOR', 'CONTRIBUTOR', 'CONSUMER'];

const SitesPage: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const isAdmin = Boolean(effectiveUser?.roles?.includes('ROLE_ADMIN'));
  const preselectedSiteId = React.useMemo(
    () => new URLSearchParams(window.location.search).get('siteId')?.trim() ?? '',
    []
  );

  const [sites, setSites] = useState<SiteDto[]>([]);
  const [requests, setRequests] = useState<MembershipRequestDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [includeArchived, setIncludeArchived] = useState(false);

  // site detail
  const [selectedSiteId, setSelectedSiteId] = useState<string | null>(null);
  const [members, setMembers] = useState<SiteMemberDto[]>([]);
  const [siteRequests, setSiteRequests] = useState<MembershipRequestDto[]>([]);
  const [siteActivity, setSiteActivity] = useState<ActivityDto[]>([]);
  const [followedSiteIds, setFollowedSiteIds] = useState<Set<string>>(new Set());
  const [isFollowingSelectedSite, setIsFollowingSelectedSite] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [addMemberName, setAddMemberName] = useState('');
  const [addMemberRole, setAddMemberRole] = useState<SiteMemberRole>('CONSUMER');

  // create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateSiteRequest>({ siteId: '', title: '', description: '', visibility: 'PUBLIC' });
  const [saving, setSaving] = useState(false);

  // request dialog
  const [requestOpen, setRequestOpen] = useState(false);
  const [requestForm, setRequestForm] = useState<{ message: string; role: SiteMemberRole; siteId: string }>({
    siteId: '',
    message: '',
    role: 'CONSUMER',
  });

  const load = async () => {
    setLoading(true);
    try {
      const [sitesData, subscriptions] = await Promise.all([
        siteService.listSites(includeArchived),
        followingService.list().catch(() => []),
      ]);
      // Load requests for all sites the user might care about
      const requestLists = await Promise.all(
        sitesData.map((s) => siteService.getMembershipRequests(s.siteId).catch(() => [] as MembershipRequestDto[]))
      );
      const requestsData = requestLists.flat().filter(
        (r) => !effectiveUser?.username || r.username === effectiveUser.username || isAdmin
      );
      setFollowedSiteIds(
        new Set(
          subscriptions
            .filter((subscription) => subscription.targetType === 'SITE')
            .map((subscription) => subscription.targetId),
        ),
      );
      setSites(sitesData);
      setRequests(requestsData);
    } catch { toast.error('Failed to load sites'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [includeArchived]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!preselectedSiteId || selectedSiteId === preselectedSiteId) {
      return;
    }
    if (sites.some((site) => site.siteId === preselectedSiteId)) {
      void loadSiteDetail(preselectedSiteId);
    }
  }, [preselectedSiteId, selectedSiteId, sites]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (selectedSiteId) {
      params.set('siteId', selectedSiteId);
    } else {
      params.delete('siteId');
    }
    const nextSearch = params.toString();
    const nextUrl = `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}`;
    window.history.replaceState(null, '', nextUrl);
  }, [selectedSiteId]);

  const loadSiteDetail = async (siteId: string) => {
    setSelectedSiteId(siteId);
    try {
      const [m, r, a, following] = await Promise.all([
        siteService.getMembers(siteId),
        siteService.getMembershipRequests(siteId),
        activityService.getSiteFeed(siteId, 0, 5).catch(() => ({ content: [] } as { content: ActivityDto[] })),
        followingService.check('SITE', siteId).catch(() => false),
      ]);
      setMembers(m);
      setSiteRequests(r);
      setSiteActivity(a.content || []);
      setIsFollowingSelectedSite(following);
      setFollowedSiteIds((previous) => {
        const next = new Set(previous);
        if (following) {
          next.add(siteId);
        } else {
          next.delete(siteId);
        }
        return next;
      });
    } catch {
      setIsFollowingSelectedSite(false);
    }
  };

  const handleToggleFollow = async () => {
    if (!selectedSiteId) return;
    setFollowLoading(true);
    try {
      if (isFollowingSelectedSite) {
        await followingService.unfollow('SITE', selectedSiteId);
        setIsFollowingSelectedSite(false);
        setFollowedSiteIds((previous) => {
          const next = new Set(previous);
          next.delete(selectedSiteId);
          return next;
        });
        toast.success('Site unfollowed');
      } else {
        await followingService.follow('SITE', selectedSiteId);
        setIsFollowingSelectedSite(true);
        setFollowedSiteIds((previous) => {
          const next = new Set(previous);
          next.add(selectedSiteId);
          return next;
        });
        toast.success('Site followed');
      }
    } catch {
      toast.error('Failed to update following');
    } finally {
      setFollowLoading(false);
    }
  };

  const handleAddMember = async () => {
    if (!selectedSiteId || !addMemberName.trim()) return;
    try {
      await siteService.addMember(selectedSiteId, addMemberName.trim(), addMemberRole);
      toast.success('Member added');
      setAddMemberName('');
      await loadSiteDetail(selectedSiteId);
    } catch { toast.error('Failed to add member'); }
  };

  const handleRemoveMember = async (username: string) => {
    if (!selectedSiteId) return;
    try {
      await siteService.removeMember(selectedSiteId, username);
      toast.success('Member removed');
      await loadSiteDetail(selectedSiteId);
    } catch { toast.error('Failed to remove member'); }
  };

  const handleRoleChange = async (username: string, currentRole: SiteMemberRole, nextRole: SiteMemberRole) => {
    if (!selectedSiteId || currentRole === nextRole) return;
    try {
      await siteService.updateMemberRole(selectedSiteId, username, nextRole);
      toast.success('Member role updated');
      await loadSiteDetail(selectedSiteId);
    } catch { toast.error('Failed to update member role'); }
  };

  const handleCreate = async () => {
    if (!createForm.siteId.trim() || !createForm.title.trim()) { toast.warn('Site ID and title required'); return; }
    setSaving(true);
    try {
      await siteService.createSite({ ...createForm, siteId: createForm.siteId.trim(), title: createForm.title.trim() });
      setCreateOpen(false);
      setCreateForm({ siteId: '', title: '', description: '', visibility: 'PUBLIC' });
      toast.success('Site created');
      await load();
    } catch { toast.error('Failed to create site'); }
    finally { setSaving(false); }
  };

  const handleDelete = async (siteId: string) => {
    if (!window.confirm(`Archive site "${siteId}"?`)) return;
    try {
      await siteService.deleteSite(siteId);
      toast.success('Site archived');
      await load();
    } catch { toast.error('Failed to archive site'); }
  };

  const handleRequestMembership = async () => {
    if (!requestForm.siteId.trim()) { toast.warn('Site ID required'); return; }
    setSaving(true);
    try {
      const site = sites.find((s) => s.siteId === requestForm.siteId);
      await siteService.requestMembership(requestForm.siteId, {
        siteTitle: site?.title || requestForm.siteId,
        role: requestForm.role,
        message: requestForm.message || undefined,
      });
      setRequestOpen(false);
      setRequestForm({ siteId: '', message: '', role: 'CONSUMER' });
      toast.success('Membership request submitted');
      await load();
    } catch { toast.error('Failed to submit request'); }
    finally { setSaving(false); }
  };

  const handleApprove = async (username: string, siteId: string) => {
    try {
      await siteService.approveMembershipRequest(siteId, username);
      toast.success('Approved');
      await load();
      if (selectedSiteId === siteId) {
        await loadSiteDetail(siteId);
      }
    } catch { toast.error('Failed to approve'); }
  };

  const handleReject = async (username: string, siteId: string) => {
    try {
      await siteService.rejectMembershipRequest(siteId, username);
      toast.success('Rejected');
      await load();
      if (selectedSiteId === siteId) {
        await loadSiteDetail(siteId);
      }
    } catch { toast.error('Failed to reject'); }
  };

  const handleWithdraw = async (siteId: string) => {
    try {
      await siteService.withdrawMembershipRequest(siteId);
      toast.success('Request withdrawn');
      await load();
      if (selectedSiteId === siteId) {
        await loadSiteDetail(siteId);
      }
    } catch { toast.error('Failed to withdraw'); }
  };

  const visibilityColor = (v: string) =>
    v === 'PUBLIC' ? 'success' as const : v === 'MODERATED' ? 'warning' as const : 'error' as const;

  const requestKey = (req: MembershipRequestDto, index: number) =>
    req.siteId ?? `${req.siteTitle}-${req.status}-${index}`;

  const selectedSite = selectedSiteId ? sites.find((site) => site.siteId === selectedSiteId) : undefined;

  return (
    <Box maxWidth={1200}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Sites</Typography>
          <Typography variant="body2" color="text.secondary">Collaboration workspaces and membership management</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void load()} disabled={loading}>Refresh</Button>
          <Button variant="outlined" onClick={() => setRequestOpen(true)}>Request Membership</Button>
          {isAdmin && <Button variant="contained" startIcon={<Add />} onClick={() => setCreateOpen(true)}>New Site</Button>}
        </Stack>
      </Box>

      <Grid container spacing={2}>
        {/* Sites table */}
        <Grid item xs={12} md={8}>
          <Card variant="outlined">
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Typography variant="h6">Site Registry ({sites.length})</Typography>
                <Chip
                  label={includeArchived ? 'Showing archived' : 'Active only'}
                  size="small"
                  variant="outlined"
                  onClick={() => setIncludeArchived((v) => !v)}
                />
              </Box>
              <TableContainer component={Paper} variant="outlined">
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Site ID</TableCell>
                      <TableCell>Title</TableCell>
                      <TableCell>Visibility</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Root Folder</TableCell>
                      {isAdmin && <TableCell align="right">Actions</TableCell>}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {sites.map((site) => (
                      <TableRow key={site.siteId} hover selected={site.siteId === selectedSiteId} sx={{ cursor: 'pointer' }} onClick={() => void loadSiteDetail(site.siteId)}>
                        <TableCell><Typography variant="body2" fontWeight={500}>{site.siteId}</Typography></TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                            <Typography variant="body2">{site.title}</Typography>
                            {followedSiteIds.has(site.siteId) && (
                              <Chip size="small" label="Following" color="primary" />
                            )}
                          </Stack>
                        </TableCell>
                        <TableCell><Chip label={site.visibility} size="small" color={visibilityColor(site.visibility)} variant="outlined" /></TableCell>
                        <TableCell><Chip label={site.status} size="small" color={site.status === 'ACTIVE' ? 'success' : 'default'} /></TableCell>
                        <TableCell>
                          {site.rootFolderId ? (
                            <Tooltip title={site.rootFolderPath || ''}>
                              <Chip
                                label={site.rootFolderTitle || 'Browse'}
                                size="small"
                                variant="outlined"
                                onClick={() => window.open(`/browse/${site.rootFolderId}`, '_blank')}
                                icon={<OpenInNew sx={{ fontSize: 12 }} />}
                              />
                            </Tooltip>
                          ) : '-'}
                        </TableCell>
                        {isAdmin && (
                          <TableCell align="right">
                            <Tooltip title="Archive"><IconButton size="small" color="warning" onClick={() => void handleDelete(site.siteId)}><Archive fontSize="small" /></IconButton></Tooltip>
                          </TableCell>
                        )}
                      </TableRow>
                    ))}
                    {sites.length === 0 && <TableRow><TableCell colSpan={isAdmin ? 6 : 5} align="center">No sites</TableCell></TableRow>}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Membership requests panel */}
        <Grid item xs={12} md={4}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="h6" gutterBottom>My Membership Requests</Typography>
              {requests.length === 0 ? (
                <Typography variant="body2" color="text.secondary">No pending requests</Typography>
              ) : (
                <Stack spacing={1}>
                  {requests.map((req, index) => (
                    <Paper key={requestKey(req, index)} variant="outlined" sx={{ p: 1.5 }}>
                      <Box display="flex" justifyContent="space-between" alignItems="center">
                        <Box>
                          <Typography variant="body2" fontWeight={500}>{req.siteTitle || req.siteId}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {isAdmin && req.username !== effectiveUser?.username ? `User: ${req.username} | ` : ''}Role: {req.role} | Status: {req.status}
                          </Typography>
                        </Box>
                        <Stack direction="row" spacing={0.5}>
                          {isAdmin && req.status === 'PENDING' && req.siteId && req.username && (
                            <>
                              <Button size="small" color="success" onClick={() => void handleApprove(req.username, req.siteId)}>Approve</Button>
                              <Button size="small" color="error" onClick={() => void handleReject(req.username, req.siteId)}>Reject</Button>
                            </>
                          )}
                          {req.status === 'PENDING' && req.siteId && req.username === effectiveUser?.username && (
                            <Button size="small" onClick={() => void handleWithdraw(req.siteId)}>Withdraw</Button>
                          )}
                        </Stack>
                      </Box>
                    </Paper>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Site detail: members + requests */}
      {selectedSiteId && (
        <Grid container spacing={2} mt={1}>
          <Grid item xs={12} md={7}>
            <Card variant="outlined">
              <CardContent>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                  <Typography variant="h6">Members of {selectedSiteId} ({members.length})</Typography>
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      variant={isFollowingSelectedSite ? 'contained' : 'outlined'}
                      startIcon={isFollowingSelectedSite ? <NotificationsActive /> : <NotificationsOff />}
                      onClick={() => void handleToggleFollow()}
                      disabled={followLoading}
                    >
                      {isFollowingSelectedSite ? 'Following' : 'Follow Site'}
                    </Button>
                    {selectedSite?.rootFolderId && (
                      <Button size="small" startIcon={<OpenInNew />} onClick={() => window.open(`/browse/${selectedSite.rootFolderId}`, '_blank')}>
                        Open Workspace
                      </Button>
                    )}
                  </Stack>
                </Box>
                {isAdmin && (
                  <Box display="flex" gap={1} mb={1}>
                    <TextField size="small" label="Username" value={addMemberName} onChange={(e) => setAddMemberName(e.target.value)} sx={{ flex: 1 }} />
                    <FormControl size="small" sx={{ minWidth: 130 }}>
                      <InputLabel>Role</InputLabel>
                      <Select label="Role" value={addMemberRole} onChange={(e) => setAddMemberRole(e.target.value as SiteMemberRole)}>
                        <MenuItem value="MANAGER">Manager</MenuItem>
                        <MenuItem value="COLLABORATOR">Collaborator</MenuItem>
                        <MenuItem value="CONTRIBUTOR">Contributor</MenuItem>
                        <MenuItem value="CONSUMER">Consumer</MenuItem>
                      </Select>
                    </FormControl>
                    <Button variant="contained" startIcon={<Add />} onClick={() => void handleAddMember()} disabled={!addMemberName.trim()}>Add</Button>
                  </Box>
                )}
                <TableContainer component={Paper} variant="outlined">
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Username</TableCell>
                        <TableCell>Role</TableCell>
                        <TableCell>Joined</TableCell>
                        {isAdmin && <TableCell align="right">Actions</TableCell>}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {members.map((m) => (
                        <TableRow key={m.id} hover>
                          <TableCell>{m.username}</TableCell>
                          <TableCell>
                            {isAdmin ? (
                              <FormControl size="small" variant="standard" sx={{ minWidth: 148 }}>
                                <Select
                                  disableUnderline
                                  value={m.role}
                                  onChange={(event) => void handleRoleChange(m.username, m.role, event.target.value as SiteMemberRole)}
                                >
                                  {SITE_MEMBER_ROLE_OPTIONS.map((role) => (
                                    <MenuItem key={role} value={role}>{role}</MenuItem>
                                  ))}
                                </Select>
                              </FormControl>
                            ) : (
                              <Chip label={m.role} size="small" variant="outlined" />
                            )}
                          </TableCell>
                          <TableCell>{m.joinedAt ? new Date(m.joinedAt).toLocaleDateString() : '-'}</TableCell>
                          {isAdmin && (
                            <TableCell align="right">
                              <Tooltip title="Remove member">
                                <IconButton size="small" color="error" onClick={() => void handleRemoveMember(m.username)}>
                                  <Delete fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
                      {members.length === 0 && <TableRow><TableCell colSpan={isAdmin ? 4 : 3} align="center">No members</TableCell></TableRow>}
                    </TableBody>
                  </Table>
                </TableContainer>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={5}>
            <Stack spacing={2}>
              <Card variant="outlined">
                <CardContent>
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                    <Typography variant="h6">Recent Activity</Typography>
                    <Button
                      size="small"
                      startIcon={<OpenInNew />}
                      onClick={() => window.open(`/activities?siteId=${encodeURIComponent(selectedSiteId)}`, '_blank')}
                    >
                      Open Feed
                    </Button>
                  </Box>
                  {siteActivity.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">No recent activity</Typography>
                  ) : (
                    <Stack spacing={1}>
                      {siteActivity.map((activity) => (
                        <Paper key={activity.id} variant="outlined" sx={{ p: 1.25 }}>
                          <Box display="flex" justifyContent="space-between" alignItems="flex-start" gap={1}>
                            <Box>
                              <>
                                <Typography variant="body2" fontWeight={500}>{formatActivityLabel(activity.activityType)}</Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {activity.userId}
                                  {activity.nodeName ? ` · ${activity.nodeName}` : ''}
                                </Typography>
                                <Typography variant="caption" display="block" color="text.secondary">
                                  {formatActivitySummary(activity)}
                                </Typography>
                                <Stack direction="row" spacing={0.75} mt={0.75}>
                                  {getActivityLinkTargets(activity)
                                    .filter((target) => target.label !== 'Open Site')
                                    .map((target) => (
                                      <Button
                                        key={`${activity.id}-${target.label}`}
                                        size="small"
                                        variant="text"
                                        onClick={() => window.open(target.href, '_blank')}
                                      >
                                        {target.label}
                                      </Button>
                                    ))}
                                </Stack>
                              </>
                            </Box>
                            <Typography variant="caption" color="text.secondary" whiteSpace="nowrap">
                              {new Date(activity.postedAt).toLocaleString()}
                            </Typography>
                          </Box>
                        </Paper>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>

              <Card variant="outlined">
                <CardContent>
                  <Typography variant="h6" gutterBottom>Requests for {selectedSiteId} ({siteRequests.length})</Typography>
                  {siteRequests.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">No membership requests</Typography>
                  ) : (
                    <Stack spacing={1}>
                      {siteRequests.map((req, i) => (
                        <Paper key={req.username + '-' + i} variant="outlined" sx={{ p: 1.5 }}>
                          <Box display="flex" justifyContent="space-between" alignItems="center">
                            <Box>
                              <Typography variant="body2" fontWeight={500}>{req.username}</Typography>
                              <Typography variant="caption" color="text.secondary">Role: {req.role} | Status: {req.status}</Typography>
                            </Box>
                            {isAdmin && req.status === 'PENDING' && (
                              <Stack direction="row" spacing={0.5}>
                                <Button size="small" color="success" onClick={() => void handleApprove(req.username, selectedSiteId)}>Approve</Button>
                                <Button size="small" color="error" onClick={() => void handleReject(req.username, selectedSiteId)}>Reject</Button>
                              </Stack>
                            )}
                          </Box>
                        </Paper>
                      ))}
                    </Stack>
                  )}
                </CardContent>
              </Card>
            </Stack>
          </Grid>
        </Grid>
      )}

      {/* Create site dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Site</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Site ID" value={createForm.siteId} onChange={(e) => setCreateForm((p) => ({ ...p, siteId: e.target.value }))} required fullWidth helperText="Lowercase alphanumeric, dots, dashes" />
            <TextField label="Title" value={createForm.title} onChange={(e) => setCreateForm((p) => ({ ...p, title: e.target.value }))} required fullWidth />
            <TextField label="Description" value={createForm.description} onChange={(e) => setCreateForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} />
            <FormControl size="small" fullWidth>
              <InputLabel>Visibility</InputLabel>
              <Select label="Visibility" value={createForm.visibility} onChange={(e) => setCreateForm((p) => ({ ...p, visibility: e.target.value as SiteVisibility }))}>
                <MenuItem value="PUBLIC">Public</MenuItem>
                <MenuItem value="MODERATED">Moderated</MenuItem>
                <MenuItem value="PRIVATE">Private</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleCreate()} disabled={saving}>Create</Button>
        </DialogActions>
      </Dialog>

      {/* Request membership dialog */}
      <Dialog open={requestOpen} onClose={() => setRequestOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Request Site Membership</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <FormControl size="small" fullWidth>
              <InputLabel>Site</InputLabel>
              <Select label="Site" value={requestForm.siteId} onChange={(e) => setRequestForm((p) => ({ ...p, siteId: e.target.value }))}>
                {sites.filter((s) => s.status === 'ACTIVE').map((s) => (
                  <MenuItem key={s.siteId} value={s.siteId}>{s.title} ({s.siteId})</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" fullWidth>
              <InputLabel>Role</InputLabel>
              <Select label="Role" value={requestForm.role} onChange={(e) => setRequestForm((p) => ({ ...p, role: e.target.value as SiteMemberRole }))}>
                <MenuItem value="CONSUMER">Consumer</MenuItem>
                <MenuItem value="CONTRIBUTOR">Contributor</MenuItem>
                <MenuItem value="COLLABORATOR">Collaborator</MenuItem>
                <MenuItem value="MANAGER">Manager</MenuItem>
              </Select>
            </FormControl>
            <TextField label="Message (optional)" value={requestForm.message} onChange={(e) => setRequestForm((p) => ({ ...p, message: e.target.value }))} fullWidth multiline minRows={2} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRequestOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleRequestMembership()} disabled={saving}>Submit Request</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SitesPage;
