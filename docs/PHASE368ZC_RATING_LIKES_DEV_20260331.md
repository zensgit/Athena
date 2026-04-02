# Phase 368ZC — Rating / Likes

> **Scope**: Node rating entity, dual-scheme service (LIKES + FIVE_STAR), REST endpoints, frontend panel
> **Date**: 2026-03-31

---

## 1. Problem Statement

Athena had no way to rate or like documents. Alfresco provides a rating service with
pluggable schemes (likes, fiveStarRating). Users need social feedback signals for
content quality and relevance.

## 2. What Was Built

### Entity

```java
@Entity @Table(name = "ratings")
Rating {
    UUID id;
    Node node;           // FK → nodes
    String userId;
    RatingScheme scheme; // LIKES | FIVE_STAR
    int score;           // LIKES=always 1, FIVE_STAR=1..5
    LocalDateTime createdAt;
}
```

Unique constraint: `(node_id, user_id, scheme)` — one rating per user per scheme per node.

### Service (RatingService)

| Method | Description |
|--------|-------------|
| `rate(nodeId, scheme, score)` | Create or update rating. LIKES coerces to 1, FIVE_STAR validates 1-5 |
| `removeRating(nodeId, scheme)` | Remove current user's rating |
| `getRatings(nodeId)` | All ratings for a node |
| `getUserRating(nodeId, scheme)` | Current user's rating |
| `getSummary(nodeId, scheme)` | Count + average + total |

### Controller Endpoints (5)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/nodes/{id}/ratings` | List all ratings |
| POST | `/api/nodes/{id}/ratings` | Rate (body: `{scheme, score}`) |
| DELETE | `/api/nodes/{id}/ratings/{scheme}` | Remove own rating |
| GET | `/api/nodes/{id}/ratings/summary` | Likes + FiveStar summaries |
| GET | `/api/nodes/{id}/ratings/mine` | Current user's ratings |

### Frontend

**ratingService.ts** — 5 methods: `listRatings`, `rate`, `removeRating`, `getSummary`, `getMyRatings`

**NodeRatingPanel.tsx** — reusable component with:
- Like toggle button (ThumbUp/ThumbUpOutlined) + like count chip
- Five-star MUI Rating component + average display

**PropertiesDialog.tsx** — NodeRatingPanel wired in between Aspects and Content Type sections.

### DB Migration 045

```sql
CREATE TABLE ratings (id UUID PK, node_id UUID FK, user_id VARCHAR, scheme VARCHAR, score INT, created_at TIMESTAMP);
UNIQUE (node_id, user_id, scheme);
```

## 3. Files Created

| File | Purpose |
|------|---------|
| `entity/Rating.java` | Entity + RatingScheme enum |
| `repository/RatingRepository.java` | CRUD + aggregate queries |
| `service/RatingService.java` | Business logic + RatingSummary record |
| `controller/RatingController.java` | REST endpoints + DTOs |
| `db/changelog/changes/045-create-ratings-table.xml` | Migration |
| `services/ratingService.ts` | Frontend API service |
| `components/ratings/NodeRatingPanel.tsx` | Like button + star rating panel |
| `test/service/RatingServiceTest.java` | 10 service tests |
| `test/controller/RatingControllerTest.java` | 5 controller tests |

## 4. Files Modified

| File | Change |
|------|--------|
| `components/dialogs/PropertiesDialog.tsx` | +NodeRatingPanel between Aspects and Content Type |
| `db/changelog/db.changelog-master.xml` | +045 |

## 5. NOT Modified

All preview/rendition/search/ops-governance files untouched.
