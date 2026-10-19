# 候选固件 v23 (A+B 熄屏，正确描述符指针)

日期: 2026-10-19
基线: v22s (已实机键位正常)

## 映射

| 按键 | 功能 |
|---|---|
| A | 上一页 |
| B | 下一页 |
| A+B 同时按住 | 熄屏/睡眠 |
| 方向键上 | 下一页 |
| 方向键下 | 上一页 |
| 方向键左 | 亮度 - |
| 方向键右 | 亮度 + |

## 关键修正
v22/v22s 早期版本把描述符选择器指针误写成 `0x1E31A60`，
实际描述符在 `0x23330` / VMA `0x1E23450`，导致 Report Map
无效、所有按键失效。v23 使用正确指针 `0x1E23450`。

## A+B 实现

聚合函数 `0x1E0C818` 入口 8 字节:
```
r2 = 0x1E2272A
goto r2
```

Trampoline 位于 VMA `0x1E2272A` / app.bin `0x2260A`
(v21b 已不再引用的旧键盘描述符槽位):
```
[--sp] = {rets, r13-r4}
r4 = r1
r2 = r0 & 3
if (r2 != 3) goto body
r0 = 4                 ; 虚拟键 index 2
body:
r11 = r0
r1 = 0
r3 = 0x1E0C820
goto r3
```

虚拟键 index 2 的键表条目为 `04 00 30 00 00 00`
(Consumer Power 0x0030)。A+B 时只处理虚拟键 2，抑制 A/B 翻页。

## 文件与审计
- `outputs/IINE_Kindle_Click_v23_ABchord.ufw`
- 大小: 645,184 B
- SHA-256:
  `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879`
- `node tools/audit_v22.js ... v23...` 全部 PASS:
  UFW/JLFS CRC、键表、trampoline、描述符解析、app 区外零修改。

## 已知边界
- A+B 需在同一扫描周期内两键都按下。
- 松开不同步时，剩余键可能发一次翻页；Kindle 已睡眠时通常忽略。
- 若实测误翻页或 Power 重复，可加组合键锁存。
