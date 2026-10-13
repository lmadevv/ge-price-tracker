@echo off
title RuneLite Dev Launcher - GE Price Tracker
cd /d "%~dp0"
set "JAVA_HOME=%~dp0..\jdk17\jdk-17.0.20.1+1"
set "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
echo Starting RuneLite with GE Price Tracker...
call gradlew.bat run
pause
