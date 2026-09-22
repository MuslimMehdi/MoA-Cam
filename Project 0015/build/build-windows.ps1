$ErrorActionPreference = 'Stop'
python -m venv .venv
. .\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r desktop\requirements.txt pyinstaller
pyinstaller --noconfirm --clean --windowed --name "MoA Cam" --icon desktop\assets\moa_logo.ico desktop\moa_cam.py
Write-Host "Built dist\\MoA Cam\\MoA Cam.exe"
