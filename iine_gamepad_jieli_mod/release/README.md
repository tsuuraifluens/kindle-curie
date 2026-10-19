# Release 目录索引

所有 UFW 均为 645,184 B。原厂基线 SHA-256:
`73005E7C885AAB2A644ADFDF2DBADC01802B34427E9E6FEBF823F50D4D7745E9`

完整哈希清单见 `SHA256SUMS.txt`。

## 推荐使用

| 用途 | 文件 | SHA-256 |
|---|---|---|
| 稳定键位版（A/B/方向键/亮度正常） | `IINE_Kindle_Click_v22s_keymap.ufw` | `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC` |
| A+B 熄屏实验版 | `IINE_Kindle_Click_v23_ABchord.ufw` | `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879` |
| 旧键位全功能参考 | `IINE_Kindle_Click_v21b_Composite.ufw` | `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C` |

## 全部候选

| 文件 | 状态 |
|---|---|
| `IINE_Kindle_Click_v18_PnP.ufw` | 已实机，按键无效 |
| `IINE_Kindle_Click_v19_PnPdirect.ufw` | 已实机，翻页正常、亮度无效 |
| `IINE_Kindle_Click_v20_Consumer.ufw` | 已实机，亮度仍无效 |
| `IINE_Kindle_Click_v21_Composite.ufw` | 工具拒绝识别 |
| `IINE_Kindle_Click_v21b_Composite.ufw` | 已实机全功能正常（旧键位） |
| `IINE_Kindle_Click_v21c_Composite.ufw` | 中间实验 |
| `IINE_Kindle_Click_v21d_Composite.ufw` | 中间实验 |
| `IINE_Kindle_Click_v22_ABchord.ufw` | 描述符指针错误，已废弃 |
| `IINE_Kindle_Click_v22s_keymap.ufw` | 已实机键位正常 |
| `IINE_Kindle_Click_v23_ABchord.ufw` | 待实机（A+B 熄屏） |

## 刷写

- Codex 不自动刷写。
- 使用官方 `L1161 固件升级程序_1.4.40.exe`，人工选择文件。
- 刷前确认工作区外原厂 UFW 备份存在。
- 刷后 Kindle 忽略旧设备并重新配对。

## 回退

- A+B 实验失败 → 回退 `IINE_Kindle_Click_v22s_keymap.ufw`。
- 键位/功能异常 → 回退 `IINE_Kindle_Click_v21b_Composite.ufw`。
- 启动/蓝牙异常 → 官方工具刷回原厂 `L1161 L1162.ufw`。
