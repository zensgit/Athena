package com.ecm.core.preview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PreviewPreflightResolverTest {

    @Mock
    private PreviewFailurePolicyRegistry previewFailurePolicyRegistry;

    @Mock
    private CadRenderEndpointRegistry cadRenderEndpointRegistry;

    private PreviewPreflightResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PreviewPreflightResolver(previewFailurePolicyRegistry, cadRenderEndpointRegistry);
        ReflectionTestUtils.setField(resolver, "preflightEnabled", true);
        ReflectionTestUtils.setField(resolver, "defaultMaxSourceSizeBytes", 1000L);
        ReflectionTestUtils.setField(resolver, "maxSourceSizeByRoute", "cad:2000,office:800");
        Mockito.when(previewFailurePolicyRegistry.resolve(Mockito.any(), Mockito.any()))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy(
                "default",
                "Default",
                3,
                60000L,
                1.6d,
                0L,
                true
            ));
    }

    @Test
    @DisplayName("Preflight declines CAD candidate when renderer endpoint is missing")
    void shouldDeclineCadWhenRendererEndpointMissing() {
        UUID documentId = UUID.randomUUID();
        Mockito.when(cadRenderEndpointRegistry.isCadPreviewEnabled()).thenReturn(true);
        Mockito.when(cadRenderEndpointRegistry.isConfigured()).thenReturn(false);
        Mockito.when(cadRenderEndpointRegistry.resolveEndpoints()).thenReturn(List.of());
        Mockito.when(previewFailurePolicyRegistry.resolve(Mockito.eq("application/dwg"), Mockito.eq("drawing.dwg")))
            .thenReturn(new PreviewFailurePolicyRegistry.PreviewFailurePolicy(
                "cad",
                "CAD",
                5,
                60000L,
                2.0d,
                120000L,
                true
            ));

        PreviewPreflightResolver.PreflightDecision decision = resolver.evaluateCandidate(
            documentId,
            "drawing.dwg",
            "application/dwg",
            512L
        );

        assertFalse(decision.accepted());
        assertEquals("DECLINED", decision.preflightStatus());
        assertEquals("cad", decision.route());
        assertEquals("CAD_ENDPOINT_UNCONFIGURED", decision.skipReason());
        assertEquals("cad", decision.policyProfileKey());
    }

    @Test
    @DisplayName("Preflight declines candidate when source size exceeds route threshold")
    void shouldDeclineWhenSourceSizeExceedsThreshold() {
        UUID documentId = UUID.randomUUID();
        PreviewPreflightResolver.PreflightDecision decision = resolver.evaluateCandidate(
            documentId,
            "sheet.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            1024L
        );

        assertFalse(decision.accepted());
        assertEquals("SOURCE_TOO_LARGE", decision.skipReason());
        assertEquals("office", decision.route());
        assertEquals(800L, decision.maxSourceSizeBytes());
    }

    @Test
    @DisplayName("Preflight accepts PDF and returns local pipeline chain")
    void shouldAcceptPdfWithLocalPipeline() {
        UUID documentId = UUID.randomUUID();
        PreviewPreflightResolver.PreflightDecision decision = resolver.evaluateCandidate(
            documentId,
            "sample.pdf",
            "application/pdf",
            256L
        );

        assertTrue(decision.accepted());
        assertEquals("ACCEPTED", decision.preflightStatus());
        assertEquals("pdf", decision.route());
        assertEquals("local-pdfbox", decision.pipelineChainSummary());
    }
}
