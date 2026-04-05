package com.ecm.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.entity.RuleAction;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RuleEngineService;
import com.ecm.core.service.ScheduledRuleRunner;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RuleControllerActionDefinitionsTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RuleEngineService ruleEngineService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ScheduledRuleRunner scheduledRuleRunner;

    @Mock
    private AuditService auditService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private RuleController ruleController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(ruleController).build();
    }

    @Test
    @DisplayName("Action definitions endpoint returns supported action metadata")
    void getActionDefinitionsShouldReturnMetadata() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/rules/actions/definitions"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        JsonNode actionsNode = root.get("actions");
        assertNotNull(actionsNode);
        assertTrue(actionsNode.isArray());
        assertEquals(RuleAction.ActionType.values().length, actionsNode.size());

        Map<String, JsonNode> actionsByType = toActionsByType(actionsNode);

        JsonNode addTag = actionsByType.get("ADD_TAG");
        assertNotNull(addTag);
        assertTrue(addTag.get("supported").asBoolean());
        assertTrue(toStringList(addTag.get("requiredParams")).contains(RuleAction.ParamKeys.TAG_NAME));

        JsonNode rename = actionsByType.get("RENAME");
        assertNotNull(rename);
        assertTrue(toStringList(rename.get("constraints")).contains("atLeastOneOf:newName,pattern"));
        assertTrue(toStringList(rename.get("optionalParams")).contains(RuleAction.ParamKeys.NEW_NAME));
        assertTrue(toStringList(rename.get("optionalParams")).contains(RuleAction.ParamKeys.PATTERN));

        JsonNode workflow = actionsByType.get("START_WORKFLOW");
        assertNotNull(workflow);
        assertTrue(toStringList(workflow.get("requiredParams")).contains(RuleAction.ParamKeys.WORKFLOW_KEY));
        assertTrue(toStringList(workflow.get("optionalParams")).contains(RuleAction.ParamKeys.VARIABLES));
        assertTrue(
            toStringList(workflow.get("constraints"))
                .contains("workflowKey=documentApproval requires approvers")
        );

        JsonNode executeScript = actionsByType.get("EXECUTE_SCRIPT");
        assertNotNull(executeScript);
        assertTrue(executeScript.get("supported").asBoolean());
        assertTrue(toStringList(executeScript.get("requiredParams")).contains(RuleAction.ParamKeys.OUTPUT_PROPERTY));
        assertTrue(toStringList(executeScript.get("optionalParams")).contains(RuleAction.ParamKeys.SCRIPT_PATH));
        assertTrue(toStringList(executeScript.get("optionalParams")).contains(RuleAction.ParamKeys.SCRIPT));
        assertTrue(toStringList(executeScript.get("constraints")).contains("atLeastOneOf:scriptPath,script"));
        assertTrue(toStringList(executeScript.get("constraints")).contains("adminOnly"));

        JsonNode renderTemplate = actionsByType.get("RENDER_TEMPLATE");
        assertNotNull(renderTemplate);
        assertTrue(renderTemplate.get("supported").asBoolean());
        assertTrue(toStringList(renderTemplate.get("requiredParams")).contains(RuleAction.ParamKeys.OUTPUT_PROPERTY));
        assertTrue(toStringList(renderTemplate.get("optionalParams")).contains(RuleAction.ParamKeys.TEMPLATE_PATH));
        assertTrue(toStringList(renderTemplate.get("optionalParams")).contains(RuleAction.ParamKeys.TEMPLATE));
        assertTrue(toStringList(renderTemplate.get("constraints")).contains("atLeastOneOf:templatePath,template"));
        assertTrue(toStringList(renderTemplate.get("constraints")).contains("adminOnly"));
    }

    private Map<String, JsonNode> toActionsByType(JsonNode actionsNode) {
        List<JsonNode> actions = new ArrayList<>();
        Iterator<JsonNode> iterator = actionsNode.elements();
        iterator.forEachRemaining(actions::add);
        return actions.stream().collect(Collectors.toMap(node -> node.get("type").asText(), node -> node));
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return values;
        }
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
    }
}
