@echo off
chcp 65001 >nul
echo === Avtonomera: install, build and run on phone ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
echo.
pause
