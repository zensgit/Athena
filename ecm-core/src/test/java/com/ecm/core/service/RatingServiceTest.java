package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Rating;
import com.ecm.core.entity.Rating.RatingScheme;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;

    private RatingService service;

    @BeforeEach
    void setUp() {
        service = new RatingService(ratingRepository, nodeRepository, securityService);
    }

    @Nested
    @DisplayName("rate()")
    class Rate {

        @Test
        @DisplayName("LIKES scheme always sets score to 1")
        void likesAlwaysScoreOne() {
            UUID nodeId = stubNode();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, "alice", RatingScheme.LIKES))
                .thenReturn(Optional.empty());
            when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Rating r = service.rate(nodeId, RatingScheme.LIKES, 999);

            assertEquals(1, r.getScore());
            assertEquals(RatingScheme.LIKES, r.getScheme());
            assertEquals("alice", r.getUserId());
        }

        @Test
        @DisplayName("FIVE_STAR accepts score 1-5")
        void fiveStarValid() {
            UUID nodeId = stubNode();
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, "bob", RatingScheme.FIVE_STAR))
                .thenReturn(Optional.empty());
            when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Rating r = service.rate(nodeId, RatingScheme.FIVE_STAR, 4);

            assertEquals(4, r.getScore());
        }

        @Test
        @DisplayName("FIVE_STAR rejects score < 1")
        void rejectsScoreTooLow() {
            UUID nodeId = stubNode();
            when(securityService.getCurrentUser()).thenReturn("bob");

            assertThrows(IllegalArgumentException.class,
                () -> service.rate(nodeId, RatingScheme.FIVE_STAR, 0));
        }

        @Test
        @DisplayName("FIVE_STAR rejects score > 5")
        void rejectsScoreTooHigh() {
            UUID nodeId = stubNode();
            when(securityService.getCurrentUser()).thenReturn("bob");

            assertThrows(IllegalArgumentException.class,
                () -> service.rate(nodeId, RatingScheme.FIVE_STAR, 6));
        }

        @Test
        @DisplayName("updates existing rating instead of creating duplicate")
        void updatesExisting() {
            UUID nodeId = stubNode();
            when(securityService.getCurrentUser()).thenReturn("alice");

            Rating existing = new Rating();
            existing.setId(UUID.randomUUID());
            existing.setScore(3);
            when(ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, "alice", RatingScheme.FIVE_STAR))
                .thenReturn(Optional.of(existing));
            when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Rating r = service.rate(nodeId, RatingScheme.FIVE_STAR, 5);

            assertEquals(5, r.getScore());
            assertNotNull(r.getId()); // same object updated
        }
    }

    @Nested
    @DisplayName("removeRating()")
    class Remove {

        @Test
        @DisplayName("delegates to repository delete")
        void delegates() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            UUID nodeId = UUID.randomUUID();

            service.removeRating(nodeId, RatingScheme.LIKES);

            verify(ratingRepository).deleteByNodeIdAndUserIdAndScheme(nodeId, "alice", RatingScheme.LIKES);
        }
    }

    @Nested
    @DisplayName("getSummary()")
    class Summary {

        @Test
        @DisplayName("returns count, average, and total for scheme")
        void returnsSummary() {
            UUID nodeId = UUID.randomUUID();
            when(ratingRepository.countByNodeIdAndScheme(nodeId, RatingScheme.FIVE_STAR)).thenReturn(10L);
            when(ratingRepository.averageScoreByNodeIdAndScheme(nodeId, RatingScheme.FIVE_STAR)).thenReturn(3.5);
            when(ratingRepository.sumScoreByNodeIdAndScheme(nodeId, RatingScheme.FIVE_STAR)).thenReturn(35L);

            RatingService.RatingSummary summary = service.getSummary(nodeId, RatingScheme.FIVE_STAR);

            assertEquals(10L, summary.count());
            assertEquals(3.5, summary.average(), 0.01);
            assertEquals(35L, summary.total());
        }

        @Test
        @DisplayName("returns zero average when no ratings")
        void zeroWhenEmpty() {
            UUID nodeId = UUID.randomUUID();
            when(ratingRepository.countByNodeIdAndScheme(nodeId, RatingScheme.FIVE_STAR)).thenReturn(0L);

            RatingService.RatingSummary summary = service.getSummary(nodeId, RatingScheme.FIVE_STAR);

            assertEquals(0L, summary.count());
            assertEquals(0.0, summary.average(), 0.01);
        }
    }

    @Nested
    @DisplayName("getUserRating()")
    class UserRating {

        @Test
        @DisplayName("returns existing user rating")
        void returnsExisting() {
            UUID nodeId = UUID.randomUUID();
            when(securityService.getCurrentUser()).thenReturn("alice");
            Rating r = new Rating();
            r.setScore(4);
            when(ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, "alice", RatingScheme.FIVE_STAR))
                .thenReturn(Optional.of(r));

            Optional<Rating> result = service.getUserRating(nodeId, RatingScheme.FIVE_STAR);

            assertTrue(result.isPresent());
            assertEquals(4, result.get().getScore());
        }

        @Test
        @DisplayName("returns empty when no rating")
        void returnsEmpty() {
            UUID nodeId = UUID.randomUUID();
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, "alice", RatingScheme.LIKES))
                .thenReturn(Optional.empty());

            assertTrue(service.getUserRating(nodeId, RatingScheme.LIKES).isEmpty());
        }
    }

    // ================================================================= helpers

    private UUID stubNode() {
        Folder folder = new Folder();
        UUID nodeId = UUID.randomUUID();
        folder.setId(nodeId);
        folder.setName("test");
        folder.setPath("/test");
        when(nodeRepository.findByIdAndDeletedFalse(nodeId)).thenReturn(Optional.of(folder));
        return nodeId;
    }
}
