# 候选固件 v21b (复合键盘+Consumer, app.bin 内描述符)

日期: 2026-10-19

## 为什么有 v21b
v21 把 87 字节复合描述符放在 app.bin 之后的尾部空白
(`0x31940` / VMA `0x1E31A60`)。官方升级程序拒绝识别该 UFW。
最可能原因是工具对 app.bin/app_area 的文件级校验不接受
app.bin 之外的改动。

v21b 把同一个描述符移到 **app.bin 内部** `0x23330`
(VMA `0x1E23450`)，该处是原厂 135 字节全 0 对齐空隙，
且没有被代码引用。这样描述符重新被 app.bin 数据 CRC 覆盖。

## 结构
键盘 Report ID 2 复合报告，输入 8 字节:

```
modifier(1) + reserved(1) + 4 keys(4) + Consumer usage(2)
```

输出 1 字节 (3 LED + 5 padding)。描述符 87 字节。

## 补丁

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `70 75` | 键盘描述符长度 69 -> 87 |
| 0xABC2 | `c1 ff 2a 27 e2 01` | `c1 ff 50 34 e2 01` | 指针 -> 0x1E23450 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 -> 聚合 +38 |
| 0x23330 | 00... | 87B | 复合描述符 (app.bin 内) |

键表保留:
```
左: 04 00 70 00 00 00
右: 04 00 6f 00 00 00
```

## 构建与审计
- 产物: `outputs/IINE_Kindle_Click_v21b_Composite.ufw`
- 大小: 645,184 B
- SHA-256:
  `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C`
- `node tools/audit_v21b.js` 全部 PASS:
  UFW/JLFS CRC、预期补丁、描述符解析 (Report ID 2 / Input 8B /
  Output 1B / Consumer page)、app 区外零修改、尾部 0xFF。

## 刷写
官方工具人工刷写，Codex 不自动写入。

刷后:
1. Kindle 忽略/删除旧遥控器。
2. 重新配对。
3. 测试 A/B、方向键上下翻页。
4. 测试左右亮度及长按连续调节。

## 若工具仍拒绝
说明工具还校验了其他字段 (例如 app_area_head、p11 或文件级
哈希)。下一步应对比 v20 (可识别) 与 v21b 的 UFW 头部/条目字段，
而不是继续盲刷。
