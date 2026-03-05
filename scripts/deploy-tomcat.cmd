@echo off
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%deploy-tomcat.ps1" %*
if errorlevel 1 (
  echo.
  echo Deployment failed.
  pause
  exit /b 1
)
exit /b 0
