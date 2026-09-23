#!/bin/bash
set -e
cd "$(dirname "$0")"
python3 build_index.py "${1:-tabs.zip}" "${2:-index.tsv}"
