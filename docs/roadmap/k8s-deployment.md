# Kubernetes Deployment

## Targets
Backend, frontend, PostgreSQL (managed), Elasticsearch, Redis, RabbitMQ, Nginx, Prometheus/Grafana.

## Manifests
- Use Helm or Kustomize; define HPA, PDB, PVCs, NetworkPolicy, Ingress.

## Readiness/Liveness
Spring Boot `/actuator/health`, frontend `/healthz`.

## Secrets & Config
Use External Secrets or sealed-secrets.

