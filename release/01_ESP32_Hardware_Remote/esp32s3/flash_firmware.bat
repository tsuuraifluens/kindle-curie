@echo off
chcp 65001 >nul
title ESP32-S3 Kindle Page Turner Firmware v1.0.0 Flasher
echo ===============================================================================
echo      Kindle Paperwhite 2024 BLE HID Page Turner - ESP32-S3 Firmware v1.0.0
echo ===============================================================================
echo.
set PORT=COM3
set /p USER_PORT="请输入 ESP32-S3 串口号 (默认: %PORT%): "
if not "%USER_PORT%"=="" set PORT=%USER_PORT%

echo.
echo 正在准备通过 %PORT% 烧录固件...
echo 注意: 脚本仅烧录 bootloader、分区表与主程序，不会擦除 NVS 分区（保留 Kindle 配对绑定密钥）。
echo.

set PYTHON_EXE=C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe

if exist "%PYTHON_EXE%" (
    echo 使用 ESP-IDF Python 环境...
    "%PYTHON_EXE%" -m esptool --chip esp32s3 -p %PORT% -b 460800 --before default_reset --after hard_reset write_flash --flash_mode dio --flash_freq 80m --flash_size 2MB 0x0 "%~dp0..\binaries\kindle_page_turner_v1.0.0_merged.bin"
) else (
    echo 未找到预设 ESP-IDF Python，尝试使用系统 PATH 中的 esptool...
    esptool.py --chip esp32s3 -p %PORT% -b 460800 --before default_reset --after hard_reset write_flash --flash_mode dio --flash_freq 80m --flash_size 2MB 0x0 "%~dp0..\binaries\kindle_page_turner_v1.0.0_merged.bin"
)

if %ERRORLEVEL% equ 0 (
    echo.
    echo ===============================================================================
    echo [OK] 固件 v1.0.0 烧录成功！设备已自动重启。
    echo ===============================================================================
) else (
    echo.
    echo ===============================================================================
    echo [ERROR] 烧录失败，请检查串口是否被其他程序占用，或设备是否进入下载模式。
    echo ===============================================================================
)
pause
