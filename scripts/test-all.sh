#!/usr/bin/env bash
set -uo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

set +e
./mvnw --no-transfer-progress test
MVN_STATUS=$?
set -e

if [[ -d target/surefire-reports ]]; then
  python3 scripts/print-test-results.py
  REPORT_STATUS=$?
else
  REPORT_STATUS=1
fi

if [[ $MVN_STATUS -ne 0 || $REPORT_STATUS -ne 0 ]]; then
  exit 1
fi
