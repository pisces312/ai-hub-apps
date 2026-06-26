
@echo off
REM ==========================================
REM Build script for SM8850 (Snapdragon 8 Elite)
REM Only includes QNN HTP v81 libraries
REM ==========================================
setlocal

echo ==========================================
echo Building chatapp_android for SM8850...
echo ==========================================

REM Check if QAIRT_PATH is set
if "%QAIRT_PATH%"=="" (
    echo [ERROR] QAIRT_PATH environment variable not set!
    echo Please set QAIRT_PATH to your QAIRT SDK directory, e.g.
    echo   set QAIRT_PATH=D:\dev\qairt\2.45.41.260507
    exit /b 1
)

echo [INFO] QAIRT_PATH = %QAIRT_PATH%

REM Find Gradle executable
set GRADLE_CMD=
if exist "%~dp0gradle\wrapper\gradlew.bat" (
    set GRADLE_CMD=%~dp0gradle\wrapper\gradlew.bat
) else (
    where gradle >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] No Gradle found!
        exit /b 1
    )
    set GRADLE_CMD=gradle
)

REM Build with v81 only
echo.
echo [INFO] Starting build...
echo ==========================================
%GRADLE_CMD% clean assembleDebug -PhtpVersion=81
set BUILD_EXIT=%ERRORLEVEL%

echo ==========================================
if %BUILD_EXIT% equ 0 (
    echo [SUCCESS] Build completed!
    echo.
    echo APK location:
    echo   %~dp0build\outputs\apk\debug\app-debug.apk
) else (
    echo [ERROR] Build failed with exit code %BUILD_EXIT%
)

echo.
echo Done.
exit /b %BUILD_EXIT%
