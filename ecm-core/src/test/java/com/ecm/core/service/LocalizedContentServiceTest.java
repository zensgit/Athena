package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.LocalizedContent;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.LocalizedContentRepository;
import com.ecm.core.repository.NodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalizedContentServiceTest {

    @Mock private LocalizedContentRepository localizedContentRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;

    @Test
    @DisplayName("listForNode requires READ on a live node")
    void listForNodeRequiresReadOnLiveNode() {
        LocalizedContentService service = service();
        Node node = document(UUID.randomUUID());
        LocalizedContent zh = localized(node, "zh-cn", "Contract CN", "Description CN");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(node.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node));
        when(localizedContentRepository.findByNodeIdOrderByLocaleAsc(node.getId()))
            .thenReturn(List.of(zh));

        List<LocalizedContentService.LocalizedContentDto> result = service.listForNode(node.getId());

        assertEquals(1, result.size());
        assertEquals("zh-cn", result.get(0).locale());
        verify(securityService).checkPermission(node, PermissionType.READ);
    }

    @Test
    @DisplayName("upsert requires WRITE and normalizes locale")
    void upsertRequiresWriteAndNormalizesLocale() {
        LocalizedContentService service = service();
        Node node = document(UUID.randomUUID());
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(node.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node));
        when(localizedContentRepository.findByNodeIdAndLocale(node.getId(), "zh-cn"))
            .thenReturn(Optional.empty());
        when(localizedContentRepository.save(any(LocalizedContent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(
            node.getId(),
            " ZH-CN ",
            new LocalizedContentService.LocalizedContentRequest(null, "Contract CN", "Description CN")
        );

        ArgumentCaptor<LocalizedContent> captor = ArgumentCaptor.forClass(LocalizedContent.class);
        verify(localizedContentRepository).save(captor.capture());
        assertEquals(node, captor.getValue().getNode());
        assertEquals("zh-cn", captor.getValue().getLocale());
        assertEquals("Contract CN", captor.getValue().getTitle());
        verify(securityService).checkPermission(node, PermissionType.WRITE);
    }

    @Test
    @DisplayName("delete requires WRITE before deleting locale")
    void deleteRequiresWrite() {
        LocalizedContentService service = service();
        Node node = document(UUID.randomUUID());
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(node.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node));
        when(localizedContentRepository.existsByNodeIdAndLocale(node.getId(), "en"))
            .thenReturn(true);

        service.delete(node.getId(), "EN");

        verify(securityService).checkPermission(node, PermissionType.WRITE);
        verify(localizedContentRepository).deleteByNodeIdAndLocale(node.getId(), "en");
    }

    @Test
    @DisplayName("resolve checks READ and falls back from region to language")
    void resolveChecksReadAndFallsBackToLanguage() {
        LocalizedContentService service = service();
        Node node = document(UUID.randomUUID());
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(node.getId(), Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(node));
        when(localizedContentRepository.findByNodeIdOrderByLocaleAsc(node.getId()))
            .thenReturn(List.of(
                localized(node, "en", "Contract", "Description"),
                localized(node, "zh", "Contract CN", "Description CN")
            ));

        Optional<LocalizedContentService.LocalizedContentDto> result =
            service.resolve(node.getId(), "zh-CN, en;q=0.8");

        assertTrue(result.isPresent());
        assertEquals("zh", result.get().locale());
        verify(securityService).checkPermission(node, PermissionType.READ);
    }

    @Test
    @DisplayName("blank locale is rejected before repository lookup")
    void blankLocaleIsRejected() {
        LocalizedContentService service = service();

        assertThrows(
            IllegalArgumentException.class,
            () -> service.upsert(
                UUID.randomUUID(),
                " ",
                new LocalizedContentService.LocalizedContentRequest(null, "Title", null)
            )
        );

        verify(nodeRepository, never()).findByIdAndDeletedFalseAndArchiveStatus(any(), any());
    }

    @Test
    @DisplayName("missing or non-live node is hidden as not found")
    void missingNodeIsNotFound() {
        LocalizedContentService service = service();
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.listForNode(nodeId));
    }

    private LocalizedContentService service() {
        return new LocalizedContentService(localizedContentRepository, nodeRepository, securityService);
    }

    private Node document(UUID id) {
        Document document = new Document();
        document.setId(id);
        document.setName("contract.pdf");
        document.setPath("/Sites/Contracts/contract.pdf");
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setStatus(Node.NodeStatus.ACTIVE);
        return document;
    }

    private LocalizedContent localized(Node node, String locale, String title, String description) {
        LocalizedContent localizedContent = new LocalizedContent();
        localizedContent.setNode(node);
        localizedContent.setLocale(locale);
        localizedContent.setTitle(title);
        localizedContent.setDescription(description);
        return localizedContent;
    }
}
