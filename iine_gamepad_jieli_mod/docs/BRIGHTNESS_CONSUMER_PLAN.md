# 亮度 Consumer 路径分析

状态: **已确认静态路径** (2026-10-19)

## 描述符
- 键盘: app.bin `0x2260A`, 69 B, Report ID 2。
- Consumer: app.bin `0x22690`, 25 B, Report ID 5,
  Report Size 16, Report Count 2 (两个 usage)。

## 选择器
VMA `0x1E0AC9C`，bit 索引:
- 8 -> 键盘 (69 B)
- 9 -> 鼠标 (66 B)
- 11 -> vendor (205 B)
- 12 -> Consumer (25 B)
- 15 -> Reader 复合描述符 (141 B)

## 键表格式
6 字节条目: `[type, b1, usage_lo, usage_hi, b4, b5]`
- type 2 = Keyboard
- type 3 = Mouse
- type 4 = Consumer

Consumer 例: `04 00 CD 00 00 00` = usage 0x00CD。

## 聚合与发送路径
1. VMA `0x1E0C818` 遍历 32 个键表条目。
2. `type = entry[0] & 7`; type 4 -> 分支 VMA `0x1E0C8E4`。
3. Consumer 分支:
   - `h[out+28] = h[entry+2]` (第一个 16-bit usage)
   - `h[out+30] = h[entry+4]` (第二个 16-bit usage)
4. VMA `0x1E0E89E` 附近读取聚合缓冲 `+28/+30`，构造:
   `[05, usage_lo, usage_hi, 00, 00]`
   即 Report ID 5 的 Consumer 报告。

## v20 采用
```
左 (亮度-): 04 00 70 00 00 00
右 (亮度+): 04 00 6f 00 00 00
```
对应 Consumer 0x0070 / 0x006F。

## 未验证
- 当前键盘模式的 Report Map 是否包含 Consumer Report ID 5。
- Kindle 是否对独立 Consumer 报告启用长按重复 (EV_REP)。
  Kindle Click 参考实现把 Consumer 与键盘放在同一 collection，
  本固件是独立报告；若只能单步调节，需要在固件内做长按重复。
