package com.ecm.core.controller;

import com.ecm.core.entity.Rating;
import com.ecm.core.entity.Rating.RatingScheme;
import com.ecm.core.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RatingControllerTest {

    private MockMvc mockMvc;
    @Mock private RatingService ratingService;

    @BeforeEach
    void setUp() {
        RatingController controller = new RatingController(ratingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST creates rating and returns 201")
    void postCreatesRating() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Rating rating = new Rating();
        rating.setId(UUID.randomUUID());
        rating.setUserId("alice");
        rating.setScheme(RatingScheme.FIVE_STAR);
        rating.setScore(4);

        when(ratingService.rate(nodeId, RatingScheme.FIVE_STAR, 4)).thenReturn(rating);

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/ratings", nodeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "scheme": "FIVE_STAR", "score": 4 }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scheme").value("FIVE_STAR"))
            .andExpect(jsonPath("$.score").value(4));
    }

    @Test
    @DisplayName("DELETE removes rating and returns 204")
    void deleteRemovesRating() throws Exception {
        UUID nodeId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/nodes/{nodeId}/ratings/{scheme}", nodeId, "LIKES"))
            .andExpect(status().isNoContent());

        verify(ratingService).removeRating(nodeId, RatingScheme.LIKES);
    }

    @Test
    @DisplayName("GET /summary returns likes and fivestar summaries")
    void getSummary() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(ratingService.getSummary(nodeId, RatingScheme.LIKES))
            .thenReturn(new RatingService.RatingSummary(RatingScheme.LIKES, 5, 1.0, 5));
        when(ratingService.getSummary(nodeId, RatingScheme.FIVE_STAR))
            .thenReturn(new RatingService.RatingSummary(RatingScheme.FIVE_STAR, 3, 4.0, 12));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/ratings/summary", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likes.count").value(5))
            .andExpect(jsonPath("$.likes.total").value(5))
            .andExpect(jsonPath("$.fivestar.count").value(3))
            .andExpect(jsonPath("$.fivestar.average").value(4.0));
    }

    @Test
    @DisplayName("GET /mine returns current user's ratings")
    void getMyRatings() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Rating like = new Rating();
        like.setScore(1);
        when(ratingService.getUserRating(nodeId, RatingScheme.LIKES)).thenReturn(Optional.of(like));
        when(ratingService.getUserRating(nodeId, RatingScheme.FIVE_STAR)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/ratings/mine", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.likeScore").value(1))
            .andExpect(jsonPath("$.starScore").isEmpty());
    }

    @Test
    @DisplayName("GET returns list of all ratings for node")
    void listRatings() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Rating r = new Rating();
        r.setId(UUID.randomUUID());
        r.setUserId("alice");
        r.setScheme(RatingScheme.LIKES);
        r.setScore(1);
        when(ratingService.getRatings(nodeId)).thenReturn(List.of(r));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/ratings", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].userId").value("alice"));
    }
}
