# 生产烧录与固件部署标准操作规程 (Flashing Guide & SOP)

**文档编号**: ENG-MFG-SOP-001  
**适用对象**: 生产线烧录工位、产线质检工程师、返修工程技术员  
**目标硬件**: Kindle Remote (ESP32-C3-WROOM-02-N4 / 4MB Flash)  
**固件版本**: v1.1.0-prod  
**版本**: v1.0.0  
**日期**: 2026-10-14  

---

## 1. 烧录物理通道与工装连接

生产烧录支持两种物理连接模式：

### 模式 A: 自动化针床测试夹具 (Pogo-Pin ATE Bed - 推荐量产方案)
- **适用工位**: PCBA 贴片完成后的首道在线烧录与全功能自动化测试。
- **夹具连接引脚 (对应 PCB 底层金手指测试点)**:
  - `TP_VBUS` (Pin 2): 接入夹具稳压 5.0V 供电源。
  - `TP_GND` (Pin 1): 接入夹具系统地。
  - `TP_TX` (Pin 4): 接入烧录器 RXD (波特率 921600 / 460800)。
  - `TP_RX` (Pin 5): 接入烧录器 TXD。
  - `TP_IO0` (Pin 6): 接入夹具电平控制开关（烧录时自动拉低进 Bootloader）。
  - `TP_EN` (Pin 7): 接入夹具复位引脚（烧录前后复位脉冲）。

### 模式 B: 标准 USB Type-C 直连 (USB Serial/JTAG - 样品与售后维护)
- **适用工位**: 整机装配完成后的抽检、展会样机调试或售后固件升级。
- **免芯片直连原理**: ESP32-C3 内部集成了 USB Serial/JTAG 硬件单元，将 Type-C 数据线直接插入电脑 USB 端口，Windows / Linux 免驱自动识别为 `USB JTAG/serial debug unit` (CDC 串口)，无需额外任何转串口芯片或短接引脚。

---

## 2. 固件二进制包清单与内存映射表 (Flash Memory Layout)

| 镜像文件名 | 相对路径 | Flash 烧录起始偏移 | 扇区用途说明 |
|:---|:---|:---:|:---|
| **`bootloader.bin`** | `firmware/build/bootloader/bootloader.bin` | **`0x00000000`** | 2级引导程序，负责时钟、Cache与安全校验 |
| **`partition-table.bin`** | `firmware/build/partition_table/partition-table.bin` | **`0x00008000`** | 分区表 (单 App 分区 + NVS 分区) |
| **`kindle_remote_c3_firmware.bin`** | `firmware/build/kindle_remote_c3_firmware.bin` | **`0x00010000`** | 主固件应用镜像 (NimBLE + HOGP + 翻页状态机) |

---

## 3. 标准量产烧录命令行 (Esptool Production Command)

### 3.1 一键快速烧录命令 (单片耗时 $\le 4.5\text{ 秒}$)
在终端或自动化测试脚本中调用 `esptool.py` 执行烧录：

```powershell
python -m esptool --chip esp32c3 -p COMx -b 921600 `
  --before default_reset --after hard_reset write_flash `
  --flash_mode dio --flash_size 4MB --flash_freq 80m `
  0x0000 firmware/build/bootloader/bootloader.bin `
  0x8000 firmware/build/partition_table/partition-table.bin `
  0x10000 firmware/build/kindle_remote_c3_firmware.bin
```

> [!CAUTION]
> **生产安全红线规范**:
> 1. **严禁执行 `erase_flash`**: 全片擦除会抹除乐鑫出厂预置的 ADC 曲线校准参数与 RF 频偏校准数据。
> 2. 每次烧录必须包含完整的校验检查 (Hash of data verified)，若校验失败应立即剔除并报警提示。

---

## 4. 自动化批量烧录 Python 脚本 (`tools/flash_production.py`)

在 `tools/` 目录下预置了支持自动化扫码枪与自动复位重试的脚本：

```powershell
python tools/flash_production.py --port COMx --baud 921600
```
脚本执行流程：
1. 握手检测 ESP32-C3 芯片 MAC 与晶振频率；
2. 校验镜像 MD5/SHA256 完整性；
3. 执行高速多块烧录与校验；
4. 触发硬复位；
5. 监听串口输出，捕获 `[TEST] Self-test executed: 7/7 passed` 与 Banner 信息；
6. 打印绿色 `PASS` 并记录设备 MAC 至生产数据库 `production_records.csv`。
