#!/usr/bin/env bash
# Rigenera le richieste della collection Bruno (bruno/) da openapi/openapi.yaml,
# con la CLI ufficiale di Bruno ("bru import openapi"). Solo le cartelle delle
# richieste (una per tag OpenAPI) sono generate e sostituite a ogni esecuzione;
# bruno/opencollection.yml (auth OAuth2 verso Keycloak) e bruno/environments/
# sono scritti a mano e non vengono mai toccati - vedi
# docs/adr/0014-openapi-contract-and-bruno-collection.md.
#
# Da eseguire dopo "./gradlew updateOpenApiSpec". La CI lo riesegue e fallisce
# se bruno/ non è allineata alla spec committata.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SPEC="openapi/openapi.yaml"
COLLECTION_DIR="bruno"
COLLECTION_NAME="user-service"
# Versione fissata: l'output generato è committato, quindi deve essere
# riproducibile identico tra macchine diverse e la CI.
BRUNO_CLI="@usebruno/cli@4.2.0"

log() { echo "[$(basename "$0")] $*"; }

require_cmd() {
  local cmd="$1" hint="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[$(basename "$0")] ERRORE: comando '$cmd' non trovato. $hint" >&2
    exit 1
  fi
}

require_cmd npx "installa Node.js (include npx): https://nodejs.org/"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
# Deve non esistere ancora: su una cartella esistente la CLI crea invece una
# sottocartella col nome della collection.
GENERATED="$WORK_DIR/collection"

log "genero la collection da ${SPEC} con ${BRUNO_CLI}..."
npx --yes "$BRUNO_CLI" import openapi --source "$SPEC" --output "$GENERATED" \
  --collection-name "$COLLECTION_NAME" --group-by tags >/dev/null

log "sostituisco le cartelle delle richieste in ${COLLECTION_DIR}/..."
find "$COLLECTION_DIR" -mindepth 1 -maxdepth 1 -type d ! -name environments -exec rm -rf {} +
find "$GENERATED" -mindepth 1 -maxdepth 1 -type d ! -name environments -exec cp -r {} "$COLLECTION_DIR/" \;

log "fatto: ${COLLECTION_DIR}/ allineata a ${SPEC}."
