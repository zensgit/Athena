package com.ecm.core.controller;

import com.ecm.core.entity.Correspondent;
import com.ecm.core.service.CorrespondentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CorrespondentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CorrespondentService correspondentService;

    @InjectMocks
    private CorrespondentController correspondentController;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(correspondentController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    @DisplayName("Create correspondent then list returns it")
    void createThenListReturnsNewCorrespondent() throws Exception {
        List<Correspondent> store = new ArrayList<>();
        String correspondentName = "ui-e2e-correspondent-test";

        Mockito.when(correspondentService.createCorrespondent(Mockito.any(Correspondent.class)))
            .thenAnswer(invocation -> {
                Correspondent input = invocation.getArgument(0);
                Correspondent saved = new Correspondent();
                saved.setId(UUID.randomUUID());
                saved.setName(input.getName());
                saved.setMatchAlgorithm(input.getMatchAlgorithm());
                saved.setMatchPattern(input.getMatchPattern());
                saved.setInsensitive(input.isInsensitive());
                saved.setEmail(input.getEmail());
                saved.setPhone(input.getPhone());
                store.add(saved);
                return saved;
            });

        Mockito.when(correspondentService.getCorrespondents(Mockito.any(Pageable.class)))
            .thenAnswer(invocation -> {
                Pageable pageable = invocation.getArgument(0);
                return new PageImpl<>(new ArrayList<>(store), pageable, store.size());
            });

        String payload = """
            {
              "name": "%s",
              "matchAlgorithm": "ANY",
              "matchPattern": "Amazon AWS",
              "insensitive": true,
              "email": "test@example.com",
              "phone": "123"
            }
            """.formatted(correspondentName);

        mockMvc.perform(post("/api/v1/correspondents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(correspondentName))
            .andExpect(jsonPath("$.matchAlgorithm").value("ANY"));

        mockMvc.perform(get("/api/v1/correspondents")
                .param("page", "0")
                .param("size", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value(correspondentName));
    }
}
