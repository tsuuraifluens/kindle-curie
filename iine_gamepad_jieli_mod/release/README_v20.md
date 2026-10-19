# v20 候选固件

- 文件: `IINE_Kindle_Click_v20_Consumer.ufw`
- 大小: 645,184 B
- SHA-256:
  `CCAE44F3DFC0D310C61F51A84B93D19D47BC9CAD6C9EF4294A2F504E12F7A519`
- 基线: 原厂 `L1161 L1162.ufw`
  (`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`)

## 修改
- v19 全部补丁 (Kindle 身份/PnP/翻页)
- 方向键左右改为 Consumer 0x0070 / 0x006F (亮度)

## 刷写前
推荐先用 nRF Connect 读当前 v19 的 HID Report Map (`0x2A4B`)，
确认包含 `05 0C` 和 `85 05`。若没有，先不要刷。

## 刷写
官方 `L1161 固件升级程序_1.4.40.exe`，人工选择本文件。
Codex 不自动刷写。刷前确认工作区外有原厂 UFW 备份。

## 刷后
Kindle 上忽略/删除旧遥控器 → 重新配对 → 测试翻页和左右亮度。
