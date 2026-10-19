# 候选固件 v21 (复合键盘+Consumer) 报告

日期: 2026-10-19

## 背景
v20 把左右键改成 Consumer type 4 后，实机亮度仍无效；翻页正常。
说明 Kindle 没有接受独立的 Consumer Report ID 5 路径。

参考项目 `Open-Kindle-Click-nRF52840` 的结论是：Consumer usage 必须
和键盘 usage 在**同一个 application collection / 同一个 Report ID**
里，Linux 才会在同一个 evdev 节点上同时提供键盘和亮度键，并启用
EV_REP。

## v21 结构
把键盘 Report ID 2 改为复合报告，输入仍为 8 字节：

| 字节 | 内容 |
|---|---|
| 0 | 8 个 modifier 位 |
| 1 | reserved |
| 2-5 | 4 个键盘 key |
| 6-7 | 16-bit Consumer usage |

输出报告仍为 1 字节 (3 个 LED 位 + 5 个 padding 位)。

描述符长度 87 字节，放在 app 区尾部空白 VMA `0x1E31A60`
(app.bin 偏移 `0x31940`)。原厂该区域为全 0。

## 补丁

| app.bin 偏移 | 原值 | 新值 | 说明 |
|---|---|---|---|
| 0xABBE | `50 74` | `70 75` | 键盘描述符长度 69 -> 87 |
| 0xABC2 | `c1 ff 2a 27 e2 01` | `c1 ff 60 1a e3 01` | 指针 -> 0x1E31A60 |
| 0xC7C6 | `e8 58` | `38 59` | Consumer usage1 写到聚合缓冲 +38 |
| 0x31940 | 00... | 87B 复合描述符 | 新键盘+Consumer 描述符 |

键表仍保留 v20 的 type 4:
```
左: 04 00 70 00 00 00
右: 04 00 6f 00 00 00
```

聚合函数把 usage1 写到 `+38/+39`，正好是键盘报告的第 5/6 个 key
字节；键盘报告构造器原样复制 6 个 key 字节，因此报告第 6-7 字节
就是 Consumer usage。

## 描述符结构校验
`tools/audit_v21.js` 内置 HID 描述符解析:
- Report ID = 2
- Input = 64 bits = 8 bytes
- Output = 8 bits = 1 byte
- 包含 Consumer Usage Page (0x0C)

## 构建与审计
- 产物: `outputs/IINE_Kindle_Click_v21_Composite.ufw`
- 大小: 645,184 B
- SHA-256:
  `F8FBAC88DFB645DB243A733402B44B55DB356508D822A5612CC2BD75C499A5BD`
- `node tools/audit_v21.js` 全部 PASS:
  UFW/JLFS CRC、预期补丁、描述符结构、app 区外零修改、尾部 0xFF。

## 与 v20 的差异
- 两条键表条目相同
- 新增: 键盘描述符长度/指针、Consumer 写入偏移、87B 描述符
- 其余 v19/v20 身份与翻页补丁不变

## 刷写
官方工具人工刷写，Codex 不自动写入。

刷后必须:
1. Kindle 上忽略/删除旧遥控器。
2. 重新配对，强制重读 Report Map。
3. 测试 A/B、方向键上下翻页。
4. 测试左右亮度 (+/-)，以及按住是否连续调节。

## 失败分支
- 亮度单步有效但不连续: 在固件内做长按重复发送。
- 亮度完全无效: 需要抓取实际 Report Map/报告，确认 Kindle 是否
  收到 Report ID 2 的第 6-7 字节。
- 翻页失效: 回退 v19/v20，检查描述符解析。
