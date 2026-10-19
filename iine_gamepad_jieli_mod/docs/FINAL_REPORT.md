# 最终总结

日期: 2026-10-19

## 当前结论

良值 L1161/L1162 手柄已经可以：
- 被 Kindle 识别为 Kindle Click 兼容遥控器
- 正常连接/重连
- A/B、方向键翻页
- 方向键左右调节亮度

当前**已知可用**的键位版是 v22s；A+B 熄屏版 v23 已构建，待实机。

## 已完成

1. 芯片识别：杰理 **AC6321A**（AC632N/BD19，q32s，73KB SRAM；
   丝印 `BP3F723-21A4`）。
2. UFW/SFC 解扰与重打包管线：与参考 v1.7 逐字节一致。
3. Kindle 身份：GAP `Kindle-PT`、DIS 厂商 `Amazon`、
   ADV `05 FF 71 01 04 43`、PnP `0x0171/0x0443`。
4. HID 逆向：键盘 Report ID 2、Consumer 字段、type 4 路径、
   聚合缓冲 +38 写入、键表 index/offset 全表。
5. v21b：87B 复合键盘+Consumer 描述符，实机连接/翻页/亮度全部正常。
6. v22s：按新键位映射，实机全部正常。
7. v23：在 v22s 基础上增加 A+B 组合键熄屏，已通过离线审计。

## 当前文件

| 用途 | 文件 | SHA-256 |
|---|---|---|
| 稳定键位版 | `release/IINE_Kindle_Click_v22s_keymap.ufw` | `2E517183D309BB2754DD30C6B056F0F6FC05A83335AADB2D82AE88C5E7DDB5FC` |
| A+B 实验版 | `release/IINE_Kindle_Click_v23_ABchord.ufw` | `F4B18CD49CF547CADBE465F2C0F1937495761FF72A4D6F84E2E595CE280A6879` |
| 旧键位参考 | `release/IINE_Kindle_Click_v21b_Composite.ufw` | `29FDADE0BE86BA0AFD27569970D70620EBCBD8A7CE9DB54E920EEDBF9945729C` |

## 尚未完成

- v23 的 Kindle 实机 A+B 熄屏验证。
- A+B 松开不同步时的误翻页/重复 Power 处理。
- 任意键开机的电源/唤醒层改造（当前只有配对键能开机）。
- 电池电量服务的完整实机验证。

## 剩余风险

- 唯一测试硬件；官方工具整片擦除；必须保留工作区外原厂备份。
- 电源管理改动（任意键开机）尚未实施，需硬件/GPIO 证据。
- 不宣称“所有 Kindle 型号完全兼容”；当前验证目标是
  Kindle Paperwhite 12 / 5.20.1。

## 索引

- `README.md`：项目总览
- `docs/PROJECT_INDEX.md`：文件索引
- `docs/RESULTS.md`：版本结果矩阵
- `docs/PROJECT_STATUS.md`：当前阶段
- `release/README.md`：发布固件索引
