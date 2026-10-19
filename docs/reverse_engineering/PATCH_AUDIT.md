# 候选固件逐项二进制补丁深度审计报告 (Patch Audit Dossier)

- **基准原始固件**: [`release/03_IINE_Gamepad_Mod/flasher/stock_backup/L1161 L1162.ufw`](../../release/03_IINE_Gamepad_Mod/flasher/stock_backup/L1161 L1162.ufw)
  - **文件大小**: 645,184 字节 (0x9D840)
  - **SHA-256**: `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`
- **候选修改固件**: `candidate/IINE_Kindle_Mod_Candidate_v0.1.ufw` (早期构建原型)
  - **文件大小**: 645,184 字节 (0x9D840)
  - **SHA-256**: `310EE500F13C495F32795071EB9D3DEA628E33BA072DE03945316D1A0EF89F88`
- **审计工具**: [`scripts/audit_candidate_patch.py`](../../scripts/audit_candidate_patch.py)
- **审计结论**: **补丁范围高度收敛（仅 4 个连续变动块，累计 28 字节改动），无结构越界，CRC16 完整性链 100% 闭环。**

---

## 一、补丁清单与地址空间映射总表

| 序号 | 目标特性 | `app.bin` 相对偏移 | Flash 物理偏移 | UFW 文件偏移 | CPU 虚拟地址 | 改动长度 | 原始十六进制 | 目标十六进制 | 语义变更 |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---|:---|:---|
| **Patch 1** | 蓝牙广播名称 (Reader) | `0x0211C6` | `0x0232E6` | `0x0236E6` | `0x01E212E6` | 11 B | `49 49 4e 45 5f 72 65 61 64 65 72` | `4b 69 6e 64 6c 65 2d 50 54 00 00` | `"IINE_reader"` -> `"Kindle-PT\0\0"` |
| **Patch 2** | DIS 厂商名称 (0x2A29) | `0x022384` | `0x0244A4` | `0x0248A4` | `0x01E224A4` | 12 B | `7a 68 75 68 61 69 5f 6a 69 65 6c 69` | `41 6d 61 7a 6f 6e 00 00 00 00 00 00` | `"zhuhai_jieli"` -> `"Amazon\0\0\0\0\0\0"` |
| **Patch 3** | DIS PnP ID (VID/PID) | `0x0223C0` | `0x0244E0` | `0x0248E0` | `0x01E224E0` | 4 B | `3b 05 07 10` | `71 01 43 04` | VID: `0x053B` -> `0x0171`<br>PID: `0x1007` -> `0x0443` |
| **Patch 4** | DIS PnP ID 版本号 | `0x0223C5` | `0x0244E5` | `0x0248E5` | `0x01E224E5` | 1 B | `00` | `01` | Version `0x0001` -> `0x0101` (v1.0.1) |

---

## 二、逐项补丁语义与数据流交叉审计

### 2.1 Patch 1: 阅读器模式蓝牙广播名称 (`0x0211C6`)
* **上下文环境**: 位于只读数据段模式字符串数组中。
* **读取函数**: `0x01E0712E` 处的指令：
  ```arm
  0x712E: FFC6 12E6 01E2    mov r6, 0x01E212E6 ; 加载 "IINE_reader" 字符串地址
  0x7134: 0860              mov r0, r6
  0x7136: F397 FE51         call ble_set_adv_local_name
  ```
* **长度与截断分析**:
  - 原字符串长度为 11 字节（`"IINE_reader"`），后接 `0x00`。
  - 目标字符串 `"Kindle-PT"` 为 9 字节，填充两个 `0x00` 终结符。
  - **安全性**: 严密保持了 11 字节的原始存储边界，未破坏相邻字符串 `0x0211D2` (`"Xbox Wireless Controller"`)。
* **副作用评估**: 仅在手柄进入 `IINE_reader` 模式时作为广播名生效，对 Switch、X-Input、键盘模式无干扰。

### 2.2 Patch 2: DIS 厂商名称特征 (`0x022384`)
* **上下文环境**: 位于 GATT DIS 服务静态属性缓冲区。
* **读取函数**: 响应 GATT Client 对 `Handle 0x0012` (`UUID 0x2A29`) 的 Read Request 时直接 memcpy。
* **安全性**:
  - 原字符串 `"zhuhai_jieli"` 长度 12 字节。
  - 替换为 `"Amazon"`（6 字节）后填充 6 个 `0x00` 字节。
  - 零内存越界风险。

### 2.3 Patch 3 & 4: DIS PnP ID 数据结构 (`0x0223C0` - `0x0223C5`)
* **上下文环境**: 位于 `0x0223BC` 的 7 字节标准 Bluetooth SIG PnP ID 结构体：
  ```
  Offset:  0x0223BC 0x0223BD 0x0223BE 0x0223BF 0x0223C0 0x0223C1 0x0223C2
  Raw Hex: 01       3B       05       07       10       01       00
  Field:   Src(01)  VID_L    VID_H    PID_L    PID_H    Ver_L    Ver_H
  Values:  SIG(1)   VID = 0x053B      PID = 0x1007      Version = 0x0001
  ```
* **读取函数**: 位于 `0x01E045B4`（偏移 `0x004494`）：
  ```arm
  0x4494: F380 A909    call puts("[BLE_HOGP]read PnP_ID\n")
  0x44B0: mov r1, =pnp_id_data (Flash 0x244DC / CPU 0x01E224DC)
  0x44B8: call att_server_response_pnp_id
  ```
* **补丁修改**:
  - `VID`: `3B 05` -> `71 01` (`0x0171` = Amazon.com / Lab126)
  - `PID`: `07 10` -> `43 04` (`0x0443` = Amazon Kindle Remote / Kindle Click)
  - `Version`: `01 00` -> `01 01` (标明为 v1.0.1 衍生固件)
* **突破效果**: 满足 Kindle `/etc/input_manager/devices.json` 白名单准入条件。

---

## 三、校验和级联重新计算审计 (Integrity Chain Audit)

补丁修改后，自动化脚本自底向上正确重置了 5 级校验节点：

```
[Level 1] app.bin 内核校验
  └─ 数据校验: jl_crc16(app.bin[0x000000 : 0x0308A8]) = 0x3E27 (原 0x38EE)
  └─ JLFS 头部: 填入 0x2020 处，更新条目自身 CRC 为 0x954E

[Level 2] SFC 流密码加密 (jl_sfc_cipher)
  └─ 使用 ChipKey 0x190D 对 0x002000 - 0x034000 进行 32 字节块混淆

[Level 3] Flash.bin 镜像条目校验
  └─ jl_crc16(flash.bin[0x000000 : 0x034000]) = 0x7900 (原 0x41F8)
  └─ 更新 UFW Entry 0 校验码为 0x7900

[Level 4] UFW 分区条目列表校验
  └─ jl_crc16(UFW_header[0x40 : 0x2C0]) = 0x9696 (原 0x41C9)
  └─ 填入 UFW Header 0x02 偏移处

[Level 5] UFW 主容器头校验
  └─ jl_crc16(UFW_header[0x02 : 0x40]) = 0x9922 (原 0xB9C1)
  └─ 填入 UFW Header 0x00 偏移处
```

经官方解包工具 [`scripts/jieli_tools/fwunpack_newfw.py`](../../scripts/jieli_tools/fwunpack_newfw.py) 复测，全部条目提取正常，**0 警告、0 错误**。

---

## 四、剩余技术瓶颈与进阶补丁建议 (Advanced Patch Gap)

**重要技术审计结论**:
当前 `Candidate v0.1` 成功解决了：
1. **设备白名单拦截**（PnP ID 伪装为 Amazon `0x0171:0x0443`）。
2. **设备蓝牙可见性**（厂商名与设备名称伪装）。

**尚未解决的最后一道关卡**:
在手柄的 `IINE_reader` 模式下，方向键物理扫描后经由 `0xE7AC` 的 `remap_key.consumer_key` 依然向主机发送 **Usage Page `0x0C` (Consumer Control) 的音量键 (`0x00E9` / `0x00EA`)**。
Kindle 的输入过滤驱动 `capability_filter` 会静默丢弃音量键。

**进阶补丁路线 (Candidate v0.2 设计)**:
将 `0xE7AC` 处的消费控制报告分发调用指令替换为跳转到固件内已有的标准键盘报告分发函数（`0x01E204EC`），并填入 `0x4E` (`KEY_PAGEDOWN`) 与 `0x4B` (`KEY_PAGEUP`)，即可彻底打通 Kindle 原生翻页链路！
