# ESP32-S3 Kindle Page Turner Firmware v1.0.0 Flasher (PowerShell)
param(
    [string]$Port = "COM3",
    [int]$Baud = 460800
)

$ErrorActionPreference = "Stop"
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "     Kindle Paperwhite 2024 BLE HID Page Turner - ESP32-S3 Firmware v1.0.0     " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "目标串口: $Port @ $Baud baud"
Write-Host "提示: 仅写入程序与分区，不擦除 NVS 分区（保留 Kindle 配对绑定状态）。" -ForegroundColor Yellow

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$mergedBin = Join-Path $scriptDir "..\binaries\kindle_page_turner_v1.0.0_merged.bin"
$pyPath = "C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe"

if (Test-Path $pyPath) {
    & $pyPath -m esptool --chip esp32s3 -p $Port -b $Baud --before default_reset --after hard_reset write_flash --flash_mode dio --flash_freq 80m --flash_size 2MB 0x0 $mergedBin
} else {
    esptool.py --chip esp32s3 -p $Port -b $Baud --before default_reset --after hard_reset write_flash --flash_mode dio --flash_freq 80m --flash_size 2MB 0x0 $mergedBin
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] 固件 v1.0.0 烧录成功！设备已自动重启。" -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] 烧录失败，请检查串口连通性或占用情况。" -ForegroundColor Red
}
