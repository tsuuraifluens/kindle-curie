# 候选固件 v19 (PnP 直写) 说明

## 背景
实测: 手柄连电脑翻页正常，连 Kindle 无反应。
说明 HID 报告/键表/模式全部正确，唯一卡点是 Kindle 侧的 VID/PID 归类。

## 为什么 v18 没生效
PnP 构造函数 `0x1E04272` 有前置早退条件（依赖运行时 `h[0x2D12]`），
若条件不满足则不会写入 VID/PID，PnP ID 仍为默认值。

## v19 的修改（无条件直写）
在 PnP 读取分支（VMA 0x1E045B4）内直接写常量，绕过构造函数调用:

| app.bin 偏移 | 原指令 | 新指令 | 含义 |
|---|---|---|---|
| 0x44AE | `00 fb 53 43` (r0=0x4353) | `00 fb 71 01` | r0 = 0x0171 |
| 0x44C0 | `00 fc b6 57` (r0 += 0x57B6) | `00 fb 43 04` | r0 = 0x0443 |
| 0x44CE | `41 16` (call PnP helper) | `00 00` (nop) | 不再让 helper 覆盖 |

随后代码把 r0 写入 PnP 值的 VID 位置、`h[sp]` 写入 PID 位置，因此
DIS `0x2A50` 将固定返回 `... 71 01 43 04 ...`（VID 0x0171, PID 0x0443）。

其余保持 v1.7 的全部修改（GAP 名 Kindle-PT、DIS 厂商 Amazon、ADV 0xFF、
模式重定向、Table1 键位）。

## 文件
- `outputs/IINE_Kindle_Click_v19_PnPdirect.ufw`
- 大小 645,184
- SHA-256: `5589B396DD04E5983FD0D6D8A470E824203F5EA3D73BF9E81EED0717F4A11B47`

## 审计（21/21 通过）
- Flash header / boot vector / uboot / isd_config / cfg_tool / p11 / 尾部 padding 与原厂逐字节相同
- UFW 三层 CRC + JLFS 四层 CRC 全部复算一致
- 与 v1.7 的差异仅: 3 处 PnP 补丁 + 对应 CRC
- 指令边界合法，无控制流变化

## 刷写后必须做
1. 在 Kindle 上 **忽略/删除** 该遥控器的旧记录（Kindle 会缓存旧的 PnP ID/设备属性）。
2. 重新扫描并配对。
3. 若仍无效，用 BLE 调试 App 读 DIS `0x2A50`，确认是否已变为 `0171/0443`，
   并把值反馈回来。

## 恢复
官方升级程序 + 原厂 `L1161 L1162 IINE-reader.ufw`。
