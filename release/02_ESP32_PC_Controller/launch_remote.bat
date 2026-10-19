@echo off
chcp 65001 >nul
title Kindle BLE Page Turner Remote
cd /d "%~dp0"

set PYTHON_CMD=python
set IDF_PYTHON=C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe

if exist "%IDF_PYTHON%" (
    "%IDF_PYTHON%" kindle_remote_gui.py
) else (
    %PYTHON_CMD% kindle_remote_gui.py
)

if %ERRORLEVEL% neq 0 (
    echo.
    echo 程序异常退出，错误码: %ERRORLEVEL%
    pause
)
