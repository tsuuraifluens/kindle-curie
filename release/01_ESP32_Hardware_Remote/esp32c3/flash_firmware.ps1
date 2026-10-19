# ESP32-C3 Firmware Flashing Script (PowerShell)
param(
    [string]$Port = "COM3"
)

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "     Kindle Paperwhite 2024 BLE HID Page Turner - ESP32-C3 Production Firmware" -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

$userPort = Read-Host "请输入 ESP32-C3 串口号 (默认: $Port)"
if (-not [string]::IsNullOrWhiteSpace($userPort)) {
    $Port = $userPort
}

Write-Host "目标端口: $Port" -ForegroundColor Yellow
Write-Host "正在准备烧录固件..."

$mergedBin = Join-Path $PSScriptRoot "binaries\kindle_remote_c3_merged.bin"
$pythonExe = "C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe"

if (Test-Path $pythonExe) {
    & $pythonExe -m esptool --chip esp32c3 -p $Port -b 460800 --before default_reset --after hard_reset write_flash --flash_mode dio --flash_freq 80m --flash_size 4MB 0x0 $mergedBin
} else {
    esptool.py --chip esp32c3 -p $Port -b 460800 --before default_reset --after hard_reset write_flash --flash_mode dio --flash_freq 80m --flash_size 4MB 0x0 $mergedBin
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] ESP32-C3 固件烧录成功！设备已自动重启。" -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] 烧录失败，请检查端口与连线设置。" -ForegroundColor Red
}
