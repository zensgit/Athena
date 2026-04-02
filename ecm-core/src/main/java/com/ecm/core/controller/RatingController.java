package com.ecm.core.controller;

import com.ecm.core.entity.Rating;
import com.ecm.core.entity.Rating.RatingScheme;
import com.ecm.core.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/nodes/{nodeId}/ratings", "/api/v1/nodes/{nodeId}/ratings"})
@Tag(name = "Ratings", description = "Node rating and like management")
public class RatingController {

    private final RatingService ratingService;

    @GetMapping
    @Operation(summary = "List ratings for a node")
    public ResponseEntity<List<RatingDto>> listRatings(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        return ResponseEntity.ok(
            ratingService.getRatings(nodeId).stream().map(RatingDto::from).toList()
        );
    }

    @PostMapping
    @Operation(summary = "Rate a node",
               description = "For LIKES scheme, score is ignored (always 1). For FIVE_STAR, score must be 1-5.")
    public ResponseEntity<RatingDto> rate(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @RequestBody RateRequest request) {
        Rating rating = ratingService.rate(nodeId, request.scheme(), request.score());
        return ResponseEntity.status(HttpStatus.CREATED).body(RatingDto.from(rating));
    }

    @DeleteMapping("/{scheme}")
    @Operation(summary = "Remove current user's rating")
    public ResponseEntity<Void> removeRating(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Rating scheme") @PathVariable RatingScheme scheme) {
        ratingService.removeRating(nodeId, scheme);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    @Operation(summary = "Get rating summary",
               description = "Returns count, average, and total for each scheme")
    public ResponseEntity<RatingSummaryResponse> getSummary(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        RatingService.RatingSummary likes = ratingService.getSummary(nodeId, RatingScheme.LIKES);
        RatingService.RatingSummary stars = ratingService.getSummary(nodeId, RatingScheme.FIVE_STAR);
        return ResponseEntity.ok(new RatingSummaryResponse(
            new SchemeSummary(likes.count(), likes.average(), likes.total()),
            new SchemeSummary(stars.count(), stars.average(), stars.total())
        ));
    }

    @GetMapping("/mine")
    @Operation(summary = "Get current user's ratings for this node")
    public ResponseEntity<MyRatingsResponse> getMyRatings(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        var myLike = ratingService.getUserRating(nodeId, RatingScheme.LIKES);
        var myStar = ratingService.getUserRating(nodeId, RatingScheme.FIVE_STAR);
        return ResponseEntity.ok(new MyRatingsResponse(
            myLike.map(Rating::getScore).orElse(null),
            myStar.map(Rating::getScore).orElse(null)
        ));
    }

    // ---- DTOs ---------------------------------------------------------------

    public record RateRequest(RatingScheme scheme, int score) {}

    public record RatingDto(UUID id, String userId, RatingScheme scheme, int score, LocalDateTime createdAt) {
        static RatingDto from(Rating r) {
            return new RatingDto(r.getId(), r.getUserId(), r.getScheme(), r.getScore(), r.getCreatedAt());
        }
    }

    public record RatingSummaryResponse(SchemeSummary likes, SchemeSummary fivestar) {}

    public record SchemeSummary(long count, double average, long total) {}

    public record MyRatingsResponse(Integer likeScore, Integer starScore) {}
}
