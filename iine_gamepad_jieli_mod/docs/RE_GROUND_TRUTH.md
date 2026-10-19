# 逆向基准（来自 kind-curie 文档 + 本项目独立核验）

来源: `.` 下的逆向报告与 evidence（只读参考）。
**未运行该目录的任何工具/脚本**；本文所有“已核验”项均由本项目自己的脚本与 pi32 工具链复核。

## 1. 芯片与固件
- SoC: 杰理 **AC632N / BD19**，q32s 32 位 RISC，73KB SRAM，512KB SPI NOR（物理映射上限 0x07F000）。
- 固件: `L1161 L1162.ufw` (645184 B)，杰理 UFW 容器。
- Flash 镜像: UFW 偏移 0x400 起，长 0x34000 (212,992 B)。
- 应用加密: **SFC 流密码 `jl_sfc_cipher`，ChipKey = `0x190D`**，32 字节块，seed = `key ^ (addr>>2)`，地址为 XIP VMA。

## 2. 已独立核验的地址/数据
| 项 | app.bin 偏移 | VMA | 结果 |
|---|---|---|---|
| app.bin 数据 | 0x120 | 0x1E00120 | 198,824 B，解密后为合法 q32s 代码 |
| GAP 名 `IINE_reader` | 0x211C6 | 0x1E212E6 | 解出 `IINE_reader\0` ✓ |
| GAP 名 `Xbox Wireless Controller` | 0x211D2 | 0x1E212F2 | 解出 ✓ |
| GAP 名 `IINE_keyboard` | 0x211EB | 0x1E2130B | 文档记载，未单独 dump |
| DIS 厂商名 | 0x22384 | 0x1E224A4 | `zhuhai_jieli\0` ✓ |
| DIS PnP ID | 0x223BC | 0x1E224DC | `11 01 00 03 3B 05 07 10` ✓ |
| HID Reader 描述符 | 0x2280C | 0x1E2292C | Consumer Control, Report ID 1 ✓ |
| Table1 (键盘默认映射) | 0x2289C | 0x1E229BC | 16×6B，A=`02 00 00 28 00 00`, B=`02 00 00 2C 00 00` ✓ |
| Table3 (Consumer 映射) | 0x22A1C | 0x1E22B3C | 首项 `04 00 CD 00 00 00` (Play/Pause) ✓ |

按键表条目格式: `[ReportID(1B), Modifiers(1B), Reserved(1B), KeyCode(1B), 0, 0]`。

## 3. 广播构造（已核验反汇编）
`adv_setup_init` 位于 VMA 0x1E04E90 附近（app.bin ~0x4D80）。
- `0x1E04E9A: r0 = 0x41`（杰理 Company ID 低字节）
- `0x1E04EA0: r0 = 0xFF`（AD Type）
- `0x1E04EB0: r6 = 9`（厂商数据长度硬编码）
- `0x1E04EEC: r9 = 0x08C4`（全局配置基址）
Kindle 需要厂商数据 `05 FF 71 01 04 43` 出现在 **ADV_IND** 中；原厂广播用的是杰理 ID 且长度 9。

## 4. UFW 容器结构（已核验）
- 0x00: u16 主头 CRC（0xB9C1）
- 0x02: u16 条目列表 CRC（0x41C9）
- 0x04: u32 文件总长（0x9D840）
- 0x08: u16 条目数=8, u16 版本=4
- 0x0C: u16 0x0200, u16 0
- 0x10: char chip[16] = "AC632N"
- 0x40 起: 8 × 0x50 条目（**每项单独用 KS 解扰**）

条目字段:
```
+0x00 u16 条目头 CRC = crc16(entry[2..32])
+0x02 u16 索引
+0x04 u32 数据 CRC16
+0x08 u32 偏移
+0x0C u32 大小
+0x10 u32 对齐大小
+0x20 32B 常量表
+0x40 16B 每项校验区
```

| # | 偏移 | 大小 | 内容 |
|---|---|---|---|
| 0 | 0x400 | 0x34000 | flash 镜像（含 uboot/isd_config/app/cfg/p11） |
| 1 | 0x34400 | 0 | 空 |
| 2 | 0x34400 | 0x2AF | 小文件 |
| 3 | 0x346C0 | 0x35F78 | OTA 镜像区（ble_ota/edr_ota2/ble_app_ota/uart_user/usb_hid_ota） |
| 4 | 0x6A640 | 0x1D | 小文件 |
| 5 | 0x6A660 | 0x33100 | 大块 |
| 6 | 0x9D760 | 0x90 | 小文件 |
| 7 | 0x9D800 | 0x40 | 尾部 |

## 5. 已核验的校验层
- **JLFS app.bin 条目** (VMA 0x1E00020): `+0x00 = crc16(entry[2..32])`；`+0x02 = crc16(app.bin 数据)`；
  原值: header CRC `0x7E39`，data CRC `0x38EE`。
- **UFW 条目 0** `+0x04 = crc16(212,992 B flash 镜像)`，原值 `0x41F8`。
- CRC 算法: CRC16-CCITT，poly `0x1021`，init `0x0000`，无反射。

## 6. 尚未确定
- UFW 主头 CRC（0xB9C1）与条目列表 CRC（0x41C9）的覆盖范围/算法（简单范围与常见变体均未匹配）。
- 条目 `+0x40` 16 字节校验区含义。
- 文档中“配对失败修复（IO Capability / check_pair_allow）”等结论未经本项目独立验证。

## 7. 对改造路线的意义
按键 GPIO **不再需要**：直接修改原厂固件的按键映射表即可沿用原厂扫描。
需要的补丁（候选，需逐项核验后实施）:
1. PnP ID: **运行时构造**，见 §8（0x223BC 不是 PnP ID）
2. DIS 厂商名: `0x22384` → `Amazon`
3. GAP 名: `0x211C6/0x211EB` → `Kindle-PT`
4. 广播: 注入 `05 FF 71 01 04 43`（需同步处理 `0x04D90: r6=9`）
5. Table1: A/B/D-pad → `0x4E/0x4B`，左右 → `0x70/0x6F`，X/Y → `0x66`
6. 配对: 视实机情况处理 IO Capability / pair 门禁
7. 重算 JLFS 两层 CRC、flash 镜像 CRC，再处理 UFW 主头/列表校验

## 8. 完整重打包流水线（已独立实现并逐字节验证）

`tools/build_kindle_click.js` 用 Node 独立实现了整条流水线，输出与
`IINE_Kindle_Click_Mod_v1.7.ufw` **逐字节相同**（SHA-256 一致），证明以下步骤正确:

1. 解密 App 区 `[0x2000:0x33CBA]`: `jl_sfc_cipher(key=0x190D, 32B blocks, seed=key^((i)>>2))`（i 为相对 0x2000 的偏移）。
2. 打补丁（app.bin 偏移 = flash 偏移 - 0x2120）。
3. JLFS 校验链:
   - `app.bin` 数据 CRC16 → 写 `0x2022`
   - `crc16(dec[0x2022:0x2040])` → 写 `0x2020`
   - `crc16(dec[0x2020:0x2020+0x30C7A])` → 写 `0x2002`
   - `crc16(dec[0x2002:0x2020])` → 写 `0x2000`
4. 重新加密并写回 `[0x2000:0x33CBA]`，`[0x33CBA:0x34000]` 填 `0xFF`。
5. `crc16(flash 镜像 212,992B)` → UFW 条目 0 `+0x04`（条目需先用 KS 解扰再重加扰）。
6. UFW 列表 CRC = `crc16(raw[0x40:0x40+8*0x50])`（**原始加扰字节**）。
7. UFW 主头 CRC = `crc16(解扰后的 header[2:0x40])`。

CRC: `jl_crc16` = CRC-16-CCITT(poly 0x1021, init 0)；`jl_crc32` init 0x26536734。

## 9. PnP ID 的真实构造方式（关键）

- `0x223BC` 的 4 字节 `11 01 00 03` 是 **HID Information**（独立读分支，长度 4，
  `0x1E04676: r6=4` 后从 `0x1E224DC` 拷贝），**不是 PnP ID**。
- PnP ID 读分支在 `0x1E045B4`，调用辅助函数 `0x1E04272`，按模式/配置
  (`h[0x2D12]`) 动态生成 VID/PID，常量含:
  - `0x045E` (Microsoft VID)
  - `0x0B13` / `0x02FD` / `0x028E` / `0x02EA` (Xbox 系列 PID)
- 因此 v1.0/v1.5 在 `0x223C0` 打 6 字节 PnP 补丁是**错位**的（`71` 被当成 source，
  VID/PID 实际未变），v1.7 干脆放弃；这也解释了“改了没用/改坏蓝牙”的现象。
- 要让 Kindle 的 `input_manager` 认到 `0171:0443`，需要在 **PnP 构造路径**上为
  reader/keyboard 模式返回 Amazon VID/PID，而不是改静态字符串。

## 10. HID 键表类型与 Consumer 发送路径（v20 新增核验）

- 键表条目 6 字节: `[type, b1, usage_lo, usage_hi, b4, b5]`
  - type 2 = Keyboard
  - type 3 = Mouse
  - type 4 = Consumer
- 聚合函数 VMA `0x1E0C818` 遍历 32 个条目:
  - `type = entry[0] & 7`
  - type 4 分支 VMA `0x1E0C8E4`
  - 把 `entry[2..3]` 写入输出 `+28`，`entry[4..5]` 写入输出 `+30`
- 发送路径 VMA `0x1E0E89E` 读取输出 `+28/+30`，构造:
  `[05, usage_lo, usage_hi, 00, 00]` → Consumer Report ID 5。
- 因此亮度必须使用:
  - 亮度-: `04 00 70 00 00 00`
  - 亮度+: `04 00 6f 00 00 00`
- 键盘页 `0x6F/0x70` 不是 Kindle 的亮度键。

## 11. v21 复合键盘+Consumer（实机 v20 失败后）

- v20 使用独立 Consumer Report ID 5，实机亮度仍无效。
- v21 把 Consumer usage 放入键盘 Report ID 2 的同一 collection:
  - 新描述符 87 B，位于 app.bin `0x31940` / VMA `0x1E31A60`
  - 键盘输入: modifier + reserved + 4 keys + 16-bit Consumer usage
  - 输入仍为 8 字节，输出仍为 1 字节
- 键表仍为 type 4 `04 00 70/6f 00 00 00`。
- Consumer 分支写入聚合缓冲 `+38`（键盘 key slot 5/6），
  由原有键盘报告构造器原样复制到 Report ID 2。
- 描述符选择器: 长度 `0xABBE` 69 -> 87；指针 `0xABC2` ->
  `0x1E31A60`。
- 审计: `tools/audit_v21.js`，含 HID 描述符解析，全部 PASS。
