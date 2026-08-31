@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "MOONFLOWER_UPDATER=%~dp0MoonFlower-Update.ps1"
if exist "%MOONFLOWER_UPDATER%" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%MOONFLOWER_UPDATER%" %*
    set "MOONFLOWER_EXIT_CODE=!ERRORLEVEL!"
    if not "!MOONFLOWER_EXIT_CODE!"=="0" (
        echo.
        echo MoonFlower exited with code !MOONFLOWER_EXIT_CODE!.
        pause
    )
    exit /b !MOONFLOWER_EXIT_CODE!
)

set "MOONFLOWER_LAUNCH_DIR=%~dp0"
if not exist "%MOONFLOWER_LAUNCH_DIR%hafen.jar" set "MOONFLOWER_LAUNCH_DIR=%~dp0bin\"

if not exist "%MOONFLOWER_LAUNCH_DIR%hafen.jar" (
    echo MoonFlower could not find the built client at:
    echo   "%~dp0hafen.jar"
    echo or:
    echo   "%~dp0bin\hafen.jar"
    echo.
    echo Close any running client, then build it with: ant clean deftgt
    pause
    exit /b 1
)

pushd "%MOONFLOWER_LAUNCH_DIR%"
java -Dsun.java2d.uiScale.enabled=false -Dsun.java2d.win.uiScaleX=1.0 -Dsun.java2d.win.uiScaleY=1.0 -Xss8m -Xms1024m -Xmx4096m --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -DrunningThroughSteam=false -jar hafen.jar
set "MOONFLOWER_EXIT_CODE=%ERRORLEVEL%"
popd

if not "%MOONFLOWER_EXIT_CODE%"=="0" (
    echo.
    echo MoonFlower exited with code %MOONFLOWER_EXIT_CODE%.
    pause
)

exit /b %MOONFLOWER_EXIT_CODE%
