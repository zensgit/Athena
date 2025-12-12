from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import pickle
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neural_network import MLPClassifier
import os

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

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: Optional[str]

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
        model_loaded=model_data is not None,
        model_version=model_data.get('version') if model_data else None
    )

@app.post("/api/ml/classify", response_model=ClassifyResponse)
async def classify_document(request: ClassifyRequest):
    """Classify document"""
    global model_data

    if model_data is None:
        # Mock response if model is not loaded (for testing/development)
        return ClassifyResponse(
            prediction="Unclassified",
            confidence=0.0,
            alternatives=[]
        )
        # Or raise exception: raise HTTPException(status_code=503, detail="Model not loaded")

    vectorizer = model_data['vectorizer']
    classifier = model_data['classifier']
    label_encoder = model_data['label_encoder']

    # Vectorize
    try:
        X = vectorizer.transform([request.text])
        
        # Predict
        proba = classifier.predict_proba(X)[0]
        pred_idx = np.argmax(proba)
        confidence = float(proba[pred_idx])
        prediction = label_encoder.inverse_transform([pred_idx])[0]

        # Get Top-3 alternatives
        top_indices = np.argsort(proba)[-3:][::-1]
        alternatives = [
            {
                "category": label_encoder.inverse_transform([idx])[0],
                "confidence": float(proba[idx])
            }
            for idx in top_indices
        ]

        return ClassifyResponse(
            prediction=prediction,
            confidence=confidence,
            alternatives=alternatives
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/ml/train")
async def train_model(request: TrainRequest):
    """Train model"""
    global model_data

    if len(request.documents) < 5: # Lowered threshold for testing
        raise HTTPException(status_code=400, detail="Need at least 5 documents")

    try:
        # Prepare training data
        texts = [doc['text'] for doc in request.documents]
        categories = [doc['category'] for doc in request.documents]

        # Vectorize
        vectorizer = TfidfVectorizer(max_features=5000, stop_words='english')
        X = vectorizer.fit_transform(texts)

        # Label encoding
        from sklearn.preprocessing import LabelEncoder
        label_encoder = LabelEncoder()
        y = label_encoder.fit_transform(categories)

        # Train (using lightweight config for speed)
        classifier = MLPClassifier(hidden_layer_sizes=(50,), max_iter=200)
        classifier.fit(X, y)

        # Save model
        model_data = {
            'vectorizer': vectorizer,
            'classifier': classifier,
            'label_encoder': label_encoder,
            'version': '1.0',
            'trained_samples': len(texts)
        }

        with open(MODEL_PATH, 'wb') as f:
            pickle.dump(model_data, f)

        return {"status": "trained", "samples": len(texts)}
    except Exception as e:
         raise HTTPException(status_code=500, detail=str(e))
