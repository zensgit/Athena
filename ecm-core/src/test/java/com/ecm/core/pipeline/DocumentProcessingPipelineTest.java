package com.ecm.core.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentProcessingPipeline.
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingPipelineTest {

    private DocumentProcessingPipeline pipeline;

    @BeforeEach
    void setUp() {
        // Create test processors with different orders
        List<DocumentProcessor> processors = List.of(
            new TestProcessor("First", 100, ProcessingResult.Status.SUCCESS),
            new TestProcessor("Second", 200, ProcessingResult.Status.SUCCESS),
            new TestProcessor("Third", 300, ProcessingResult.Status.SUCCESS)
        );
        pipeline = new DocumentProcessingPipeline(processors);
    }

    @Test
    @DisplayName("Pipeline executes processors in order")
    void executeProcessorsInOrder() {
        DocumentContext context = createTestContext();

        PipelineResult result = pipeline.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExecutions()).hasSize(3);
        assertThat(result.getExecutions().get(0).getProcessorName()).isEqualTo("First");
        assertThat(result.getExecutions().get(1).getProcessorName()).isEqualTo("Second");
        assertThat(result.getExecutions().get(2).getProcessorName()).isEqualTo("Third");
    }

    @Test
    @DisplayName("Pipeline stops on fatal error")
    void stopOnFatalError() {
        List<DocumentProcessor> processors = List.of(
            new TestProcessor("First", 100, ProcessingResult.Status.SUCCESS),
            new TestProcessor("Fatal", 200, ProcessingResult.Status.FATAL),
            new TestProcessor("NotExecuted", 300, ProcessingResult.Status.SUCCESS)
        );
        pipeline = new DocumentProcessingPipeline(processors);

        DocumentContext context = createTestContext();
        PipelineResult result = pipeline.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExecutions()).hasSize(2);
        assertThat(result.getExecutions().get(1).getResult().isFatal()).isTrue();
    }

    @Test
    @DisplayName("Pipeline continues on non-fatal failure")
    void continueOnNonFatalFailure() {
        List<DocumentProcessor> processors = List.of(
            new TestProcessor("First", 100, ProcessingResult.Status.SUCCESS),
            new TestProcessor("Failed", 200, ProcessingResult.Status.FAILED),
            new TestProcessor("Third", 300, ProcessingResult.Status.SUCCESS)
        );
        pipeline = new DocumentProcessingPipeline(processors);

        DocumentContext context = createTestContext();
        PipelineResult result = pipeline.execute(context);

        // Pipeline continues but has errors
        assertThat(result.getExecutions()).hasSize(3);
        assertThat(result.getExecutions().get(1).getResult().getStatus())
            .isEqualTo(ProcessingResult.Status.FAILED);
        assertThat(result.getExecutions().get(2).getResult().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Pipeline skips unsupported processors")
    void skipUnsupportedProcessors() {
        List<DocumentProcessor> processors = List.of(
            new TestProcessor("First", 100, ProcessingResult.Status.SUCCESS),
            new UnsupportedProcessor("Unsupported", 200),
            new TestProcessor("Third", 300, ProcessingResult.Status.SUCCESS)
        );
        pipeline = new DocumentProcessingPipeline(processors);

        DocumentContext context = createTestContext();
        PipelineResult result = pipeline.execute(context);

        assertThat(result.getExecutions()).hasSize(3);
        assertThat(result.getExecutions().get(1).getResult().getStatus())
            .isEqualTo(ProcessingResult.Status.SKIPPED);
    }

    @Test
    @DisplayName("Pipeline records execution times")
    void recordExecutionTimes() {
        DocumentContext context = createTestContext();

        PipelineResult result = pipeline.execute(context);

        assertThat(result.getTotalDurationMs()).isGreaterThanOrEqualTo(0);
        for (ProcessorExecution execution : result.getExecutions()) {
            assertThat(execution.getDurationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    private DocumentContext createTestContext() {
        return DocumentContext.builder()
            .originalFilename("test.pdf")
            .inputStream(new ByteArrayInputStream("test content".getBytes()))
            .userId("testUser")
            .documentId(UUID.randomUUID())
            .contentId("test-content-id")
            .parentFolderId(UUID.randomUUID())
            .build();
    }

    // Test processor implementations

    static class TestProcessor implements DocumentProcessor {
        private final String name;
        private final int order;
        private final ProcessingResult.Status resultStatus;

        TestProcessor(String name, int order, ProcessingResult.Status resultStatus) {
            this.name = name;
            this.order = order;
            this.resultStatus = resultStatus;
        }

        @Override
        public ProcessingResult process(DocumentContext context) {
            return ProcessingResult.builder()
                .status(resultStatus)
                .message(name + " processed")
                .build();
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class UnsupportedProcessor implements DocumentProcessor {
        private final String name;
        private final int order;

        UnsupportedProcessor(String name, int order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public ProcessingResult process(DocumentContext context) {
            return ProcessingResult.success();
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public boolean supports(DocumentContext context) {
            return false;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
