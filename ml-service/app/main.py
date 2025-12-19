from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import pickle
import os
import re
from collections import Counter

app = FastAPI(title="ECM ML Service", version="1.0.0")

# Global model variable
model_data = None
MODEL_PATH = os.getenv("MODEL_PATH", "/var/ml-service/model.pkl")

class ClassifyRequest(BaseModel):
    text: str
    candidates: Optional[List[str]] = None

class ClassifyResponse(BaseModel):
    prediction: str
    confidence: float
    alternatives: List[dict]

class TrainRequest(BaseModel):
    documents: List[dict]  # [{text, tags, category}]

class TagSuggestRequest(BaseModel):
    text: str
    maxTags: int = 5

class TagSuggestResponse(BaseModel):
    tags: List[str]

class HealthResponse(BaseModel):
    status: str
    modelLoaded: bool
    modelVersion: Optional[str]

DEFAULT_ENGLISH_STOPWORDS = {
    "the", "and", "for", "with", "from", "that", "this", "are", "was", "were", "have", "has", "had",
    "but", "not", "you", "your", "their", "they", "them", "can", "could", "should", "would",
    "http", "https", "www", "com",
}

def _extract_tokens(text: str) -> List[str]:
    lower = text.lower()

    english_tokens = re.findall(r"[a-zA-Z][a-zA-Z0-9_-]{2,}", lower)
    english_tokens = [
        t for t in english_tokens
        if t not in DEFAULT_ENGLISH_STOPWORDS and len(t) <= 40
    ]

    cjk_tokens = re.findall(r"[\u4e00-\u9fff]{2,}", text)
    cjk_tokens = [t for t in cjk_tokens if len(t) <= 12]

    return english_tokens + cjk_tokens

def _classify_heuristic(text: str, candidates: Optional[List[str]] = None):
    lower = text.lower()
    business_keywords = [
        "invoice", "contract", "purchase", "order", "payment", "finance", "budget",
        "proposal", "quotation", "sales", "agreement", "business",
        "发票", "合同", "付款", "预算", "采购", "报价", "订单",
    ]
    technical_keywords = [
        "api", "sdk", "spec", "architecture", "design", "implementation", "bug", "stacktrace",
        "database", "schema", "kubernetes", "docker", "spring", "react", "java", "typescript",
        "技术", "架构", "设计", "实现", "接口", "数据库", "规范", "错误",
    ]

    score_business = sum(1 for kw in business_keywords if kw in lower)
    score_technical = sum(1 for kw in technical_keywords if kw in lower)

    if score_business == 0 and score_technical == 0:
        probs = {"General": 0.6, "Business": 0.2, "Technical": 0.2}
    elif score_business >= score_technical:
        probs = {"Business": 0.6, "General": 0.25, "Technical": 0.15}
    else:
        probs = {"Technical": 0.6, "General": 0.25, "Business": 0.15}

    if candidates:
        filtered = {k: v for k, v in probs.items() if k in candidates}
        if filtered:
            total = sum(filtered.values()) or 1.0
            probs = {k: v / total for k, v in filtered.items()}
        else:
            probs = {candidates[0]: 1.0}

    prediction = max(probs.items(), key=lambda x: x[1])[0]

    alternatives = [
        {"category": cat, "confidence": float(conf)}
        for cat, conf in sorted(probs.items(), key=lambda x: x[1], reverse=True)
    ]
    confidence = float(dict(probs)[prediction])
    return prediction, confidence, alternatives

def _suggest_tags_simple(text: str, max_tags: int) -> List[str]:
    max_tags = max(1, min(int(max_tags or 5), 20))
    counts = Counter(_extract_tokens(text))
    if not counts:
        return []

    return [token for token, _ in counts.most_common(max_tags)]

@app.on_event("startup")
async def load_model():
    """Load model on startup"""
    global model_data
    try:
        with open(MODEL_PATH, 'rb') as f:
            model_data = pickle.load(f)
        print("Model loaded successfully")
    except FileNotFoundError:
        print("No model found, will need training")
        model_data = None
    except Exception as e:
        print(f"Error loading model: {e}")
        model_data = None

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check"""
    return HealthResponse(
        status="healthy",
        modelLoaded=model_data is not None,
        modelVersion=model_data.get("version") if isinstance(model_data, dict) else None
    )

@app.post("/api/ml/classify", response_model=ClassifyResponse)
async def classify_document(request: ClassifyRequest):
    """Classify document"""
    global model_data

    candidates = request.candidates or None

    try:
        if model_data and isinstance(model_data, dict) and model_data.get("category_keywords"):
            tokens = _extract_tokens(request.text)
            token_counts = Counter(tokens)

            available_categories = list(model_data.get("category_keywords", {}).keys())
            target_categories = candidates or available_categories or ["General", "Business", "Technical"]

            scores = {}
            for cat in target_categories:
                keywords = set(model_data.get("category_keywords", {}).get(cat, []))
                if not keywords:
                    scores[cat] = 0
                    continue
                scores[cat] = sum(count for token, count in token_counts.items() if token in keywords)

            total = sum(scores.values())
            if total <= 0:
                prediction, confidence, alternatives = _classify_heuristic(request.text, candidates=target_categories)
                return ClassifyResponse(prediction=prediction, confidence=confidence, alternatives=alternatives)

            sorted_scores = sorted(scores.items(), key=lambda x: x[1], reverse=True)
            prediction = sorted_scores[0][0]
            confidence = float(sorted_scores[0][1] / total)
            alternatives = [
                {"category": cat, "confidence": float(score / total)}
                for cat, score in sorted_scores[:3]
            ]
            return ClassifyResponse(prediction=prediction, confidence=confidence, alternatives=alternatives)

        prediction, confidence, alternatives = _classify_heuristic(request.text, candidates=candidates)
        return ClassifyResponse(prediction=prediction, confidence=confidence, alternatives=alternatives)

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/ml/suggest-tags", response_model=TagSuggestResponse)
async def suggest_tags(request: TagSuggestRequest):
    """Suggest tags for given text"""
    if request.text is None or len(request.text) < 10:
        return TagSuggestResponse(tags=[])

    tags = _suggest_tags_simple(request.text, request.maxTags)
    return TagSuggestResponse(tags=tags)

@app.post("/api/ml/train")
async def train_model(request: TrainRequest):
    """Train a lightweight keyword model"""
    global model_data

    if len(request.documents) < 5:
        raise HTTPException(status_code=400, detail="Need at least 5 documents")

    try:
        category_counters = {}
        trained = 0

        for doc in request.documents:
            text = (doc.get("text") or "").strip()
            category = (doc.get("category") or "General").strip() or "General"
            if not text:
                continue

            tokens = _extract_tokens(text)
            if not tokens:
                continue

            category_counters.setdefault(category, Counter()).update(tokens)
            trained += 1

        if trained < 5:
            raise HTTPException(status_code=400, detail="Not enough usable documents for training")

        category_keywords = {
            category: [token for token, _ in counter.most_common(50)]
            for category, counter in category_counters.items()
        }

        model_data = {
            "version": "simple-1.0",
            "trained_samples": trained,
            "category_keywords": category_keywords,
        }

        with open(MODEL_PATH, "wb") as f:
            pickle.dump(model_data, f)

        return {
            "status": "trained",
            "samples": trained,
            "categories": list(category_keywords.keys()),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
