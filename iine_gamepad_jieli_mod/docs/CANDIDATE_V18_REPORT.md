# 候选固件 v18 (PnP ID 修正) 说明

## 文件
- `release/IINE_Kindle_Click_v18_PnP.ufw`
- 大小: 645,184 字节（与原厂一致）
- SHA-256: `AA6E8986E174203B51D8192B28B86B1E053214CCFA07BEB771EC1317D70549B0`

## 为什么做这个版本
实测反馈：v1.7 能正常连接 Kindle，但所有按键无效。

根因判断：Kindle 的 `input_manager` 按 uhid 上报的 VID/PID 匹配
`/etc/input_manager/devices.json` 的 `0171:0443`。原厂 PnP ID 是运行时由
`0x1E04272` 构造的（Microsoft `0x045E` + Xbox PID），不匹配 → 设备连接后按键被
`capability_filter` 全部丢弃。

## 补丁清单（app.bin 偏移）
| # | 名称 | 偏移 | 原始 | 新值 |
|---|---|---|---|---|
| 1 | PnP VID | 0x4168 | `04 fb 5e 04` (0x045E) | `04 fb 71 01` (0x0171) |
| 2 | PnP PID (r0=1) | 0x4174 | `00 fb fd 02` (0x02FD) | `00 fb 43 04` (0x0443) |
| 3 | PnP PID (r0=0) | 0x417A | `00 fb 13 0b` (0x0B13) | `00 fb 43 04` (0x0443) |
| 4 | PnP PID (other) | 0x4180 | `00 fb 8e 02` (0x028E) | `00 fb 43 04` (0x0443) |
| 5 | PnP +0x5C 分支 | 0x4188 | `c0 85` | `00 00` (NOP) |
| 6 | GAP 名 (reader) | 0x211C6 | `IINE_reader` | `Kindle-PT` |
| 7 | GAP 名 (keyboard) | 0x211EB | `IINE_keyboard` | `Kindle-PT` |
| 8 | DIS 厂商名 | 0x22384 | `zhuhai_jieli` | `Amazon` |
| 9 | 广播 PID 字符串 | 0x22EF0 | - | `04 43 00` |
| 10 | adv_setup_init 指针 | 0x4D36 | - | `c4 ff 10 30 e2 01` |
| 11 | adv Company ID 低/高 | 0x4D58 / 0x4D5E | `0x41` | `0x71` / `0x01` |
| 12 | adv 厂商数据长度 | 0x4D90 | `r6=9` (`96 70`) | `r6=4` (`46 70`) |
| 13 | 模式重定向 | 0x712E | `IINE_reader` | `IINE_keyboard` |
| 14-21 | Table1 键位 | 0x2289C 起 | 见下 | 见下 |

Table1 最终映射（Report ID 2）:
- A → `0x4E` PageDown；B → `0x4B` PageUp
- X/Y → `0x66` Power
- D-pad 上/下 → `0x4B/0x4E`
- D-pad 左/右 → `0x70/0x6F` Brightness Down/Up

## 静态校验（全部通过）
- 文件大小 645,184；`uboot.boot`(4831B) 与 `isd_config.ini`(122B) 与原厂逐字节相同。
- UFW 条目 0 数据 CRC = `0x4A29`（已写入）。
- UFW 列表 CRC = `0x800A`；主头 CRC = `0x420D`（复算一致）。
- JLFS：app.bin 数据 CRC、app.bin 条目头 CRC、app_area_head 数据/头 CRC 全部复算一致。
- 解密后确认 PnP 构造常量、GAP/DIS/ADV/按键表补丁均已生效。

## 刷写（必须人工执行，Codex 不会自动刷写）
1. 先确认原厂恢复材料可用：原厂 `.ufw` + 官方升级程序 `1.4.40`。
2. 打开官方升级程序，选择 `IINE_Kindle_Click_v18_PnP.ufw`。
3. 勾选“擦除整个FLASH”和“成功后复位”。
4. 手柄开机 → 插 USB → 按住“配对+B”2 秒进入升级模式（显示 ONLINE）。
5. 点击升级，等待成功。
6. 刷完后先在 Kindle 上“忽略/删除”旧设备记录，再重新扫描配对。

## 恢复
- 官方升级程序 + 原厂 `L1161 L1162 IINE-reader.ufw` 即可恢复。
- 若应用损坏但 Bootloader 正常：同上，无需拆机。
- 若 Bootloader 也损坏：短接 SPI NOR 的 CS 到 GND 约 0.5 秒进入芯片 Mask ROM，
  再用官方工具刷原厂包（该路径来自逆向报告，未在本机实测）。

## 风险与未验证项
- 本候选**未在真机刷写/测试**；合法性来自静态校验与对已验证样本的复现。
- PnP 构造函数可能被多种模式共用，改常量会影响其他模式（如 Xbox 模式）。
- 若 PnP 构造的 early-return 条件在 reader/keyboard 模式下不成立，VID/PID 可能仍不生效。
- 若刷后仍按键无效，下一步应抓取 HID 报告（Report ID / usage）并检查
  `input_manager` 的 `devices.json` 匹配结果。
