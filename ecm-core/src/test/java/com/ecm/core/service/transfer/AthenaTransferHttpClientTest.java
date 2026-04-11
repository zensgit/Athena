package com.ecm.core.service.transfer;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class AthenaTransferHttpClientTest {

    @Mock
    private ContentService contentService;

    @Mock
    private NodeRepository nodeRepository;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private AthenaTransferHttpClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        client = new AthenaTransferHttpClient(
            restTemplate,
            contentService,
            nodeRepository,
            new RepositoryIdentityProvider("athena", "athena")
        );
    }

    @Test
    @DisplayName("verifyTarget calls remote receiver verify endpoint with transfer headers")
    void verifyTargetCallsRemoteFolderEndpoint() {
        TransferTarget target = remoteTarget();
        server.expect(requestTo("https://remote.example/api/v1/transfer/receiver/verify?folderId=" + target.getTargetFolderId()))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(TransferReceiverHeaders.SECRET_HEADER, "token-123"))
            .andRespond(withSuccess("""
                {"folderId":"%s","folderName":"Outbound Remote","repositoryId":"remote-athena"}
                """.formatted(target.getTargetFolderId()), MediaType.APPLICATION_JSON));

        TransferClient.TransferVerificationResult result = client.verifyTarget(target);

        assertEquals("Verified remote Athena transfer receiver folder: Outbound Remote", result.message());
        assertEquals("remote-athena", result.remoteRepositoryId());
        server.verify();
    }

    @Test
    @DisplayName("replicate uploads document content to remote transfer receiver")
    void replicateUploadsDocumentContent() throws Exception {
        TransferTarget target = remoteTarget();
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName("contract.pdf");
        document.setContentId("content-1");
        document.setMimeType("application/pdf");
        document.setDescription("Signed contract");
        document.setLastModifiedDate(java.time.LocalDateTime.parse("2026-04-11T12:00:00"));
        Folder parent = new Folder();
        parent.setId(UUID.randomUUID());
        document.setParent(parent);

        when(contentService.getContent("content-1")).thenReturn(new ByteArrayInputStream("pdf-data".getBytes()));

        server.expect(requestTo("https://remote.example/api/v1/transfer/receiver/documents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(TransferReceiverHeaders.SECRET_HEADER, "token-123"))
            .andExpect(content().string(containsString("name=\"conflictPolicy\"")))
            .andExpect(content().string(containsString("OVERWRITE")))
            .andExpect(content().string(containsString("name=\"sourceRepositoryId\"")))
            .andExpect(content().string(containsString("athena")))
            .andExpect(content().string(containsString("name=\"sourceNodeId\"")))
            .andExpect(content().string(containsString(document.getId().toString())))
            .andRespond(withSuccess("""
                {"documentId":"%s","documentName":"contract.pdf","disposition":"OVERWRITTEN","message":"Overwrote remote document"}
                """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        TransferClient.TransferExecutionResult result = client.replicate(
            target,
            document,
            false,
            ReplicationDefinition.ConflictPolicy.OVERWRITE
        );

        assertNotNull(result.copiedNodeId());
        assertEquals("Overwrote remote document", result.message());
        server.verify();
    }

    @Test
    @DisplayName("replicate still creates unchanged child folders so changed descendants keep the correct parent")
    void replicateStillCreatesUnchangedChildFoldersBeforeUploadingChangedDescendants() throws Exception {
        TransferTarget target = remoteTarget();
        LocalDateTime watermark = LocalDateTime.parse("2026-04-11T12:00:00");

        Folder root = new Folder();
        root.setId(UUID.randomUUID());
        root.setName("Contracts");
        root.setDescription("Root folder");
        root.setLastModifiedDate(LocalDateTime.parse("2026-04-11T11:00:00"));

        Folder childFolder = new Folder();
        childFolder.setId(UUID.randomUUID());
        childFolder.setName("FY26");
        childFolder.setDescription("Year folder");
        childFolder.setParent(root);
        childFolder.setLastModifiedDate(LocalDateTime.parse("2026-04-11T10:00:00"));

        Document changedDocument = new Document();
        changedDocument.setId(UUID.randomUUID());
        changedDocument.setName("contract.pdf");
        changedDocument.setContentId("content-2");
        changedDocument.setMimeType("application/pdf");
        changedDocument.setParent(childFolder);
        changedDocument.setLastModifiedDate(LocalDateTime.parse("2026-04-11T13:00:00"));

        when(nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(root.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(List.of(childFolder));
        when(nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(childFolder.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(List.of(changedDocument));
        when(contentService.getContent("content-2")).thenReturn(new ByteArrayInputStream("pdf-data".getBytes()));

        UUID remoteRootId = UUID.randomUUID();
        UUID remoteChildId = UUID.randomUUID();
        UUID remoteDocId = UUID.randomUUID();

        server.expect(requestTo("https://remote.example/api/v1/transfer/receiver/folders"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"folderId":"%s","folderName":"Contracts","disposition":"CREATED","message":"Created receiver folder"}
                """.formatted(remoteRootId), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://remote.example/api/v1/transfer/receiver/folders"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString(childFolder.getId().toString())))
            .andRespond(withSuccess("""
                {"folderId":"%s","folderName":"FY26","disposition":"UNCHANGED","message":"Receiver folder already up to date"}
                """.formatted(remoteChildId), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://remote.example/api/v1/transfer/receiver/documents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("name=\"sourceParentNodeId\"")))
            .andExpect(content().string(containsString(childFolder.getId().toString())))
            .andRespond(withSuccess("""
                {"documentId":"%s","documentName":"contract.pdf","disposition":"CREATED","message":"Uploaded receiver document"}
                """.formatted(remoteDocId), MediaType.APPLICATION_JSON));

        TransferClient.TransferExecutionResult result = client.replicate(
            target,
            root,
            true,
            ReplicationDefinition.ConflictPolicy.RENAME,
            watermark
        );

        assertEquals(remoteRootId, result.copiedNodeId());
        assertEquals(3, result.entries().size());
        assertEquals("UNCHANGED", result.entries().get(1).action());
        assertEquals(remoteDocId, result.entries().get(2).targetNodeId());
        server.verify();
    }

    private TransferTarget remoteTarget() {
        TransferTarget target = new TransferTarget();
        target.setTransportType(TransferTarget.TransportType.ATHENA_HTTP);
        target.setTargetFolderId(UUID.randomUUID());
        target.setEndpointUrl("https://remote.example/");
        target.setEndpointPath("/api/v1/");
        target.setAuthType(TransferTarget.AuthType.BEARER);
        target.setAuthSecret("token-123");
        return target;
    }
}
