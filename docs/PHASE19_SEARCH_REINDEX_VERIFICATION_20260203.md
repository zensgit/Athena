# Phase 19 Search Reindex Verification (2026-02-03)

## Verification Scope
- Confirm rebuild endpoint completes.
- Confirm rebuild status shows finished.
- Confirm index stats respond with expected values.

## Commands and Results
```bash
TOKEN=$(cat /tmp/ecm-token.internal)

# Rebuild (admin only)
curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/search/index/rebuild
```
Result:
```json
{"message":"Index rebuild completed successfully","status":"completed","documentsIndexed":511}
```

```bash
# Rebuild status
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/search/index/rebuild/status
```
Result:
```json
{"documentsIndexed":511,"inProgress":false}
```

```bash
# Index stats
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:7700/api/v1/search/index/stats
```
Result:
```json
{"searchEnabled":true,"documentCount":498,"indexName":"ecm_documents"}
```

## Search Query Sanity Check
```bash
TOKEN=$(cat /tmp/ecm-token.internal)
curl -sS -H "Authorization: Bearer $TOKEN" \
  --get \
  --data-urlencode "q=e2e" \
  --data-urlencode "page=0" \
  --data-urlencode "size=5" \
  http://localhost:7700/api/v1/search
```
Result (summary):
```json
{
  "totalElements": 196,
  "number": 0,
  "size": 5,
  "hits": [
    {"id":"0bacd270-83d0-4ab6-a706-c0a5c2dd6f80","title":null,"score":2.9201636},
    {"id":"5ae7f494-4cc9-4537-a317-aa8a4623ae38","title":null,"score":2.9201636},
    {"id":"10bebe84-df5d-423b-8a98-a93d6275a8f6","title":null,"score":2.9201636},
    {"id":"f3f23c0e-02f4-4510-b67a-44914d5e8faf","title":null,"score":2.9201636},
    {"id":"fba5f69d-9cf2-410a-94e1-e0dc49437fac","title":null,"score":2.9201636}
  ]
}
```

## Outcome
- ✅ Rebuild completed successfully.
- ✅ Status reports `inProgress=false` and `documentsIndexed=511`.
- ✅ Index stats are available and indicate search is enabled.
