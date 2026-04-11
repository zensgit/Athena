package com.ecm.core.cmis;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.service.CheckOutCheckInService;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmisContentVersioningServiceTest {

    @Mock
    private NodeService nodeService;

    @Mock
    private ContentService contentService;

    @Mock
    private VersionService versionService;

    @Mock
    private CheckOutCheckInService checkOutCheckInService;

    private CmisContentVersioningService service;

    @BeforeEach
    void setUp() {
        service = new CmisContentVersioningService(
            nodeService,
            contentService,
            versionService,
            checkOutCheckInService,
            new CmisObjectFactory(new RepositoryIdentityProvider("athena", "athena"))
        );
    }

    @Test
    @DisplayName("Content selector returns stream metadata for document")
    void getContentStreamReturnsPayload() throws Exception {
        Document document = buildDocument("contract.pdf");
        document.setContentId("content-1");
        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(contentService.getContent("content-1"))
            .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        CmisContentVersioningService.ContentStreamResponse response = service.getContentStream(document.getId().toString(), null);

        assertEquals("application/pdf", response.mimeType());
        assertEquals("contract.pdf", response.filename());
        assertEquals(1024L, response.contentLength());
    }

    @Test
    @DisplayName("Version-specific objectId returns frozen version content")
    void getContentStreamReturnsVersionPayload() throws Exception {
        Document document = buildDocument("contract.pdf");
        Version version = new Version();
        version.setId(UUID.randomUUID());
        version.setDocument(document);
        version.setVersionLabel("2.0");
        version.setMimeType("application/pdf");
        version.setFileSize(2048L);

        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(versionService.getVersionByLabel(document.getId(), "2.0")).thenReturn(version);
        when(versionService.getVersionContent(version.getId()))
            .thenReturn(new ByteArrayInputStream("frozen".getBytes(StandardCharsets.UTF_8)));

        CmisContentVersioningService.ContentStreamResponse response =
            service.getContentStream(document.getId() + ";v2.0", null);

        assertEquals("application/pdf", response.mimeType());
        assertEquals("contract.pdf", response.filename());
        assertEquals(2048L, response.contentLength());
    }

    @Test
    @DisplayName("Set content stream creates version and returns refreshed object")
    void setContentStreamCreatesVersion() throws Exception {
        Document document = buildDocument("contract.pdf");
        when(nodeService.getNode(document.getId())).thenReturn(document);

        CmisModels.MutationResponse response = service.setContentStream(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "contract.pdf",
            "aGVsbG8=",
            "updated",
            Boolean.TRUE,
            null
        ));

        verify(versionService).createVersion(eq(document.getId()), any(ByteArrayInputStream.class), eq("contract.pdf"), eq("updated"), eq(true));
        assertEquals("setContentStream", response.action());
        assertEquals(document.getId().toString(), response.object().objectId());
    }

    @Test
    @DisplayName("Check out delegates to node service")
    void checkOutDelegatesToNodeService() {
        Document document = buildDocument("contract.pdf");
        document.checkout("alice");
        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(nodeService.checkoutDocument(document.getId())).thenReturn(document);

        CmisModels.MutationResponse response = service.checkOut(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));

        verify(nodeService).checkoutDocument(document.getId());
        assertEquals("checkOut", response.action());
    }

    @Test
    @DisplayName("Check in can create version before clearing checkout state")
    void checkInCreatesVersionThenChecksIn() throws Exception {
        Document document = buildDocument("contract.pdf");
        document.checkout("alice");
        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(nodeService.checkinDocument(document.getId(), true)).thenReturn(document);

        CmisModels.MutationResponse response = service.checkIn(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "contract.pdf",
            "aGVsbG8=",
            "checkin",
            Boolean.FALSE,
            Boolean.TRUE
        ));

        verify(versionService).createVersion(eq(document.getId()), any(ByteArrayInputStream.class), eq("contract.pdf"), eq("checkin"), eq(false));
        verify(nodeService).checkinDocument(document.getId(), true);
        assertEquals("checkIn", response.action());
    }

    @Test
    @DisplayName("Cancel check out delegates to node service")
    void cancelCheckOutDelegatesToNodeService() {
        Document document = buildDocument("contract.pdf");
        when(nodeService.getNode(document.getId())).thenReturn(document);
        when(nodeService.cancelCheckoutDocument(document.getId())).thenReturn(document);

        CmisModels.MutationResponse response = service.cancelCheckOut(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));

        verify(nodeService).cancelCheckoutDocument(document.getId());
        assertEquals("cancelCheckOut", response.action());
    }

    @Test
    @DisplayName("Set content stream rejects invalid base64")
    void setContentStreamRejectsInvalidBase64() {
        Document document = buildDocument("contract.pdf");
        when(nodeService.getNode(document.getId())).thenReturn(document);

        assertThrows(IllegalArgumentException.class, () -> service.setContentStream(new CmisModels.MutationRequest(
            document.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "contract.pdf",
            "%%%invalid%%%",
            null,
            null,
            null
        )));
    }

    @Test
    @DisplayName("AtomPub checkout delegates to working-copy service")
    void checkOutWorkingCopyDelegatesToWorkingCopyService() {
        Document original = buildDocument("contract.pdf");
        Document workingCopy = buildWorkingCopy("contract.pdf", original.getId());
        when(nodeService.getNode(original.getId())).thenReturn(original);
        when(checkOutCheckInService.checkout(original.getId())).thenReturn(workingCopy);

        CmisModels.MutationResponse response = service.checkOutWorkingCopy(new CmisModels.MutationRequest(
            original.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));

        verify(checkOutCheckInService).checkout(original.getId());
        assertEquals("checkOut", response.action());
        assertEquals(workingCopy.getId().toString(), response.object().objectId());
    }

    @Test
    @DisplayName("AtomPub check in rejects non-working-copy nodes")
    void checkInWorkingCopyRejectsNonWorkingCopy() {
        Document original = buildDocument("contract.pdf");
        when(nodeService.getNode(original.getId())).thenReturn(original);

        assertThrows(IllegalArgumentException.class, () -> service.checkInWorkingCopy(new CmisModels.MutationRequest(
            original.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Boolean.FALSE
        )));
    }

    @Test
    @DisplayName("AtomPub check in versions content and delegates via working-copy service")
    void checkInWorkingCopyCreatesVersionAndDelegates() throws Exception {
        UUID originalId = UUID.randomUUID();
        Document workingCopy = buildWorkingCopy("contract.pdf", originalId);
        Document checkedIn = buildDocument("contract.pdf");
        checkedIn.setId(originalId);

        when(nodeService.getNode(workingCopy.getId())).thenReturn(workingCopy);
        when(checkOutCheckInService.checkin(workingCopy.getId(), true)).thenReturn(checkedIn);

        CmisModels.MutationResponse response = service.checkInWorkingCopy(new CmisModels.MutationRequest(
            workingCopy.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "contract.pdf",
            "aGVsbG8=",
            "checkin",
            Boolean.TRUE,
            Boolean.TRUE
        ));

        verify(versionService).createVersion(eq(originalId), any(ByteArrayInputStream.class), eq("contract.pdf"), eq("checkin"), eq(true));
        verify(checkOutCheckInService).checkin(workingCopy.getId(), true);
        assertEquals("checkIn", response.action());
        assertEquals(originalId.toString(), response.object().objectId());
    }

    @Test
    @DisplayName("AtomPub cancel checkout accepts working copy id")
    void cancelWorkingCopyCheckoutDelegatesToWorkingCopyService() {
        UUID originalId = UUID.randomUUID();
        Document workingCopy = buildWorkingCopy("contract.pdf", originalId);
        Document original = buildDocument("contract.pdf");
        original.setId(originalId);

        when(nodeService.getNode(workingCopy.getId())).thenReturn(workingCopy);
        when(checkOutCheckInService.cancelCheckout(workingCopy.getId())).thenReturn(original);

        CmisModels.MutationResponse response = service.cancelWorkingCopyCheckout(new CmisModels.MutationRequest(
            workingCopy.getId().toString(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));

        verify(checkOutCheckInService).cancelCheckout(workingCopy.getId());
        assertEquals("cancelCheckOut", response.action());
        assertEquals(originalId.toString(), response.object().objectId());
    }

    private Document buildDocument(String name) {
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        parent.setName("Contracts");
        parent.setPath("/Contracts");

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setPath("/Contracts/" + name);
        document.setParent(parent);
        document.setMimeType("application/pdf");
        document.setFileSize(1024L);
        document.setVersionLabel("1.0");
        document.setCreatedBy("alice");
        document.setCreatedDate(LocalDateTime.now());
        document.setLastModifiedBy("alice");
        document.setLastModifiedDate(LocalDateTime.now());
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }

    private Document buildWorkingCopy(String name, UUID originalId) {
        Document workingCopy = buildDocument("(Working Copy) " + name);
        workingCopy.setWorkingCopy(true);
        workingCopy.setWorkingCopyOf(originalId);
        workingCopy.setCheckoutUser("alice");
        return workingCopy;
    }
}
