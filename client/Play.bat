@echo off
setlocal EnableExtensions EnableDelayedExpansion

if /I "%~1"=="-BranchSelect" (
    set "MOONFLOWER_BRANCH_SELECTOR=%~dp0..\scripts\BranchSelector.ps1"
    set "MOONFLOWER_BRANCH_REPO=%~dp0.."
    if not exist "!MOONFLOWER_BRANCH_SELECTOR!" (
        set "MOONFLOWER_BRANCH_SELECTOR=%~dp0..\..\scripts\BranchSelector.ps1"
        set "MOONFLOWER_BRANCH_REPO=%~dp0..\.."
    )
    if not exist "!MOONFLOWER_BRANCH_SELECTOR!" (
        echo MoonFlower branch selection is available only from a source checkout.
        echo Could not find:
        echo   "%~dp0..\scripts\BranchSelector.ps1"
        echo or:
        echo   "%~dp0..\..\scripts\BranchSelector.ps1"
        pause
        exit /b 1
    )
    rem The selector is a developer tool and must run in a single-threaded
    rem Windows PowerShell session for its Windows Forms dialog.
    set "PSModulePath="
    powershell.exe -NoProfile -Sta -ExecutionPolicy Bypass -File "!MOONFLOWER_BRANCH_SELECTOR!" -RepoPath "!MOONFLOWER_BRANCH_REPO!"
    set "MOONFLOWER_EXIT_CODE=!ERRORLEVEL!"
    exit /b !MOONFLOWER_EXIT_CODE!
)

set "MOONFLOWER_UPDATER=%~dp0MoonFlower-Update.ps1"
if exist "%MOONFLOWER_UPDATER%" (
    rem Do not pass a PowerShell 7 module path into Windows PowerShell 5.1.
    set "PSModulePath="
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
