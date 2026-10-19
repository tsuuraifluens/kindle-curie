# 参考项目协议分析

## 项目清单与状态
| 项目 | 许可证 | 实现语言/平台 | 证据强度 |
|---|---|---|---|
| Open-Kindle-Click-nRF52840 | MIT | C++ / Seeed XIAO nRF52840 | 最强: PROTOCOL.md 基于 Kindle 5.20.1 二进制逆向 + Paperwhite 12 实测 |
| Flipper-Zero-Kindle-Click | MIT | C / Flipper Zero | 强: 手写 GATT 服务表, 实测 |
| Open-Kindle-Click-Android | Apache-2.0 | Java / Android | 强: 自研 BLE HID 库 |
| kindlemodding.org pageturner | Wiki (CC?) | 文档 | 佐证: /etc/input_manager/devices.json 与按键映射 |

## 关键字段逐项来源
| 字段 | 值 | 位置 | 来源 | 实测 |
|---|---|---|---|---|
| Company ID | 0x0171 (LE) | 广播 0xFF 前 2 字节 | A/B | 是 |
| Product ID | 0x0443 (BE) | 广播 0xFF 后 2 字节 | A/B | 是 |
| Flags | 0x06 | 广播 0x01 | A/B | 是 |
| PnP ID | 01 71 01 43 04 vv vv | DIS 0x2A50 | A/B | 是 |
| HID 服务 | 0x1812 | GATT | A/B | 是 |
| Battery | 0x180F / 0x2A19 | GATT | A/B | 是 |
| Report Map | 10 字节键盘+consumer 混合 | HID | A/B/C | 是 |
| 前进 usage | 0x4E | 键盘报告 | A/B | 是 |
| 后退 usage | 0x4B | 键盘报告 | A/B | 是 |
| 亮度+ usage | 0x006F | consumer | A/B/C | 是 |
| 亮度- usage | 0x0070 | consumer | A/B/C | 是 |
| 睡眠 usage | 0x0030 | consumer | A/B/C | 是 |
| 长按睡眠时长 | 1s (参考实现) | 遥控器侧 | B | 参考实现自定 |
| 广播间隔 | 20ms 快 / 152.5ms 慢 | GAP | A/B | 是 |

## 实现差异总结
- 三者协议字段完全一致, 差异仅在: 厂商数据放 ADV_IND 的技巧、是否分发 IRK、名称有无、长按/重连策略细节。
- 没有一处字段是三实现相互矛盾的, 交叉验证充分。

## 再利用条件
- 均为宽松许可 (MIT / Apache-2.0), 允许商用与修改, 需保留版权声明。
- PROTOCOL.md 是分析文档, 移植时应引用来源并保留作者署名。
- nRF52840/Flipper 实现可整体作为移植蓝本或协议测试参照。