@echo off
REM ═══════════════════════════════════════════════════════════════
REM  Moonflower Plugin Suite — Build Script for Haven and Hearth
REM ═══════════════════════════════════════════════════════════════

setlocal

set PLUGIN_DIR=%~dp0
set HAVEN_DIR=C:\Program Files (x86)\Steam\steamapps\common\Haven
set HAVEN_JAR=%HAVEN_DIR%\launcher.jar
set SRC_DIR=%PLUGIN_DIR%src
set BUILD_DIR=%PLUGIN_DIR%build
set JAR_NAME=MoonflowerPlugin.jar
set OUTPUT_JAR=%PLUGIN_DIR%%JAR_NAME%
set GAME_DATA=%APPDATA%\Haven and Hearth
set GAME_PLUGINS=%GAME_DATA%\plugins

echo.
echo [Moonflower] ════════════════════════════════════════
echo [Moonflower]  Moonflower Plugin Suite — Build
echo [Moonflower]  Target: Haven and Hearth (Java 25)
echo [Moonflower] ════════════════════════════════════════
echo.

REM ── Check for Haven game JAR ──
if not exist "%HAVEN_JAR%" (
    echo [Moonflower] WARNING: launcher.jar not found at:
    echo [Moonflower]   %HAVEN_JAR%
    echo [Moonflower] Attempting fallback paths...
    
    REM Try custom path from environment variable
    if defined HAVEN_PATH (
        set HAVEN_JAR=%HAVEN_PATH%\launcher.jar
        echo [Moonflower] Trying: %HAVEN_JAR%
    )
)

if not exist "%HAVEN_JAR%" (
    echo [Moonflower] ERROR: Cannot find Haven launcher.jar!
    echo [Moonflower] Set HAVEN_PATH environment variable to your Haven installation.
    exit /b 1
)

echo [Moonflower] Using Haven JAR: %HAVEN_JAR%
echo.

echo [Moonflower] Cleaning build directory...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%"

echo [Moonflower] Compiling Java sources (Java 25)...
javac -source 25 -target 25 -cp "%HAVEN_JAR%" -d "%BUILD_DIR%" ^
    "%SRC_DIR%\haven\plugins\MoonflowerForager.java" ^
    "%SRC_DIR%\haven\plugins\MoonflowerMap.java" ^
    "%SRC_DIR%\haven\plugins\MoonflowerTileSync.java" ^
    "%SRC_DIR%\haven\plugins\MoonflowerTracker.java"
if errorlevel 1 (
    echo [Moonflower] ERROR: Compilation failed!
    exit /b 1
)

echo [Moonflower] Copying resources...
xcopy /e /i /y "%PLUGIN_DIR%META-INF" "%BUILD_DIR%\META-INF" >nul
if exist "%PLUGIN_DIR%res" xcopy /e /i /y "%PLUGIN_DIR%res" "%BUILD_DIR%\res" >nul

echo [Moonflower] Packaging JAR...
if exist "%OUTPUT_JAR%" del "%OUTPUT_JAR%"
cd /d "%BUILD_DIR%"
jar cf "%OUTPUT_JAR%" haven/ META-INF/
if exist res\ jar uf "%OUTPUT_JAR%" res/
cd /d "%PLUGIN_DIR%"
if errorlevel 1 (
    echo [Moonflower] ERROR: JAR packaging failed!
    exit /b 1
)

echo.
echo [Moonflower] ════════════════════════════════════════
echo [Moonflower]  Build successful!
echo [Moonflower]  JAR: %OUTPUT_JAR%
echo [Moonflower] ════════════════════════════════════════
echo.

echo [Moonflower] Deploying to game plugins folder...
if not exist "%GAME_PLUGINS%" mkdir "%GAME_PLUGINS%"
copy /y "%OUTPUT_JAR%" "%GAME_PLUGINS%\%JAR_NAME%" >nul
echo [Moonflower] Deployed to: %GAME_PLUGINS%\%JAR_NAME%
echo.
echo [Moonflower] Plugins included:
echo [Moonflower]   - MoonflowerForager   (Forage bot with config UI)
echo [Moonflower]   - MoonflowerMap       (HavenCartographer map viewer)
echo [Moonflower]   - MoonflowerTileSync  (Live tile upload to Cartographer)
echo [Moonflower]   - MoonflowerTracker   (Bot tracking and remote commands)
echo.
echo Restart Haven and Hearth to load the updated plugins.

endlocal
