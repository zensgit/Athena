package com.ecm.core.event;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Version;
import com.ecm.core.entity.AutomationRule.TriggerType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RepositoryLifecycleEvent {
    RepositoryLifecycleAction action;
    Node node;
    Document document;
    Version version;
    Node oldParent;
    Node newParent;
    String username;
    TriggerType ruleTriggerType;
    boolean includeDescendants;
    boolean permanentDelete;
}
