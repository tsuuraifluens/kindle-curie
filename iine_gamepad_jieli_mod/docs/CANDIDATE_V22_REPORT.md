# 候选固件 v22 (A+B 熄屏 + 新按键映射)

> **已废弃，不要刷写。** v22 的描述符选择器指针误写为
> `0x1E31A60`，实际应为 `0x1E23450`，结果连接正常但所有按键
> 失效。A+B 熄屏请使用 `docs/CANDIDATE_V23_REPORT.md` /
> `release/IINE_Kindle_Click_v23_ABchord.ufw`。

日期: 2026-10-19
基线: v21b (已实机全功能正常)

## 目标映射

| 按键 | 功能 | HID |
|---|---|---|
| A | 上一页 | Keyboard 0x4B PageUp |
| B | 下一页 | Keyboard 0x4E PageDown |
| A+B 同时按住 | 熄屏/睡眠 | Consumer 0x0030 Power |
| 方向键上 | 下一页 | Keyboard 0x4E PageDown |
| 方向键下 | 上一页 | Keyboard 0x4B PageUp |
| 方向键左 | 亮度 - | Consumer 0x0070 |
| 方向键右 | 亮度 + | Consumer 0x006F |

## 实现

### 键表

| index | 偏移 | 条目 | 功能 |
|---:|---|---|---|
| 0 | 0x2289C | `02 00 00 4B 00 00` | A -> PageUp |
| 1 | 0x228A2 | `02 00 00 4E 00 00` | B -> PageDown |
| 2 | 0x228A8 | `04 00 30 00 00 00` | 虚拟组合键 -> Consumer Power |
| 3 | 0x228AE | `00 00 00 00 00 00` | Y 禁用 (无 Y 键) |
| 16 | 0x228FC | `02 00 00 4E 00 00` | D-pad 上 -> PageDown |
| 17 | 0x22902 | `02 00 00 4B 00 00` | D-pad 下 -> PageUp |
| 18 | 0x22908 | `04 00 70 00 00 00` | D-pad 左 -> 亮度 - |
| 19 | 0x2290E | `04 00 6f 00 00 00` | D-pad 右 -> 亮度 + |

### A+B 组合键

聚合函数 `0x1E0C818` 前 8 字节改为间接跳转到 trampoline
(VMA `0x1E2272A`, app.bin `0x2260A`，v21b 已不再引用的旧键盘描述符槽位)：

```
r2 = 0x1E2272A
goto r2
```

Trampoline:

```
[--sp] = {rets, r13-r4}
r4 = r1
r2 = r0 & 3
if (r2 != 3) goto body
r0 = 4                 ; 只保留虚拟键 index 2 (bit 2)
body:
r11 = r0
r1 = 0
r3 = 0x1E0C820
goto r3                ; 回到原聚合函数
```

即 A/B 同时按下时，按键掩码被替换为虚拟键 2，原 A/B 翻页被抑制；
虚拟键 2 的键表条目是 Consumer Power 0x0030。

## 构建与审计
- 产物: `outputs/IINE_Kindle_Click_v22_ABchord.ufw`
- 大小: 645,184 B
- SHA-256:
  `C05DF35D4C0252A95DC3A45CC47868A939C79D039780A50A1B2E0C3FE27D02D8`
- `node tools/audit_v22.js` 全部 PASS:
  UFW/JLFS CRC、键表、trampoline、描述符解析、app 区外零修改、
  尾部 0xFF。

## 已知边界
- A+B 的“同时”指同一按键扫描周期内两键都按下。
- 松开时若两键不是同一周期释放，剩余键可能发一次翻页；Kindle
  已休眠时通常忽略。若实测会翻页，v22b 可加组合键锁存。
- Kindle 只能被遥控器睡眠，不能被遥控器唤醒。
- Y 已禁用；无 X/Y 硬件时不会触发。

## 刷写
官方工具人工刷写，Codex 不自动写入。刷后 Kindle 忽略旧设备后
重新配对，测试 A/B、方向键、亮度、A+B 熄屏。
