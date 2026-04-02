package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.FollowSubscription;
import com.ecm.core.entity.FollowTargetType;
import com.ecm.core.entity.Site;
import com.ecm.core.entity.User;
import com.ecm.core.repository.FollowSubscriptionRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.SiteRepository;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowingServiceTest {

    @Mock private FollowSubscriptionRepository followSubscriptionRepository;
    @Mock private SecurityService securityService;
    @Mock private UserRepository userRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private NodeRepository nodeRepository;

    private FollowingService service;

    @BeforeEach
    void setUp() {
        service = new FollowingService(
            followSubscriptionRepository,
            securityService,
            userRepository,
            siteRepository,
            nodeRepository
        );
    }

    @Test
    @DisplayName("follow validates site target and saves subscription")
    void followValidatesSiteTargetAndSavesSubscription() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse("engineering")).thenReturn(Optional.of(new Site()));
        when(followSubscriptionRepository.findByUserIdAndTargetTypeAndTargetId("alice", FollowTargetType.SITE, "engineering"))
            .thenReturn(Optional.empty());
        when(followSubscriptionRepository.save(any(FollowSubscription.class))).thenAnswer(invocation -> {
            FollowSubscription subscription = invocation.getArgument(0);
            subscription.setId(UUID.randomUUID());
            return subscription;
        });

        FollowingService.FollowSubscriptionDto result = service.follow(FollowTargetType.SITE, "engineering");

        assertEquals("alice", result.userId());
        assertEquals(FollowTargetType.SITE, result.targetType());
        assertEquals("engineering", result.targetId());
        ArgumentCaptor<FollowSubscription> captor = ArgumentCaptor.forClass(FollowSubscription.class);
        verify(followSubscriptionRepository).save(captor.capture());
        assertEquals("alice", captor.getValue().getUserId());
        assertEquals(FollowTargetType.SITE, captor.getValue().getTargetType());
    }

    @Test
    @DisplayName("follow rejects missing user target")
    void followRejectsMissingUserTarget() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.follow(FollowTargetType.USER, "bob"));
        verify(followSubscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("unfollow rejects missing subscription")
    void unfollowRejectsMissingSubscription() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(followSubscriptionRepository.existsByUserIdAndTargetTypeAndTargetId("alice", FollowTargetType.SITE, "engineering"))
            .thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.unfollow(FollowTargetType.SITE, "engineering"));
    }

    @Test
    @DisplayName("getFollowingTargets splits subscriptions by target type")
    void getFollowingTargetsSplitsSubscriptionsByTargetType() {
        UUID nodeId = UUID.randomUUID();
        when(followSubscriptionRepository.findByUserIdOrderByCreatedAtDesc("alice")).thenReturn(List.of(
            subscription("alice", FollowTargetType.USER, "bob"),
            subscription("alice", FollowTargetType.SITE, "engineering"),
            subscription("alice", FollowTargetType.NODE, nodeId.toString())
        ));

        FollowingService.FollowingTargets result = service.getFollowingTargets("alice");

        assertEquals(List.of("bob"), result.userIds());
        assertEquals(List.of("engineering"), result.siteIds());
        assertEquals(List.of(nodeId), result.nodeIds());
    }

    @Test
    @DisplayName("follow normalizes node ids before validation")
    void followNormalizesNodeIds() {
        UUID nodeId = UUID.randomUUID();
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(new Document()));
        when(followSubscriptionRepository.findByUserIdAndTargetTypeAndTargetId("alice", FollowTargetType.NODE, nodeId.toString()))
            .thenReturn(Optional.empty());
        when(followSubscriptionRepository.save(any(FollowSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FollowingService.FollowSubscriptionDto result = service.follow(FollowTargetType.NODE, nodeId.toString());

        assertEquals(nodeId.toString(), result.targetId());
    }

    private FollowSubscription subscription(String userId, FollowTargetType targetType, String targetId) {
        FollowSubscription subscription = new FollowSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(userId);
        subscription.setTargetType(targetType);
        subscription.setTargetId(targetId);
        return subscription;
    }
}
