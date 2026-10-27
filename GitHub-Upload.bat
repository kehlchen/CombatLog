@echo off
setlocal EnableExtensions
title GitHub Projekt-Upload

echo ============================================
echo        GitHub Projekt-Upload
echo ============================================
echo.
echo Aktueller Projektordner:
cd
echo.

where git >nul 2>&1
if errorlevel 1 (
    echo FEHLER: Git ist nicht installiert.
    echo https://git-scm.com/downloads/win
    pause
    exit /b 1
)

set "GH_USER="
set /p "GH_USER=GitHub-Benutzername: "
if not defined GH_USER goto :cancel

set "GIT_NAME="
set /p "GIT_NAME=Git-Commit-Name [Enter = %GH_USER%]: "
if not defined GIT_NAME set "GIT_NAME=%GH_USER%"

set "GIT_EMAIL="
set /p "GIT_EMAIL=Git-E-Mail-Adresse: "
if not defined GIT_EMAIL goto :cancel

set "REPO_NAME="
set /p "REPO_NAME=Repository-Name: "
if not defined REPO_NAME goto :cancel

rem .git am Ende entfernen, falls eingegeben
if /I "%REPO_NAME:~-4%"==".git" set "REPO_NAME=%REPO_NAME:~0,-4%"

set "REPO_URL=https://github.com/%GH_USER%/%REPO_NAME%.git"

echo.
echo Ziel: %REPO_URL%
echo.
set "CONFIRM="
set /p "CONFIRM=Projektdateien hochladen? (J/N): "
if /I not "%CONFIRM%"=="J" goto :cancel

rem Zuerst Git initialisieren
if not exist ".git" (
    git init
    if errorlevel 1 goto :error
)

rem Erst nach git init die Identitaet konfigurieren
git config user.name "%GIT_NAME%"
if errorlevel 1 goto :error

git config user.email "%GIT_EMAIL%"
if errorlevel 1 goto :error

echo.
echo [1/5] Dateien hinzufuegen...
git add -A
if errorlevel 1 goto :error

echo.
echo [2/5] Commit erstellen...
git rev-parse --verify HEAD >nul 2>&1
if errorlevel 1 (
    git diff --cached --quiet
    if not errorlevel 1 (
        echo Keine Dateien fuer den ersten Commit gefunden.
        goto :cancel
    )
    git commit -m "Initial upload"
    if errorlevel 1 goto :error
) else (
    git diff --cached --quiet
    if errorlevel 1 (
        git commit -m "Update project"
        if errorlevel 1 goto :error
    ) else (
        echo Keine neuen Aenderungen vorhanden.
    )
)

echo.
echo [3/5] Branch auf main setzen...
git branch -M main
if errorlevel 1 goto :error

echo.
echo [4/5] GitHub-Repository verbinden...
git remote get-url origin >nul 2>&1
if errorlevel 1 (
    git remote add origin "%REPO_URL%"
) else (
    git remote set-url origin "%REPO_URL%"
)
if errorlevel 1 goto :error

echo.
echo [5/5] Upload starten...
git push -u origin main
if errorlevel 1 (
    echo.
    echo Upload fehlgeschlagen.
    echo Es wurde kein Force-Push ausgefuehrt.
    echo Pruefe die Git-Fehlermeldung oben.
    pause
    exit /b 1
)

echo.
echo ============================================
echo Upload erfolgreich!
echo ============================================
echo %REPO_URL%
pause
exit /b 0

:cancel
echo.
echo Abgebrochen oder erforderliche Eingabe fehlt.
pause
exit /b 1

:error
echo.
echo FEHLER: Ein Git-Befehl ist fehlgeschlagen.
echo Pruefe die Meldung weiter oben.
pause
exit /b 1