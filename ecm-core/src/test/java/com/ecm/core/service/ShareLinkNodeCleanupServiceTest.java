package com.ecm.core.service;

import com.ecm.core.repository.ShareLinkAccessLogRepository;
import com.ecm.core.repository.ShareLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ShareLinkNodeCleanupServiceTest {

    @Mock private ShareLinkAccessLogRepository accessLogRepository;
    @Mock private ShareLinkRepository shareLinkRepository;

    @Test
    void deleteByNodeIdRemovesAccessLogsBeforeShareLinks() {
        UUID nodeId = UUID.randomUUID();
        ShareLinkNodeCleanupService service = new ShareLinkNodeCleanupService(accessLogRepository, shareLinkRepository);

        service.deleteByNodeId(nodeId);

        InOrder inOrder = inOrder(accessLogRepository, shareLinkRepository);
        inOrder.verify(accessLogRepository).deleteByShareLinkNodeId(nodeId);
        inOrder.verify(shareLinkRepository).deleteByNodeId(nodeId);
    }
}
