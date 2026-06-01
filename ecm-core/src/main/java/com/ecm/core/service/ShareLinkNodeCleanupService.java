package com.ecm.core.service;

import com.ecm.core.repository.ShareLinkAccessLogRepository;
import com.ecm.core.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Removes share-link dependents before a document node is permanently deleted.
 */
@Service
@RequiredArgsConstructor
public class ShareLinkNodeCleanupService {

    private final ShareLinkAccessLogRepository accessLogRepository;
    private final ShareLinkRepository shareLinkRepository;

    @Transactional
    public void deleteByNodeId(UUID nodeId) {
        accessLogRepository.deleteByShareLinkNodeId(nodeId);
        shareLinkRepository.deleteByNodeId(nodeId);
    }
}
