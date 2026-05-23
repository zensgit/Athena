package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission;
import com.ecm.core.entity.Permission.AuthorityType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.SecurityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityControllerPermissionResponseContractTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private NodeService nodeService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SecurityController controller = new SecurityController(securityService, nodeService);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    @DisplayName("GET /security/nodes/{id}/permissions locks PermissionDto field set and nullable fields")
    void getNodePermissionsLocksPermissionDtoContract() throws Exception {
        Folder node = folder(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        Permission permission = permission(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "legal-team",
            AuthorityType.GROUP,
            PermissionType.READ
        );
        permission.setAllowed(false);
        permission.setInherited(true);
        permission.setExpiryDate(null);
        permission.setNotes(null);

        when(nodeService.getNode(node.getId())).thenReturn(node);
        when(securityService.getNodePermissions(node)).thenReturn(List.of(permission));

        MvcResult result = mockMvc.perform(get("/api/v1/security/nodes/{nodeId}/permissions", node.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(permission.getId().toString()))
            .andExpect(jsonPath("$[0].authority").value("legal-team"))
            .andExpect(jsonPath("$[0].authorityType").value("GROUP"))
            .andExpect(jsonPath("$[0].permission").value("READ"))
            .andExpect(jsonPath("$[0].allowed").value(false))
            .andExpect(jsonPath("$[0].inherited").value(true))
            .andExpect(jsonPath("$[0].expiryDate", nullValue()))
            .andExpect(jsonPath("$[0].notes", nullValue()))
            .andReturn();

        JsonNode permissionNode = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(permissionDtoFieldNames(), fieldNames(permissionNode));
    }

    @Test
    @DisplayName("GET /security/nodes/{id}/permission-diagnostics locks PermissionDecision field set")
    void getPermissionDiagnosticsLocksDecisionContract() throws Exception {
        Folder node = folder(UUID.fromString("33333333-3333-3333-3333-333333333333"));

        when(nodeService.getNode(node.getId())).thenReturn(node);
        when(securityService.hasPermission(node, PermissionType.READ)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.explainPermission(node, PermissionType.WRITE, "alice")).thenReturn(
            new SecurityService.PermissionDecision(
                node.getId(),
                "alice",
                PermissionType.WRITE,
                false,
                "ACL_DENY",
                null,
                List.of("GROUP_editors"),
                List.of("GROUP_legal")
            )
        );

        MvcResult result = mockMvc.perform(get("/api/v1/security/nodes/{nodeId}/permission-diagnostics", node.getId())
                .param("permissionType", "WRITE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value(node.getId().toString()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.permission").value("WRITE"))
            .andExpect(jsonPath("$.allowed").value(false))
            .andExpect(jsonPath("$.reason").value("ACL_DENY"))
            .andExpect(jsonPath("$.dynamicAuthority", nullValue()))
            .andExpect(jsonPath("$.allowedAuthorities[0]").value("GROUP_editors"))
            .andExpect(jsonPath("$.deniedAuthorities[0]").value("GROUP_legal"))
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(permissionDecisionFieldNames(), fieldNames(root));
    }

    @Test
    @DisplayName("GET /security/permission-sets locks permission-set map shape")
    void getPermissionSetsLocksMapShape() throws Exception {
        Map<String, Set<PermissionType>> permissionSets = new LinkedHashMap<>();
        permissionSets.put("CONSUMER", EnumSet.of(PermissionType.READ));
        permissionSets.put("EDITOR", EnumSet.of(PermissionType.READ, PermissionType.WRITE));
        when(securityService.getPermissionSets()).thenReturn(permissionSets);

        mockMvc.perform(get("/api/v1/security/permission-sets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.CONSUMER[0]").value("READ"))
            .andExpect(jsonPath("$.EDITOR[0]").value("READ"))
            .andExpect(jsonPath("$.EDITOR[1]").value("WRITE"));
    }

    @Test
    @DisplayName("GET /security/permission-sets/metadata locks PermissionSetDto field set")
    void getPermissionSetMetadataLocksDtoContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/security/permission-sets/metadata"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("COORDINATOR"))
            .andExpect(jsonPath("$[0].label").value("Coordinator"))
            .andExpect(jsonPath("$[0].description").value("Full control including permission changes"))
            .andExpect(jsonPath("$[0].order").value(1))
            .andExpect(jsonPath("$[0].permissions").isArray())
            .andExpect(jsonPath("$[3].name").value("CONSUMER"))
            .andExpect(jsonPath("$[3].permissions[0]").value("READ"))
            .andReturn();

        JsonNode first = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertEquals(permissionSetDtoFieldNames(), fieldNames(first));
    }

    private static Folder folder(UUID id) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("Contracts");
        folder.setPath("/Contracts");
        return folder;
    }

    private static Permission permission(
        UUID id,
        String authority,
        AuthorityType authorityType,
        PermissionType permissionType
    ) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setAuthority(authority);
        permission.setAuthorityType(authorityType);
        permission.setPermission(permissionType);
        return permission;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> permissionDtoFieldNames() {
        return List.of(
            "id",
            "authority",
            "authorityType",
            "permission",
            "allowed",
            "inherited",
            "expiryDate",
            "notes"
        );
    }

    private static List<String> permissionDecisionFieldNames() {
        return List.of(
            "nodeId",
            "username",
            "permission",
            "allowed",
            "reason",
            "dynamicAuthority",
            "allowedAuthorities",
            "deniedAuthorities"
        );
    }

    private static List<String> permissionSetDtoFieldNames() {
        return List.of(
            "name",
            "label",
            "description",
            "order",
            "permissions"
        );
    }
}
