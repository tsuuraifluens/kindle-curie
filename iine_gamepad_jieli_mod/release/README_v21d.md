# v21d 候选固件 (67B 复合描述符，原槽位)

- 文件: `IINE_Kindle_Click_v21d_Composite.ufw`
- 大小: 645,184 B
- SHA-256:
  `1BA7B3FC2DED26D22987995E339548F2EA49A56EA489BD82EB273D148132F3D2`
- 基线: 原厂 `L1161 L1162.ufw`
  (`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`)

v21b 能刷入但无法连接。v21d 把 67B 复合描述符直接写回原厂
键盘描述符槽位 `0x2260A`，原指针不变，长度比原厂 69B 还短 2B，
并去掉 LED 输出报告。输入仍为 8 字节:
modifier + reserved + 4 keys + 16-bit Consumer usage。

## 刷写
官方 `L1161 固件升级程序_1.4.40.exe`，人工选择本文件。
Codex 不自动刷写。刷前确认工作区外有原厂 UFW 备份。

## 刷后
Kindle 上忽略/删除旧遥控器 → 重新配对 → 测试连接、翻页、左右亮度。
