package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Rating;
import com.ecm.core.entity.Rating.RatingScheme;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;

    /**
     * Rate a node (create or update). For LIKES scheme, score is always 1.
     */
    @Transactional
    public Rating rate(UUID nodeId, RatingScheme scheme, int score) {
        Node node = nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
        String userId = securityService.getCurrentUser();

        if (scheme == RatingScheme.LIKES) {
            score = 1;
        } else if (scheme == RatingScheme.FIVE_STAR && (score < 1 || score > 5)) {
            throw new IllegalArgumentException("FIVE_STAR score must be between 1 and 5");
        }

        Optional<Rating> existing = ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, userId, scheme);
        Rating rating;
        if (existing.isPresent()) {
            rating = existing.get();
            rating.setScore(score);
        } else {
            rating = new Rating();
            rating.setNode(node);
            rating.setUserId(userId);
            rating.setScheme(scheme);
            rating.setScore(score);
        }
        return ratingRepository.save(rating);
    }

    /**
     * Remove the current user's rating for a node+scheme.
     */
    @Transactional
    public void removeRating(UUID nodeId, RatingScheme scheme) {
        String userId = securityService.getCurrentUser();
        ratingRepository.deleteByNodeIdAndUserIdAndScheme(nodeId, userId, scheme);
    }

    /**
     * Get all ratings for a node.
     */
    @Transactional(readOnly = true)
    public List<Rating> getRatings(UUID nodeId) {
        return ratingRepository.findByNodeId(nodeId);
    }

    /**
     * Get the current user's rating for a node+scheme.
     */
    @Transactional(readOnly = true)
    public Optional<Rating> getUserRating(UUID nodeId, RatingScheme scheme) {
        String userId = securityService.getCurrentUser();
        return ratingRepository.findByNodeIdAndUserIdAndScheme(nodeId, userId, scheme);
    }

    /**
     * Get summary stats for a node+scheme.
     */
    @Transactional(readOnly = true)
    public RatingSummary getSummary(UUID nodeId, RatingScheme scheme) {
        long count = ratingRepository.countByNodeIdAndScheme(nodeId, scheme);
        double average = count > 0 ? ratingRepository.averageScoreByNodeIdAndScheme(nodeId, scheme) : 0;
        long total = ratingRepository.sumScoreByNodeIdAndScheme(nodeId, scheme);
        return new RatingSummary(scheme, count, average, total);
    }

    public record RatingSummary(RatingScheme scheme, long count, double average, long total) {}
}
