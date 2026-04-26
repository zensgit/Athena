package com.ecm.core.integration.email.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NotificationDispatcher {

    private final Map<String, NotificationChannel> channelsById;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        Map<String, NotificationChannel> byId = new LinkedHashMap<>();
        for (NotificationChannel channel : channels) {
            byId.put(channel.getId(), channel);
        }
        this.channelsById = Map.copyOf(byId);
        log.info("NotificationDispatcher: registered channels {}", this.channelsById.keySet());
    }

    public void dispatch(NotificationPayload payload, Collection<String> channelIds) {
        if (payload == null || channelIds == null || channelIds.isEmpty()) {
            return;
        }
        for (String channelId : channelIds) {
            NotificationChannel channel = channelsById.get(channelId);
            if (channel == null) {
                log.warn("dispatch: unknown channelId={} for type={}", channelId, payload.getType());
                continue;
            }
            try {
                channel.dispatch(payload);
            } catch (Exception ex) {
                log.warn(
                    "dispatch: channel={} failed type={} cause={}",
                    channelId,
                    payload.getType(),
                    ex.getMessage()
                );
            }
        }
    }
}
