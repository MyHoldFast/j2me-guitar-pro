#!/bin/bash
set -e
cd "$(dirname "$0")"
exec python3 server.py "${TABS:-tabs.zip}" "${INDEX:-index.tsv}" "${CACHE:-cache}" "${HOST:-0.0.0.0}" "${PORT:-7000}"
