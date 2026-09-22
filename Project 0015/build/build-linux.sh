#!/usr/bin/env bash
set -euo pipefail
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r desktop/requirements.txt pyinstaller
pyinstaller --noconfirm --clean --windowed --name 'MoA-Cam' desktop/moa_cam.py
printf '\nBuilt: dist/MoA-Cam/MoA-Cam\n'
