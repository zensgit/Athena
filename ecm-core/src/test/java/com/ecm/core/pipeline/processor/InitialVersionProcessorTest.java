package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Version;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.VersionRepository;
import com.ecm.core.service.ContentReferenceService;
import com.ecm.core.service.VersionLabelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialVersionProcessorTest {

    @Mock private VersionRepository versionRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private VersionLabelService versionLabelService;
    @Mock private ContentReferenceService contentReferenceService;

    @Test
    @DisplayName("Attaches version ownership for pipeline-created initial version")
    void attachesVersionOwnershipForInitialVersion() {
        InitialVersionProcessor processor = new InitialVersionProcessor(
            versionRepository,
            documentRepository,
            eventPublisher,
            versionLabelService,
            contentReferenceService
        );

        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Document document = new Document();
        document.setId(documentId);
        document.setVersioned(true);
        document.setContentId("content-1");
        document.setMimeType("application/pdf");
        document.setFileSize(123L);

        DocumentContext context = DocumentContext.builder()
            .document(document)
            .userId("alice")
            .build();

        when(versionRepository.findMaxVersionNumber(documentId)).thenReturn(null);
        when(versionLabelService.generateLabel(document, 1)).thenReturn("1.0");
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> {
            Version version = invocation.getArgument(0);
            version.setId(versionId);
            return version;
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingResult result = processor.process(context);

        assertThat(result.isSuccess()).isTrue();
        verify(contentReferenceService).attach("content-1", OwnerType.VERSION, versionId);
    }
}
