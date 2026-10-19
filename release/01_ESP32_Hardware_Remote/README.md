# 方案一：ESP32 独立硬件翻页器方案 (ESP32 Hardware Page Turner)

本方案基于 **ESP-IDF v5.3.2 + NimBLE** 架构，将微型 ESP32 开发板或定制板打造成与官方 Amazon Kindle Remote（Kindle Click）100% 协议兼容的实体蓝牙翻页遥控器。无需手机依赖，开机即连，支持极低功耗与按键防抖。

---

## 硬件选型对比

| 方案分支 | 主控芯片 | 适用场景 | 关键优势 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| **ESP32-S3 原型方案** (`esp32s3/`) | ESP32-S3 (Xtensa Dual-Core) | 创客开发板、原型验证、串口实时监控 | 双 USB 接口、原生 JTAG 调试、已完成 25/25 实机自动化验证 | **已实机验证通过** |
| **ESP32-C3 产品化方案** (`esp32c3/`) | ESP32-C3 (RISC-V 160MHz) | 定制 PCB、量产制造、超小体积成品 | 单芯片成本极低 (<20元BOM)、内置低功耗 LDO、高集成度 | **编译完成并生成固件** |

---

## 硬件引脚与接线说明 (Pinout)

### 1. ESP32-S3 默认按键接线
- **上一页 (PREV)**: `GPIO 4` (内部弱上拉，按键另一端接地 GND，按下为低电平)
- **下一页 (NEXT)**: `GPIO 5` (内部弱上拉，按键另一端接地 GND，按下为低电平)
- *(可选)* 串口连接：内置 USB-UART (115200 波特率) 支持交互式控制台

### 2. ESP32-C3 定制板接线
- **上一页按键 (PREV)**: `GPIO 2`
- **下一页按键 (NEXT)**: `GPIO 3`
- **触觉震动马达 (Haptic PWM)**: `GPIO 4`
- **电池电压 ADC (分压测量)**: `GPIO 0`
- **状态指示 LED**: `GPIO 8`

---

## 一键烧录步骤

### Windows 用户（双击批处理）：
1. 用 Type-C 数据线将 ESP32 板卡连接到电脑 USB 口。
2. 打开设备管理器查看 COM 端口（例如 `COM3`）。
3. 对应方案目录：
   - 烧录 ESP32-S3：进入 `esp32s3/`，双击运行 `flash_firmware.bat`。
   - 烧录 ESP32-C3：进入 `esp32c3/`，双击运行 `flash_firmware.bat`。
4. 按提示输入串口号，等待烧录完成（自动写入已合并的 `merged.bin`）。

### 跨平台 / 命令行用户：
```bash
# ESP32-S3
esptool.py --chip esp32s3 -p <PORT> -b 460800 write_flash 0x0 binaries/kindle_page_turner_v1.0.0_merged.bin

# ESP32-C3
esptool.py --chip esp32c3 -p <PORT> -b 460800 write_flash 0x0 binaries/kindle_remote_c3_merged.bin
```

---

## Kindle 蓝牙配对说明

1. 烧录完成后，ESP32 自动启动 BLE 广播（设备名 `"Kindle Remote"`，含亚马逊专用 Manufacturer Data `0x0171`）。
2. 在 Kindle 上进入：**设置 -> 蓝牙与无线 -> 蓝牙 -> 配对新设备**。
3. 在扫描列表中点击 `"Kindle Remote"` 完成配对。
4. 打开电子书，按下接有 GND 的按键，即可体验零延迟丝滑翻页！
