#!/bin/bash
set -e
cd "$(dirname "$0")"
python3 tgz2zip.py "${1:?usage: tgz2zip.sh archive.tgz|- [tabs.zip]}" "${2:-tabs.zip}"
