# 技术路线可行性比较与建议 (更新: 已选定路线 B)

## 决策
用户已选定 **路线 B (基于 fw-AC63_BT_SDK 重建)**。SDK 审查确认该路线源码级可行。

## 路线 B 可行性确认清单
| 要求 | SDK 支持 | 证据 |
|---|---|---|
| BLE 广播 Flags 0x06 | 是 | GAP adv 配置 |
| 广播厂商数据 71 01 04 43 (ADV_IND) | 是 | `BLUETOOTH_DATA_TYPE_MANUFACTURER_SPECIFIC_DATA` API |
| 不广播 0xFE03 | 是(由应用控制) | 应用自定义 adv 内容 |
| HOGP + 自定义 Report Map | 是 | `apps/hid/modules/bt/ble_hogp.c`, `le_hogp_set_ReportMap` |
| DIS PnP ID / Firmware Rev | 是 | DIS profile 标准支持 |
| BAS 电池服务 | 是 | SDK battery service |
| 按键扫描/去抖/长按 | 是(应用层自写) | examples/gamebox/keyboard |
| 断连重广播/低功耗 | 是 | `le_hogp_set_reconnect_adv_cfg` + power manage |
| 升级格式兼容 | 是(原生) | 同一 isd_download/ufw_maker 管线, uboot 沿用 SDK 自带 |

## 与路线 A/C/D 的最终对比 (要点)
- A (补丁原厂): 仍不推荐 — 代码区加扰 + 无符号, 且无法在无 pi32 反汇编能力下安全定位广播/GATT。
- C (最小修改): 不成立 — Kindle Click 身份是编译期结构。
- D (协处理器): 保留为应急回退 (若拆机后发现 GPIO/封装与 SDK 匹配困难或 flash 规格异常)。

## 剩余风险 (路线 B)
- 子型号/flash 不匹配 → 镜像不兼容 (防呆检查会拒绝, 相对安全)。
- 按键 GPIO 错误 → 按键失灵, 但可重刷恢复。
- 工具链版本与 SDK 2.3.0 不匹配 → 编译问题, 需对应用户手册的版本说明。
- 唯一设备 + 恢复未实测 → 刷写前必须实测原厂恢复。

## 结论
路线 B 可行且风险可控, 前提是补齐: 子型号、GPIO、工具链、恢复实测 (见 docs/ROUTE_B_PLAN.md)。