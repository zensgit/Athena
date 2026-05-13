import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Collapse,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  IconButton,
  MenuItem,
  Divider,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import {
  Add,
  Delete,
  ExpandLess,
  ExpandMore,
  PlayArrow,
  PublishedWithChanges,
  Refresh,
  Replay,
  Save,
} from "@mui/icons-material";
import { toast } from "react-toastify";
import FolderTree from "components/browser/FolderTree";
import transferReplicationService, {
  AuthType,
  ReceiverAccessStatus,
  buildReplicationDefinitionRequest,
  ReplicationConflictPolicy,
  ReplicationDefinitionDto,
  ReplicationJobDto,
  ReplicationJobEntryReportItem,
  ReplicationTransportStatus,
  TransportType,
  TransferReceiverDto,
  TransferReceiverMutationRequest,
  TransferTargetDto,
  TransferTargetMutationRequest,
  VerificationStatus,
} from "services/transferReplicationService";

const EMPTY_TARGET_FORM: TransferTargetMutationRequest = {
  name: "",
  description: "",
  transportType: "LOOPBACK",
  targetFolderId: "",
  endpointUrl: "",
  endpointPath: "/api/v1",
  authType: "NONE",
  authUsername: "",
  authSecret: "",
  enabled: true,
};

interface ReplicationDefinitionFormState {
  name: string;
  description: string;
  sourceNodeId: string;
  transferTargetId: string;
  includeChildren: boolean;
  enabled: boolean;
  conflictPolicy: ReplicationConflictPolicy;
  cronExpression: string;
  scheduleTimezone: string;
  autoRetryEnabled: boolean;
  maxRetryAttempts: string;
  retryBackoffMinutes: string;
  jobRetentionDays: string;
}

const EMPTY_DEFINITION_FORM: ReplicationDefinitionFormState = {
  name: "",
  description: "",
  sourceNodeId: "",
  transferTargetId: "",
  includeChildren: true,
  enabled: true,
  conflictPolicy: "RENAME",
  cronExpression: "",
  scheduleTimezone: "UTC",
  autoRetryEnabled: false,
  maxRetryAttempts: "",
  retryBackoffMinutes: "",
  jobRetentionDays: "30",
};

const EMPTY_RECEIVER_FORM: TransferReceiverMutationRequest = {
  name: "",
  description: "",
  rootFolderId: "",
  authType: "NONE",
  authUsername: "",
  authSecret: "",
  enabled: true,
};

const verificationColor: Record<
  VerificationStatus,
  "default" | "success" | "warning" | "error"
> = {
  NEVER_VERIFIED: "warning",
  VERIFIED: "success",
  FAILED: "error",
};

const jobColor: Record<
  ReplicationJobDto["status"],
  "default" | "primary" | "success" | "error" | "warning"
> = {
  PENDING: "default",
  RUNNING: "primary",
  COMPLETED: "success",
  FAILED: "error",
  CANCELED: "warning",
};

const transportColor: Record<
  ReplicationTransportStatus,
  "default" | "primary" | "success" | "error"
> = {
  NEVER_RUN: "default",
  RUNNING: "primary",
  SUCCESS: "success",
  FAILED: "error",
};

const receiverAccessColor: Record<
  ReceiverAccessStatus,
  "default" | "success" | "error"
> = {
  NEVER_USED: "default",
  SUCCESS: "success",
  FAILED: "error",
};

const runningStatuses = new Set<ReplicationJobDto["status"]>([
  "PENDING",
  "RUNNING",
]);

const formatTimestamp = (value?: string | null) => {
  if (!value) {
    return "—";
  }
  return new Date(value).toLocaleString();
};

const formatEntrySource = (entry: ReplicationJobEntryReportItem) => {
  if (entry.sourcePath && entry.sourceNodeId) {
    return entry.sourcePath;
  }
  return entry.sourcePath || entry.sourceNodeId || "—";
};

const formatEntrySourceDetail = (entry: ReplicationJobEntryReportItem) => {
  const details: string[] = [];
  if (entry.sourceType) {
    details.push(entry.sourceType);
  }
  if (entry.sourceNodeId) {
    details.push(`Node ${entry.sourceNodeId}`);
  }
  return details.length > 0 ? details.join(" · ") : "—";
};

const formatEntryAction = (entry: ReplicationJobEntryReportItem) =>
  entry.action || "—";

const hasRenderableEntryReport = (entryReport: ReplicationJobDto["entryReport"]): entryReport is NonNullable<ReplicationJobDto["entryReport"]> =>
  Boolean(
    entryReport
      && typeof entryReport.totalEntries === "number"
      && typeof entryReport.successCount === "number"
      && typeof entryReport.failureCount === "number"
      && Array.isArray(entryReport.entries)
  );

const formatScheduleSummary = (definition: ReplicationDefinitionDto) => {
  if (!definition.cronExpression) {
    return "Ad hoc";
  }
  return definition.scheduleTimezone
    ? `${definition.cronExpression} (${definition.scheduleTimezone})`
    : definition.cronExpression;
};

const formatFailurePolicySummary = (definition: ReplicationDefinitionDto) => {
  const summary: string[] = [];
  if (definition.autoRetryEnabled) {
    summary.push(`Auto retry (${definition.maxRetryAttempts} max)`);
    summary.push(`${definition.retryBackoffMinutes} min backoff`);
  } else {
    summary.push("Manual retry only");
  }
  summary.push(`Retain ${definition.jobRetentionDays}d`);
  return summary.join(" · ");
};

const formatConflictPolicySummary = (definition: ReplicationDefinitionDto) => {
  switch (definition.conflictPolicy || "RENAME") {
    case "RENAME":
      return "Rename conflicting content";
    case "OVERWRITE":
      return "Overwrite conflicting content";
    default:
      return "Skip existing content";
  }
};

const toTextValue = (value?: string | null) => value || "";

const TransferReplicationPage: React.FC = () => {
  const [targets, setTargets] = useState<TransferTargetDto[]>([]);
  const [receivers, setReceivers] = useState<TransferReceiverDto[]>([]);
  const [definitions, setDefinitions] = useState<ReplicationDefinitionDto[]>(
    []
  );
  const [jobs, setJobs] = useState<ReplicationJobDto[]>([]);
  const [jobsTotal, setJobsTotal] = useState(0);
  const [jobsPage, setJobsPage] = useState(0);
  const [jobsRowsPerPage, setJobsRowsPerPage] = useState(10);
  const [expandedJobId, setExpandedJobId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [targetDialogOpen, setTargetDialogOpen] = useState(false);
  const [receiverDialogOpen, setReceiverDialogOpen] = useState(false);
  const [definitionDialogOpen, setDefinitionDialogOpen] = useState(false);
  const [selectedTargetId, setSelectedTargetId] = useState<string | null>(null);
  const [selectedReceiverId, setSelectedReceiverId] = useState<string | null>(
    null
  );
  const [selectedDefinitionId, setSelectedDefinitionId] = useState<
    string | null
  >(null);
  const [targetForm, setTargetForm] =
    useState<TransferTargetMutationRequest>(EMPTY_TARGET_FORM);
  const [targetFolderName, setTargetFolderName] = useState("");
  const [receiverForm, setReceiverForm] =
    useState<TransferReceiverMutationRequest>(EMPTY_RECEIVER_FORM);
  const [receiverRootFolderName, setReceiverRootFolderName] = useState("");
  const [definitionForm, setDefinitionForm] =
    useState<ReplicationDefinitionFormState>(EMPTY_DEFINITION_FORM);
  const [refreshTick, setRefreshTick] = useState(0);
  const [savingTarget, setSavingTarget] = useState(false);
  const [savingReceiver, setSavingReceiver] = useState(false);
  const [savingDefinition, setSavingDefinition] = useState(false);

  const hasRunningJobs = useMemo(
    () => jobs.some((job) => runningStatuses.has(job.status)),
    [jobs]
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [nextTargets, nextReceivers, nextDefinitions, jobsPageResult] =
        await Promise.all([
          transferReplicationService.listTargets(),
          transferReplicationService.listReceivers(),
          transferReplicationService.listDefinitions(),
          transferReplicationService.listJobs(jobsPage, jobsRowsPerPage),
        ]);
      setTargets(nextTargets);
      setReceivers(nextReceivers);
      setDefinitions(nextDefinitions);
      setJobs(jobsPageResult.content);
      setJobsTotal(jobsPageResult.totalElements);
    } catch {
      toast.error("Failed to load transfer replication data");
    } finally {
      setLoading(false);
    }
  }, [jobsPage, jobsRowsPerPage]);

  useEffect(() => {
    void loadData();
  }, [loadData, refreshTick]);

  useEffect(() => {
    if (!hasRunningJobs) {
      return undefined;
    }
    const timer = window.setInterval(
      () => setRefreshTick((value) => value + 1),
      4000
    );
    return () => window.clearInterval(timer);
  }, [hasRunningJobs]);

  const openTargetDialog = (target?: TransferTargetDto) => {
    if (target) {
      setSelectedTargetId(target.id);
      setTargetForm({
        name: target.name,
        description: target.description || "",
        transportType: target.transportType,
        targetFolderId: target.targetFolderId,
        endpointUrl: target.endpointUrl || "",
        endpointPath: target.endpointPath || "/api/v1",
        authType: target.authType,
        authUsername: target.authUsername || "",
        authSecret: "",
        enabled: target.enabled,
      });
      setTargetFolderName(target.targetFolderName || "");
    } else {
      setSelectedTargetId(null);
      setTargetForm(EMPTY_TARGET_FORM);
      setTargetFolderName("");
    }
    setTargetDialogOpen(true);
  };

  const openReceiverDialog = (receiver?: TransferReceiverDto) => {
    if (receiver) {
      setSelectedReceiverId(receiver.id);
      setReceiverForm({
        name: receiver.name,
        description: receiver.description || "",
        rootFolderId: receiver.rootFolderId,
        authType: receiver.authType,
        authUsername: receiver.authUsername || "",
        authSecret: "",
        enabled: receiver.enabled,
      });
      setReceiverRootFolderName(receiver.rootFolderName || "");
    } else {
      setSelectedReceiverId(null);
      setReceiverForm(EMPTY_RECEIVER_FORM);
      setReceiverRootFolderName("");
    }
    setReceiverDialogOpen(true);
  };

  const openDefinitionDialog = (definition?: ReplicationDefinitionDto) => {
    if (definition) {
      setSelectedDefinitionId(definition.id);
      setDefinitionForm({
        name: definition.name,
        description: definition.description || "",
        sourceNodeId: definition.sourceNodeId,
        transferTargetId: definition.transferTargetId,
        includeChildren: definition.includeChildren,
        enabled: definition.enabled,
        conflictPolicy: definition.conflictPolicy || "RENAME",
        cronExpression: definition.cronExpression || "",
        scheduleTimezone: definition.scheduleTimezone || "UTC",
        autoRetryEnabled: definition.autoRetryEnabled,
        maxRetryAttempts: String(definition.maxRetryAttempts),
        retryBackoffMinutes: String(definition.retryBackoffMinutes),
        jobRetentionDays: String(definition.jobRetentionDays),
      });
    } else {
      setSelectedDefinitionId(null);
      setDefinitionForm({
        ...EMPTY_DEFINITION_FORM,
        transferTargetId: targets[0]?.id || "",
        conflictPolicy: "RENAME",
      });
    }
    setDefinitionDialogOpen(true);
  };

  const closeTargetDialog = () => {
    setTargetDialogOpen(false);
    setSelectedTargetId(null);
    setTargetForm(EMPTY_TARGET_FORM);
    setTargetFolderName("");
  };

  const closeReceiverDialog = () => {
    setReceiverDialogOpen(false);
    setSelectedReceiverId(null);
    setReceiverForm(EMPTY_RECEIVER_FORM);
    setReceiverRootFolderName("");
  };

  const closeDefinitionDialog = () => {
    setDefinitionDialogOpen(false);
    setSelectedDefinitionId(null);
    setDefinitionForm(EMPTY_DEFINITION_FORM);
  };

  const buildTargetPayload = (): TransferTargetMutationRequest => {
    const base: TransferTargetMutationRequest = {
      name: targetForm.name.trim(),
      description: targetForm.description?.trim() || undefined,
      transportType: targetForm.transportType,
      targetFolderId: targetForm.targetFolderId.trim(),
      enabled: targetForm.enabled,
    };

    if (targetForm.transportType === "ATHENA_HTTP") {
      base.endpointUrl = targetForm.endpointUrl?.trim() || undefined;
      base.endpointPath = targetForm.endpointPath?.trim() || undefined;
      base.authType = targetForm.authType;
      base.authUsername = targetForm.authUsername?.trim() || undefined;
      if (targetForm.authSecret?.trim()) {
        base.authSecret = targetForm.authSecret.trim();
      }
    }

    return base;
  };

  const buildReceiverPayload = (): TransferReceiverMutationRequest => ({
    name: receiverForm.name.trim(),
    description: receiverForm.description?.trim() || undefined,
    rootFolderId: receiverForm.rootFolderId.trim(),
    authType: receiverForm.authType,
    authUsername: receiverForm.authUsername?.trim() || undefined,
    authSecret: receiverForm.authSecret?.trim() || undefined,
    enabled: receiverForm.enabled,
  });

  const handleSaveTarget = async () => {
    setSavingTarget(true);
    try {
      const payload = buildTargetPayload();
      if (selectedTargetId) {
        await transferReplicationService.updateTarget(
          selectedTargetId,
          payload
        );
        toast.success("Transfer target updated");
      } else {
        await transferReplicationService.createTarget(payload);
        toast.success("Transfer target created");
      }
      closeTargetDialog();
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message || "Failed to save transfer target"
      );
    } finally {
      setSavingTarget(false);
    }
  };

  const handleSaveReceiver = async () => {
    setSavingReceiver(true);
    try {
      const payload = buildReceiverPayload();
      if (selectedReceiverId) {
        await transferReplicationService.updateReceiver(
          selectedReceiverId,
          payload
        );
        toast.success("Receiver registry entry updated");
      } else {
        await transferReplicationService.createReceiver(payload);
        toast.success("Receiver registry entry created");
      }
      closeReceiverDialog();
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message ||
          "Failed to save receiver registry entry"
      );
    } finally {
      setSavingReceiver(false);
    }
  };

  const handleVerifyReceiver = async (receiverId: string) => {
    try {
      await transferReplicationService.verifyReceiver(receiverId);
      toast.success("Receiver registry entry verified");
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message || "Receiver verification failed"
      );
      setRefreshTick((value) => value + 1);
    }
  };

  const handleDeleteReceiver = async (receiverId: string) => {
    if (!window.confirm("Delete this receiver registry entry?")) {
      return;
    }
    try {
      await transferReplicationService.deleteReceiver(receiverId);
      toast.success("Receiver registry entry deleted");
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message ||
          "Failed to delete receiver registry entry"
      );
    }
  };

  const handleVerifyTarget = async (targetId: string) => {
    try {
      await transferReplicationService.verifyTarget(targetId);
      toast.success("Transfer target verified");
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message || "Target verification failed"
      );
      setRefreshTick((value) => value + 1);
    }
  };

  const handleDeleteTarget = async (targetId: string) => {
    if (!window.confirm("Delete this transfer target?")) {
      return;
    }
    try {
      await transferReplicationService.deleteTarget(targetId);
      toast.success("Transfer target deleted");
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message || "Failed to delete transfer target"
      );
    }
  };

  const handleSaveDefinition = async () => {
    setSavingDefinition(true);
    try {
      const payload = buildReplicationDefinitionRequest(definitionForm);
      if (selectedDefinitionId) {
        await transferReplicationService.updateDefinition(
          selectedDefinitionId,
          payload
        );
        toast.success("Replication definition updated");
      } else {
        await transferReplicationService.createDefinition(payload);
        toast.success("Replication definition created");
      }
      closeDefinitionDialog();
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message ||
          "Failed to save replication definition"
      );
    } finally {
      setSavingDefinition(false);
    }
  };

  const handleDeleteDefinition = async (definitionId: string) => {
    if (!window.confirm("Delete this replication definition?")) {
      return;
    }
    try {
      await transferReplicationService.deleteDefinition(definitionId);
      toast.success("Replication definition deleted");
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message ||
          "Failed to delete replication definition"
      );
    }
  };

  const handleRunDefinition = async (definitionId: string) => {
    try {
      await transferReplicationService.runDefinition(definitionId);
      toast.success("Replication job queued");
      setJobsPage(0);
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message || "Failed to start replication job"
      );
    }
  };

  const handleRetryJob = async (jobId: string) => {
    try {
      await transferReplicationService.retryJob(jobId);
      toast.success("Replication retry queued");
      setJobsPage(0);
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(
        error?.response?.data?.message || "Failed to retry replication job"
      );
    }
  };

  const toggleJobDetails = (jobId: string) => {
    setExpandedJobId((current) => (current === jobId ? null : jobId));
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        spacing={2}
        sx={{ mb: 3 }}
      >
        <Box>
          <Typography variant="h4" gutterBottom>
            Transfer Replication
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage outbound transfer targets, replication definitions, remote
            verification, and execution jobs.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<Refresh />}
          onClick={() => setRefreshTick((value) => value + 1)}
          disabled={loading}
        >
          Refresh
        </Button>
      </Stack>

      <Stack spacing={3}>
        <Alert severity="info">
          LOOPBACK keeps local same-repo replication. ATHENA_HTTP verifies a
          remote Athena target and replicates content via folder and upload
          APIs.
        </Alert>

        <Grid container spacing={3}>
          <Grid item xs={12} xl={6}>
            <Card>
              <CardContent>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="center"
                  sx={{ mb: 2 }}
                >
                  <Box>
                    <Typography variant="h6">Transfer Targets</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Configure local loopback and remote Athena outbound
                      destinations.
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    startIcon={<Add />}
                    onClick={() => openTargetDialog()}
                  >
                    New Target
                  </Button>
                </Stack>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Transport</TableCell>
                      <TableCell>Folder</TableCell>
                      <TableCell>Verification</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {targets.map((target) => (
                      <TableRow key={target.id} hover>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="body2" fontWeight={600}>
                              {target.name}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {target.description ||
                                target.endpointUrl ||
                                "No description"}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={target.transportType} />
                        </TableCell>
                        <TableCell sx={{ maxWidth: 180 }}>
                          <Typography
                            variant="body2"
                            noWrap
                            title={
                              target.targetFolderName || target.targetFolderId
                            }
                          >
                            {target.targetFolderName || target.targetFolderId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip
                              size="small"
                              label={target.verificationStatus}
                              color={
                                verificationColor[target.verificationStatus]
                              }
                            />
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {target.verificationMessage ||
                                formatTimestamp(target.lastVerifiedAt)}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">
                          <Stack
                            direction="row"
                            justifyContent="flex-end"
                            spacing={1}
                          >
                            <Button
                              size="small"
                              onClick={() => openTargetDialog(target)}
                            >
                              Edit
                            </Button>
                            <Button
                              size="small"
                              startIcon={<PublishedWithChanges />}
                              onClick={() => void handleVerifyTarget(target.id)}
                            >
                              Verify
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              startIcon={<Delete />}
                              onClick={() => void handleDeleteTarget(target.id)}
                            >
                              Delete
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {targets.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5}>
                          <Typography variant="body2" color="text.secondary">
                            No transfer targets configured yet.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} xl={6}>
            <Card>
              <CardContent>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="center"
                  sx={{ mb: 2 }}
                >
                  <Box>
                    <Typography variant="h6">
                      Replication Definitions
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Bind source nodes to transfer targets, author
                      conflict/schedule/failure policy, and queue outbound
                      replication jobs.
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    startIcon={<Add />}
                    onClick={() => openDefinitionDialog()}
                    disabled={targets.length === 0}
                  >
                    New Definition
                  </Button>
                </Stack>

                {targets.length === 0 && (
                  <Alert severity="warning" sx={{ mb: 2 }}>
                    Create at least one transfer target before adding
                    replication definitions.
                  </Alert>
                )}
                <Alert severity="info" sx={{ mb: 2 }}>
                  Conflict, schedule, retry, and retention policy are authored
                  on replication definitions. Conflict policy is surfaced for
                  operator intent and sent with definition requests; the backend
                  currently uses the existing schedule and retry fields.
                </Alert>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Source</TableCell>
                      <TableCell>Target</TableCell>
                      <TableCell>Schedule</TableCell>
                      <TableCell>Failure Policy</TableCell>
                      <TableCell>Last Run</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {definitions.map((definition) => (
                      <TableRow key={definition.id} hover>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="body2" fontWeight={600}>
                              {definition.name}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {definition.includeChildren
                                ? "Deep copy enabled"
                                : "Single node only"}
                            </Typography>
                            <Stack
                              direction="row"
                              spacing={0.5}
                              flexWrap="wrap"
                              useFlexGap
                            >
                              <Chip
                                size="small"
                                variant="outlined"
                                label={`Schedule: ${formatScheduleSummary(
                                  definition
                                )}`}
                              />
                              <Chip
                                size="small"
                                variant="outlined"
                                label={`Conflict: ${formatConflictPolicySummary(
                                  definition
                                )}`}
                              />
                              <Chip
                                size="small"
                                variant="outlined"
                                label={`Policy: ${formatFailurePolicySummary(
                                  definition
                                )}`}
                              />
                            </Stack>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            noWrap
                            title={
                              definition.sourceNodeName ||
                              definition.sourceNodeId
                            }
                          >
                            {definition.sourceNodeName ||
                              definition.sourceNodeId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            noWrap
                            title={
                              definition.transferTargetName ||
                              definition.transferTargetId
                            }
                          >
                            {definition.transferTargetName ||
                              definition.transferTargetId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            noWrap
                            title={definition.cronExpression || ""}
                          >
                            {definition.cronExpression
                              ? definition.cronExpression
                              : "Ad hoc"}
                          </Typography>
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            noWrap
                            title={definition.scheduleTimezone || ""}
                          >
                            {definition.scheduleTimezone || "UTC"}
                          </Typography>
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            display="block"
                          >
                            Next: {formatTimestamp(definition.nextRunAt)}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            noWrap
                            title={formatFailurePolicySummary(definition)}
                          >
                            {formatFailurePolicySummary(definition)}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Auto retry and retention are enforced server-side.
                          </Typography>
                        </TableCell>
                        <TableCell>
                          {formatTimestamp(definition.lastRunAt)}
                        </TableCell>
                        <TableCell align="right">
                          <Stack
                            direction="row"
                            justifyContent="flex-end"
                            spacing={1}
                          >
                            <Button
                              size="small"
                              onClick={() => openDefinitionDialog(definition)}
                            >
                              Edit
                            </Button>
                            <Button
                              size="small"
                              startIcon={<PlayArrow />}
                              onClick={() =>
                                void handleRunDefinition(definition.id)
                              }
                            >
                              Run
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              startIcon={<Delete />}
                              onClick={() =>
                                void handleDeleteDefinition(definition.id)
                              }
                            >
                              Delete
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {definitions.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={7}>
                          <Typography variant="body2" color="text.secondary">
                            No replication definitions configured yet.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="center"
                  sx={{ mb: 2 }}
                >
                  <Box>
                    <Typography variant="h6">Receiver Registry</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Manage inbound receiver roots, auth mode, verification,
                      and last access diagnostics.
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    startIcon={<Add />}
                    onClick={() => openReceiverDialog()}
                  >
                    New Receiver
                  </Button>
                </Stack>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Root Folder</TableCell>
                      <TableCell>Auth</TableCell>
                      <TableCell>Verification</TableCell>
                      <TableCell>Last Access</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {receivers.map((receiver) => (
                      <TableRow key={receiver.id} hover>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="body2" fontWeight={600}>
                              {receiver.name}
                            </Typography>
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {receiver.description || "No description"}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 180 }}>
                          <Typography
                            variant="body2"
                            noWrap
                            title={
                              receiver.rootFolderName || receiver.rootFolderId
                            }
                          >
                            {receiver.rootFolderName || receiver.rootFolderId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip size="small" label={receiver.authType} />
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {receiver.authSecretConfigured
                                ? "Secret configured"
                                : "No secret configured"}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip
                              size="small"
                              label={receiver.verificationStatus}
                              color={
                                verificationColor[receiver.verificationStatus]
                              }
                            />
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {receiver.verificationMessage ||
                                formatTimestamp(receiver.lastVerifiedAt)}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip
                              size="small"
                              label={receiver.lastAccessStatus}
                              color={
                                receiverAccessColor[receiver.lastAccessStatus]
                              }
                            />
                            <Typography
                              variant="caption"
                              color="text.secondary"
                            >
                              {receiver.lastAccessMessage ||
                                formatTimestamp(receiver.lastAccessedAt)}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">
                          <Stack
                            direction="row"
                            justifyContent="flex-end"
                            spacing={1}
                          >
                            <Button
                              size="small"
                              onClick={() => openReceiverDialog(receiver)}
                            >
                              Edit
                            </Button>
                            <Button
                              size="small"
                              startIcon={<PublishedWithChanges />}
                              onClick={() =>
                                void handleVerifyReceiver(receiver.id)
                              }
                            >
                              Verify
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              startIcon={<Delete />}
                              onClick={() =>
                                void handleDeleteReceiver(receiver.id)
                              }
                            >
                              Delete
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {receivers.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <Typography variant="body2" color="text.secondary">
                            No receiver registry entries configured yet.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        <Card>
          <CardContent>
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="center"
              sx={{ mb: 2 }}
            >
              <Box>
                <Typography variant="h6">Replication Jobs</Typography>
                <Typography variant="body2" color="text.secondary">
                  Monitor queued, running, completed, and failed outbound
                  replication jobs, including transport diagnostics and manual
                  retry.
                </Typography>
              </Box>
              <Chip
                size="small"
                color={hasRunningJobs ? "primary" : "default"}
                label={hasRunningJobs ? "Auto-refreshing" : "Idle"}
              />
            </Stack>

            <Paper variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell />
                    <TableCell>Status</TableCell>
                    <TableCell>Attempt</TableCell>
                    <TableCell>Transport</TableCell>
                    <TableCell>User</TableCell>
                    <TableCell>Source Node</TableCell>
                    <TableCell>Target</TableCell>
                    <TableCell>Message</TableCell>
                    <TableCell>Last Attempt</TableCell>
                    <TableCell>Started</TableCell>
                    <TableCell>Completed</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {jobs.map((job) => {
                    const expanded = expandedJobId === job.id;
                    const entryReport = hasRenderableEntryReport(job.entryReport) ? job.entryReport : null;
                    const entryRows = entryReport?.entries || [];

                    return (
                      <React.Fragment key={job.id}>
                        <TableRow hover>
                          <TableCell sx={{ width: 48 }}>
                            <IconButton
                              size="small"
                              aria-label={
                                expanded
                                  ? `Collapse job ${job.id}`
                                  : `Expand job ${job.id}`
                              }
                              onClick={() => toggleJobDetails(job.id)}
                            >
                              {expanded ? (
                                <ExpandLess fontSize="small" />
                              ) : (
                                <ExpandMore fontSize="small" />
                              )}
                            </IconButton>
                          </TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={job.status}
                              color={jobColor[job.status]}
                            />
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Typography variant="body2">
                                {job.attemptNumber}
                              </Typography>
                              <Typography
                                variant="caption"
                                color="text.secondary"
                              >
                                {job.retryOfJobId ? "Retry" : "Initial run"}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Chip
                                size="small"
                                label={job.transportStatus}
                                color={transportColor[job.transportStatus]}
                              />
                              <Typography
                                variant="caption"
                                color="text.secondary"
                                noWrap
                                title={job.transportMessage || ""}
                              >
                                {job.transportMessage || "—"}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{job.userId}</TableCell>
                          <TableCell sx={{ maxWidth: 180 }}>
                            <Typography
                              variant="body2"
                              noWrap
                              title={job.sourceNodeId}
                            >
                              {job.sourceNodeId}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 180 }}>
                            <Typography
                              variant="body2"
                              noWrap
                              title={job.transferTargetId}
                            >
                              {job.transferTargetId}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 260 }}>
                            <Typography
                              variant="body2"
                              noWrap
                              title={job.errorLog || job.lastMessage || ""}
                            >
                              {job.errorLog || job.lastMessage || "—"}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            {formatTimestamp(job.lastAttemptedAt)}
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Typography variant="body2">
                                {formatTimestamp(job.startedAt)}
                              </Typography>
                              {job.scheduledFor && (
                                <Typography
                                  variant="caption"
                                  color="text.secondary"
                                >
                                  Scheduled {formatTimestamp(job.scheduledFor)}
                                </Typography>
                              )}
                            </Stack>
                          </TableCell>
                          <TableCell>
                            {formatTimestamp(job.completedAt)}
                          </TableCell>
                          <TableCell align="right">
                            {job.status === "FAILED" ||
                            job.status === "CANCELED" ? (
                              <Button
                                size="small"
                                startIcon={<Replay />}
                                onClick={() => void handleRetryJob(job.id)}
                              >
                                Retry
                              </Button>
                            ) : (
                              <Typography
                                variant="caption"
                                color="text.secondary"
                              >
                                —
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell
                            colSpan={12}
                            sx={{
                              p: 0,
                              borderBottom: expanded ? "none" : undefined,
                            }}
                          >
                            <Collapse
                              in={expanded}
                              timeout="auto"
                              unmountOnExit
                            >
                              <Box
                                sx={{ px: 3, py: 2, bgcolor: "action.hover" }}
                              >
                                <Stack spacing={2}>
                                  <Stack
                                    direction={{ xs: "column", md: "row" }}
                                    spacing={1}
                                    justifyContent="space-between"
                                    alignItems={{
                                      xs: "flex-start",
                                      md: "center",
                                    }}
                                  >
                                    <Box>
                                      <Typography variant="subtitle2">
                                        Entry report
                                      </Typography>
                                      <Typography
                                        variant="body2"
                                        color="text.secondary"
                                      >
                                        Per-entry execution results for this
                                        replication job.
                                      </Typography>
                                    </Box>
                                    {entryReport && (
                                      <Stack
                                        direction="row"
                                        spacing={1}
                                        flexWrap="wrap"
                                        useFlexGap
                                      >
                                        <Chip
                                          size="small"
                                          label={`Total ${entryReport.totalEntries}`}
                                          variant="outlined"
                                        />
                                        <Chip
                                          size="small"
                                          label={`Succeeded ${entryReport.successCount}`}
                                          color="success"
                                          variant="outlined"
                                        />
                                        <Chip
                                          size="small"
                                          label={`Failed ${entryReport.failureCount}`}
                                          color="error"
                                          variant="outlined"
                                        />
                                      </Stack>
                                    )}
                                  </Stack>
                                  {job.reportTruncated && (
                                    <Alert severity="warning">
                                      The entry report was truncated by the
                                      backend, so only the first returned
                                      entries are shown here.
                                    </Alert>
                                  )}
                                  {entryRows.length > 0 ? (
                                    <Table size="small">
                                      <TableHead>
                                        <TableRow>
                                          <TableCell>Action</TableCell>
                                          <TableCell>Source</TableCell>
                                          <TableCell>Target Node</TableCell>
                                          <TableCell>Message</TableCell>
                                          <TableCell>Started</TableCell>
                                          <TableCell>Completed</TableCell>
                                        </TableRow>
                                      </TableHead>
                                      <TableBody>
                                        {entryRows.map((entry, index) => (
                                          <TableRow
                                            key={`${job.id}-${entry.sourceNodeId || "source"}-${entry.sourcePath || "path"}-${index}`}
                                          >
                                            <TableCell sx={{ width: 140 }}>
                                              <Stack spacing={0.25}>
                                                <Typography
                                                  variant="body2"
                                                  fontWeight={600}
                                                >
                                                  {formatEntryAction(entry)}
                                                </Typography>
                                                <Typography
                                                  variant="caption"
                                                  color="text.secondary"
                                                >
                                                  {formatEntrySourceDetail(
                                                    entry
                                                  )}
                                                </Typography>
                                              </Stack>
                                            </TableCell>
                                            <TableCell sx={{ maxWidth: 260 }}>
                                              <Typography
                                                variant="body2"
                                                noWrap
                                                title={formatEntrySource(entry)}
                                              >
                                                {formatEntrySource(entry)}
                                              </Typography>
                                              {entry.sourceNodeId &&
                                                entry.sourcePath && (
                                                  <Typography
                                                    variant="caption"
                                                    color="text.secondary"
                                                    display="block"
                                                    noWrap
                                                    title={entry.sourceNodeId}
                                                  >
                                                    Source node{" "}
                                                    {entry.sourceNodeId}
                                                  </Typography>
                                                )}
                                            </TableCell>
                                            <TableCell sx={{ maxWidth: 180 }}>
                                              <Typography
                                                variant="body2"
                                                noWrap
                                                title={entry.targetNodeId || ""}
                                              >
                                                {entry.targetNodeId || "—"}
                                              </Typography>
                                            </TableCell>
                                            <TableCell sx={{ maxWidth: 320 }}>
                                              <Typography
                                                variant="body2"
                                                noWrap
                                                title={entry.message || ""}
                                              >
                                                {entry.message || "—"}
                                              </Typography>
                                            </TableCell>
                                            <TableCell>
                                              {formatTimestamp(entry.startedAt)}
                                            </TableCell>
                                            <TableCell>
                                              {formatTimestamp(
                                                entry.completedAt
                                              )}
                                            </TableCell>
                                          </TableRow>
                                        ))}
                                      </TableBody>
                                    </Table>
                                  ) : (
                                    <Typography
                                      variant="body2"
                                      color="text.secondary"
                                    >
                                      {entryReport
                                        ? "No per-entry results were returned for this job."
                                        : "No entry report was returned for this job yet."}
                                    </Typography>
                                  )}
                                  <Divider />
                                  <Typography
                                    variant="caption"
                                    color="text.secondary"
                                  >
                                    Timestamps reflect the backend job record
                                    and the per-entry execution window.
                                  </Typography>
                                </Stack>
                              </Box>
                            </Collapse>
                          </TableCell>
                        </TableRow>
                      </React.Fragment>
                    );
                  })}
                  {jobs.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={12}>
                        <Typography variant="body2" color="text.secondary">
                          No replication jobs yet.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <TablePagination
                component="div"
                count={jobsTotal}
                page={jobsPage}
                rowsPerPage={jobsRowsPerPage}
                onPageChange={(_, nextPage) => setJobsPage(nextPage)}
                onRowsPerPageChange={(event) => {
                  setJobsRowsPerPage(Number(event.target.value));
                  setJobsPage(0);
                }}
                rowsPerPageOptions={[10, 20, 50]}
              />
            </Paper>
          </CardContent>
        </Card>
      </Stack>

      <Dialog
        open={targetDialogOpen}
        onClose={closeTargetDialog}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {selectedTargetId ? "Edit Transfer Target" : "New Transfer Target"}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={targetForm.name}
              onChange={(event) =>
                setTargetForm((current) => ({
                  ...current,
                  name: event.target.value,
                }))
              }
              fullWidth
            />
            <TextField
              label="Description"
              value={targetForm.description || ""}
              onChange={(event) =>
                setTargetForm((current) => ({
                  ...current,
                  description: event.target.value,
                }))
              }
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="transfer-target-transport-label">
                Transport
              </InputLabel>
              <Select
                labelId="transfer-target-transport-label"
                label="Transport"
                value={targetForm.transportType}
                onChange={(event) => {
                  const transportType = event.target.value as TransportType;
                  setTargetForm((current) => ({
                    ...current,
                    transportType,
                    authType:
                      transportType === "ATHENA_HTTP"
                        ? current.authType || "NONE"
                        : "NONE",
                  }));
                  if (transportType === "ATHENA_HTTP") {
                    setTargetFolderName("");
                  }
                }}
              >
                <MenuItem value="LOOPBACK">LOOPBACK</MenuItem>
                <MenuItem value="ATHENA_HTTP">ATHENA_HTTP</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label="Target Folder ID"
              value={targetForm.targetFolderId}
              onChange={(event) => {
                setTargetForm((current) => ({
                  ...current,
                  targetFolderId: event.target.value,
                }));
                setTargetFolderName("");
              }}
              helperText={
                targetFolderName
                  ? `Selected: ${targetFolderName}`
                  : "LOOPBACK uses a local folder UUID. ATHENA_HTTP uses a remote folder UUID."
              }
              fullWidth
            />
            {targetForm.transportType === "LOOPBACK" && (
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  Pick Local Target Folder
                </Typography>
                <Box
                  sx={{
                    border: 1,
                    borderColor: "divider",
                    borderRadius: 1,
                    p: 1,
                    height: 260,
                    overflow: "auto",
                  }}
                >
                  <FolderTree
                    rootNodeId="root"
                    selectedNodeId={targetForm.targetFolderId || undefined}
                    variant="picker"
                    onNodeSelect={(node) => {
                      if (node.nodeType !== "FOLDER") {
                        return;
                      }
                      setTargetForm((current) => ({
                        ...current,
                        targetFolderId: node.id,
                      }));
                      setTargetFolderName(node.name);
                    }}
                  />
                </Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ display: "block", mt: 0.5 }}
                >
                  Local LOOPBACK targets can be picked from the repository tree.
                  Remote ATHENA_HTTP targets still require a remote folder UUID.
                </Typography>
              </Box>
            )}
            {targetForm.transportType === "ATHENA_HTTP" && (
              <>
                <TextField
                  label="Endpoint URL"
                  value={targetForm.endpointUrl || ""}
                  onChange={(event) =>
                    setTargetForm((current) => ({
                      ...current,
                      endpointUrl: event.target.value,
                    }))
                  }
                  placeholder="https://replica.example.com"
                  fullWidth
                />
                <TextField
                  label="Endpoint Path"
                  value={targetForm.endpointPath || "/api/v1"}
                  onChange={(event) =>
                    setTargetForm((current) => ({
                      ...current,
                      endpointPath: event.target.value,
                    }))
                  }
                  fullWidth
                />
                <FormControl fullWidth>
                  <InputLabel id="transfer-target-auth-label">
                    Auth Type
                  </InputLabel>
                  <Select
                    labelId="transfer-target-auth-label"
                    label="Auth Type"
                    value={targetForm.authType || "NONE"}
                    onChange={(event) =>
                      setTargetForm((current) => ({
                        ...current,
                        authType: event.target.value as AuthType,
                      }))
                    }
                  >
                    <MenuItem value="NONE">NONE</MenuItem>
                    <MenuItem value="BASIC">BASIC</MenuItem>
                    <MenuItem value="BEARER">BEARER</MenuItem>
                  </Select>
                </FormControl>
                {targetForm.authType === "BASIC" && (
                  <TextField
                    label="Auth Username"
                    value={targetForm.authUsername || ""}
                    onChange={(event) =>
                      setTargetForm((current) => ({
                        ...current,
                        authUsername: event.target.value,
                      }))
                    }
                    fullWidth
                  />
                )}
                {targetForm.authType !== "NONE" && (
                  <TextField
                    label={
                      selectedTargetId
                        ? "Auth Secret (leave blank to keep current)"
                        : "Auth Secret"
                    }
                    type="password"
                    value={targetForm.authSecret || ""}
                    onChange={(event) =>
                      setTargetForm((current) => ({
                        ...current,
                        authSecret: event.target.value,
                      }))
                    }
                    fullWidth
                  />
                )}
              </>
            )}
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(targetForm.enabled)}
                  onChange={(event) =>
                    setTargetForm((current) => ({
                      ...current,
                      enabled: event.target.checked,
                    }))
                  }
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeTargetDialog}>Cancel</Button>
          <Button
            variant="contained"
            startIcon={<Save />}
            onClick={() => void handleSaveTarget()}
            disabled={savingTarget}
          >
            {selectedTargetId ? "Save Target" : "Create Target"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={definitionDialogOpen}
        onClose={closeDefinitionDialog}
        fullWidth
        maxWidth="md"
      >
        <DialogTitle>
          {selectedDefinitionId
            ? "Edit Replication Definition"
            : "New Replication Definition"}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={definitionForm.name}
              onChange={(event) =>
                setDefinitionForm((current) => ({
                  ...current,
                  name: event.target.value,
                }))
              }
              fullWidth
            />
            <TextField
              label="Description"
              value={definitionForm.description || ""}
              onChange={(event) =>
                setDefinitionForm((current) => ({
                  ...current,
                  description: event.target.value,
                }))
              }
              fullWidth
            />
            <TextField
              label="Source Node ID"
              value={definitionForm.sourceNodeId}
              onChange={(event) =>
                setDefinitionForm((current) => ({
                  ...current,
                  sourceNodeId: event.target.value,
                }))
              }
              helperText="Document or folder UUID to replicate."
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="replication-definition-target-label">
                Transfer Target
              </InputLabel>
              <Select
                labelId="replication-definition-target-label"
                label="Transfer Target"
                value={definitionForm.transferTargetId}
                onChange={(event) =>
                  setDefinitionForm((current) => ({
                    ...current,
                    transferTargetId: event.target.value,
                  }))
                }
              >
                {targets.map((target) => (
                  <MenuItem key={target.id} value={target.id}>
                    {target.name} ({target.transportType})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(definitionForm.includeChildren)}
                  onChange={(event) =>
                    setDefinitionForm((current) => ({
                      ...current,
                      includeChildren: event.target.checked,
                    }))
                  }
                />
              }
              label="Include children"
            />
            <FormControl fullWidth>
              <InputLabel id="replication-definition-conflict-policy-label">
                Conflict Policy
              </InputLabel>
              <Select
                labelId="replication-definition-conflict-policy-label"
                label="Conflict Policy"
                value={definitionForm.conflictPolicy}
                onChange={(event) =>
                  setDefinitionForm((current) => ({
                    ...current,
                    conflictPolicy: event.target
                      .value as ReplicationConflictPolicy,
                  }))
                }
              >
                <MenuItem value="RENAME">Rename conflicting content</MenuItem>
                <MenuItem value="SKIP">Skip existing content</MenuItem>
                <MenuItem value="OVERWRITE">
                  Overwrite conflicting content
                </MenuItem>
              </Select>
            </FormControl>
            <Typography variant="caption" color="text.secondary">
              This policy is sent with the definition request so operators can
              capture intent even before the backend persists it.
            </Typography>
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(definitionForm.enabled)}
                  onChange={(event) =>
                    setDefinitionForm((current) => ({
                      ...current,
                      enabled: event.target.checked,
                    }))
                  }
                />
              }
              label="Enabled"
            />
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Schedule & Failure Policy
              </Typography>
              <Stack spacing={2}>
                <TextField
                  label="Cron Expression"
                  value={toTextValue(definitionForm.cronExpression)}
                  onChange={(event) =>
                    setDefinitionForm((current) => ({
                      ...current,
                      cronExpression: event.target.value,
                    }))
                  }
                  helperText="Leave blank for ad hoc execution only. Example: 0 0 * * *"
                  fullWidth
                />
                <TextField
                  label="Schedule Timezone"
                  value={toTextValue(definitionForm.scheduleTimezone)}
                  onChange={(event) =>
                    setDefinitionForm((current) => ({
                      ...current,
                      scheduleTimezone: event.target.value,
                    }))
                  }
                  helperText="Defaults to UTC when a cron expression is set."
                  fullWidth
                />
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={Boolean(definitionForm.autoRetryEnabled)}
                      onChange={(event) =>
                        setDefinitionForm((current) => ({
                          ...current,
                          autoRetryEnabled: event.target.checked,
                        }))
                      }
                    />
                  }
                  label="Enable automatic retry"
                />
                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Max Retry Attempts"
                      type="number"
                      value={toTextValue(definitionForm.maxRetryAttempts)}
                      onChange={(event) =>
                        setDefinitionForm((current) => ({
                          ...current,
                          maxRetryAttempts: event.target.value,
                        }))
                      }
                      helperText="Used only when automatic retry is enabled."
                      disabled={!definitionForm.autoRetryEnabled}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Retry Backoff (minutes)"
                      type="number"
                      value={toTextValue(definitionForm.retryBackoffMinutes)}
                      onChange={(event) =>
                        setDefinitionForm((current) => ({
                          ...current,
                          retryBackoffMinutes: event.target.value,
                        }))
                      }
                      helperText="Delay before a scheduled retry job becomes runnable."
                      disabled={!definitionForm.autoRetryEnabled}
                      fullWidth
                    />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField
                      label="Job Retention (days)"
                      type="number"
                      value={toTextValue(definitionForm.jobRetentionDays)}
                      onChange={(event) =>
                        setDefinitionForm((current) => ({
                          ...current,
                          jobRetentionDays: event.target.value,
                        }))
                      }
                      helperText="Completed, failed, and canceled jobs older than this are cleaned up."
                      fullWidth
                    />
                  </Grid>
                </Grid>
              </Stack>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDefinitionDialog}>Cancel</Button>
          <Button
            variant="contained"
            startIcon={<Save />}
            onClick={() => void handleSaveDefinition()}
            disabled={savingDefinition}
          >
            {selectedDefinitionId ? "Save Definition" : "Create Definition"}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={receiverDialogOpen}
        onClose={closeReceiverDialog}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {selectedReceiverId
            ? "Edit Receiver Registry Entry"
            : "New Receiver Registry Entry"}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={receiverForm.name}
              onChange={(event) =>
                setReceiverForm((current) => ({
                  ...current,
                  name: event.target.value,
                }))
              }
              fullWidth
            />
            <TextField
              label="Description"
              value={receiverForm.description || ""}
              onChange={(event) =>
                setReceiverForm((current) => ({
                  ...current,
                  description: event.target.value,
                }))
              }
              fullWidth
            />
            <TextField
              label="Root Folder ID"
              value={receiverForm.rootFolderId}
              onChange={(event) => {
                setReceiverForm((current) => ({
                  ...current,
                  rootFolderId: event.target.value,
                }));
                setReceiverRootFolderName("");
              }}
              helperText={
                receiverRootFolderName
                  ? `Selected: ${receiverRootFolderName}`
                  : "The receiver can only accept uploads under this root folder subtree."
              }
              fullWidth
            />
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Pick Root Folder
              </Typography>
              <Box
                sx={{
                  border: 1,
                  borderColor: "divider",
                  borderRadius: 1,
                  p: 1,
                  height: 260,
                  overflow: "auto",
                }}
              >
                <FolderTree
                  rootNodeId="root"
                  selectedNodeId={receiverForm.rootFolderId || undefined}
                  variant="picker"
                  onNodeSelect={(node) => {
                    if (node.nodeType !== "FOLDER") {
                      return;
                    }
                    setReceiverForm((current) => ({
                      ...current,
                      rootFolderId: node.id,
                    }));
                    setReceiverRootFolderName(node.name);
                  }}
                />
              </Box>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: "block", mt: 0.5 }}
              >
                Use the tree to avoid copying UUIDs manually. The text field
                remains editable for remote or pre-known folder IDs.
              </Typography>
            </Box>
            <FormControl fullWidth>
              <InputLabel id="receiver-auth-label">Auth Type</InputLabel>
              <Select
                labelId="receiver-auth-label"
                label="Auth Type"
                value={receiverForm.authType || "NONE"}
                onChange={(event) =>
                  setReceiverForm((current) => ({
                    ...current,
                    authType: event.target.value as AuthType,
                  }))
                }
              >
                <MenuItem value="NONE">NONE</MenuItem>
                <MenuItem value="BASIC">BASIC</MenuItem>
                <MenuItem value="BEARER">BEARER</MenuItem>
              </Select>
            </FormControl>
            {receiverForm.authType === "BASIC" && (
              <TextField
                label="Auth Username"
                value={receiverForm.authUsername || ""}
                onChange={(event) =>
                  setReceiverForm((current) => ({
                    ...current,
                    authUsername: event.target.value,
                  }))
                }
                fullWidth
              />
            )}
            {receiverForm.authType !== "NONE" && (
              <TextField
                label={
                  selectedReceiverId
                    ? "Auth Secret (leave blank to keep current)"
                    : "Auth Secret"
                }
                type="password"
                value={receiverForm.authSecret || ""}
                onChange={(event) =>
                  setReceiverForm((current) => ({
                    ...current,
                    authSecret: event.target.value,
                  }))
                }
                fullWidth
              />
            )}
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(receiverForm.enabled)}
                  onChange={(event) =>
                    setReceiverForm((current) => ({
                      ...current,
                      enabled: event.target.checked,
                    }))
                  }
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeReceiverDialog}>Cancel</Button>
          <Button
            variant="contained"
            startIcon={<Save />}
            onClick={() => void handleSaveReceiver()}
            disabled={savingReceiver}
          >
            {selectedReceiverId ? "Save Receiver" : "Create Receiver"}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TransferReplicationPage;
