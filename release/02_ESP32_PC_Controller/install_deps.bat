@echo off
chcp 65001 >nul
title 安装 Kindle Remote 控制端运行环境
echo 正在安装 Python 依赖 (pyserial)...
python -m pip install -r "%~dp0requirements.txt"
if %ERRORLEVEL% equ 0 (
    echo.
    echo [OK] 依赖安装成功！现在可双击 launch_remote.bat 启动程序。
) else (
    echo.
    echo [ERROR] 依赖安装失败，请确保已安装 Python 3.8+ 并将 python.exe 加入系统环境变量 PATH。
)
pause
