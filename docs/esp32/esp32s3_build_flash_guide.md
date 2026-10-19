# ESP32-S3 BLE HID 固件构建、烧录与串口验证指南

**目标芯片**：ESP32-S3 (QFN56, Revision v0.2, 16MB Quad Flash, 8MB PSRAM)  
**当前串口**：`COM3` (USB-Enhanced-SERIAL CH343, VID `0x1A86`, PID `0x55D3`)  
**工具链路径**：`C:\Espressif\esp-idf-v5.3.2`  
**Python 环境**：`C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe`  
**出厂备份镜像**：[`esp32s3_firmware/backups/factory_backup_2mb.bin`](../../esp32s3_firmware/backups/factory_backup_2mb.bin) (2,097,152 字节, SHA-256: `C78D1EBBC14645B839C0130E15090FFF33AB3C6FAC327823056694CC750A85F1`)  

---

## 1. 环境准备与配置

在 Windows PowerShell 中加载 ESP-IDF v5.3.2 环境变量：

```powershell
$env:IDF_TOOLS_PATH = "C:\Espressif"
. "C:\Espressif\esp-idf-v5.3.2\export.ps1"
```

进入固件工程目录：

```powershell
cd esp32s3_firmware
```

---

## 2. 编译工程 (Build)

执行编译：

```powershell
idf.py build
```

编译生成的主要文件位于 `build/` 目录：
- `bootloader/bootloader.bin` (偏移地址 `0x0`)
- `partition_table/partition-table.bin` (偏移地址 `0x8000`)
- `kindle_page_turner_esp32s3.bin` (偏移地址 `0x10000`)

---

## 3. 烧录固件到开发板 (Flash)

> [!CAUTION]
> **保护机制警告**：
> 绝不执行 `idf.py erase_flash`！全片擦除会破坏可能存储的出厂校准数据。
> 仅覆盖应用程序、引导程序与分区表对应扇区。

执行烧录：

```powershell
idf.py -p COM3 flash
```

或使用独立的 `esptool.py` 写入对应偏移：

```powershell
& "C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe" -m esptool --chip esp32s3 -p COM3 -b 460800 --before default_reset --after hard_reset write_flash --flash_mode dio --flash_size 2MB --flash_freq 80m 0x0 build/bootloader/bootloader.bin 0x8000 build/partition_table/partition-table.bin 0x10000 build/kindle_page_turner_esp32s3.bin
```

---

## 4. 自动化回归测试与串口验证

固件内置了端到端自动化测试脚本，执行后自动连接 `COM3`，逐项测试所有 10 组交互功能：

```powershell
& "C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe" tests/verify_serial_and_ble.py
```

测试覆盖范围：
1. `help` 命令索引校验。
2. `selftest` 7/7 项板载单元测试。
3. `info` 逆向证据、PnP ID、EIR 广播字节比对。
4. `status` 遥测状态与对端读取计数器。
5. `prev` / `next` 未连接保护与状态机互锁。
6. `battery` 0..100 范围合法性与通知发送。
7. `ble_stop` 与 `ble_start` 启停控制。
8. `bond_status` 与 `bond_clear` 双重确认防护。
9. `logs` 事件环形缓冲区。
10. 溢出保护（输入 >128 字符不崩溃）与未知命令容错。

---

## 5. 交互式串口调试 (Monitor)

通过 ESP-IDF 监视器进入实时交互控制台：

```powershell
idf.py -p COM3 monitor
```

（按 `Ctrl+]` 退出监视器）

### 常用诊断命令速查

| 命令 | 功能说明 | 预期输出示例 |
|:---|:---|:---|
| `help` | 显示控制台命令帮助 | 打印完整命令列表 |
| `status` | 查询连接、遥测与 GATT 读取记录 | 显示连接句柄、PnP ID 读取次数、CCCD 状态 |
| `info` | 显示固件身份、逆向证据来源与广播 Hex | 表格形式展示 VID `0x0171`、PID `0x0443` 等 |
| `selftest` | 运行板载 7 项单元测试 | `Result: 7 / 7 tests passed (ALL PASSED)` |
| `prev` | 发送上一页 (`0x4B` PageUp) | 已连接时发送按下与释放，未连接时报告 REJECTED |
| `next` | 发送下一页 (`0x4E` PageDown) | 已连接时发送按下与释放，未连接时报告 REJECTED |
| `battery 85` | 动态修改电量为 85% | `CMD battery 85%: OK` |
| `name Kindle Remote` | 动态修改广播设备名称 | 重启广播并生效 |
| `adv_mode 0` | 切换为包含 0xFF 广播过滤模式 | 重启广播并携带 `[05 FF 71 01 04 43]` |
| `bond_status` | 查看 NVS 配对记录 | 显示已配对设备数量与 MAC |
| `bond_clear confirm` | 清除已存配对记录 | `CMD bond_clear confirm: CLEARED` |
| `logs` | 导出最近 64 条环形缓冲区日志 | 打印带毫秒时间戳的事件轨迹 |
| `reboot` | 安全重启 ESP32-S3 | 开发板即时软复位 |

---

## 6. 出厂固件回滚 (Rollback Recovery)

如果需要完全恢复开发板在本次任务开始前的原始固件状态（Stadia Dongle v1.0.4 镜像），可使用备份镜像一键回滚：

```powershell
& "C:\Espressif\python_env\idf5.3_py3.13_env\Scripts\python.exe" -m esptool --chip esp32s3 -p COM3 -b 460800 write_flash 0x0 backups/factory_backup_2mb.bin
```
