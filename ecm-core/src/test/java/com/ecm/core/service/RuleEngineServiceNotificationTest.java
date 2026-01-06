package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.entity.RuleExecutionResult.ActionExecutionResult;
import com.ecm.core.repository.AutomationRuleRepository;
import com.ecm.core.repository.CategoryRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.TagRepository;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceNotificationTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private TagService tagService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private NodeService nodeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private SecurityService securityService;

    @Mock
    private AuditService auditService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RuleEngineService ruleEngineService;

    @BeforeEach
    void wireNotificationService() {
        ReflectionTestUtils.setField(ruleEngineService, "notificationService", notificationService);
    }

    @Test
    @DisplayName("SEND_NOTIFICATION expands placeholders and uses default title")
    void sendNotificationUsesNotificationService() {
        Document document = buildDocument();

        RuleAction action = RuleAction.builder()
            .type(RuleAction.ActionType.SEND_NOTIFICATION)
            .params(Map.of(
                RuleAction.ParamKeys.RECIPIENT, "alice",
                RuleAction.ParamKeys.MESSAGE, "Doc {documentName} ({documentId})"
            ))
            .build();

        ActionExecutionResult result = ReflectionTestUtils.invokeMethod(
            ruleEngineService,
            "executeAction",
            action,
            document
        );

        assertTrue(result.isSuccess());
        verify(notificationService).notifyUser(
            eq("alice"),
            eq("Rule Notification"),
            eq("Doc " + document.getName() + " (" + document.getId() + ")")
        );
    }

    @Test
    @DisplayName("SEND_NOTIFICATION uses type-specific title when provided")
    void sendNotificationUsesTypeSpecificTitle() {
        Document document = buildDocument();

        RuleAction action = RuleAction.builder()
            .type(RuleAction.ActionType.SEND_NOTIFICATION)
            .params(Map.of(
                RuleAction.ParamKeys.RECIPIENT, "bob",
                RuleAction.ParamKeys.MESSAGE, "Doc {documentName}",
                RuleAction.ParamKeys.NOTIFICATION_TYPE, "email"
            ))
            .build();

        ActionExecutionResult result = ReflectionTestUtils.invokeMethod(
            ruleEngineService,
            "executeAction",
            action,
            document
        );

        assertTrue(result.isSuccess());
        verify(notificationService).notifyUser(
            eq("bob"),
            eq("Rule Notification (email)"),
            eq("Doc " + document.getName())
        );
    }

    private Document buildDocument() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("doc.pdf");
        document.setPath("/doc.pdf");
        document.setMimeType("application/pdf");
        return document;
    }
}
