# Cloud Run image for the inference gateway only.
# Build from the repo root:
#   docker build -f backend/Dockerfile -t medtrack-gateway .
FROM python:3.12-slim

WORKDIR /srv/MedTrack/backend

COPY backend/pyproject.toml ./pyproject.toml
COPY backend/medtrack_gateway ./medtrack_gateway
COPY contracts /srv/MedTrack/contracts

RUN pip install --no-cache-dir .

ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "uvicorn medtrack_gateway.app:app --host 0.0.0.0 --port ${PORT}"]
