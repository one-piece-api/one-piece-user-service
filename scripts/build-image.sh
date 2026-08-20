#!/usr/bin/env bash
# Builda il progetto (Gradle) e poi l'immagine Docker, usando il Dockerfile
# alla radice del repo. Nessun push: l'immagine resta solo nel Docker
# daemon locale, pronta per "kind load docker-image" (vedi
# onepiece-infrastructure/helm/charts/user-service).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

IMAGE_NAME="one-piece-user-service"
IMAGE_TAG="${IMAGE_TAG:-local}"

log() { echo "[$(basename "$0")] $*"; }

require_cmd() {
  local cmd="$1" hint="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[$(basename "$0")] ERRORE: comando '$cmd' non trovato. $hint" >&2
    exit 1
  fi
}

require_cmd docker "installa Docker Desktop (o il daemon Docker) e assicurati che sia in esecuzione."

log "build Gradle (./gradlew bootJar)..."
./gradlew bootJar

log "build immagine Docker ${IMAGE_NAME}:${IMAGE_TAG}..."
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .

log "fatto: ${IMAGE_NAME}:${IMAGE_TAG} disponibile nel Docker daemon locale."
