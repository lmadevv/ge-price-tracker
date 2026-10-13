@echo off
title Build Verification - GE Price Tracker
cd /d "%~dp0"
set "JAVA_HOME=%~dp0..\jdk17\jdk-17.0.20.1+1"
set "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
echo Running Gradle build and tests...
call gradlew.bat build
echo.
if %ERRORLEVEL% EQU 0 (
    echo ==============================================
    echo [SUCCESS] BUILD PASSED! Ready for Plugin Hub.
    echo ==============================================
) else (
    echo ==============================================
    echo [FAILED] Build failed. Check errors above.
    echo ==============================================
)
pause
