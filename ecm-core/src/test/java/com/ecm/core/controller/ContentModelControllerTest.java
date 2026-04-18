package com.ecm.core.controller;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ConstraintDefinition;
import com.ecm.core.entity.ConstraintType;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.exception.ModelValidationException;
import com.ecm.core.service.ContentModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentModelControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContentModelService contentModelService;

    @BeforeEach
    void setUp() {
        ContentModelController controller = new ContentModelController(contentModelService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("List models returns DTO graph without entity back references")
    void listModelsReturnsDtoGraph() throws Exception {
        ContentModelDefinition model = buildModelGraph();
        Mockito.when(contentModelService.listModels()).thenReturn(List.of(model));

        mockMvc.perform(get("/api/v1/content-models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].prefix").value("acme"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].types[0].qualifiedName").value("acme:invoice"))
            .andExpect(jsonPath("$[0].types[0].properties[0].constraints[0].constraintType").value("REGEX"))
            .andExpect(jsonPath("$[0].types[0].model").doesNotExist())
            .andExpect(jsonPath("$[0].aspects[0].qualifiedName").value("acme:classifiable"));
    }

    @Test
    @DisplayName("Create model returns created DTO")
    void createModelReturnsCreatedDto() throws Exception {
        ContentModelDefinition created = new ContentModelDefinition();
        created.setId(UUID.randomUUID());
        created.setPrefix("ops");
        created.setNamespaceUri("http://example.com/model/ops/1.0");
        created.setName("Operations");
        created.setStatus(ModelStatus.DRAFT);

        Mockito.when(contentModelService.createModel(Mockito.any(ContentModelDefinition.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/content-models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "prefix": "ops",
                      "namespaceUri": "http://example.com/model/ops/1.0",
                      "name": "Operations"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(created.getId().toString()))
            .andExpect(jsonPath("$.prefix").value("ops"))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("Add type returns DTO with qualified name and property metadata")
    void addTypeReturnsDto() throws Exception {
        UUID modelId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();

        ContentModelDefinition model = new ContentModelDefinition();
        model.setId(modelId);
        model.setPrefix("acme");

        TypeDefinition type = new TypeDefinition();
        type.setId(typeId);
        type.setName("invoice");
        type.setTitle("Invoice");
        type.setModel(model);
        type.setMandatoryAspects(List.of("cm:auditable"));

        Mockito.when(contentModelService.addType(Mockito.eq(modelId), Mockito.any(TypeDefinition.class))).thenReturn(type);

        mockMvc.perform(post("/api/v1/content-models/{modelId}/types", modelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "invoice",
                      "title": "Invoice",
                      "mandatoryAspects": ["cm:auditable"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(typeId.toString()))
            .andExpect(jsonPath("$.qualifiedName").value("acme:invoice"))
            .andExpect(jsonPath("$.mandatoryAspects[0]").value("cm:auditable"));
    }

    @Test
    @DisplayName("Activate model returns validation details on invalid model graph")
    void activateModelReturnsValidationDetails() throws Exception {
        UUID modelId = UUID.randomUUID();

        Mockito.when(contentModelService.activateModel(modelId))
            .thenThrow(new ModelValidationException(
                "Model validation failed",
                List.of("Circular type inheritance detected at 'acme:invoice'")
            ));

        mockMvc.perform(post("/api/v1/content-models/{modelId}/activate", modelId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Model validation failed"))
            .andExpect(jsonPath("$.details[0]").value("Circular type inheritance detected at 'acme:invoice'"));
    }

    private ContentModelDefinition buildModelGraph() {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setId(UUID.randomUUID());
        model.setPrefix("acme");
        model.setNamespaceUri("http://example.com/model/acme/1.0");
        model.setName("Acme Model");
        model.setStatus(ModelStatus.ACTIVE);

        TypeDefinition type = new TypeDefinition();
        type.setId(UUID.randomUUID());
        type.setName("invoice");
        type.setTitle("Invoice");
        type.setModel(model);
        type.setMandatoryAspects(List.of("cm:auditable"));

        PropertyDefinition property = new PropertyDefinition();
        property.setId(UUID.randomUUID());
        property.setName("invoiceNo");
        property.setTitle("Invoice Number");
        property.setDataType(PropertyDataType.TEXT);
        property.setTypeDefinition(type);

        ConstraintDefinition constraint = new ConstraintDefinition();
        constraint.setId(UUID.randomUUID());
        constraint.setConstraintType(ConstraintType.REGEX);
        constraint.setParameters(Map.of("pattern", "INV-[0-9]+"));
        constraint.setPropertyDefinition(property);
        property.setConstraints(List.of(constraint));
        type.setProperties(List.of(property));

        AspectDefinition aspect = new AspectDefinition();
        aspect.setId(UUID.randomUUID());
        aspect.setName("classifiable");
        aspect.setTitle("Classifiable");
        aspect.setModel(model);
        model.setTypes(List.of(type));
        model.setAspects(List.of(aspect));
        return model;
    }
}
