# 候选固件 v20 (Consumer 亮度) 报告

日期: 2026-10-19

## 目标
在 v19 (翻页正常) 的基础上，让方向键左右真正产生 Kindle 的
`KEY_BRIGHTNESSUP` / `KEY_BRIGHTNESSDOWN`。

## 根因
v19 把左右键写成键盘页 usage：

```
Left : 02 00 00 70 00 00   (Keyboard page 0x70)
Right: 02 00 00 6f 00 00   (Keyboard page 0x6f)
```

Linux `hid-input` 只把 **Consumer page** 的 `0x006F/0x0070` 映射为
`KEY_BRIGHTNESSUP/DOWN`。键盘页的同名 usage 不是亮度键。

## v20 修改
只改键表两条，类型从 2 (Keyboard) 改为 4 (Consumer)：

| app.bin 偏移 | 原值 (v19) | 新值 (v20) | 含义 |
|---|---|---|---|
| 0x22908 | `02 00 00 70 00 00` | `04 00 70 00 00 00` | Consumer 0x0070 (亮度-) |
| 0x2290E | `02 00 00 6f 00 00` | `04 00 6f 00 00 00` | Consumer 0x006F (亮度+) |

其余 v19 修改全部保留：GAP 名 `Kindle-PT`、DIS 厂商 `Amazon`、
ADV 厂商数据 `05 FF 71 01 04 43`、模式重定向、PnP 直写
VID `0x0171` / PID `0x0443`、A/B/方向键翻页映射。

## 静态证据链 (已核验)
1. 描述符选择函数 VMA `0x1E0AC9C`：
   - bit 8 -> 键盘描述符 (69B, Report ID 2)
   - bit 9 -> 鼠标描述符 (66B, Report ID 4)
   - bit 12 -> Consumer 描述符 (25B, Report ID 5, 两个 16-bit usage)
2. 键表聚合函数 VMA `0x1E0C818`：
   - 对每个 6 字节条目取 `type = entry[0] & 7`
   - `type-1` 索引 TBB，type 4 -> 分支 `0x1E0C8E4`
   - Consumer 分支把 `entry[2..3]` 写入聚合缓冲 `+28`，
     把 `entry[4..5]` 写入聚合缓冲 `+30`
3. 发送路径 VMA `0x1E0E89E` 附近：
   - 读取聚合缓冲 `+28/+30` (即 `sp+232/sp+234`)
   - 构造 `[05, usage_lo, usage_hi, 00, 00]`
   - 即 Consumer Report ID 5

因此 v20 的 `04 00 6f/70 00 00 00` 会走 Consumer Report ID 5，
不会再被当成键盘页 usage。

## 构建
- 源: `analysis/firmware/L1161 L1162.ufw`
- 脚本: `tools/build_v20.js`
- 产物: `outputs/IINE_Kindle_Click_v20_Consumer.ufw`
- 大小: 645,184 B
- SHA-256: `CCAE44F3DFC0D310C61F51A84B93D19D47BC9CAD6C9EF4294A2F504E12F7A519`

## 审计结果 (`tools/audit_v20.js`)
全部通过:
- UFW 大小/条目数/芯片名/总长
- UFW 主头 CRC、条目列表 CRC
- 条目 0 flash 镜像 CRC
- JLFS 四层 CRC (app data / entry header / area / area header)
- 预期补丁字节逐项匹配
- 解密后 app 区无预期外修改
- boot/uboot/isd_config/cfg_tool/p11 等 app 区外原始字节完全一致
- app 区尾部填充为 0xFF
- UFW 容器除 CRC 字段外未改变

与 v19 的差异只有:
- 两条键表条目 (解密后)
- JLFS 两个 CRC 字段
- UFW 条目 0 数据 CRC / 主头 CRC
- 对应加密块的字节

## 风险评估
- **变砖风险: 极低**。未修改启动区、uboot、isd_config、cfg_tool、p11、
  flash 参数或设备唯一数据；只改 app 键表并重算校验。
- **功能风险: 中等**。前提是当前键盘模式的 Report Map 包含 Consumer
  Report ID 5。若未包含，表现应为“翻页仍正常、亮度仍无效”，不会影响启动。
- 未验证: Kindle 实机上 Report Map 是否包含 ID 5；亮度长按是否由
  Kindle 自动重复 (协议参考项目要求 Consumer 与键盘在同一 collection
  才有 EV_REP)。

## 刷写后测试
1. 在 Kindle 上忽略/删除旧遥控器记录 (PnP ID 缓存)。
2. 重新配对。
3. A/B 及方向键上下翻页应保持正常。
4. 方向键左右应调节亮度。
5. 若亮度无效但翻页正常: 反馈即可，下一版改复合描述符
   (键盘 + Consumer 同一 Report ID/collection)。

## 刷写前可选验证 (推荐, 不需要刷写)
当前设备上是 v19。用 nRF Connect / LightBlue 连接手柄，读取:

- Service `0x1812` (HID)
- Characteristic `0x2A4B` (Report Map)

在返回的描述符里查找:
- `05 0C` (Consumer Usage Page)
- `85 05` (Report ID 5)

若两者都存在，说明当前 Report Map 已包含 Consumer ID 5，v20 的
type-4 键表条目应能直接工作。

若没有 `85 05`，不要刷 v20；需要先做复合描述符版本。

## 刷写安全
Codex 不自动刷写。使用官方 `L1161 固件升级程序_1.4.40.exe`，
人工选择 v20 文件并确认。原厂 `L1161 L1162.ufw` 必须保留在工作区外，
SHA-256 `73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`。
