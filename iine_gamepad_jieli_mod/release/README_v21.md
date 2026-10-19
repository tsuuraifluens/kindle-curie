# v21 候选固件 (复合键盘+Consumer)

- 文件: `IINE_Kindle_Click_v21_Composite.ufw`
- 大小: 645,184 B
- SHA-256:
  `F8FBAC88DFB645DB243A733402B44B55DB356508D822A5612CC2BD75C499A5BD`
- 基线: 原厂 `L1161 L1162.ufw`
  (`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`)

## 修改
- v19/v20 全部补丁
- 键盘 Report ID 2 改为复合报告: modifier + reserved + 4 keys +
  16-bit Consumer usage
- 左右键仍为 Consumer 0x0070 / 0x006F

## 刷写
官方 `L1161 固件升级程序_1.4.40.exe`，人工选择本文件。
Codex 不自动刷写。刷前确认工作区外有原厂 UFW 备份。

## 刷后
Kindle 上忽略/删除旧遥控器 → 重新配对 → 测试翻页和左右亮度。
