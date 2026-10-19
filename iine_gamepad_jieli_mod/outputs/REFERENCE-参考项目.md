# 参考项目元数据

克隆时间: 2026-10-19 (Asia/Shanghai)

| 仓库 | HEAD | 许可证 | 用途 |
|---|---|---|---|
| Flipper-Zero-Kindle-Click | a8cfd74c5b8ea52f8bb59e653ab05dd03472a7f0 | MIT | Flipper Zero Kindle Click 实现 |
| Open-Kindle-Click-Android | 7abcb07fa8924981f7388851831eca92e258883f | Apache-2.0 | Android BLE HID 实现 |
| Open-Kindle-Click-nRF52840 | 3f2b8ecc4fc43a4c3627d8181a66ad471fa9fafa | MIT | XIAO nRF52840 固件 + PROTOCOL.md |
| fw-AC63_BT_SDK (GitHub 镜像) | fdc018de81629e21e594d677cf05f87d288be0f0 | 杰理 SDK 许可(见仓库 LICENSE) | 路线 B 基础 SDK (apps/hid + cpu/bd19 工具链) |

kindlemodding.org: analysis/reference/kindlemodding-pageturner.html/.txt (2026-10-19 抓取)

关键文件:
- Open-Kindle-Click-nRF52840/PROTOCOL.md — 协议权威说明 (Kindle 5.20.1 逆向 + 实测)
- OpenKindleClick.ino / kc_profile.c / KindleClickPeripheral.java — 三实现源码
- fw-AC63_BT_SDK/apps/hid/modules/bt/ble_hogp.c — JLBTSDK HOGP profile
- fw-AC63_BT_SDK/apps/hid/examples/keypage/app_keypage.c — 翻页器示例
- fw-AC63_BT_SDK/cpu/bd19/tools/isd_config_rule.c — 配置规则(已用于解码原厂配置)
- fw-AC63_BT_SDK/doc/datasheet/AC632N/ — AC632N 全系列数据手册