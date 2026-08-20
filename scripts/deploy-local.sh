#!/usr/bin/env bash
# Builda l'immagine e la rilascia sul cluster kind locale ("onepiece",
# namespace "app" — vedi onepiece-infrastructure/kubernetes/kind-config.yaml
# e helmfile.yaml). Nessun registry: l'immagine viene caricata direttamente
# nel containerd di kind, poi il Deployment viene riavviato per usarla —
# imagePullPolicy IfNotPresent + tag fisso ":local" significano che
# Kubernetes non si accorgerebbe da solo di un'immagine ricostruita.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CLUSTER_NAME="onepiece"
NAMESPACE="app"
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

require_cmd kind "installa kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
require_cmd kubectl "installa kubectl: https://kubernetes.io/docs/tasks/tools/"

"$REPO_ROOT/scripts/build-image.sh"

log "carico ${IMAGE_NAME}:${IMAGE_TAG} nel cluster kind '${CLUSTER_NAME}'..."
kind load docker-image "${IMAGE_NAME}:${IMAGE_TAG}" --name "$CLUSTER_NAME"

log "riavvio il deployment ${IMAGE_NAME} nel namespace ${NAMESPACE}..."
kubectl rollout restart "deployment/${IMAGE_NAME}" -n "$NAMESPACE"
kubectl rollout status "deployment/${IMAGE_NAME}" -n "$NAMESPACE"

log "fatto: ${IMAGE_NAME}:${IMAGE_TAG} è in esecuzione nel cluster."
