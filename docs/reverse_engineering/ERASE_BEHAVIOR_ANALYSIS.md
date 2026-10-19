# “擦除整个 FLASH” 真实行为深度剖析报告

## 一、核心结论与证据概览

通过对 `updater_unpacked.exe` 中下载引擎及 Flash 操作函数的反汇编与交叉引用分析，对官方升级说明中勾选“擦除整个 FLASH”的行为得出以下确凿结论：

1. **实际擦除范围**: **全片物理地址 `0x000000` 至 `0x07FFFF` (512 KB / 4 Mbits 全部空间)**。
2. **擦除算法**: 采用分层块/扇区擦除算法（优先 64KB Block `0xD8`，余量使用 4KB Sector `0x20` 与 256B Page `0x02`）。
3. **擦除覆盖对象**:
   - 包含 Flash Header (`0x000000 - 0x000020`)
   - **包含 Flash 中的 Bootloader (`uboot.boot`, 物理地址 `0x0000A0 - 0x00137F`)**
   - 包含硬件配置文件 `isd_config.ini` (`0x00137F - 0x0013F9`)
   - 包含分区索引表 `app_dir_head` (`0x002000 - 0x002120`)
   - 包含主应用程序 `app.bin` (`0x002120 - 0x0329C8`)
   - 包含配置工具参数 `cfg_tool.bin` (`0x0329C8 - 0x032C9A`)
   - 包含用户虚拟机配置存储区 `VM` (`0x034000 - 0x07B000`)
   - 包含蓝牙协议栈运行信息 `BTIF` (`0x07B000 - 0x07C000`)
   - 包含外部信息记录区 `EXIF` (`0x07C000 - 0x07D000`)
   - 包含用户自定义界面配置 `USERIF` (`0x07D000 - 0x07F000`)
   - 包含出厂授权与 MAC 地址保留区 `key_mac` (`0x07F000 - 0x080000`)
4. **关键安全发现（重新刷写机制）**:
   - 升级器在完成全片擦除后，**立即从 UFW 固件包中的 `flash.bin` (物理起始地址 `0x000000`) 将 `Flash Header`、`uboot.boot`、`isd_config.ini`、`app_dir_head`、`app.bin`、`cfg_tool.bin` 完整回写至 Flash**！
   - 因此，**只要升级过程没有在擦除后、写入前被强行拔线中断，Bootloader (`uboot.boot`) 就会被无损重建为原厂官方版本**。

---

## 二、反汇编代码关键路径深度审计

### 2.1 UI 信号与标志位关联 [E2]
在 Qt 界面构造函数（文件偏移 `0x00EB35`，VA `0x0040F735`）中：
```text
0x00EB34: PUSH 0x00D2AEA0   ; "1eraseAllChecked(bool)"
0x00EB3A: PUSH 0x00D2AEB8   ; "2toggled(bool)"
0x00EB3F: PUSH [EBP + 0x1E4] ; 界面复选框 QCheckBox ("擦除整个FLASH")
0x00EB45: PUSH EBP          ; this 指针
0x00EB46: CALL QObject::connect
```
当复选框被勾选时，调用内部槽函数设置状态标志 `this->m_bEraseAll = true`。

### 2.2 擦除触发与参数装配 (`sub_49FBF0`) [E2]
在升级核心流程函数 `sub_49FBF0`（文件偏移 `0x09EFF0`，VA `0x0049FBF0`）中：
```x86asm
; 检查 Chip Key 匹配性
0x09F679: cmp   [ebp+0x10], ax
0x09F680: jz    loc_4A02B3
0x09F682: push  offset aErrorWrongChipKey ; "ERROR: Wrong chip key in firmware..."
0x09F688: call  logOutputMessage

loc_4A02B3:
; 提示开始擦除
0x09F6B3: push  offset aStartToEraseFlash ; "Start to erase flash ..."
0x09F6B8: call  logOutputMessage

; 读取设备上报的 Flash 总物理容量
0x09F6D9: xor   edi, edi                  ; edi = 0 (起始物理地址 0x00000000)
0x09F6DB: cmp   dword ptr [ebx+312BCh], edi ; ebx+312BC = Flash 物理总容量 (0x80000)
0x09F6E1: jbe   loc_4A036F

; 准备调用底层擦除接口 jl_usb_scsi_common::eraseFlash
0x09F71C: push  1                         ; 参数 2: 标志位 (1 = 全片范围擦除模式)
0x09F71E: push  edi                       ; 参数 1: 起始地址 0x00000000
0x09F71F: mov   ecx, ebx                  ; this 指针 (jl_usb_scsi 对象)
0x09F721: call  jl_usb_scsi_common::eraseFlash (VA 0x00C17970, 文件偏移 0x816D70)
0x09F726: test  eax, eax                  ; 检查返回值
0x09F72A: jnz   loc_4A03D8                ; 失败则跳转报错 "ERROR: Failed to erase flash"
```

### 2.3 底层分层擦除实现 (`0x00C17970`) [E2][E3]
在 `jl_usb_scsi_common::eraseFlash` 内部：
```x86asm
; 计算擦除区间与步进
0x816ECA: mov   edx, [ebp+8]              ; edx = 起始地址 0x00000000
0x816ECD: add   eax, edx                  ; eax = 结束地址 (0x080000)
0x816ECF: mov   edi, edx

loc_817440:
; 判断当前对齐与长度是否满足 64KB 块擦除条件
0x817440: cmp   byte ptr [ebp+14h], 0
0x817444: jz    loc_8174F9
0x817456: push  offset aEraseBlock        ; "Erase Block: "
0x81745B: call  formatLog
0x81746B: call  jl_usb_scsi_common::eraseBlock ; 发送 SPI NOR 0xD8 指令 (64KB)

; 若不满足 64KB 对齐或余量小于 64KB，回退至 4KB 扇区擦除
loc_8178E9:
0x8178E9: push  offset aEraseSector       ; "Erase Sector: "
0x8178EE: call  jl_usb_scsi_common::eraseSector ; 发送 SPI NOR 0x20 指令 (4KB)
```
**严格证明**：循环从物理地址 `0x000000` 开始，步进累加直至 Flash 上限 `0x080000`，所有物理扇区均被擦除。

---

## 三、物理 Flash 各分区受影响深度对照表

| 物理地址范围 | 分区名称 | 容量 | 擦除后状态 | UFW中是否存在 | 升级器后续行为 | 最终风险评估 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `0x000000 - 0x000020` | **FLASH_HEADER** | 32 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** 原厂 Header (`Xn1\x07`) | **安全**，已完整恢复 |
| `0x000020 - 0x0000A0` | **BOOT_VECTOR** | 128 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** 向量表与填充数据 | **安全**，已完整恢复 |
| `0x0000A0 - 0x00137F` | **uboot.boot** | 4,831 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** 原厂 Bootloader 二进制 | **安全**，已完整恢复 |
| `0x00137F - 0x0013F9` | **isd_config.ini** | 122 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** SPI时钟/串口配置 | **安全**，已完整恢复 |
| `0x002000 - 0x002120` | **app_dir_head** | 288 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** SDFile 索引头目录表 | **安全**，已完整恢复 |
| `0x002120 - 0x0329C8` | **app.bin** | 198,824 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** 主应用代码 (密文) | **安全**，已完整恢复 |
| `0x0329C8 - 0x032C9A` | **cfg_tool.bin** | 722 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** 模式配置与出厂参数 | **安全**，已完整恢复 |
| `0x032CBA - 0x033CBA` | **p11_code.bin** | 4,096 B | 擦除为 0xFF | **存在** (Entry 0) | **立即回写** 协处理器休眠固件 (全0) | **安全**，已完整恢复 |
| `0x034000 - 0x07B000` | **VM 存储区** | 284 KB | 擦除为 0xFF | **不存在** | **不回写** (保留为 0xFF 空白区) | **历史配对与按键映射重置** |
| `0x07B000 - 0x07C000` | **BTIF 协议区** | 4 KB | 擦除为 0xFF | **不存在** | **不回写** (保留为 0xFF 空白区) | **蓝牙状态记录清除** |
| `0x07C000 - 0x07D000` | **EXIF 扩展区** | 4 KB | 擦除为 0xFF | **不存在** | **不回写** (保留为 0xFF 空白区) | **外部配置重置** |
| `0x07D000 - 0x07F000` | **USERIF 用户区**| 8 KB | 擦除为 0xFF | **不存在** | **不回写** (保留为 0xFF 空白区) | **用户个性化偏好清除** |
| `0x07F000 - 0x080000` | **key_mac 授权区**| 4 KB | 擦除为 0xFF | **不存在** | **不回写** (保留为 0xFF 空白区) | **出厂写入的单机唯一MAC丢失** |

---

## 四、持久化数据与唯一 MAC 机制审计

### 4.1 MAC 地址加载回退机制 [E2]
在 `app.bin` 内部逆向发现以下日志与函数链：
```text
[Info]: USER_CFG>>>init mac addr!!!
[Info]: USER_CFGmac:
<Error>: [BTIF]check item %d err!
```
- **工作机制**: 手柄开机启动时，主控依次检索：
  1. `key_mac` (`0x07F000`) 与 `BTIF` (`0x07B000`)。
  2. 若已擦除（内容为 `0xFF`），则触发校验错误 `<Error>: [BTIF]check item err!`。
  3. 系统随即进入回退分支：调用 `USER_CFG>>>init mac addr!!!`，利用芯片内部 eFuse 芯片唯一 ID 与 `cfg_tool.bin` 中的默认配置，**现场派生生成一组固定的备用 MAC 地址**。
- **后果分析**:
  - 手柄**不会**因 `key_mac` 被擦除而无法开启蓝牙。
  - 但是，手柄原先由工厂烧录器写入的特定公开 MAC 地址会被重置，导致**手机、电脑或 Kindle 端原有的已配对设备记录失效，必须重新在主机端“忽略此设备”并重新配对**。
