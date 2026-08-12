#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
./scripts/test-all.sh
./mvnw --no-transfer-progress -DskipTests package
printf '\nBuild verified: target/resource-entitlement-engine-0.1.0-SNAPSHOT.jar\n'
