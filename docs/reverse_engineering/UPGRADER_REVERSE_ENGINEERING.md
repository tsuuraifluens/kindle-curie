# IINE L1161/L1162 固件升级器深度逆向工程报告

## 一、概述与分析基线

- **分析目标**: 良值（IINE）L1161 / L1162 迷你控制器官方升级程序 (`L1161 固件升级程序_1.4.40.exe`)
- **脱壳产物**: `extracted/updater_unpacked.exe` (14,338,896 字节, SHA-256: `D90811A474F15C9A7714D53D800E2E78C30786682962B99A6F71865AA006F6EB`)
- **主控平台**: 杰理（JieLi）AC632N / BD19 系列低功耗蓝牙 SoC (CPU: 32位自主研发 `q32s` RISC 架构)
- **Flash 容量**: 512 KB (4 Megabits, 物理地址空间 `0x000000 - 0x080000`)
- **证据分级标准**:
  - **[E1]**: 静态字符串与启发式线索 (Strings / Heuristics)
  - **[E2]**: 反汇编代码逻辑与交叉引用 (Disassembly / Xrefs)
  - **[E3]**: 多工具与二进制跨空间严格交叉验证 (Multi-tool Cross-validation)
  - **[E4]**: 主机侧通信日志与协议抓包 (Protocol Trace)
  - **[E5]**: 真实硬件测试与实验验证 (Physical Verification)

---

## 二、升级器技术栈与逆向架构

### 2.1 基础属性与编译器特征 [E2]
- **文件格式**: PE32 executable (GUI) Intel 80386
- **加载基址 (ImageBase)**: `0x00400000`
- **主要分段**:
  - `.text`: 虚拟地址 `0x00401000 - 0x00D28441` (代码段，文件偏移 `0x000400 - 0x00927A00`)
  - `.rdata`: 虚拟地址 `0x00D29000 - 0x0113A932` (只读常量与 RTTI，文件偏移 `0x00927A00 - 0x00D39400`)
  - `.data`: 虚拟地址 `0x0113B000 - 0x01166048` (读写全局变量，文件偏移 `0x00D39400 - 0x00D55E00`)
  - `.rsrc`: 虚拟地址 `0x0116A000 - 0x0116A638` (图标与资源)
- **GUI 框架**: Qt 5.x C++ Library (包含静态链接的 `QObject`, `QWidget`, `QMetaObject`, `QString`)
- **核心引擎**: 杰理专有刷写引擎 (`jl_format_flash_fs`, `jl_fw::package`, `jl_usb_scsi_common`)

### 2.2 命令行与 GUI 双模式接口 [E2]
升级程序同时支持 CLI 参数调用与 GUI 窗口操作：
- **CLI 语法格式 (文件偏移 `0x0092A060`, VA `0x00D2B660`)**:
  ```text
  Usage: %1 -d <device path> -f <firmware file> [-r] [-e|-a]
         %1 --help
    -d, --device    set target device drive letter label, eg: --device F
    -f, --firmware  set firmware file
    -e, --erase     Erase the entire flash
    -a, --fast      Fast download mode, donot erase flash first
    -r, --reset     reboot device if upgrade is successful
    -h, --help      show this help
  ```
- **GUI 交互控件与信号槽映射 (文件偏移 `0x00EB35 - 0x00EC60`, VA `0x0040F735`)**:
  - `eraseAllChecked(bool)` -> 绑定界面复选框 `擦除整个FLASH`
  - `resetChecked(bool)` -> 绑定界面复选框 `成功后复位`
  - `dualUbootChecked(bool)` -> 绑定高级选项 `双UBOOT`
  - `dualBackupBurningChecked(bool)` -> 绑定高级选项 `双备份烧录`
  - `keyBlankChecked(bool)` -> 绑定高级选项 `空片烧录`
  - `updateClicked()` -> 绑定按钮 `升级` (Update)

---

## 三、主机与手柄通信协议逆向

### 3.1 物理层与设备枚举机制 [E2][E3]
- 手柄在正常开机并插入 USB 时，按住 `配对键 + B键` 持续 2 秒，手柄指示灯熄灭，进入 USB 固件下载模式。
- 设备在 USB 总线上枚举为 **USB Mass Storage / SCSI 设备**（而非标准 HID 控制器）。
- 升级程序调用 Windows SCSI Passthrough API 直接与芯片硬件会话：
  - **IOCTL 控制码**: `0x0004D014` (`IOCTL_SCSI_PASS_THROUGH_DIRECT`)
  - **调用位置**: 文件偏移 `0x8141AC`、`0x81476C`、`0x81588E`
  - **实现类**: `jl_usb_scsi_windows::ioctl` 与 `jl_usb_scsi_windows::read` / `write`

### 3.2 刷写引擎类层次结构 [E2]
升级程序通过面向对象的通信抽象层控制芯片：
```text
jl_usb_scsi_windows (Windows 底层 DeviceIoControl 驱动封装)
   └── jl_usb_scsi_common (杰理跨芯片协议通用基类)
          ├── jl_usb_scsi_br20 (BR20/BR23/AC632N 派生类)
          ├── jl_usb_scsi_br22
          ├── jl_usb_scsi_dv15
          └── jl_usb_scsi_wl80
```

`jl_usb_scsi_common` 核心虚方法表 (`0x00C17970` 等):
1. `getOnlineDevice()`: 查询在线设备状态与芯片系列
2. `readChipKey()`: 读取芯片硬件加密密钥 (当前设备 Key: `0x190D`)
3. `maskromVersion()`: 查询内部 Mask ROM 版本
4. `writeRam()`: 向芯片内部 SRAM 写入执行代码
5. `cpuRunAddr()` / `runLoader()`: 跳转至 SRAM 执行 Loader
6. `readFlash()` / `writeFlash()`: Flash 读写交互
7. `eraseBlock()` / `eraseSector()` / `erasePage()` / `eraseFlash()`: Flash 分层擦除控制
8. `chipReset()`: 发送硬件复位指令

---

## 四、内嵌 SRAM Loader 深度逆向

在升级过程中，PC 端工具并不直接通过固定硬件逻辑操作 Flash，而是先向芯片 SRAM 注入一段专用驱动运行程序——**Loader**。

### 4.1 Loader 提取与特征分析 [E2][E3]
- **存放位置**: `updater_unpacked.exe` 文件偏移 `0x00930000 - 0x00970000` (Qt 资源 `:/loader/loader.bin`)
- **构建标识**:
  - `loader-$-@20230630-$37387d7` (文件偏移 `0x0096F19C`)
  - `drivers-$-@20230630-$759eaf8` (文件偏移 `0x0096F1BC`)
- **指令集**: 杰理自主研发 `q32s` 32位 RISC 指令集 (`bf ea` 密集跳转)
- **运行环境**: 芯片内部高速 SRAM (起始运行地址通常为 `0x00000000` 或 `0x00001000`)

### 4.2 Loader 支持的专有协议指令 [E2]
Loader 加载运行后，通过 USB 接收并响应以下命令码：
- `GET_ONLINE_DEVICE`: 握手并报告当前 Loader 处于就绪状态
- `READ_ID`: 读取 SPI NOR Flash 的 JEDEC ID (厂商、器件型号、容量)
- `READ_STATUS`: 读取 SPI Flash 状态寄存器 (WIP 等待、WEL 写使能)
- `SET_FLASH_CMD`: 配置 SPI NOR Flash 控制器工作模式 (时钟分频、1-2-2 Fast Read、Quad IO)
- `4KB eraser_cmd %x`: 执行 4KB 扇区擦除 (`0x20`)
- `norflash_erase_otp`: 针对 OTP / 安全保护区的擦除接口
- `RUN_APP`: 固件刷写完成后引导应用程序启动

---

## 五、固件升级全流程时序图

```mermaid
sequenceDiagram
    autonumber
    participant Host as PC 升级程序 (Qt / jl_fw)
    participant ROM as 芯片 Mask ROM (0x02100000)
    participant SRAM as 芯片 SRAM (运行 Loader)
    participant Flash as 板载 SPI NOR Flash (512KB)

    Host->>ROM: 1. 扫描 USB SCSI 设备 (IOCTL_SCSI_PASS_THROUGH_DIRECT)
    ROM--Host: 2. 握手返回: ONLINE, ChipKey=0x190D
    Host->>ROM: 3. writeRam: 传输 loader.bin 至 SRAM
    Host->>ROM: 4. cpuRunAddr: 跳转至 Loader 入口执行
    Note over SRAM: 5. Loader 初始化 SPI Flash 控制器<br/>执行 SET_FLASH_CMD
    SRAM--Host: 6. 报告 Flash ID 与容量 (0x80000 = 512KB)

    alt 勾选“擦除整个 FLASH” (eraseAllChecked = true)
        Host->>SRAM: 7. eraseFlash(0x000000, 0x080000)
        Note over SRAM,Flash: 8. 分层擦除: 64KB Block -> 4KB Sector<br/>整片 Flash 物理擦除为 0xFF
    end

    Host->>SRAM: 9. writeFlash: 刷写 flash.bin (0x000000 - 0x034000)
    Note over Flash: 包含 Flash Header, uboot.boot, isd_config,<br/>app_dir_head, app.bin, cfg_tool.bin
    Host->>SRAM: 10. getFlashCrc16: 校验各分区数据完整性
    
    alt 勾选“成功后复位” (resetChecked = true)
        Host->>SRAM: 11. chipReset() 硬件复位命令
        Note over Flash,ROM: 12. 芯片冷重启，从 Flash 0x0000A0 引导 uboot.boot
    end
```
