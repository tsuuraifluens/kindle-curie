# v21b 候选固件 (复合键盘+Consumer, app.bin 内描述符)

- 文件: `IINE_Kindle_Click_v21b_Composite.ufw`
- 大小: 645,184 B
- SHA-256:
  `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C`
- 基线: 原厂 `L1161 L1162.ufw`
  (`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`)

v21 的描述符放在 app.bin 之外，官方升级程序拒绝识别。
v21b 把同一个 87 字节复合描述符移到 app.bin 内部 `0x23330`
(原厂 135 字节全 0 对齐空隙)，重新被 app.bin 数据 CRC 覆盖。

## 修改
- v19/v20 全部身份与翻页补丁
- 键盘 Report ID 2 复合报告: modifier + reserved + 4 keys +
  16-bit Consumer usage
- 左右键仍为 Consumer 0x0070 / 0x006F

## 刷写
官方 `L1161 固件升级程序_1.4.40.exe`，人工选择本文件。
Codex 不自动刷写。刷前确认工作区外有原厂 UFW 备份。

## 刷后
Kindle 上忽略/删除旧遥控器 → 重新配对 → 测试翻页和左右亮度。
