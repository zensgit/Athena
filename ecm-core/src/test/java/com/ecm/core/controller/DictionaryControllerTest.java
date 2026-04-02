package com.ecm.core.controller;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.ModelStatus;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.service.DictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DictionaryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        DictionaryController controller = new DictionaryController(dictionaryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Get type decodes qualified name and returns DTO shape")
    void getTypeDecodesQualifiedName() throws Exception {
        TypeDefinition type = buildType("cm", "content");
        Mockito.when(dictionaryService.getType("cm:content")).thenReturn(type);

        mockMvc.perform(get("/api/v1/dictionary/types/{qualifiedName}", URLEncoder.encode("cm:content", StandardCharsets.UTF_8)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qualifiedName").value("cm:content"))
            .andExpect(jsonPath("$.properties[0].qualifiedName").value("cm:title"));
    }

    @Test
    @DisplayName("List aspects returns aspect DTOs without model back reference")
    void listAspectsReturnsDtos() throws Exception {
        AspectDefinition aspect = new AspectDefinition();
        ContentModelDefinition model = new ContentModelDefinition();
        model.setId(UUID.randomUUID());
        model.setPrefix("cm");
        model.setName("Content");
        model.setStatus(ModelStatus.ACTIVE);
        aspect.setId(UUID.randomUUID());
        aspect.setName("titled");
        aspect.setTitle("Titled");
        aspect.setModel(model);

        Mockito.when(dictionaryService.listAspects()).thenReturn(List.of(aspect));

        mockMvc.perform(get("/api/v1/dictionary/aspects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].qualifiedName").value("cm:titled"))
            .andExpect(jsonPath("$[0].model").doesNotExist());
    }

    @Test
    @DisplayName("Type hierarchy and mandatory aspects return decoded values")
    void typeHierarchyAndMandatoryAspectsReturnValues() throws Exception {
        Mockito.when(dictionaryService.resolveTypeHierarchy("cm:content")).thenReturn(List.of("sys:base", "cm:content"));
        Mockito.when(dictionaryService.getMandatoryAspectsForType("cm:content")).thenReturn(List.of("cm:auditable", "cm:titled"));

        mockMvc.perform(get("/api/v1/dictionary/types/{qualifiedName}/hierarchy", URLEncoder.encode("cm:content", StandardCharsets.UTF_8)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("sys:base"))
            .andExpect(jsonPath("$[1]").value("cm:content"));

        mockMvc.perform(get("/api/v1/dictionary/types/{qualifiedName}/mandatory-aspects", URLEncoder.encode("cm:content", StandardCharsets.UTF_8)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("cm:auditable"))
            .andExpect(jsonPath("$[1]").value("cm:titled"));
    }

    private TypeDefinition buildType(String prefix, String name) {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setId(UUID.randomUUID());
        model.setPrefix(prefix);
        model.setName("Content");
        model.setStatus(ModelStatus.ACTIVE);

        TypeDefinition type = new TypeDefinition();
        type.setId(UUID.randomUUID());
        type.setName(name);
        type.setTitle("Content");
        type.setModel(model);

        PropertyDefinition property = new PropertyDefinition();
        property.setId(UUID.randomUUID());
        property.setName("title");
        property.setTitle("Title");
        property.setDataType(PropertyDataType.TEXT);
        property.setTypeDefinition(type);
        type.setProperties(List.of(property));
        return type;
    }
}
