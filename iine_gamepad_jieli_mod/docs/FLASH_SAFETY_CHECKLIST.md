# 刷写安全与架构完整性检查表 (Flash Safety & Security Audit Checklist)

候选固件: `outputs/IINE_Kindle_Click_v25_SleepLockout.ufw`
日期: 2026-10-20
状态: **全自动化审计通过 (ALL CHECKS PASSED)**

---

## 1. 结论与评估
**候选固件已通过多层次硬件与内存安全防御审查，可安全进入人工刷写测试。**

核心安全保证:
1. **硬件零变砖保证 (Zero-Brick Guarantee)**: 启动区、Mask ROM 下载握手、uboot (`0x400..0x2400`)、isd_config、cfg_tool、p11、flash 擦写参数与设备校准完全未触碰（100% 逐字节原厂一致），芯片永远可以通过「配对键 + B」重新枚举 USB 下载协议刷回原厂。
2. **BLE 协议栈内存零污染保证 (RAM Load Image Immunity)**: 汲取初版 v25 断联事故教训，建立了严格的 RAM 数据加载段只读免疫审查门禁，确保开机 `memcpy` 不篡改 RAM 中的蓝牙协议栈与 RTOS 结构体。

---

## 2. 设备与固件指纹

- **目标硬件**: 良值（IINE）L1161 / L1162 口袋迷你蓝牙手柄
- **微控器 (MCU)**: 珠海杰理 AC6321A (AC632N / BD19 系列，32位 RISC架构)
- **升级模式 USB**: `VID_4C4A&PID_3442`
- **候选固件**: `outputs/IINE_Kindle_Click_v25_SleepLockout.ufw`
- **固件大小**: 645,184 字节（与原厂逐字节对齐）
- **候选 SHA-256**:
  `C17D1026179B96EB18E63A96F7F396A9BC22D64E4E91281971B31E3ABCAB9ACF`
- **已知安全回退版本**:
  - `IINE_Kindle_Click_v24_FastConnect.ufw` (`5E7448EF3AFB53671FF4E2A56743659FED9A916FF5F937E2FBE0F235690F2072`)
  - `IINE_Kindle_Click_v23_ABchord.ufw` (`F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879`)
  - `IINE_Kindle_Click_v22s_keymap.ufw` (`2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC`)
- **原厂基线 SHA-256**:
  `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`

---

## 3. 安全审计清单 (8大核心防御门禁)

### A. 容器与校验链完整性
- [x] **[R1-1] UFW 容器尺寸与格式合规**：长度 645,184 字节，80 字节加密文件头。
- [x] **[R1-2] UFW Header CRC-16 校验**：头 CRC 复算一致。
- [x] **[R1-3] UFW List CRC-16 校验**：分区条目表 CRC 复算一致。
- [x] **[R1-4] Entry 0 Flash 映像 CRC 校验**：208KB Flash 烧录映像 CRC 匹配条目 0 声明。
- [x] **[R2-1..R2-4] JLFS 四级完整性校验链**：`app.bin` 数据 CRC、条目头 CRC、区域数据 CRC、区域头 CRC 全数匹配。

### B. 硬件引导与变砖防御 (Zero-Brick Protection)
- [x] **[R3-1] Uboot 分区与引导程序保护**：`[0x400 : 0x2400]`（包含 `uboot.boot` 和 `isd_config.ini`）100% 逐字节原厂一致。
- [x] **[R3-2] Flash 尾部设备物理分区保护**：`[0x34400 : EOF]`（包含 `key_mac`、`VM`、`PRCT`、`BTIF`、`EXIF`、`USERIF`）100% 逐字节原厂一致。
- [x] **[R3-3..R3-4] JLFS 元数据头与条目结构**：`0x2004..0x2040` 保持原厂二进制一致。

### C. RAM 数据加载段与 BLE 协议栈免疫 (Anti-RAM-Corruption Gate) ★新增重大规则
- [x] **[R4-1] RAM `.data` Flash LMA (`0x2DEA8..0x308A8`) 100% 原厂一致**：
  - **事故教训**：在 AC6321A 中，`app.bin` 偏移 `0x2DEA8` 起是系统 RAM 的 Flash 加载映像。芯片每次开机在 `_start` 中通过 `memcpy(0x0840, 0x1E2DFC8, 3168)` 将其载入 RAM。
  - **严重后果**：向该区域（如 `0x2E100`）写入任何数据，将直接覆盖 RAM `0x0A98`，即 **BLE 协议栈的核心连接与 GATT 句柄**，导致“连接成功后几秒内异常断开”。
  - **审查准则**：`0x2DEA8` 起的所有字节必须严格只读且 100% 与原厂镜像相同，构建脚本与审计工具严密设防，一旦发现越界写入立即终止构建。
- [x] **[R5-1] 可执行代码严格局限于 `.text` 空间 (< `0x2DEA8`)**：
  - 所有蹦床代码、状态机代码、HID 描述符只允许放置在验证安全的 `.text` 空隙（如 `0x2260A` 旧描述符槽位，或 `0x23330` 对齐空隙）。
- [x] **[R6-1] 间接跳转与调用目标 VMA 审计**：
  - 严禁任何补丁代码跳转至 `[0x1E2DFC8..0x1E309B0)`（RAM 数据段的 Flash 地址）。所有跳转目标必须指向有效可执行的 `.text` 地址（`< 0x1E2DFC8`）。

### D. 无线通信与外设协议审查
- [x] **[R7-1] BLE 广播参数规范合规**：广播间隔设置为 32 slots（20ms），处于 BLE SIG 规范合法范围（20ms ~ 10240ms），既实现 1~3 秒极速重连，又不造成射频拥塞。
- [x] **[R8-1] 全 Flash 零预期外随机跳变**：解密后全片比对，修改严格收敛于 23 处白名单补丁内，零意外越界。

---

## 4. 自动化审计执行方式

在终端中运行独立全功能安全审计脚本：
```bash
node iine_gamepad_jieli_mod/tools/universal_safety_audit.js iine_gamepad_jieli_mod/analysis/firmware/"L1161 L1162.ufw" release/03_IINE_Gamepad_Mod/firmware/IINE_Kindle_Click_v25_SleepLockout.ufw
```
当且仅当所有门禁全部输出 `PASS` 且退出码为 `0` 时，固件方可提交发布。

---

## 5. 人工刷写与救砖指引

1. **进入刷机模式**:
   - 插入 Type-C 数据线连接 Windows PC。
   - 长按手柄「**配对键 + B**」约 2 秒，手柄指示灯熄灭，电脑发出 USB 枚举音，官方升级程序显示 **ONLINE**。
2. **安全刷入**:
   - 载入最新推荐固件 `IINE_Kindle_Click_v25_SleepLockout.ufw`。
   - 勾选「擦除整个 FLASH」与「成功后复位」，点击升级。
3. **100% 救砖保证**:
   - 无论发生任何意外（中途断电、电脑死机、固件错误），手柄的 **硬件 Mask ROM 引导机制物理只读、永不损毁**。
   - 重新按住「配对键 + B」连线，均能被官方工具识别并刷回 `flasher/stock_backup/L1161 L1162.ufw` 原厂固件完全复原。
