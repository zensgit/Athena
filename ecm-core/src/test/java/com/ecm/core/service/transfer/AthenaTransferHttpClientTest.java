package com.ecm.core.service.transfer;

import com.ecm.core.entity.Document;
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
        client = new AthenaTransferHttpClient(restTemplate, contentService, nodeRepository);
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

        when(contentService.getContent("content-1")).thenReturn(new ByteArrayInputStream("pdf-data".getBytes()));

        server.expect(requestTo("https://remote.example/api/v1/transfer/receiver/documents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(TransferReceiverHeaders.SECRET_HEADER, "token-123"))
            .andExpect(content().string(containsString("name=\"conflictPolicy\"")))
            .andExpect(content().string(containsString("OVERWRITE")))
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
