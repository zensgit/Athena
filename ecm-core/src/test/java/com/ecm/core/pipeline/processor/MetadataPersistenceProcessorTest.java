package com.ecm.core.pipeline.processor;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.pipeline.DocumentContext;
import com.ecm.core.pipeline.ProcessingResult;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.service.ContentReferenceService;
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
class MetadataPersistenceProcessorTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContentReferenceService contentReferenceService;

    @Test
    @DisplayName("Attaches document content ownership after persistence")
    void attachesDocumentOwnershipAfterPersistence() {
        MetadataPersistenceProcessor processor = new MetadataPersistenceProcessor(
            documentRepository,
            folderRepository,
            eventPublisher,
            contentReferenceService
        );

        UUID documentId = UUID.randomUUID();
        DocumentContext context = DocumentContext.builder()
            .originalFilename("contract.pdf")
            .contentId("content-1")
            .mimeType("application/pdf")
            .fileSize(123L)
            .userId("alice")
            .build();

        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(documentId);
            return document;
        });

        ProcessingResult result = processor.process(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getDocumentId()).isEqualTo(documentId);
        verify(contentReferenceService).attach("content-1", OwnerType.DOCUMENT, documentId);
    }
}
