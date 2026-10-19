# 候选固件 v22s (安全键表版)

日期: 2026-10-19
基线: v21b (已实机全功能正常)

## 背景
v22 加入 A+B 组合键 trampoline 后，连接正常但所有按键失效。
进一步对比发现：v22/v22s 早期版本把描述符选择器指针误写成
`0x1E31A60`，而实际描述符在 `0x23330` / VMA `0x1E23450`，
导致 Report Map 为空/无效，所有按键失效。v22s 已修正为
`0x1E23450`，并完全移除代码补丁，只改键表。

## 映射

| 按键 | 功能 |
|---|---|
| A | 上一页 (PageUp 0x4B) |
| B | 下一页 (PageDown 0x4E) |
| 方向键上 | 下一页 (PageDown 0x4E) |
| 方向键下 | 上一页 (PageUp 0x4B) |
| 方向键左 | 亮度 - (Consumer 0x0070) |
| 方向键右 | 亮度 + (Consumer 0x006F) |

**A+B 熄屏未实现**。A+B 会同时产生 PageUp/PageDown，不会发 Power。

## 与 v21b 的差异
- 只改 Table1 键表条目。
- 描述符、选择器、Consumer 写入偏移全部保持 v21b 原样。
- 无 trampoline、无代码入口重定向。

## 文件
- `outputs/IINE_Kindle_Click_v22s_keymap.ufw`
- 大小: 645,184 B
- SHA-256:
  `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC`

## 审计
`node tools/audit_v22s.js` 全部 PASS:
UFW/JLFS CRC、键表、描述符解析、app 区外零修改、尾部 0xFF。

## 用途
先刷 v22s 恢复按键，确认 A/B/方向键/亮度全部正常。
A+B 熄屏需要另做可靠方案（现有宏/组合键机制，或真正的 .text
code cave），不能在未验证的 rodata/旧描述符槽位里执行代码。
