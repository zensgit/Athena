package com.ecm.core.service;

import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.UserRepository;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTenantScopeTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private RepositoryService repositoryService;
    @Mock private HistoryService historyService;
    @Mock private NodeRepository nodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private SecurityService securityService;
    @Mock private AuditService auditService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService(
            runtimeService,
            taskService,
            repositoryService,
            historyService,
            nodeRepository,
            userRepository,
            securityService,
            auditService,
            tenantWorkspaceScopeService
        );
    }

    @Test
    @DisplayName("startDocumentApproval rejects documents outside current tenant workspace")
    void startDocumentApprovalRejectsOutOfScopeDocument() {
        UUID documentId = UUID.randomUUID();
        when(tenantWorkspaceScopeService.isNodeVisible(documentId)).thenReturn(false);

        assertThrows(
            ResourceNotFoundException.class,
            () -> workflowService.startDocumentApproval(documentId, List.of("approver"), "Review please")
        );

        verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    @DisplayName("getProcessDetail hides process outside current tenant workspace")
    void getProcessDetailHidesOutOfScopeProcess() {
        String processInstanceId = "pi-1";
        UUID hiddenNodeId = UUID.randomUUID();

        ProcessInstance runtimeInstance = mock(ProcessInstance.class);
        ProcessInstanceQuery processQuery = mock(ProcessInstanceQuery.class);
        HistoricProcessInstanceQuery historicProcessQuery = mock(HistoricProcessInstanceQuery.class);

        when(runtimeService.createProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId(processInstanceId)).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(runtimeInstance);
        when(runtimeInstance.getProcessDefinitionId()).thenReturn(null);
        when(runtimeInstance.getBusinessKey()).thenReturn(hiddenNodeId.toString());
        when(runtimeService.getVariables(processInstanceId)).thenReturn(Map.of("documentId", hiddenNodeId.toString()));

        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.processInstanceId(processInstanceId)).thenReturn(historicProcessQuery);
        when(historicProcessQuery.singleResult()).thenReturn(null);

        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Acme Workspace [acme]");
        when(tenantWorkspaceScopeService.isNodeVisible(hiddenNodeId, "/Acme Workspace [acme]")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> workflowService.getProcessDetail(processInstanceId));
    }

    @Test
    @DisplayName("listProcesses filters out processes outside current tenant workspace")
    void listProcessesFiltersOutOfScopeProcesses() {
        UUID visibleNodeId = UUID.randomUUID();
        UUID hiddenNodeId = UUID.randomUUID();

        HistoricProcessInstance visibleProcess = mock(HistoricProcessInstance.class);
        HistoricProcessInstance hiddenProcess = mock(HistoricProcessInstance.class);
        HistoricProcessInstanceQuery historicProcessQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
        AtomicReference<String> requestedProcessId = new AtomicReference<>();

        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.orderByProcessInstanceStartTime()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.desc()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.list()).thenReturn(List.of(visibleProcess, hiddenProcess));

        when(visibleProcess.getId()).thenReturn("pi-visible");
        when(visibleProcess.getBusinessKey()).thenReturn(visibleNodeId.toString());
        when(visibleProcess.getProcessDefinitionId()).thenReturn(null);
        when(hiddenProcess.getId()).thenReturn("pi-hidden");
        when(hiddenProcess.getBusinessKey()).thenReturn(hiddenNodeId.toString());

        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
        when(variableQuery.processInstanceId(anyString())).thenAnswer(invocation -> {
            requestedProcessId.set(invocation.getArgument(0));
            return variableQuery;
        });

        HistoricVariableInstance visibleVar = mock(HistoricVariableInstance.class);
        when(visibleVar.getVariableName()).thenReturn("documentId");
        when(visibleVar.getValue()).thenReturn(visibleNodeId.toString());
        HistoricVariableInstance hiddenVar = mock(HistoricVariableInstance.class);
        when(hiddenVar.getVariableName()).thenReturn("documentId");
        when(hiddenVar.getValue()).thenReturn(hiddenNodeId.toString());
        when(variableQuery.list()).thenAnswer(invocation ->
            "pi-visible".equals(requestedProcessId.get()) ? List.of(visibleVar) : List.of(hiddenVar)
        );

        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/Acme Workspace [acme]");
        when(tenantWorkspaceScopeService.isNodeVisible(visibleNodeId, "/Acme Workspace [acme]")).thenReturn(true);
        when(tenantWorkspaceScopeService.isNodeVisible(hiddenNodeId, "/Acme Workspace [acme]")).thenReturn(false);

        WorkflowService.WorkflowProcessBrowserPage page = workflowService.listProcesses("ALL", null, null, null, null, 0, 20);

        assertEquals(1, page.items().size());
        assertEquals("pi-visible", page.items().get(0).id());
        assertEquals(1, page.totalItems());
    }
}
