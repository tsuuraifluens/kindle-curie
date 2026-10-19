# 候选固件 v21c (67B 复合键盘+Consumer，无输出报告)

日期: 2026-10-19

## 背景
- v21b 能刷入，但无法与 Kindle 建立连接。
- v21b 的复合描述符为 87B，比原厂键盘描述符 69B 长 18B，并保留
  LED 输出报告。
- 连接失败可能来自 Report Map 长度上限、输出报告声明，或描述符结构。

## v21c 修改
保留“键盘 + Consumer 同一 Report ID 2/collection”的结构，但:
- 去掉 LED 输出报告 (原描述符最后 20B)。
- 描述符从 87B 降为 **67B**，比原厂 69B 还短 2B。
- 输入报告仍为 8 字节:
  `modifier + reserved + 4 keys + 16-bit Consumer usage`。
- 键表仍为 type 4 `04 00 70/6f 00 00 00`。
- Consumer usage 仍写到聚合缓冲 `+38` (键盘 key slot 5/6)。

描述符:
```
05 01 09 06 a1 01 85 02
05 07 19 e0 29 e7 15 00 25 01 75 01 95 08 81 02
95 01 75 08 81 01
95 04 75 08 15 00 26 ff 00 05 07 19 00 2a ff 00 81 00
05 0c 15 00 26 ff 03 19 00 2a ff 03 75 10 95 01 81 00
c0
```

## 补丁

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `30 74` | 键盘描述符长度 69 -> 67 |
| 0xABC2 | `c1 ff 2a 27 e2 01` | `c1 ff 50 34 e2 01` | 指针 -> 0x1E23450 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 -> 聚合 +38 |
| 0x23330 | 00... | 67B | 复合描述符 (app.bin 内) |

## 构建与审计
- 产物: `outputs/IINE_Kindle_Click_v21c_Composite.ufw`
- 大小: 645,184 B
- SHA-256:
  `B0EA4DD6C0A03AE0C0BF9A22E8FFF9EC54687BA88D7E7D856D8C490301931214`
- `node tools/audit_v21c.js` 全部 PASS:
  UFW/JLFS CRC、预期补丁、描述符解析 (Report ID 2 / Input 8B /
  Output 0B / Consumer page)、app 区外零修改、尾部 0xFF。

## 用途
这是用来区分“长度/输出报告问题”和“复合 Consumer 结构问题”的
最小版本:
- 若 v21c 能连接: v21b 的失败来自 Report Map 长度或输出报告。
- 若 v21c 仍不能连接: 问题在复合 Consumer 字段或描述符落点，
  需要回退 v20 并抓取实际 Report Map/广播数据。

## 刷写
官方工具人工刷写，Codex 不自动写入。刷后 Kindle 忽略旧设备后
重新配对，测试连接、翻页、左右亮度。
