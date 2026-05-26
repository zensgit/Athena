FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-chi-sim \
        poppler-utils \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --default-timeout=120 --retries 10 -r requirements.txt

COPY app/ ./app/

# Create model storage directory and a fixed-uid non-root user (P0a-3b / matrix A11).
# chown the writable paths BEFORE dropping privilege: /var/ml-service is the model-volume root
# (/train writes model.pkl here) and /app is the WORKDIR + app code.
# Fixed numeric uid 10001 is stable across rebuilds and drops into K8s runAsUser.
# Brownfield note: an empty named volume inherits this owner on first mount, but a PRE-EXISTING
# root-owned ml_models (resolved name athena_ml_models) volume must be chowned once by the operator
# (see docs/HARDENING_P0A3B_MLSERVICE_NONROOT_BRIEF_20260526.md), else /train fails on write.
RUN mkdir -p /var/ml-service \
    && groupadd -r app -g 10001 \
    && useradd -r -g app -u 10001 app \
    && chown -R 10001:10001 /var/ml-service /app
USER 10001:10001

EXPOSE 8080

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
