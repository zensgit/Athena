package com.ecm.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DispositionActionExecutorService {

    private final NodeService nodeService;

    public DestroyMutationDto destroyNodeByDisposition(UUID nodeId, String actor) {
        NodeService.GovernanceDeleteResult result = nodeService.deleteNodeByGovernance(nodeId, actor);
        return new DestroyMutationDto(result.nodeId(), result.name(), result.affectedNodeCount());
    }

    public record DestroyMutationDto(
        UUID nodeId,
        String name,
        int affectedNodeCount
    ) {
    }
}
