@echo off
setlocal EnableExtensions EnableDelayedExpansion

if exist "%~dp0MoonFlower-Update.ps1" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0MoonFlower-Update.ps1" -Steam %*
    exit /b !ERRORLEVEL!
)

pushd "%~dp0"
java -Dsun.java2d.uiScale.enabled=false -Dsun.java2d.win.uiScaleX=1.0 -Dsun.java2d.win.uiScaleY=1.0 -Xss8m -Xms1024m -Xmx4096m --add-exports java.base/java.lang=ALL-UNNAMED --add-exports java.desktop/sun.awt=ALL-UNNAMED --add-exports java.desktop/sun.java2d=ALL-UNNAMED -DrunningThroughSteam=true -jar hafen.jar
set "MOONFLOWER_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %MOONFLOWER_EXIT_CODE%
